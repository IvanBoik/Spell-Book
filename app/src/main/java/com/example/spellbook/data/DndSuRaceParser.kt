package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Разобранная страница расы dnd.su.
 *
 * @param book книга, в которой появилась раса; пустая, если на странице не указана.
 */
data class ParsedRace(val name: String, val description: String, val book: String = "")

/**
 * Парсер страницы расы с сайта dnd.su.
 *
 * Разметка такая же, как у черт и заклинаний: название в `h2.card-title`, текст —
 * в блоке описания. Отличий два, и оба обрабатываются здесь:
 * - источник лежит не в плашке, а строкой «Источник: «…»» в списке `ul.params`;
 * - в начале описания сайт вставляет собственное меню-навигацию по разделам
 *   (`ul.new-article-menu`). В приложении навигация строится по заголовкам,
 *   поэтому дублирующий список ссылок из текста убирается.
 */
object DndSuRaceParser {

    fun parse(html: String): ParsedRace {
        val doc = Jsoup.parse(html)
        val name = parseName(doc)
        val description = parseDescription(doc)
        require(name.isNotBlank() && description.isNotBlank()) {
            "На странице не найдены название и описание расы"
        }
        return ParsedRace(name = name, description = description, book = parseBook(doc))
    }

    /** «Голиаф [Goliath]» → «Голиаф». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /**
     * Книга-источник из строки «Источник: «Volo's guide to monsters»» в списке параметров.
     *
     * На страницах рас плашки `source-plaque`, как у черт, нет, поэтому берём
     * пункт списка, начинающийся со слова «Источник».
     */
    private fun parseBook(doc: Document): String {
        // Плашка всё же встречается на части страниц — она точнее, поэтому проверяем первой.
        doc.selectFirst("h2.card-title .source-plaque")?.let { plaque ->
            val title = plaque.attr("title").trim().ifBlank { plaque.text().trim() }
            if (title.isNotBlank()) return title
        }
        val item = doc.select("ul.params li").firstOrNull {
            it.selectFirst("strong")?.text()?.startsWith(SOURCE_LABEL, ignoreCase = true) == true
        } ?: return ""
        // Внутри пункта название книги обёрнуто в <span>, вокруг — кавычки-ёлочки.
        val book = item.selectFirst("span")?.text() ?: item.ownText()
        return book.trim().trim('«', '»', '"').trim()
    }

    /** Ссылки-подсказки превращаем в токены `[[ref слово]]`, как у заклинаний. */
    private fun parseDescription(doc: Document): String {
        val desc = doc.selectFirst("div.desc[itemprop=articleBody]")
            ?: doc.selectFirst("[itemprop=description]")
            ?: return ""
        // Меню сайта заменяется навигацией приложения — в тексте оно только мешает.
        desc.select("ul.new-article-menu").remove()
        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        // Комментарии маскота сайта — не часть расы, поэтому в описание не попадают.
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }

    private const val SOURCE_LABEL = "Источник"
}
