package com.example.spellbook.data

import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Ошибка загрузки заклинания с dnd.su с понятным пользователю сообщением. */
class DndSuException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Загружает и разбирает заклинание по ссылке на dnd.su.
 * Сетевой запрос выполняется на IO-диспетчере.
 */
object DndSuLoader {

    private const val HOST = "dnd.su"
    private const val SPELLS_PATH = "/spells/"
    private const val FEATS_PATH = "/feats/"
    private const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
    private const val TIMEOUT_MS = 15_000
    private const val HTTP_NOT_FOUND = 404

    /** Проверяет, что ссылка ведёт на страницу заклинания dnd.su. */
    fun isSpellUrl(url: String): Boolean {
        val normalized = url.trim().lowercase()
        return normalized.contains(HOST) && normalized.contains(SPELLS_PATH)
    }

    /** Проверяет, что ссылка ведёт на страницу черты dnd.su (включая homebrew). */
    fun isFeatUrl(url: String): Boolean {
        val normalized = url.trim().lowercase()
        return normalized.contains(HOST) && normalized.contains(FEATS_PATH)
    }

    /**
     * Скачивает страницу черты и разбирает её в [ParsedFeat].
     * @throws DndSuException с понятным сообщением при любой ошибке.
     */
    suspend fun loadFeat(url: String): ParsedFeat = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!isFeatUrl(cleanUrl)) {
            throw DndSuException("Ссылка должна вести на черту с сайта dnd.su")
        }
        val html = fetchHtml(cleanUrl)
        try {
            DndSuFeatParser.parse(html)
        } catch (e: Exception) {
            throw DndSuException(
                "Не удалось распознать черту на странице. Убедитесь, что ссылка ведёт на черту dnd.su.",
                e,
            )
        }
    }

    /**
     * Скачивает страницу и парсит её в [Spell].
     * @throws DndSuException с понятным сообщением при любой ошибке (неверная ссылка,
     * сетевая ошибка, отсутствие заклинания на странице).
     */
    suspend fun load(url: String): Spell = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!isSpellUrl(cleanUrl)) {
            throw DndSuException("Ссылка должна вести на заклинание с сайта dnd.su")
        }

        val html = fetchHtml(cleanUrl)

        try {
            DndSuSpellParser.parse(html)
        } catch (e: Exception) {
            throw DndSuException(
                "Не удалось распознать заклинание на странице. Убедитесь, что ссылка ведёт на заклинание dnd.su.",
                e,
            )
        }
    }

    /** Общая загрузка страницы с понятными сообщениями об ошибках сети. */
    private fun fetchHtml(url: String): String = try {
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()
            .outerHtml()
    } catch (e: HttpStatusException) {
        throw DndSuException(
            if (e.statusCode == HTTP_NOT_FOUND) "Страница не найдена (404). Проверьте ссылку."
            else "Сайт вернул ошибку ${e.statusCode}.",
            e,
        )
    } catch (e: UnknownHostException) {
        throw DndSuException("Нет подключения к интернету или сайт недоступен.", e)
    } catch (e: SocketTimeoutException) {
        throw DndSuException("Превышено время ожидания. Попробуйте ещё раз.", e)
    } catch (e: IOException) {
        throw DndSuException("Не удалось загрузить страницу: ${e.localizedMessage ?: "ошибка сети"}", e)
    }
}
