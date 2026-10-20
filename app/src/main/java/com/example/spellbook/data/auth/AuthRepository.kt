package com.example.spellbook.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Аккаунт пользователя: вход, регистрация и управление сессиями.
 *
 * Главная тонкость — обновление токенов. Refresh-токен одноразовый: если отправить
 * его повторно (два параллельных обновления или повтор после обрыва сети), сервер
 * сочтёт это кражей и завершит сессию устройства. Поэтому:
 * - все обновления идут через один [refreshMutex]: запросы, получившие отказ,
 *   ждут общий результат, а не запускают свои обновления;
 * - новый refresh-токен сохраняется сразу после ответа, до любых других действий;
 * - обновление никогда не повторяется автоматически.
 *
 * @param clock текущее время в миллисекундах; подменяется в тестах.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: TokenStorage,
    private val deviceName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(initialState())

    /** Вошёл ли пользователь. Без аккаунта приложение полностью работает в локальном режиме. */
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun initialState(): AuthState {
        val email = store.email
        return if (store.hasSession && email != null) AuthState.SignedIn(email) else AuthState.SignedOut
    }

    // region Вход и регистрация

    /** Регистрация: на почту уходит код, вход завершает [verifyEmail]. */
    suspend fun register(email: String, password: String): CodeSent =
        api.register(normalize(email), password)

    suspend fun verifyEmail(email: String, code: String) {
        val normalized = normalize(email)
        signIn(api.verifyEmail(normalized, code, deviceName), normalized)
    }

    suspend fun resendVerification(email: String): CodeSent =
        api.resendVerification(normalize(email))

    /**
     * Вход по почте и паролю.
     *
     * @throws AuthException.Api с кодом `EMAIL_NOT_VERIFIED`, если почта не подтверждена:
     * тогда нужно отправить код повторно и показать экран его ввода.
     */
    suspend fun login(email: String, password: String) {
        val normalized = normalize(email)
        signIn(api.login(normalized, password, deviceName), normalized)
    }

    /** Ответ одинаков для любой почты: сервер не раскрывает, есть ли такой аккаунт. */
    suspend fun forgotPassword(email: String): CodeSent = api.forgotPassword(normalize(email))

    /** Новый пароль по коду. Остальные устройства при этом разлогиниваются. */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val normalized = normalize(email)
        signIn(api.resetPassword(normalized, code, newPassword, deviceName), normalized)
    }

    // endregion

    // region Аккаунт

    /**
     * Текущий пользователь. Заодно проверяет, что сессия жива: если аккаунт удалён
     * с другого устройства, приложение возвращается в локальный режим.
     */
    suspend fun account(): AccountInfo {
        val account = try {
            authorized { api.account(it) }
        } catch (e: AuthException.Api) {
            if (e.code == AuthErrorCode.ACCOUNT_NOT_FOUND) throw expireSession()
            throw e
        }
        // Почта могла смениться на сервере — показываем актуальную.
        if (account.email != store.email) {
            store.saveEmail(account.email)
            _state.value = AuthState.SignedIn(account.email)
        }
        return account
    }

    /** Смена пароля. Остальные устройства разлогиниваются, текущее остаётся в аккаунте. */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        authorized { api.changePassword(it, currentPassword, newPassword) }
    }

    /** Безвозвратно удаляет аккаунт и все его данные на сервере. */
    suspend fun deleteAccount(password: String) {
        authorized { api.deleteAccount(it, password) }
        signOutLocally()
    }

    suspend fun sessions(): List<DeviceSession> = authorized { api.sessions(it) }

    /** Завершает сессию другого устройства. Своё устройство закрывается через [logout]. */
    suspend fun terminateSession(sessionId: String) {
        authorized { api.terminateSession(it, sessionId) }
    }

    /**
     * Выход с этого устройства.
     *
     * Токены стираются локально, не дожидаясь ответа сервера: даже если запрос
     * не дойдёт, пользователь должен оказаться разлогинен сразу. Сессия на сервере
     * в этом случае истечёт сама.
     */
    suspend fun logout() {
        val token = runCatching { validAccessToken() }.getOrNull()
        signOutLocally()
        if (token != null) runCatching { api.logout(token) }
    }

    /**
     * Выход со всех устройств, включая это.
     *
     * В отличие от [logout], сначала дожидается сервера: смысл действия — закрыть
     * другие устройства, и если запрос не прошёл, пользователь должен об этом узнать.
     */
    suspend fun logoutAll() {
        authorized { api.logoutAll(it) }
        signOutLocally()
    }

    // endregion

    // region Токены

    /**
     * Выполняет защищённый запрос.
     *
     * Access-токен обновляется заранее, если истекает в ближайшую минуту. Если сервер
     * всё равно ответил `INVALID_ACCESS_TOKEN` (например, часы устройства спешат),
     * токены обновляются и запрос повторяется ровно один раз.
     */
    private suspend fun <T> authorized(block: suspend (accessToken: String) -> T): T {
        val token = validAccessToken()
        return try {
            block(token)
        } catch (e: AuthException.Api) {
            if (e.status != HTTP_UNAUTHORIZED || e.code != AuthErrorCode.INVALID_ACCESS_TOKEN) throw e
            block(refreshTokens(rejectedAccessToken = token))
        }
    }

    private suspend fun validAccessToken(): String =
        store.freshAccessToken(clock(), REFRESH_MARGIN_MILLIS) ?: refreshTokens(rejectedAccessToken = null)

    /**
     * Обновляет токены — строго в одном экземпляре.
     *
     * Пока один вызов ждал блокировку, другой мог уже обновить токены. Тогда новый
     * токен берётся готовым: повторная отправка того же refresh-токена завершила бы сессию.
     *
     * @param rejectedAccessToken токен, который сервер только что отверг; его нельзя
     * считать действующим, даже если срок по часам устройства ещё не вышел.
     */
    private suspend fun refreshTokens(rejectedAccessToken: String?): String = refreshMutex.withLock {
        store.freshAccessToken(clock(), REFRESH_MARGIN_MILLIS)
            ?.takeIf { it != rejectedAccessToken }
            ?.let { return@withLock it }

        val refreshToken = store.refreshToken() ?: throw expireSession()
        val tokens = try {
            api.refresh(refreshToken)
        } catch (e: AuthException.Api) {
            if (e.code == AuthErrorCode.INVALID_REFRESH_TOKEN) throw expireSession()
            throw e
        }
        // Сохраняем первым делом: старый refresh-токен уже недействителен.
        store.save(tokens, store.email)
        tokens.accessToken
    }

    private fun signIn(tokens: AuthTokens, email: String) {
        store.save(tokens, email)
        _state.value = AuthState.SignedIn(email)
    }

    /** Стирает сессию и возвращает приложение в локальный режим. */
    private fun signOutLocally() {
        store.clear()
        _state.value = AuthState.SignedOut
    }

    /** Сессии больше нет — разлогиниваемся и сообщаем об этом вызывающему. */
    private fun expireSession(): AuthException.SessionExpired {
        signOutLocally()
        return AuthException.SessionExpired()
    }


    // endregion

    /** Сервер приводит почту к нижнему регистру — делаем так же, чтобы показывать её одинаково. */
    private fun normalize(email: String): String = AuthValidation.normalizeEmail(email).lowercase()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401

        /** Обновляем токен заранее, если до истечения осталось меньше минуты. */
        const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}
