package com.example.spellbook.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

/**
 * Тесты логики токенов.
 *
 * Сеть подменяется перехватчиком OkHttp, который отвечает заранее заданными
 * ответами и записывает каждый запрос. Главное, что здесь проверяется, — что
 * одноразовый refresh-токен никогда не отправляется дважды: сервер расценил бы
 * это как кражу и завершил сессию.
 */
class AuthRepositoryTest {

    /** Хранилище токенов в памяти вместо Android Keystore. */
    private class MemoryStorage : TokenStorage {
        var tokens: AuthTokens? = null
        override var email: String? = null
        override val hasSession: Boolean get() = tokens != null
        override fun refreshToken(): String? = tokens?.refreshToken
        override fun freshAccessToken(nowMillis: Long, marginMillis: Long): String? =
            tokens?.takeIf { it.accessTokenExpiresAtMillis - nowMillis > marginMillis }?.accessToken
        override fun save(tokens: AuthTokens, email: String?) {
            this.tokens = tokens
            this.email = email
        }
        override fun saveEmail(email: String) { this.email = email }
        override fun clear() {
            tokens = null
            email = null
        }
    }

    /** Записанный запрос: путь, заголовок авторизации и тело. */
    private data class Call(val path: String, val authorization: String?, val body: String)

    private val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())

    /** Ответ сервера по пути; вызывается на каждый запрос. */
    private var respond: (Call) -> Pair<Int, String> = { 200 to "{}" }

    private val storage = MemoryStorage()

    private fun repository(nowMillis: Long = NOW): AuthRepository {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                val call = Call(request.url.encodedPath, request.header("Authorization"), body)
                calls += call
                val (status, text) = respond(call)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("")
                    .body(text.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return AuthRepository(AuthApi(client, "http://test"), storage, "Test device") { nowMillis }
    }

    private fun tokensJson(access: String, refresh: String, accessExpiresAt: String = "2030-01-01T00:15:00Z") =
        """{"accessToken":"$access","accessTokenExpiresAt":"$accessExpiresAt",
           "refreshToken":"$refresh","refreshTokenExpiresAt":"2030-03-01T00:00:00Z",
           "sessionId":"s1","tokenType":"Bearer"}"""

    private fun error(code: String) = """{"status":401,"code":"$code"}"""

    private val accountJson = """{"id":"u1","email":"user@example.com"}"""

    /** Сессия с истёкшим access-токеном: первый же защищённый запрос потребует refresh. */
    private fun signedInWithExpiredAccess() {
        storage.save(
            AuthTokens("old-access", NOW - 1, "refresh-1", NOW + DAY, "s1"),
            "user@example.com",
        )
    }

    @Test
    fun `login saves tokens and signs in`() = runBlocking {
        respond = { 200 to tokensJson("a1", "r1") }
        val repo = repository()

        repo.login("  User@Example.com ", "password")

        assertEquals("r1", storage.refreshToken())
        assertEquals(AuthState.SignedIn("user@example.com"), repo.state.value)
        // Почта нормализуется так же, как на сервере: пробелы и регистр.
        assertEquals("user@example.com", JSONObject(calls.single().body).getString("email"))
        assertEquals("Test device", JSONObject(calls.single().body).getString("deviceName"))
    }

    @Test
    fun `expired access token is refreshed before the request`() = runBlocking {
        signedInWithExpiredAccess()
        respond = { call ->
            when (call.path) {
                "/auth/refresh" -> 200 to tokensJson("a2", "r2")
                else -> 200 to accountJson
            }
        }

        repository().account()

        assertEquals(listOf("/auth/refresh", "/account"), calls.map { it.path })
        assertEquals("Bearer a2", calls[1].authorization)
        // Новый refresh-токен сохранён: старый сервер уже не примет.
        assertEquals("r2", storage.refreshToken())
    }

    @Test
    fun `parallel requests share a single refresh`() = runBlocking {
        signedInWithExpiredAccess()
        respond = { call ->
            when (call.path) {
                "/auth/refresh" -> {
                    // Задержка даёт остальным запросам время упереться в блокировку.
                    Thread.sleep(100)
                    200 to tokensJson("a2", "r2")
                }
                "/account/sessions" -> 200 to "[]"
                else -> 200 to accountJson
            }
        }
        val repo = repository()

        (1..5).map { async(kotlinx.coroutines.Dispatchers.IO) { repo.sessions() } }.awaitAll()

        // Пять параллельных запросов — ровно одно обновление: повторная отправка
        // того же refresh-токена завершила бы сессию.
        assertEquals(1, calls.count { it.path == "/auth/refresh" })
        assertEquals(5, calls.count { it.path == "/account/sessions" })
    }

    @Test
    fun `rejected access token triggers one refresh and one retry`() = runBlocking {
        // Токен по часам устройства ещё действует, но сервер его уже не принимает.
        storage.save(AuthTokens("stale", NOW + DAY, "refresh-1", NOW + DAY, "s1"), "user@example.com")
        respond = { call ->
            when {
                call.path == "/auth/refresh" -> 200 to tokensJson("fresh", "r2")
                call.authorization == "Bearer stale" -> 401 to error(AuthErrorCode.INVALID_ACCESS_TOKEN)
                else -> 200 to accountJson
            }
        }

        repository().account()

        assertEquals(listOf("/account", "/auth/refresh", "/account"), calls.map { it.path })
        assertEquals("Bearer fresh", calls.last().authorization)
    }

    @Test
    fun `request is retried only once`() = runBlocking {
        signedInWithExpiredAccess()
        respond = { call ->
            when (call.path) {
                "/auth/refresh" -> 200 to tokensJson("a2", "r2")
                // Сервер отвергает даже свежий токен — повторять бесконечно нельзя.
                else -> 401 to error(AuthErrorCode.INVALID_ACCESS_TOKEN)
            }
        }

        try {
            repository().account()
            fail("Ожидалась ошибка")
        } catch (e: AuthException.Api) {
            assertEquals(AuthErrorCode.INVALID_ACCESS_TOKEN, e.code)
        }
        assertEquals(2, calls.count { it.path == "/account" })
    }

    @Test
    fun `invalid refresh token signs out locally`() = runBlocking {
        signedInWithExpiredAccess()
        respond = { 401 to error(AuthErrorCode.INVALID_REFRESH_TOKEN) }
        val repo = repository()

        try {
            repo.account()
            fail("Ожидался выход из сессии")
        } catch (e: AuthException.SessionExpired) {
            // ожидаемо
        }

        assertNull(storage.refreshToken())
        assertEquals(AuthState.SignedOut, repo.state.value)
    }

    @Test
    fun `deleted account signs out locally`() = runBlocking {
        storage.save(AuthTokens("a1", NOW + DAY, "r1", NOW + DAY, "s1"), "user@example.com")
        respond = { 404 to """{"status":404,"code":"${AuthErrorCode.ACCOUNT_NOT_FOUND}"}""" }
        val repo = repository()

        try {
            repo.account()
            fail("Ожидался выход из сессии")
        } catch (e: AuthException.SessionExpired) {
            // ожидаемо
        }
        assertEquals(AuthState.SignedOut, repo.state.value)
    }

    @Test
    fun `logout clears tokens even when the server is unreachable`() = runBlocking {
        storage.save(AuthTokens("a1", NOW + DAY, "r1", NOW + DAY, "s1"), "user@example.com")
        respond = { 500 to "" }
        val repo = repository()

        repo.logout()

        assertNull(storage.refreshToken())
        assertEquals(AuthState.SignedOut, repo.state.value)
    }

    @Test
    fun `error fields are parsed from problem json`() {
        val error = AuthApi.parseError(
            status = 400,
            text = """{"code":"INVALID_CODE","attemptsLeft":4,"detail":"Invalid confirmation code"}""",
            retryAfterHeader = null,
        )

        assertEquals("INVALID_CODE", error.code)
        assertEquals(4, error.attemptsLeft)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun `retry after falls back to the header`() {
        val error = AuthApi.parseError(429, """{"code":"LOGIN_LOCKED"}""", retryAfterHeader = "900")

        assertEquals(900, error.retryAfterSeconds)
    }

    @Test
    fun `non json error body does not crash`() {
        val error = AuthApi.parseError(502, "<html>Bad Gateway</html>", retryAfterHeader = null)

        assertEquals(502, error.status)
        assertTrue(error.code.isEmpty())
    }

    @Test
    fun `instants with and without fractional seconds are parsed`() {
        assertEquals(1_791_281_363_000L, AuthApi.parseInstant("2026-10-06T10:09:23Z"))
        assertEquals(1_791_281_363_294L, AuthApi.parseInstant("2026-10-06T10:09:23.294Z"))
        assertNull(AuthApi.parseInstant("not a date"))
    }

    private companion object {
        /** Фиксированное «сейчас», заведомо раньше сроков в ответах сервера. */
        const val NOW = 1_800_000_000_000L
        const val DAY = 86_400_000L
    }
}
