package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Разобранная страница магического предмета dnd.su.
 *
 * @param category строка типа и редкости целиком, как напечатана на сайте.
 * @param kind тип предмета, выделенный из [category].
 * @param rarity редкость, выделенная из [category].
 * @param requiresAttunement требуется ли настройка.
 * @param price рекомендованная стоимость; пустая строка, если не указана.
 * @param book книга-источник; пустая строка, если на странице не указана.
 */
data class ParsedMagicItem(
    val name: String,
    val description: String,
    val category: String = "",
    val kind: String = "",
    val rarity: String = "",
    val requiresAttunement: Boolean = false,
    val price: String = "",
    val book: String = "",
)

/**
 * Парсер страницы магического предмета с сайта dnd.su.
 *
 * Страница устроена проще статьи о классе: карточка с параметрами (тип и редкость,
 * стоимость) и описание. Разметка описания та же, что у остальных разделов, поэтому
 * заголовки и врезки приводятся к общему виду — см. [normalizeHeadings].
 */
object DndSuMagicItemParser {

    /** Класс строки с типом и редкостью: «Чудесный предмет, редкий (требуется настройка)». */
    private const val CATEGORY_CLASS = "size-type-alignment"

    /** Класс строки со стоимостью. */
    private const val PRICE_CLASS = "price"

    /** Класс заголовка, прячущего следующий блок. */
    private const val HIDE_NEXT_CLASS = "hide-next"

    /** Класс подписи таблицы. */
    private const val TABLE_TITLE_CLASS = "tableTitle"

    /** Класс заголовка раздела. */
    private const val SECTION_TITLE_CLASS = "smallSectionTitle"

    /** Теги, которые на сайте открывают крупную часть страницы. */
    private val TOP_LEVEL_TAGS = setOf("h1", "h2")

    /** Подпись строки со стоимостью: её надо отбросить, оставив само значение. */
    private const val PRICE_LABEL = "Рекомендованная стоимость:"

    /** Признак настройки внутри скобок строки категории. */
    private const val ATTUNEMENT_MARKER = "требует"

    /**
     * Класс карточки с пользовательским материалом.
     *
     * По адресу homebrew виден не всегда: часть таких страниц лежит в обычном
     * `/items/`, но сама карточка помечена этим классом и плашкой «Неофициальные материалы».
     */
    private const val HOMEBREW_CARD_CLASS = "card__group-homebrew"

    /**
     * Относится ли страница к пользовательским материалам.
     *
     * Проверяется по разметке самой карточки, а не по адресу: так отсекаются
     * и страницы в `/items/`, которые по адресу выглядят официальными.
     */
    fun isHomebrew(html: String): Boolean =
        Jsoup.parse(html).selectFirst("div.$HOMEBREW_CARD_CLASS") != null

    fun parse(html: String): ParsedMagicItem {
        val doc = Jsoup.parse(html)
        val name = parseName(doc)
        val description = parseDescription(doc)
        require(name.isNotBlank() && description.isNotBlank()) {
            "На странице не найдены название и описание предмета"
        }

        val category = parseCategory(doc)
        return ParsedMagicItem(
            name = name,
            description = description,
            category = category,
            kind = parseKind(category),
            rarity = parseRarity(category),
            requiresAttunement = category.contains(ATTUNEMENT_MARKER, ignoreCase = true),
            price = parsePrice(doc),
            book = parseBook(doc),
        )
    }

    /** «Адамантиновый доспех [Adamantine armor]» → «Адамантиновый доспех». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /** Строка типа и редкости целиком. */
    private fun parseCategory(doc: Document): String =
        doc.selectFirst("ul.params li.$CATEGORY_CLASS")?.text()?.trim().orEmpty()

    /**
     * Тип предмета — всё до первой запятой верхнего уровня.
     *
     * Скобки пропускаем: у доспехов тип записан как «Доспех (средний или тяжёлый,
     * кроме шкурного), необычный», и запятая внутри скобок обрезала бы его посередине.
     */
    private fun parseKind(category: String): String {
        val separator = category.topLevelCommaIndex()
        val kind = if (separator < 0) category else category.substring(0, separator)
        return kind.substringBefore('(').trim()
    }

    /**
     * Редкость — часть после типа, без уточнения про настройку.
     *
     * «Чудесный предмет, редкий (требуется настройка)» → «редкий».
     */
    private fun parseRarity(category: String): String {
        val separator = category.topLevelCommaIndex()
        if (separator < 0) return ""
        return category.substring(separator + 1).substringBefore('(').trim()
    }

    /** Позиция первой запятой вне скобок; -1, если такой нет. */
    private fun String.topLevelCommaIndex(): Int {
        var depth = 0
        forEachIndexed { index, char ->
            when (char) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) return index
            }
        }
        return -1
    }

    /** Рекомендованная стоимость без подписи: «101-500 зм». */
    private fun parsePrice(doc: Document): String {
        val text = doc.selectFirst("ul.params li.$PRICE_CLASS")?.text()?.trim() ?: return ""
        return text.removePrefix(PRICE_LABEL).trim()
    }

    /**
     * Книга-источник из плашки у названия.
     *
     * Плашек бывает несколько: рядом с изданием 2014 года стоит ссылка на версию
     * из редакции 2024. Берём первую — она описывает саму страницу.
     */
    private fun parseBook(doc: Document): String {
        val plaque = doc.selectFirst("h2.card-title .source-plaque") ?: return ""
        return plaque.attr("title").trim().ifBlank { plaque.text().trim() }
    }

    /** Описание предмета без меню сайта, встроенных статблоков и галереи. */
    private fun parseDescription(doc: Document): String {
        val desc = doc.selectFirst("[itemprop=description]")
            ?: doc.selectFirst("div.desc[itemprop=articleBody]")
            ?: return ""

        // Навигацию приложение строит по заголовкам, дублирующие списки ссылок не нужны.
        desc.select("ul.new-article-menu, ul.article-menu").remove()
        // Встроенные карточки бестиария — отдельные существа, а не часть предмета.
        desc.select("div.card.embed").remove()
        // Превью свёрнутой врезки дублирует её первую строку: в приложении текст виден целиком.
        desc.select("blockquote.spoiler_neck").remove()
        // Интерактивные кнопки сайта («ВЫТЯНУТЬ КАРТУ…» у колод) работают на JS
        // и в приложении превращаются в строки-пустышки перед таблицей.
        desc.select("a[onclick], a.btn").remove()
        removeGallery(desc)

        normalizeHeadings(desc)

        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }

    /**
     * Раскладывает заголовки по ролям — так же, как на страницах предысторий.
     *
     * - Сворачиваемый блок (`## `): заголовки с классом [HIDE_NEXT_CLASS]; на сайте
     *   именно они прячут следующий текст (например, таблицы колоды многих вещей).
     * - Раздел (`# `): [SECTION_TITLE_CLASS] и обычные `h1`/`h2`.
     * - Подпись таблицы (жирный абзац): [TABLE_TITLE_CLASS]. Как раздел она отрывала бы
     *   таблицу от её описания.
     * - Подзаголовок (`### `): всё остальное, например названия свойств предмета.
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
                // сворачиваемые разделы и подзаголовки.
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
