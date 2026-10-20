package com.example.spellbook.data.auth

import android.content.Context
import android.os.Build
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Сборка зависимостей аккаунта.
 *
 * Репозиторий — один на процесс: блокировка обновления токенов работает только
 * внутри одного экземпляра, а два экземпляра могли бы обновить токен параллельно.
 */
object AuthModule {

    private const val TIMEOUT_SECONDS = 20L

    @Volatile
    private var repository: AuthRepository? = null

    fun repository(context: Context): AuthRepository =
        repository ?: synchronized(this) {
            repository ?: create(context.applicationContext).also { repository = it }
        }

    private fun create(context: Context): AuthRepository {
        val client = OkHttpClient.Builder()
            // Тихий повтор запроса после обрыва соединения отправил бы одноразовый
            // refresh-токен второй раз, и сервер завершил бы сессию как украденную.
            .retryOnConnectionFailure(false)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        return AuthRepository(
            api = AuthApi(client, AuthConfig.BASE_URL),
            store = AuthTokenStore(context),
            deviceName = deviceName(),
        )
    }

    /**
     * Название устройства для списка сессий: «Google Pixel 8».
     *
     * Производитель часто уже входит в название модели («Pixel 8» у Google — нет,
     * а «SM-S918B» у Samsung — да, но без бренда), поэтому дописываем его, только
     * если модель с него не начинается.
     */
    internal fun deviceName(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        model: String = Build.MODEL.orEmpty(),
    ): String {
        val name = when {
            model.isBlank() -> manufacturer
            manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
        return name.trim().take(AuthConfig.DEVICE_NAME_MAX_LENGTH)
    }
}
