package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Разобранная страница черты dnd.su. */
data class ParsedFeat(val name: String, val description: String)

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
        return ParsedFeat(name = name, description = description)
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
        return HtmlUtils.htmlToPlain(desc.html())
    }
}
