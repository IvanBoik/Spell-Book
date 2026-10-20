package com.example.spellbook.data.auth

/** Проблема с паролем, найденная до отправки на сервер. */
enum class PasswordProblem {
    TOO_SHORT,

    /**
     * Длиннее 72 символов или 72 байт в UTF-8. Байты важны отдельно: кириллица
     * занимает по 2 байта, поэтому русский пароль упирается в лимит уже на ~36 символах.
     */
    TOO_LONG,
}

/**
 * Проверки полей на клиенте — по правилам из описания API.
 *
 * Сервер проверяет то же самое; клиентская проверка нужна, чтобы подсказать
 * пользователю раньше и не тратить запрос.
 */
object AuthValidation {

    private const val EMAIL_MAX_LENGTH = 254
    private const val PASSWORD_MIN_LENGTH = 8
    private const val PASSWORD_MAX_LENGTH = 72
    private const val PASSWORD_MAX_BYTES = 72

    /** Достаточно грубая проверка: окончательно адрес проверяет письмо с кодом. */
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Пробелы по краям обрезаем на клиенте: при вставке из буфера они попадают часто. */
    fun normalizeEmail(email: String): String = email.trim()

    fun isValidEmail(email: String): Boolean {
        val normalized = normalizeEmail(email)
        return normalized.length <= EMAIL_MAX_LENGTH && EMAIL_REGEX.matches(normalized)
    }

    /**
     * Проверка нового пароля (регистрация, сброс, смена).
     *
     * При входе и для текущего пароля длина не проверяется — так требует сервер:
     * правила могли поменяться после того, как пароль был задан.
     */
    fun newPasswordProblem(password: String): PasswordProblem? = when {
        password.length < PASSWORD_MIN_LENGTH -> PasswordProblem.TOO_SHORT
        password.length > PASSWORD_MAX_LENGTH -> PasswordProblem.TOO_LONG
        password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> PasswordProblem.TOO_LONG
        else -> null
    }

    fun isValidCode(code: String): Boolean =
        code.length == AuthConfig.CODE_LENGTH && code.all { it.isDigit() }

    /** Оставляет в вводе кода только цифры и не даёт превысить длину. */
    fun sanitizeCode(input: String): String =
        input.filter { it.isDigit() }.take(AuthConfig.CODE_LENGTH)
}
