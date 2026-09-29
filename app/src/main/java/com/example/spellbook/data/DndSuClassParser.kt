package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Разобранная страница класса dnd.su.
 *
 * @param book книга, в которой появился класс; пустая, если на странице не указана.
 */
data class ParsedClass(val name: String, val description: String, val book: String = "")

/**
 * Парсер страницы класса с сайта dnd.su.
 *
 * Страница класса устроена как страница расы, но крупнее: помимо описания самого
 * класса в неё встроены все подклассы. Неофициальные идут в конце, сгруппированные
 * по происхождению под заголовками вроде «Пути дикости из «Unearthed Arcana»» и
 * «Пути дикости из «Homebrew»». По условию задачи подклассы из UA сохраняются,
 * а homebrew вырезается целиком — см. [removeHomebrewSubclasses].
 */
object DndSuClassParser {

    /** Префикс якоря у homebrew-подклассов и заголовка их блока. */
    private const val HOMEBREW_ANCHOR_PREFIX = "hb."

    /** Подпись, по которой узнаётся группа пользовательских материалов. */
    private const val HOMEBREW_LABEL = "Homebrew"

    /** Класс заголовка группы подклассов («… из «Homebrew»», «… из «Unearthed Arcana»»). */
    private const val BIG_SECTION_CLASS = "bigSectionTitle"

    /** Класс пункта меню, обозначающего группу подклассов. */
    private const val MENU_GROUP_CLASS = "level-1"

    /** Класс заголовка, прячущего следующий блок: так оформлены подклассы и инфузии. */
    private const val HIDE_NEXT_CLASS = "hide-next"

    /** Класс подписи таблицы. */
    private const val TABLE_TITLE_CLASS = "tableTitle"

    /** Теги, которые на сайте открывают крупную часть страницы. */
    private val TOP_LEVEL_TAGS = setOf("h1", "h2")

    /** Подпись раздела неофициальных материалов; после него идут блоки UA и Homebrew. */
    private const val SOURCE_LABEL = "Источник"

    fun parse(html: String): ParsedClass {
        val doc = Jsoup.parse(html)
        val name = parseName(doc)
        val description = parseDescription(doc)
        require(name.isNotBlank() && description.isNotBlank()) {
            "На странице не найдены название и описание класса"
        }
        return ParsedClass(name = name, description = description, book = parseBook(doc))
    }

    /** «Варвар [Barbarian]» → «Варвар». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /** Книга-источник: сначала плашка, затем строка «Источник: «…»» в параметрах. */
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

    /** Описание класса без меню сайта, встроенных статблоков и homebrew-подклассов. */
    private fun parseDescription(doc: Document): String {
        val desc = doc.selectFirst("div.desc[itemprop=articleBody]")
            ?: doc.selectFirst("[itemprop=description]")
            ?: return ""

        // Меню сайта задаёт каркас страницы («Создание», «Классовые умения»…) —
        // запоминаем его якоря до того, как само меню будет удалено.
        val menuAnchors = doc.select("ul.new-article-menu a[href^=#]")
            .map { it.attr("href").removePrefix("#") }
            .toSet()
        // Меню блока неофициальных материалов — единственный надёжный список homebrew.
        val homebrewTitles = collectHomebrewTitles(desc)

        // Навигацию приложение строит по заголовкам, дублирующие списки ссылок не нужны.
        desc.select("ul.new-article-menu, ul.article-menu").remove()
        // Встроенные карточки бестиария — отдельные существа, а не часть класса.
        desc.select("div.card.embed").remove()
        // Превью свёрнутой врезки: на сайте это первая строка художественного текста,
        // видная до раскрытия блока. В приложении текст виден целиком, и превью его дублировало.
        desc.select("blockquote.spoiler_neck").remove()
        removeGallery(desc)

        removeHomebrewSubclasses(desc, homebrewTitles)
        removeHomebrewMenuLinks(desc)
        normalizeHeadings(desc, menuAnchors)

        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        collapseTableHeaderAbbreviations(desc)
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }

    /**
     * Раскладывает заголовки страницы по четырём ролям — так, как они работают на dnd.su.
     *
     * - Сворачиваемый блок (`## `): только заголовки с классом [HIDE_NEXT_CLASS]. На сайте
     *   именно они прячут следующий за ними текст: подклассы, инфузии, воззвания и
     *   врезка «Углублённая предыстория». Класс `spoiler_head` для этого не годится:
     *   им помечены и обычные абзацы описания класса.
     * - Заголовок раздела (`# `): остальные `h1`/`h2` и заголовки из меню сайта
     *   («Создание варвара», «Классовые умения»). Они членят страницу, но не сворачиваются.
     * - Подпись таблицы (жирный абзац): заголовки [TABLE_TITLE_CLASS]. Как подзаголовки они
     *   мешали бы: в инфузиях таблица предметов стала бы отдельной записью списка.
     * - Подзаголовок (`### `): всё остальное, в том числе названия способностей.
     *
     * @param menuAnchors якоря из меню сайта — по ним узнаём заголовки каркаса страницы.
     */
    private fun normalizeHeadings(desc: Element, menuAnchors: Set<String>) {
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
                heading.tagName() in TOP_LEVEL_TAGS || heading.anchorIds().any { it in menuAnchors } ->
                    heading.replaceWith(Element("p").text(HtmlUtils.TOP_HEADING_PREFIX + text))

                else -> heading.tagName("h4")
            }
        }
    }

    /**
     * Сворачивает подписи столбцов в сокращение с расшифровкой.
     *
     * В шапке таблицы класса сайт держит два варианта названия сразу — полный и короткий,
     * переключая их по ширине экрана. При снятии тегов они склеивались в «Известные заговорыиз».
     * Оставляем сокращение — на телефоне ширины мало, — а полное название прячем в токен
     * [HtmlUtils.ABBR_PREFIX]: экран покажет его по нажатию на заголовок.
     */
    private fun collapseTableHeaderAbbreviations(desc: Element) {
        desc.select("table span.short").forEach { short ->
            val cell = short.parent() ?: return@forEach
            val full = cell.selectFirst("span.long")?.text()?.trim().orEmpty()
                .ifBlank { short.attr("title").trim() }
            val abbreviation = short.text().trim()
            if (abbreviation.isEmpty()) return@forEach
            cell.empty()
            cell.text(HtmlUtils.abbreviationToken(abbreviation, full))
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

    /**
     * Вырезает homebrew-подклассы.
     *
     * Группа homebrew начинается заголовком `h2.bigSectionTitle` с подписью «… «Homebrew»»
     * и тянется до следующего заголовка группы. Подпись — единственный надёжный признак:
     * - якоря подклассов внутри группы бывают любыми (`mhh.*` у Midgard Heroes Handbook,
     *   слова без префикса у чародея), поэтому по префиксу `hb.` часть проскакивала;
     * - у жреца и колдуна у группы homebrew вообще тот же якорь, что у группы UA.
     *
     * Подклассы лежат на странице вперемежку с обёртками `div`, поэтому границы
     * группы ищутся по порядку элементов в документе, а не по соседям одного уровня.
     */
    private fun removeHomebrewSubclasses(desc: Element, homebrewTitles: Set<String>) {
        val headings = desc.select("h1, h2, h3, h4, h5, h6")
        val groupHeadings = headings.filter { it.isSubclassGroupHeading() }

        groupHeadings.forEachIndexed { index, heading ->
            if (!heading.text().contains(HOMEBREW_LABEL, ignoreCase = true)) return@forEachIndexed
            val end = groupHeadings.getOrNull(index + 1)
            removeRange(desc, from = heading, until = end)
        }

        // Отдельные homebrew-подклассы: по якорю `hb.*` или по списку из меню блока.
        // Второе нужно для страниц, где заголовок группы размечен иначе (у жреца это `h4`).
        desc.select("h1, h2, h3, h4, h5, h6")
            .filter { h ->
                h.anchorIds().any { it.startsWith(HOMEBREW_ANCHOR_PREFIX) } ||
                    (h.hasClass(HIDE_NEXT_CLASS) && h.text().trim().lowercase() in homebrewTitles)
            }
            .forEach { heading ->
                if (heading.parent() == null) return@forEach // уже удалён вместе с группой
                val level = heading.headingLevel() ?: return@forEach
                // Блок заканчивается на следующем заголовке того же или более высокого уровня:
                // у подкласса это следующий подкласс или следующая группа.
                val next = desc.select("h1, h2, h3, h4, h5, h6").firstOrNull { other ->
                    other.isAfter(heading) && (other.headingLevel() ?: Int.MAX_VALUE) <= level
                }
                removeRange(desc, from = heading, until = next)
            }
    }

    /**
     * Названия homebrew-подклассов из меню блока «Unearthed Arcana & Unofficial».
     *
     * В этом меню подклассы (`li.level-2`) лежат под своими группами (`li.level-1`),
     * и группа homebrew подписана всегда одинаково — в отличие от заголовков самих групп,
     * которые на разных страницах размечены разными тегами.
     */
    private fun collectHomebrewTitles(desc: Element): Set<String> {
        val titles = mutableSetOf<String>()
        var insideHomebrew = false
        desc.select("ul.article-menu > li").forEach { item ->
            val text = item.text().trim()
            if (item.hasClass(MENU_GROUP_CLASS)) {
                insideHomebrew = text.contains(HOMEBREW_LABEL, ignoreCase = true)
            } else if (insideHomebrew && text.isNotEmpty()) {
                titles += text.lowercase()
            }
        }
        return titles
    }

    /** Заголовок группы подклассов: крупный и не сворачиваемый, в отличие от подкласса. */
    private fun Element.isSubclassGroupHeading(): Boolean =
        tagName() == "h2" && hasClass(BIG_SECTION_CLASS) && !hasClass(HIDE_NEXT_CLASS)

    /**
     * Удаляет всё от [from] до [until] (не включительно) в порядке документа.
     *
     * Снимаются только узлы, целиком лежащие внутри диапазона: обёртка, в которой
     * есть граница [until], остаётся — иначе вместе с homebrew уходил бы и следующий раздел.
     */
    private fun removeRange(desc: Element, from: Element, until: Element?) {
        // Предки границы: их удалять нельзя, иначе пропадёт и сама граница.
        val protectedNodes = until?.parents()?.toIdentitySet().orEmpty()
        val doomed = desc.allElements.filter { element ->
            element != desc &&
                element !in protectedNodes &&
                !element.isBefore(from) &&
                (until == null || element.isBefore(until))
        }.toIdentitySet()
        // Снимаем только верхние узлы диапазона: вложенные уйдут вместе с ними.
        doomed.filter { it.parent() !in doomed }.forEach { it.remove() }
    }

    /** Множество по ссылкам: сравнение узлов документа по содержимому здесь не нужно. */
    private fun Collection<Element>.toIdentitySet(): Set<Element> =
        Collections.newSetFromMap(IdentityHashMap<Element, Boolean>()).also { it.addAll(this) }

    /** Лежит ли элемент в документе раньше [other] (предки [other] тоже считаются «раньше»). */
    private fun Element.isBefore(other: Element): Boolean {
        if (this == other) return false
        val mine = path()
        val theirs = other.path()
        val common = mine.zip(theirs).takeWhile { (a, b) -> a == b }.size
        // Предок идёт раньше потомка.
        if (common == mine.size) return true
        if (common == theirs.size) return false
        return mine[common] < theirs[common]
    }

    private fun Element.isAfter(other: Element): Boolean =
        this != other && !isBefore(other) && other.parents().none { it === this }

    /** Путь от корня документа: индексы элемента и его предков среди соседей. */
    private fun Element.path(): List<Int> =
        (parents().reversed() + this).map { it.elementSiblingIndex() }

    /** Убирает ссылки меню, ведущие на удалённые homebrew-разделы. */
    private fun removeHomebrewMenuLinks(desc: Element) {
        desc.select("a[href^=#]").forEach { link ->
            if (link.attr("href").removePrefix("#").startsWith(HOMEBREW_ANCHOR_PREFIX)) {
                link.remove()
            }
        }
    }

    /** Якоря заголовка: собственный id и id вложенного span, как на dnd.su. */
    private fun Element.anchorIds(): List<String> =
        buildList {
            attr("id").takeIf { it.isNotBlank() }?.let { add(it) }
            select("[id]").forEach { nested ->
                nested.attr("id").takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }

    /** Уровень заголовка (1–6) или null, если элемент заголовком не является. */
    private fun Element.headingLevel(): Int? =
        tagName().takeIf { it.length == 2 && it[0] == 'h' }?.get(1)?.digitToIntOrNull()
}
