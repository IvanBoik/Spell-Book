package com.example.spellbook.data

import androidx.annotation.StringRes
import com.example.spellbook.R
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Ошибка загрузки с dnd.su с понятным пользователю сообщением.
 *
 * Текст хранится как ресурс с аргументами: слой данных не знает языка интерфейса,
 * а сообщение собирается непосредственно перед показом.
 */
class DndSuException(
    @param:StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception("dnd.su error: res=$messageRes", cause)

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
            throw DndSuException(R.string.dndsu_link_must_be_feat)
        }
        val html = fetchHtml(cleanUrl)
        try {
            DndSuFeatParser.parse(html)
        } catch (e: Exception) {
            throw DndSuException(R.string.dndsu_feat_parse_failed, cause = e)
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
            throw DndSuException(R.string.msg_link_must_be_dndsu)
        }

        val html = fetchHtml(cleanUrl)

        try {
            DndSuSpellParser.parse(html)
        } catch (e: Exception) {
            throw DndSuException(R.string.dndsu_spell_parse_failed, cause = e)
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
        if (e.statusCode == HTTP_NOT_FOUND) {
            throw DndSuException(R.string.dndsu_not_found, cause = e)
        }
        throw DndSuException(R.string.dndsu_http_error, listOf(e.statusCode), e)
    } catch (e: UnknownHostException) {
        throw DndSuException(R.string.dndsu_no_connection, cause = e)
    } catch (e: SocketTimeoutException) {
        throw DndSuException(R.string.dndsu_timeout, cause = e)
    } catch (e: IOException) {
        throw DndSuException(R.string.dndsu_page_load_failed, cause = e)
    }
}
