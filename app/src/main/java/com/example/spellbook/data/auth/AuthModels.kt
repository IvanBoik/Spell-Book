package com.example.spellbook.data.auth

/** Адрес API аккаунтов. Когда появится домен с HTTPS, меняется только он. */
object AuthConfig {
    const val BASE_URL = "http://185.240.103.127"

    /** Длина кода подтверждения из письма. Сервер принимает только цифры. */
    const val CODE_LENGTH = 6

    /** Ограничение сервера на название устройства в списке сессий. */
    const val DEVICE_NAME_MAX_LENGTH = 100
}

/**
 * Токены сессии, полученные при входе или обновлении.
 *
 * Сроки хранятся в миллисекундах эпохи: JWT разбирать не нужно, сервер присылает их явно.
 */
data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAtMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtMillis: Long,
    val sessionId: String,
)

/** Ответ на запрос кода: через сколько секунд можно запросить следующий. */
data class CodeSent(val resendAvailableInSeconds: Int)

/** Текущий пользователь. */
data class AccountInfo(
    val id: String,
    val email: String,
    val emailVerifiedAtMillis: Long?,
    val createdAtMillis: Long?,
)

/** Сессия — одно устройство, на котором выполнен вход. */
data class DeviceSession(
    val id: String,
    val deviceName: String,
    val createdAtMillis: Long?,
    val lastUsedAtMillis: Long?,
    /** Устройство, с которого сделан запрос. */
    val current: Boolean,
)

/** Стабильные коды ошибок API — контракт сервера. Текст `detail` пользователю не показывается. */
object AuthErrorCode {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val MALFORMED_REQUEST = "MALFORMED_REQUEST"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN"
    const val CONFLICT = "CONFLICT"
    const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED"
    const val PASSWORD_TOO_LONG = "PASSWORD_TOO_LONG"
    const val INVALID_CODE = "INVALID_CODE"
    const val CODE_EXPIRED = "CODE_EXPIRED"
    const val CODE_ATTEMPTS_EXCEEDED = "CODE_ATTEMPTS_EXCEEDED"
    const val CODE_RESEND_TOO_EARLY = "CODE_RESEND_TOO_EARLY"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    const val LOGIN_LOCKED = "LOGIN_LOCKED"
    const val INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN"
    const val ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
    const val INVALID_PASSWORD = "INVALID_PASSWORD"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
}

/** Ошибка поля из ответа `VALIDATION_ERROR`. */
data class FieldError(val field: String, val message: String)

/** Сбои работы с аккаунтом. */
sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Сервер ответил ошибкой.
     *
     * @param code стабильный код из ответа; пустой, если тело не удалось разобрать.
     * @param retryAfterSeconds через сколько секунд можно повторить (таймеры кнопок).
     * @param attemptsLeft сколько попыток ввода кода осталось.
     */
    class Api(
        val status: Int,
        val code: String,
        val retryAfterSeconds: Int? = null,
        val attemptsLeft: Int? = null,
        val fieldErrors: List<FieldError> = emptyList(),
    ) : AuthException("HTTP $status $code")

    /** Сервер недоступен: нет сети, таймаут, обрыв соединения. */
    class Network(cause: Throwable) : AuthException("Network error", cause)

    /**
     * Сессии больше нет: refresh-токен истёк, отозван с другого устройства или
     * аккаунт удалён. Токены уже стёрты локально — нужен повторный вход.
     */
    class SessionExpired : AuthException("Session expired")
}

/** Состояние входа в аккаунт. */
sealed interface AuthState {
    /** Локальный режим: приложение работает без аккаунта. */
    data object SignedOut : AuthState

    data class SignedIn(val email: String) : AuthState
}
