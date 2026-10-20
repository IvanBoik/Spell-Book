package com.example.spellbook.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spellbook.R
import com.example.spellbook.data.auth.AccountInfo
import com.example.spellbook.data.auth.AuthErrorCode
import com.example.spellbook.data.auth.AuthException
import com.example.spellbook.data.auth.AuthModule
import com.example.spellbook.data.auth.AuthRepository
import com.example.spellbook.data.auth.AuthState
import com.example.spellbook.data.auth.AuthValidation
import com.example.spellbook.data.auth.CodeSent
import com.example.spellbook.data.auth.DeviceSession
import com.example.spellbook.data.auth.PasswordProblem
import com.example.spellbook.ui.UiMessage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Шаг сценария входа, пока пользователь не в аккаунте. */
enum class AuthStep {
    LOGIN,
    REGISTER,

    /** Ввод кода из письма после регистрации или при входе с неподтверждённой почтой. */
    VERIFY_EMAIL,
    FORGOT_PASSWORD,

    /** Ввод кода из письма и нового пароля. */
    RESET_PASSWORD,
}

/** Диалог управления аккаунтом, открытый поверх профиля. */
enum class AccountDialog { CHANGE_PASSWORD, DELETE_ACCOUNT, LOGOUT_ALL }

/**
 * Авторизация: формы входа, регистрации и восстановления пароля, профиль и устройства.
 *
 * Аккаунт опционален и нужен только для будущей синхронизации, поэтому любые ошибки
 * здесь остаются внутри экрана аккаунта и не мешают остальному приложению.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = AuthModule.repository(application)

    var authState: AuthState by mutableStateOf(repository.state.value)
        private set

    var step by mutableStateOf(AuthStep.LOGIN)
        private set

    // region Поля форм

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    /** Повтор пароля при регистрации и смене пароля. */
    var passwordRepeat by mutableStateOf("")
        private set
    var code by mutableStateOf("")
        private set

    /** Новый пароль в диалоге смены; [password] там — текущий. */
    var newPassword by mutableStateOf("")
        private set

    fun updateEmail(value: String) { email = value; clearError() }
    fun updateNewPassword(value: String) { newPassword = value; clearError() }
    fun updatePassword(value: String) { password = value; clearError() }
    fun updatePasswordRepeat(value: String) { passwordRepeat = value; clearError() }
    fun updateCode(value: String) { code = AuthValidation.sanitizeCode(value); clearError() }

    // endregion

    /** Идёт запрос к серверу: кнопки блокируются, чтобы не отправить его дважды. */
    var loading by mutableStateOf(false)
        private set

    /** Ошибка текущей формы; показывается под полями. */
    var error by mutableStateOf<UiMessage?>(null)
        private set

    /** Пояснение под формой, не являющееся ошибкой: «код отправлен на …». */
    var notice by mutableStateOf<UiMessage?>(null)
        private set

    /** Когда можно снова запросить код; 0 — уже можно. */
    var resendAvailableAtMillis by mutableStateOf(0L)
        private set

    /** Разовое сообщение для всплывающего уведомления. */
    var message by mutableStateOf<UiMessage?>(null)
        private set

    fun consumeMessage() { message = null }

    // region Профиль

    var account by mutableStateOf<AccountInfo?>(null)
        private set
    var sessions by mutableStateOf<List<DeviceSession>>(emptyList())
        private set
    var profileLoading by mutableStateOf(false)
        private set

    /** Ошибка загрузки профиля (например, нет сети); профиль всё равно показывается. */
    var profileError by mutableStateOf<UiMessage?>(null)
        private set

    var dialog by mutableStateOf<AccountDialog?>(null)
        private set

    /** Сессия, завершение которой ждёт подтверждения. */
    var sessionToTerminate by mutableStateOf<DeviceSession?>(null)
        private set

    // endregion

    init {
        repository.state
            .onEach { state ->
                val signedOut = authState is AuthState.SignedIn && state is AuthState.SignedOut
                authState = state
                // Сессия закончилась сама (выход на другом устройстве, удаление аккаунта):
                // профиль больше не актуален, а форма входа должна начинаться с чистого листа.
                if (signedOut) {
                    account = null
                    sessions = emptyList()
                    dialog = null
                    resetForms(AuthStep.LOGIN)
                }
            }
            .launchIn(viewModelScope)
    }

    /** Экран аккаунта открыт: у вошедшего пользователя подгружаем профиль и устройства. */
    fun onScreenOpened() {
        if (authState is AuthState.SignedIn) {
            loadProfile()
        } else {
            resetForms(AuthStep.LOGIN)
        }
    }

    /**
     * Шаг назад внутри сценария.
     *
     * @return false — сценарий уже на первом шаге, и экран аккаунта нужно закрыть.
     */
    fun navigateBack(): Boolean {
        if (authState is AuthState.SignedIn) return false
        val previous = when (step) {
            AuthStep.LOGIN -> return false
            AuthStep.REGISTER, AuthStep.FORGOT_PASSWORD -> AuthStep.LOGIN
            AuthStep.VERIFY_EMAIL -> AuthStep.LOGIN
            AuthStep.RESET_PASSWORD -> AuthStep.FORGOT_PASSWORD
        }
        goTo(previous)
        return true
    }

    /** Переход между формами. Почта сохраняется: её не нужно вводить заново. */
    fun goTo(target: AuthStep) {
        step = target
        resetFields()
        notice = null
    }

    // region Сценарии

    fun login() {
        val normalized = AuthValidation.normalizeEmail(email)
        if (!AuthValidation.isValidEmail(normalized)) return fail(R.string.auth_error_invalid_email)
        if (password.isEmpty()) return fail(R.string.auth_error_password_empty)

        launchRequest {
            try {
                repository.login(normalized, password)
                onSignedIn()
            } catch (e: AuthException.Api) {
                if (e.code != AuthErrorCode.EMAIL_NOT_VERIFIED) throw e
                // Пароль верный, но почта не подтверждена: отправляем код и ждём его.
                startVerification(repository.resendVerification(normalized), resent = true)
            }
        }
    }

    fun register() {
        val normalized = AuthValidation.normalizeEmail(email)
        if (!AuthValidation.isValidEmail(normalized)) return fail(R.string.auth_error_invalid_email)
        newPasswordError(password)?.let { return fail(it) }
        if (password != passwordRepeat) return fail(R.string.auth_error_passwords_differ)

        launchRequest { startVerification(repository.register(normalized, password), resent = false) }
    }

    fun verifyEmail() {
        if (!AuthValidation.isValidCode(code)) return fail(R.string.auth_error_code_format)
        launchRequest {
            repository.verifyEmail(email, code)
            onSignedIn()
        }
    }

    fun resendVerification() {
        if (!canResend()) return
        launchRequest {
            val sent = repository.resendVerification(email)
            scheduleResend(sent)
            notice = UiMessage.of(R.string.auth_code_resent, AuthValidation.normalizeEmail(email))
        }
    }

    fun forgotPassword() {
        val normalized = AuthValidation.normalizeEmail(email)
        if (!AuthValidation.isValidEmail(normalized)) return fail(R.string.auth_error_invalid_email)

        launchRequest {
            val sent = repository.forgotPassword(normalized)
            goTo(AuthStep.RESET_PASSWORD)
            scheduleResend(sent)
            // Ответ одинаков для любой почты, поэтому и формулировка нейтральная.
            notice = UiMessage.of(R.string.auth_reset_code_sent, normalized)
        }
    }

    /** Повторный код для сброса — тот же запрос, что и первый. */
    fun resendResetCode() {
        if (!canResend()) return
        launchRequest {
            scheduleResend(repository.forgotPassword(email))
            notice = UiMessage.of(R.string.auth_reset_code_sent, AuthValidation.normalizeEmail(email))
        }
    }

    fun resetPassword() {
        if (!AuthValidation.isValidCode(code)) return fail(R.string.auth_error_code_format)
        newPasswordError(password)?.let { return fail(it) }
        if (password != passwordRepeat) return fail(R.string.auth_error_passwords_differ)

        launchRequest {
            repository.resetPassword(email, code, password)
            onSignedIn()
            message = UiMessage.of(R.string.auth_password_reset_done)
        }
    }

    fun canResend(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis >= resendAvailableAtMillis

    // endregion

    // region Профиль и устройства

    fun loadProfile() {
        if (profileLoading) return
        viewModelScope.launch {
            profileLoading = true
            profileError = null
            try {
                account = repository.account()
                sessions = repository.sessions()
            } catch (e: AuthException) {
                // Профиль без сети не загрузится, но выйти из аккаунта можно и офлайн.
                profileError = errorMessage(e)
            } finally {
                profileLoading = false
            }
        }
    }

    fun requestTerminateSession(session: DeviceSession) { sessionToTerminate = session }

    fun cancelTerminateSession() { sessionToTerminate = null }

    fun confirmTerminateSession() {
        val session = sessionToTerminate ?: return
        sessionToTerminate = null
        viewModelScope.launch {
            try {
                repository.terminateSession(session.id)
                sessions = sessions.filterNot { it.id == session.id }
                message = UiMessage.of(R.string.account_session_terminated)
            } catch (e: AuthException) {
                // Сессия уже завершена с другого устройства — просто убираем её из списка.
                if (e is AuthException.Api && e.code == AuthErrorCode.SESSION_NOT_FOUND) {
                    sessions = sessions.filterNot { it.id == session.id }
                } else {
                    message = errorMessage(e)
                }
            }
        }
    }

    fun openDialog(target: AccountDialog) {
        resetFields()
        dialog = target
    }

    fun closeDialog() {
        if (loading) return
        dialog = null
        resetFields()
    }

    /** Смена пароля: [password] — текущий, [newPassword] и [passwordRepeat] — новый и повтор. */
    fun changePassword() {
        if (password.isEmpty()) return fail(R.string.auth_error_password_empty)
        newPasswordError(newPassword)?.let { return fail(it) }
        if (newPassword != passwordRepeat) return fail(R.string.auth_error_passwords_differ)

        launchRequest {
            repository.changePassword(password, newPassword)
            dialog = null
            resetFields()
            message = UiMessage.of(R.string.account_password_changed)
            // Остальные устройства разлогинены — обновляем список.
            loadProfile()
        }
    }

    fun deleteAccount() {
        if (password.isEmpty()) return fail(R.string.auth_error_password_empty)
        launchRequest {
            repository.deleteAccount(password)
            dialog = null
            message = UiMessage.of(R.string.account_deleted)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            message = UiMessage.of(R.string.account_signed_out)
        }
    }

    fun logoutAll() {
        launchRequest {
            repository.logoutAll()
            dialog = null
            message = UiMessage.of(R.string.account_signed_out_all)
        }
    }

    // endregion

    private fun onSignedIn() {
        resetForms(AuthStep.LOGIN)
        message = UiMessage.of(R.string.auth_signed_in)
        loadProfile()
    }

    private fun startVerification(sent: CodeSent, resent: Boolean) {
        goTo(AuthStep.VERIFY_EMAIL)
        scheduleResend(sent)
        val normalized = AuthValidation.normalizeEmail(email)
        notice = UiMessage.of(
            if (resent) R.string.auth_verify_required else R.string.auth_code_sent,
            normalized,
        )
    }

    private fun scheduleResend(sent: CodeSent) {
        resendAvailableAtMillis = System.currentTimeMillis() + sent.resendAvailableInSeconds * 1000L
    }

    /** Выполняет запрос, блокируя форму и переводя ошибку в понятный текст. */
    private fun launchRequest(block: suspend () -> Unit) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: AuthException) {
                error = errorMessage(e)
                // Рано запрашивать код — запускаем таймер по подсказке сервера.
                if (e is AuthException.Api && e.code == AuthErrorCode.CODE_RESEND_TOO_EARLY) {
                    e.retryAfterSeconds?.let { scheduleResend(CodeSent(it)) }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun fail(resId: Int) {
        error = UiMessage.of(resId)
    }

    private fun fail(message: UiMessage) {
        error = message
    }

    private fun clearError() { error = null }

    private fun resetFields() {
        password = ""
        passwordRepeat = ""
        newPassword = ""
        code = ""
        error = null
    }

    private fun resetForms(target: AuthStep) {
        step = target
        resetFields()
        notice = null
    }

    private fun newPasswordError(value: String): UiMessage? = when (AuthValidation.newPasswordProblem(value)) {
        PasswordProblem.TOO_SHORT -> UiMessage.of(R.string.auth_error_password_short)
        PasswordProblem.TOO_LONG -> UiMessage.of(R.string.auth_error_password_long)
        null -> null
    }
}

/**
 * Понятный пользователю текст ошибки.
 *
 * Сопоставление идёт по стабильному полю `code`; текст `detail` от сервера
 * не показывается — он предназначен для отладки.
 */
internal fun errorMessage(e: AuthException): UiMessage = when (e) {
    is AuthException.Network -> UiMessage.of(R.string.auth_error_network)
    is AuthException.SessionExpired -> UiMessage.of(R.string.auth_error_session_expired)
    is AuthException.Api -> when (e.code) {
        AuthErrorCode.INVALID_CREDENTIALS -> UiMessage.of(R.string.auth_error_invalid_credentials)
        AuthErrorCode.EMAIL_ALREADY_REGISTERED -> UiMessage.of(R.string.auth_error_email_registered)
        AuthErrorCode.PASSWORD_TOO_LONG -> UiMessage.of(R.string.auth_error_password_long)
        AuthErrorCode.INVALID_CODE -> e.attemptsLeft
            ?.let { UiMessage.of(R.string.auth_error_invalid_code_attempts, it) }
            ?: UiMessage.of(R.string.auth_error_invalid_code)
        AuthErrorCode.CODE_EXPIRED -> UiMessage.of(R.string.auth_error_code_expired)
        AuthErrorCode.CODE_ATTEMPTS_EXCEEDED -> UiMessage.of(R.string.auth_error_code_attempts_exceeded)
        AuthErrorCode.CODE_RESEND_TOO_EARLY -> UiMessage.of(R.string.auth_error_resend_too_early)
        AuthErrorCode.LOGIN_LOCKED -> e.retryAfterSeconds
            ?.let { UiMessage.of(R.string.auth_error_login_locked_minutes, minutesCeil(it)) }
            ?: UiMessage.of(R.string.auth_error_login_locked)
        AuthErrorCode.INVALID_PASSWORD -> UiMessage.of(R.string.auth_error_invalid_password)
        AuthErrorCode.VALIDATION_ERROR -> UiMessage.of(R.string.auth_error_validation)
        else -> UiMessage.of(R.string.auth_error_unknown)
    }
}

/** Минуты с округлением вверх: «через 1 минуту», а не «через 0», когда осталось 30 секунд. */
internal fun minutesCeil(seconds: Int): Int = (seconds.coerceAtLeast(1) + 59) / 60
