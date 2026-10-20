package com.example.spellbook.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/**
 * HTTP-клиент API аккаунтов — один метод на эндпоинт, без логики токенов.
 *
 * Обновлением токенов и повтором запросов занимается [AuthRepository]: здесь только
 * отправка запроса и разбор ответа. Ошибки сервера превращаются в [AuthException.Api]
 * по полю `code` — это стабильный контракт; текст `detail` не используется.
 */
class AuthApi(private val client: OkHttpClient, baseUrl: String) {

    private val base = baseUrl.trimEnd('/')

    // region Публичные эндпоинты

    suspend fun register(email: String, password: String): CodeSent =
        parseCodeSent(post("/auth/register", jsonOf("email" to email, "password" to password)))

    suspend fun verifyEmail(email: String, code: String, deviceName: String): AuthTokens =
        parseTokens(
            post(
                "/auth/verify-email",
                jsonOf("email" to email, "code" to code, "deviceName" to deviceName),
            ),
        )

    suspend fun resendVerification(email: String): CodeSent =
        parseCodeSent(post("/auth/verify-email/resend", jsonOf("email" to email)))

    suspend fun login(email: String, password: String, deviceName: String): AuthTokens =
        parseTokens(
            post(
                "/auth/login",
                jsonOf("email" to email, "password" to password, "deviceName" to deviceName),
            ),
        )

    suspend fun refresh(refreshToken: String): AuthTokens =
        parseTokens(post("/auth/refresh", jsonOf("refreshToken" to refreshToken)))

    suspend fun forgotPassword(email: String): CodeSent =
        parseCodeSent(post("/auth/password/forgot", jsonOf("email" to email)))

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        deviceName: String,
    ): AuthTokens = parseTokens(
        post(
            "/auth/password/reset",
            jsonOf(
                "email" to email,
                "code" to code,
                "newPassword" to newPassword,
                "deviceName" to deviceName,
            ),
        ),
    )

    // endregion

    // region Защищённые эндпоинты

    suspend fun account(accessToken: String): AccountInfo {
        val text = execute("GET", "/account", null, accessToken)
        return parsing {
            val json = JSONObject(text)
            AccountInfo(
                id = json.getString("id"),
                email = json.getString("email"),
                emailVerifiedAtMillis = parseInstant(json.optStringOrNull("emailVerifiedAt")),
                createdAtMillis = parseInstant(json.optStringOrNull("createdAt")),
            )
        }
    }

    suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String) {
        execute(
            "POST",
            "/account/password",
            jsonOf("currentPassword" to currentPassword, "newPassword" to newPassword),
            accessToken,
        )
    }

    /** У этого `DELETE` есть тело с паролем — так требует сервер. */
    suspend fun deleteAccount(accessToken: String, password: String) {
        execute("DELETE", "/account", jsonOf("password" to password), accessToken)
    }

    suspend fun sessions(accessToken: String): List<DeviceSession> {
        val text = execute("GET", "/account/sessions", null, accessToken)
        return parsing {
            val array = JSONArray(text)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                DeviceSession(
                    id = json.getString("id"),
                    deviceName = json.optStringOrNull("deviceName").orEmpty(),
                    createdAtMillis = parseInstant(json.optStringOrNull("createdAt")),
                    lastUsedAtMillis = parseInstant(json.optStringOrNull("lastUsedAt")),
                    current = json.optBoolean("current"),
                )
            }
        }
    }

    suspend fun terminateSession(accessToken: String, sessionId: String) {
        execute("DELETE", "/account/sessions/$sessionId", null, accessToken)
    }

    suspend fun logout(accessToken: String) {
        execute("POST", "/account/logout", null, accessToken)
    }

    suspend fun logoutAll(accessToken: String) {
        execute("POST", "/account/logout-all", null, accessToken)
    }

    // endregion

    private suspend fun post(path: String, body: JSONObject): String =
        execute("POST", path, body, accessToken = null)

    /**
     * Отправляет запрос и возвращает тело успешного ответа (пустая строка для 202/204 без тела).
     *
     * Запрос выполняется ровно один раз: тихие повторы отключены в самом клиенте
     * (см. [AuthModule]). Для `/auth/refresh` повтор после обрыва сети сервер
     * считает кражей токена и завершает сессию.
     */
    private suspend fun execute(
        method: String,
        path: String,
        body: JSONObject?,
        accessToken: String?,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(base + path)
            .method(method, requestBody(method, body))
            .header("Accept", "application/json")
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()

        val (status, text, retryAfter) = try {
            client.newCall(request).execute().use { response ->
                Triple(response.code, response.body?.string().orEmpty(), response.header("Retry-After"))
            }
        } catch (e: IOException) {
            throw AuthException.Network(e)
        }

        if (status !in 200..299) throw parseError(status, text, retryAfter)
        text
    }

    /** POST без данных всё равно требует тело — OkHttp не отправит его с `null`. */
    private fun requestBody(method: String, body: JSONObject?): RequestBody? = when {
        body != null -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
        method == "POST" -> ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
        else -> null
    }

    private fun parseCodeSent(text: String): CodeSent = parsing {
        CodeSent(resendAvailableInSeconds = JSONObject(text).optInt("resendAvailableInSeconds"))
    }

    private fun parseTokens(text: String): AuthTokens = parsing {
        val json = JSONObject(text)
        AuthTokens(
            accessToken = json.getString("accessToken"),
            // Без срока считаем токен истёкшим: следующий запрос просто обновит его.
            accessTokenExpiresAtMillis = parseInstant(json.optStringOrNull("accessTokenExpiresAt")) ?: 0L,
            refreshToken = json.getString("refreshToken"),
            refreshTokenExpiresAtMillis = parseInstant(json.optStringOrNull("refreshTokenExpiresAt")) ?: 0L,
            sessionId = json.optStringOrNull("sessionId").orEmpty(),
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Код для ответа, который не удалось разобрать: сервер прислал не то, что обещал. */
        const val INVALID_RESPONSE = "INVALID_RESPONSE"

        /**
         * Ошибка сервера в формате `application/problem+json`.
         *
         * `retryAfterSeconds` берётся из тела, а если его там нет — из заголовка
         * `Retry-After`, которым сервер его дублирует.
         */
        internal fun parseError(status: Int, text: String, retryAfterHeader: String?): AuthException.Api {
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return AuthException.Api(status, code = "", retryAfterSeconds = retryAfterHeader?.toIntOrNull())

            val fieldErrors = json.optJSONArray("errors")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let {
                        FieldError(it.optString("field"), it.optString("message"))
                    }
                }
            }.orEmpty()

            return AuthException.Api(
                status = status,
                code = json.optStringOrNull("code").orEmpty(),
                retryAfterSeconds = json.optIntOrNull("retryAfterSeconds") ?: retryAfterHeader?.toIntOrNull(),
                attemptsLeft = json.optIntOrNull("attemptsLeft"),
                fieldErrors = fieldErrors,
            )
        }

        /** Даты ISO-8601 в UTC; дробная часть секунд может быть или не быть. */
        internal fun parseInstant(text: String?): Long? =
            text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        private fun jsonOf(vararg pairs: Pair<String, String>) =
            JSONObject().apply { pairs.forEach { (key, value) -> put(key, value) } }

        /** `optString` возвращает строку "null" для JSON-null — нам нужен настоящий null. */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key)

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (isNull(key)) null else optInt(key)

        /** Успешный ответ, который не разобрался, — тоже ошибка API, а не падение приложения. */
        private inline fun <T> parsing(block: () -> T): T = try {
            block()
        } catch (e: JSONException) {
            throw AuthException.Api(status = 200, code = INVALID_RESPONSE)
        }
    }
}
