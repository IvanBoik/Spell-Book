package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Разобранная страница предыстории dnd.su.
 *
 * @param book книга, в которой появилась предыстория; пустая, если на странице не указана.
 */
data class ParsedBackground(val name: String, val description: String, val book: String = "")

/**
 * Парсер страницы предыстории с сайта dnd.su.
 *
 * Страница устроена проще страницы класса: сплошной текст, разбитый заголовками
 * разделов («Умение: …», «Персонализация»), с таблицами случайных черт характера.
 * Сворачиваемых блоков на большинстве страниц нет — они встречаются только в
 * сборных статьях-адаптациях, где каждая предыстория спрятана под своим заголовком.
 *
 * Роли заголовков раскладываются так же, как на сайте, — см. [normalizeHeadings].
 */
object DndSuBackgroundParser {

    /** Класс заголовка, прячущего следующий блок: так оформлены предыстории в адаптациях. */
    private const val HIDE_NEXT_CLASS = "hide-next"

    /** Класс подписи таблицы: «Черты характера великаньего подкидыша». */
    private const val TABLE_TITLE_CLASS = "tableTitle"

    /** Класс заголовка раздела предыстории: «Умение: Все взгляды прикованы к вам». */
    private const val SECTION_TITLE_CLASS = "smallSectionTitle"

    /** Класс врезки со справочной информацией — его знает общий разбор HTML. */
    private const val CALLOUT_CLASS = "additionalInfo"

    /** Теги, которые на сайте открывают крупную часть страницы. */
    private val TOP_LEVEL_TAGS = setOf("h1", "h2")

    /** Подпись строки с книгой-источником в списке параметров. */
    private const val SOURCE_LABEL = "Источник"

    /**
     * Подписи абзацев с игровыми параметрами предыстории.
     *
     * На сайте это обычные абзацы сразу за художественным описанием, без всякого
     * визуального разделения, из-за чего всё читается сплошным текстом — см. [wrapTraits].
     * Сравнивается начало подписи: встречаются и «Владение навыками», и просто «Навыки».
     */
    private val TRAIT_LABELS = listOf(
        "Владение",
        "Навыки",
        "Инструменты",
        "Языки",
        "Снаряжение",
    )

    fun parse(html: String): ParsedBackground {
        val doc = Jsoup.parse(html)
        val name = parseName(doc)
        val description = parseDescription(doc)
        require(name.isNotBlank() && description.isNotBlank()) {
            "На странице не найдены название и описание предыстории"
        }
        return ParsedBackground(name = name, description = description, book = parseBook(doc))
    }

    /** «Артист [Entertainer]» → «Артист». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /**
     * Книга-источник: сначала плашка у названия, затем строка «Источник: «…»».
     *
     * Плашек бывает несколько: рядом с изданием 2014 года стоит ссылка на версию
     * из PHB 2024. Берём первую — она описывает саму страницу.
     */
    private fun parseBook(doc: Document): String {
        doc.selectFirst("h2.card-title .source-plaque")?.let { plaque ->
            val title = plaque.attr("title").trim().ifBlank { plaque.text().trim() }
            if (title.isNotBlank()) return title
        }
        val item = doc.select("ul.params li").firstOrNull {
            it.selectFirst("strong")?.text()?.startsWith(SOURCE_LABEL, ignoreCase = true) == true
        } ?: return ""
        val book = item.selectFirst("span")?.text() ?: item.ownText()
        return book.trim().trim('«', '»', '"').trim()
    }

    /** Описание предыстории без меню сайта, встроенных статблоков и галереи. */
    private fun parseDescription(doc: Document): String {
        // У предысторий текст лежит в `[itemprop=description]` внутри списка параметров,
        // а `articleBody` охватывает всю карточку вместе со служебной разметкой.
        val desc = doc.selectFirst("[itemprop=description]")
            ?: doc.selectFirst("div.desc[itemprop=articleBody]")
            ?: return ""

        // Навигацию приложение строит по заголовкам, дублирующие списки ссылок не нужны.
        desc.select("ul.new-article-menu, ul.article-menu").remove()
        // Встроенные карточки бестиария — отдельные существа, а не часть предыстории.
        desc.select("div.card.embed").remove()
        // Превью свёрнутой врезки дублирует её первую строку: в приложении текст виден целиком.
        desc.select("blockquote.spoiler_neck").remove()
        removeGallery(desc)

        normalizeHeadings(desc)

        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        wrapTraits(desc)
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }

    /**
     * Собирает абзацы с владениями и снаряжением во врезку со справочной информацией.
     *
     * На dnd.su художественное описание и игровые параметры идут одинаковыми абзацами
     * подряд и сливаются в сплошной текст. Врезка отбивает блок владений фоном
     * и полосой — тем же оформлением, что и `additionalInfo` сайта, поэтому
     * никакой новой разметки вводить не нужно.
     *
     * Берётся только непрерывная цепочка таких абзацев в начале описания: ниже
     * по странице те же слова встречаются внутри обычного текста умений.
     */
    private fun wrapTraits(desc: Element) {
        val paragraphs = desc.select("p")
        val first = paragraphs.indexOfFirst { it.isTraitParagraph() }
        if (first < 0) return

        // Цепочка обрывается на первом абзаце без подписи — дальше идёт обычный текст.
        var last = first
        while (last + 1 < paragraphs.size && paragraphs[last + 1].isTraitParagraph()) last++

        val block = paragraphs.subList(first, last + 1)
        // Вставляем контейнер на место первого абзаца и переносим в него всю цепочку.
        val callout = Element("div").addClass(CALLOUT_CLASS)
        block.first().before(callout)
        block.forEach { callout.appendChild(it) }
    }

    /**
     * Начинается ли абзац с жирной подписи игрового параметра.
     *
     * Проверяется именно первый узел: упоминание «владения» внутри обычной фразы
     * под определение не попадает.
     */
    private fun Element.isTraitParagraph(): Boolean {
        val label = children().firstOrNull()?.takeIf { it.tagName() == "strong" }?.text()?.trim()
            ?: return false
        return TRAIT_LABELS.any { label.startsWith(it, ignoreCase = true) }
    }

    /**
     * Раскладывает заголовки страницы по ролям — так, как они работают на dnd.su.
     *
     * - Сворачиваемый блок (`## `): заголовки с классом [HIDE_NEXT_CLASS]. На сайте
     *   именно они прячут следующий за ними текст; в статьях-адаптациях так оформлена
     *   каждая отдельная предыстория.
     * - Раздел (`# `): заголовки [SECTION_TITLE_CLASS] и обычные `h1`/`h2`. Это «Умение: …»,
     *   «Персонализация» и подобные части, которые членят текст, но не сворачиваются.
     * - Подпись таблицы (жирный абзац): заголовки [TABLE_TITLE_CLASS]. Как раздел она
     *   мешала бы: таблица черт характера отрывалась бы от своего описания.
     * - Подзаголовок (`### `): всё остальное, например «Талисман тумана» внутри блока.
     */
    private fun normalizeHeadings(desc: Element) {
        desc.select("h1, h2, h3, h4, h5, h6").forEach { heading ->
            val classes = heading.classNames()
            val text = heading.text().trim()
            when {
                text.isEmpty() -> heading.remove()

                TABLE_TITLE_CLASS in classes ->
                    heading.replaceWith(Element("p").apply { appendElement("strong").text(text) })

                HIDE_NEXT_CLASS in classes -> heading.tagName("h2")

                // Заголовок раздела пишем абзацем с маркером: общий разбор HTML знает только
                // сворачиваемые разделы и подзаголовки, а его менять ради классов не хочется.
                SECTION_TITLE_CLASS in classes || heading.tagName() in TOP_LEVEL_TAGS ->
                    heading.replaceWith(Element("p").text(HtmlUtils.TOP_HEADING_PREFIX + text))

                else -> heading.tagName("h4")
            }
        }
    }

    /** Галерея в конце страницы — картинки сайта, в тексте от неё остаётся пустой заголовок. */
    private fun removeGallery(desc: Element) {
        val heading = desc.select("h2.section-title").firstOrNull() ?: return
        var node = heading.nextElementSibling()
        heading.remove()
        while (node != null) {
            val next = node.nextElementSibling()
            node.remove()
            node = next
        }
    }
}
