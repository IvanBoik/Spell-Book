package com.example.spellbook.data

import com.example.spellbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Получает список ссылок на официальные заклинания dnd.su.
 *
 * Список берётся из карты сайта: страница каталога подгружает содержимое скриптом,
 * поэтому обычным запросом ссылки оттуда не получить.
 */
object DndSuCatalog {

    private const val SITEMAP_URL = "https://dnd.su/sitemap.xml"
    private const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
    private const val TIMEOUT_MS = 30_000

    /** Раздел пользовательских материалов — в официальный список не попадает. */
    private const val HOMEBREW_MARKER = "/homebrew/"

    /** Ссылка на страницу заклинания: `/spells/<id>-<slug>/`. */
    private val SPELL_URL_REGEX = Regex("https?://[^<\\s]*?/spells/\\d+-[^<\\s]*")

    /**
     * Возвращает адреса всех официальных заклинаний.
     *
     * @throws DndSuException при сетевой ошибке или пустом ответе.
     */
    suspend fun loadOfficialSpellUrls(): List<String> = withContext(Dispatchers.IO) {
        val xml = runCatching {
            Jsoup.connect(SITEMAP_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .ignoreContentType(true)
                .execute()
                .body()
        }.getOrElse { throw DndSuException(R.string.msg_spell_list_failed, cause = it) }

        val urls = SPELL_URL_REGEX.findAll(xml)
            .map { it.value.trimEnd('<') }
            .filterNot { it.contains(HOMEBREW_MARKER, ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()

        if (urls.isEmpty()) throw DndSuException(R.string.msg_spell_list_failed)
        urls
    }
}
