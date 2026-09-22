package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Разобранная страница черты dnd.su.
 *
 * @param book книга, в которой появилась черта; пустая, если на странице не указана.
 */
data class ParsedFeat(val name: String, val description: String, val book: String = "")

/**
 * Парсер страницы черты с сайта dnd.su. Разметка совпадает со страницами заклинаний:
 * название лежит в `h2.card-title`, а текст — в `div[itemprop=description]`.
 */
object DndSuFeatParser {

    fun parse(html: String): ParsedFeat {
        val doc = Jsoup.parse(html)
        val name = parseName(doc)
        val description = parseDescription(doc)
        require(name.isNotBlank() && description.isNotBlank()) {
            "На странице не найдены название и описание черты"
        }
        return ParsedFeat(name = name, description = description, book = parseBook(doc))
    }

    /**
     * Книга-источник из плашки рядом с названием: `<span class="source-plaque" title="Player's Handbook">`.
     *
     * Плашек может быть несколько (например, редакции 2014 и 2024 года) — берём первую:
     * это книга, где черта появилась впервые, и именно её текст показан на странице.
     */
    private fun parseBook(doc: Document): String {
        val plaque = doc.selectFirst("h2.card-title .source-plaque") ?: return ""
        // В title лежит полное название, в тексте — короткий код вроде `PH14`.
        return plaque.attr("title").trim().ifBlank { plaque.text().trim() }
    }

    /** «Бдительный [Alert]» → «Бдительный». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /** Ссылки-подсказки превращаем в токены `[[ref слово]]`, как у заклинаний. */
    private fun parseDescription(doc: Document): String {
        val desc = doc.selectFirst("[itemprop=description]") ?: return ""
        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        // Комментарии маскота сайта — не часть черты, поэтому в описание не попадают.
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }
}
