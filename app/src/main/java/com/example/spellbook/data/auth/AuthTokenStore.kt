package com.example.spellbook.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Хранилище токенов сессии.
 *
 * Выделено интерфейсом, чтобы логику обновления токенов можно было проверить
 * в JVM-тестах без Android Keystore.
 */
interface TokenStorage {
    /** Почта вошедшего пользователя; не секрет, нужна для показа без сети. */
    val email: String?

    /** Есть ли сохранённая сессия. Действительна ли она, знает только сервер. */
    val hasSession: Boolean

    /** Refresh-токен; null — если его нет или его не удалось прочитать. */
    fun refreshToken(): String?

    /** Access-токен, если он есть и до истечения срока остаётся больше [marginMillis]. */
    fun freshAccessToken(nowMillis: Long, marginMillis: Long): String?

    /** Сохраняет новые токены. Должно завершиться до любых других действий — см. реализацию. */
    fun save(tokens: AuthTokens, email: String?)

    /** Обновляет почту, не трогая токены. */
    fun saveEmail(email: String)

    /** Стирает всё, что относится к сессии. */
    fun clear()
}

/**
 * Хранилище токенов на устройстве.
 *
 * Refresh-токен живёт 60 дней и даёт полный доступ к аккаунту, поэтому хранится
 * только зашифрованным ключом из Android Keystore: сам ключ не покидает защищённое
 * хранилище устройства. Access-токен живёт 15 минут и держится только в памяти —
 * после перезапуска приложения он получается заново через refresh.
 *
 * Файл настроек исключён из резервного копирования (см. `backup_rules.xml`):
 * ключ Keystore на другое устройство не переносится, и восстановленный
 * зашифрованный токен там было бы нечем расшифровать.
 */
class AuthTokenStore(context: Context) : TokenStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAtMillis: Long = 0L

    override val email: String?
        get() = prefs.getString(KEY_EMAIL, null)

    override val hasSession: Boolean
        get() = prefs.contains(KEY_REFRESH_TOKEN)

    /** Идентификатор сессии этого устройства. */
    val sessionId: String?
        get() = prefs.getString(KEY_SESSION_ID, null)

    /**
     * Refresh-токен; null — если его нет или расшифровать не удалось
     * (например, ключ Keystore сброшен вместе со сменой блокировки экрана).
     */
    override fun refreshToken(): String? {
        val encrypted = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    override fun freshAccessToken(nowMillis: Long, marginMillis: Long): String? {
        val token = accessToken ?: return null
        return token.takeIf { accessTokenExpiresAtMillis - nowMillis > marginMillis }
    }

    /**
     * Сохраняет новые токены синхронно (`commit()`, а не `apply()`).
     *
     * Refresh-токен одноразовый: сразу после ответа сервера старый уже недействителен.
     * Если процесс умрёт до того, как новый окажется на диске, сессия будет потеряна,
     * поэтому описание API требует сохранять его немедленно.
     */
    override fun save(tokens: AuthTokens, email: String?) {
        accessToken = tokens.accessToken
        accessTokenExpiresAtMillis = tokens.accessTokenExpiresAtMillis
        prefs.edit()
            .putString(KEY_REFRESH_TOKEN, encrypt(tokens.refreshToken))
            .putLong(KEY_REFRESH_EXPIRES_AT, tokens.refreshTokenExpiresAtMillis)
            .putString(KEY_SESSION_ID, tokens.sessionId)
            .apply { if (email != null) putString(KEY_EMAIL, email) else remove(KEY_EMAIL) }
            .commit()
    }

    override fun saveEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    override fun clear() {
        accessToken = null
        accessTokenExpiresAtMillis = 0L
        prefs.edit().clear().commit()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // IV хранится вместе с шифротекстом: он не секретен, но нужен для расшифровки.
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, decode(parts[0])))
        return String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        /** Имя файла упомянуто в `backup_rules.xml` и `data_extraction_rules.xml`. */
        const val PREFS_NAME = "spellbook_auth"

        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_REFRESH_EXPIRES_AT = "refresh_token_expires_at"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_EMAIL = "email"

        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "spellbook_auth_key"
        const val KEY_SIZE_BITS = 256
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
