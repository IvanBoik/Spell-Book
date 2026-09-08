package com.example.spellbook.util

/**
 * Преобразование между простым текстом (как его вводит пользователь) и HTML,
 * который требует формат LSS в полях описания.
 *
 * Поддерживаемая разметка описания:
 * - `**жирный**` и `*курсив*`;
 * - `## Заголовок`;
 * - списки: `- пункт` и `1. пункт`;
 * - таблицы строками вида `| ячейка | ячейка |`;
 * - блоки-врезки со справочной информацией: `::: Заголовок` … `:::`.
 */
object HtmlUtils {

    // region Разметка описания

    /** Маркер начала и конца блока-врезки. После открывающего может идти заголовок. */
    const val CALLOUT_MARKER = ":::"

    /** Префикс заголовка внутри описания. */
    const val HEADING_PREFIX = "## "

    /** Префикс пункта маркированного списка. */
    const val BULLET_PREFIX = "- "

    /** Пункт нумерованного списка: `1. текст`. Группа 1 — сам текст. */
    private val NUMBERED_ITEM_REGEX = Regex("^\\d+\\.\\s+(.*)$")

    /**
     * Строка таблицы в описании: ячейки разделены `|`, как в Markdown.
     * Пример: `| Знание | Модификатор |`.
     */
    private val TABLE_ROW_REGEX = Regex("^\\s*\\|.*\\|\\s*$")

    /** Разделитель шапки: `| --- | :---: |`. Помечает предыдущую строку как заголовок. */
    private val TABLE_SEPARATOR_REGEX = Regex("^\\s*\\|[\\s:|-]*\\|\\s*$")

    /** Является ли строка частью таблицы. */
    fun isTableRow(line: String): Boolean = TABLE_ROW_REGEX.matches(line)

    /** Является ли строка разделителем шапки таблицы. */
    fun isTableSeparator(line: String): Boolean =
        TABLE_SEPARATOR_REGEX.matches(line) && line.contains('-')

    /** Разбивает строку таблицы на ячейки, отбрасывая крайние разделители. */
    fun parseTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /** Собирает строку таблицы из ячеек. */
    fun buildTableRow(cells: List<String>): String = cells.joinToString(" | ", "| ", " |")

    /** Является ли строка заголовком. */
    fun isHeading(line: String): Boolean = line.startsWith(HEADING_PREFIX)

    /** Текст заголовка без префикса. */
    fun headingText(line: String): String = line.removePrefix(HEADING_PREFIX).trim()

    /** Является ли строка пунктом маркированного списка. */
    fun isBulletItem(line: String): Boolean = line.startsWith(BULLET_PREFIX)

    /** Является ли строка пунктом нумерованного списка. */
    fun isNumberedItem(line: String): Boolean = NUMBERED_ITEM_REGEX.matches(line)

    /** Текст пункта списка без маркера или номера. */
    fun listItemText(line: String): String = when {
        isBulletItem(line) -> line.removePrefix(BULLET_PREFIX).trim()
        else -> NUMBERED_ITEM_REGEX.find(line)?.groupValues?.get(1)?.trim() ?: line.trim()
    }

    /** Открывает ли строка блок-врезку. */
    fun isCalloutStart(line: String): Boolean =
        line.startsWith(CALLOUT_MARKER) && line.trim() != CALLOUT_MARKER

    /** Закрывает ли строка блок-врезку. */
    fun isCalloutEnd(line: String): Boolean = line.trim() == CALLOUT_MARKER

    /** Заголовок врезки из открывающей строки; пустая строка, если заголовка нет. */
    fun calloutTitle(line: String): String = line.removePrefix(CALLOUT_MARKER).trim()

    // endregion

    // region plainToHtml

    /** Преобразует описание в HTML формата LSS, сохраняя списки, таблицы и врезки. */
    fun plainToHtml(text: String): String {
        val lines = text
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        return blocksToHtml(lines)
    }

    /** Собирает HTML из строк описания, группируя списки, таблицы и врезки. */
    private fun blocksToHtml(lines: List<String>): String {
        val html = StringBuilder()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            when {
                isCalloutStart(line) -> {
                    // Содержимое врезки собираем отдельно и обрабатываем рекурсивно.
                    val title = calloutTitle(line)
                    val body = mutableListOf<String>()
                    index++
                    while (index < lines.size && !isCalloutEnd(lines[index])) {
                        body += lines[index]
                        index++
                    }
                    if (index < lines.size) index++ // пропускаем закрывающий маркер
                    html.append("<div class=\"additionalInfo\">")
                    if (title.isNotEmpty()) {
                        html.append("<h3 class=\"smallSectionTitle\"><strong>")
                            .append(inlineToHtml(title))
                            .append("</strong></h3>")
                    }
                    html.append(blocksToHtml(body)).append("</div>")
                }

                isHeading(line) -> {
                    html.append("<h4>").append(inlineToHtml(headingText(line))).append("</h4>")
                    index++
                }

                isTableRow(line) -> {
                    val block = mutableListOf<String>()
                    while (index < lines.size && isTableRow(lines[index])) {
                        block += lines[index]
                        index++
                    }
                    html.append(tableToHtml(block))
                }

                isBulletItem(line) || isNumberedItem(line) -> {
                    val numbered = isNumberedItem(line)
                    val tag = if (numbered) "ol" else "ul"
                    html.append("<$tag>")
                    // Список продолжается, пока пункты того же типа идут подряд.
                    while (index < lines.size &&
                        (if (numbered) isNumberedItem(lines[index]) else isBulletItem(lines[index]))
                    ) {
                        html.append("<li>").append(inlineToHtml(listItemText(lines[index]))).append("</li>")
                        index++
                    }
                    html.append("</$tag>")
                }

                else -> {
                    html.append("<p>").append(inlineToHtml(line)).append("</p>")
                    index++
                }
            }
        }
        return html.toString()
    }

    /** Собирает HTML-таблицу из строк вида `| a | b |`. */
    private fun tableToHtml(block: List<String>): String {
        val separatorIndex = block.indexOfFirst { isTableSeparator(it) }
        val html = StringBuilder("<table><tbody>")
        block.forEachIndexed { rowIndex, line ->
            if (isTableSeparator(line)) return@forEachIndexed
            // Шапка — строка перед разделителем; без него таблица без заголовка.
            val isHeader = separatorIndex > 0 && rowIndex < separatorIndex
            html.append(if (isHeader) "<tr class=\"table_header\">" else "<tr>")
            parseTableRow(line).forEach { cell ->
                html.append("<td>").append(inlineToHtml(cell)).append("</td>")
            }
            html.append("</tr>")
        }
        return html.append("</tbody></table>").toString()
    }

    /** Экранирует текст и превращает `**жирный**` и `*курсив*` в теги. */
    private fun inlineToHtml(text: String): String = escape(text)
        .replace(BOLD_REGEX) { "<strong>${it.groupValues[1]}</strong>" }
        .replace(ITALIC_REGEX) { "<em>${it.groupValues[1]}</em>" }

    // endregion

    // region htmlToPlain

    /**
     * Внутренние ссылки FoundryVTT/LSS вида `@Compendium[ref]{подпись}` или `@UUID[ref]{подпись}`.
     * Из ссылки оставляем только видимую подпись (например, «Рывок»),
     * а ссылку без подписи — последний сегмент идентификатора как запасной вариант.
     */
    private val LINK_WITH_LABEL = Regex("@\\w+\\[[^\\]]*\\]\\{([^}]*)\\}")
    private val LINK_WITHOUT_LABEL = Regex("@\\w+\\[([^\\]]*)\\]")

    /**
     * Токен выделенного из ссылки слова: `[[ref Рывок]]`. Группа 1 — само слово.
     * Используется на экране деталей для подсветки таких слов.
     */
    val REF_TOKEN_REGEX = Regex("\\[\\[ref\\s+(.+?)\\]\\]")

    /** Парные инлайновые выделения: `**жирный**` и `*курсив*`. */
    val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
    val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")

    /** Врезка dnd.su со справочной информацией и её заголовок. */
    private val HTML_CALLOUT_REGEX = Regex("(?is)<div[^>]*class=\"[^\"]*additionalInfo[^\"]*\"[^>]*>(.*?)</div\\s*>")
    private val HTML_HEADING_REGEX = Regex("(?is)<h[1-6][^>]*>(.*?)</h[1-6]\\s*>")

    /** Таблица целиком и её строки/ячейки — для разбора страниц dnd.su. */
    private val HTML_TABLE_REGEX = Regex("(?is)<table[^>]*>(.*?)</table\\s*>")
    private val HTML_ROW_REGEX = Regex("(?is)<tr([^>]*)>(.*?)</tr\\s*>")
    private val HTML_CELL_REGEX = Regex("(?is)<(td|th)[^>]*>(.*?)</\\1\\s*>")

    /** Списки и их пункты. */
    private val HTML_LIST_REGEX = Regex("(?is)<(ul|ol)[^>]*>(.*?)</\\1\\s*>")
    private val HTML_LIST_ITEM_REGEX = Regex("(?is)<li[^>]*>(.*?)</li\\s*>")

    /** Инлайновые выделения в HTML. */
    private val HTML_BOLD_REGEX = Regex("(?is)<(strong|b)[^>]*>(.*?)</\\1\\s*>")
    private val HTML_ITALIC_REGEX = Regex("(?is)<(em|i)[^>]*>(.*?)</\\1\\s*>")

    /**
     * Грубо очищает HTML до простого текста, сохраняя разметку описания:
     * выделения, заголовки, списки, таблицы и блоки-врезки.
     */
    fun htmlToPlain(html: String): String {
        if (html.isBlank()) return ""
        // Врезки разбираем первыми: внутри них может быть любая другая разметка.
        val withCallouts = html.replace(HTML_CALLOUT_REGEX) { match ->
            "\n$CALLOUT_MARKER${calloutHtmlToPlain(match.groupValues[1])}\n$CALLOUT_MARKER\n"
        }
        val withTables = withCallouts.replace(HTML_TABLE_REGEX) { match ->
            "\n" + htmlTableToPlain(match.groupValues[1]) + "\n"
        }
        val withLists = withTables.replace(HTML_LIST_REGEX) { match ->
            // Группа 1 — имя тега (`ul`/`ol`), группа 2 — содержимое списка.
            val numbered = match.groupValues[1].equals("ol", ignoreCase = true)
            "\n" + htmlListToPlain(match.groupValues[2], numbered) + "\n"
        }
        val withHeadings = withLists.replace(HTML_HEADING_REGEX) { match ->
            val title = stripTags(match.groupValues[1])
            if (title.isBlank()) "" else "\n$HEADING_PREFIX$title\n"
        }
        val withInline = applyInlineMarkup(withHeadings)
        val withBreaks = withInline
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</div\\s*>"), "\n")
        val noTags = withBreaks.replace(Regex("<[^>]*>"), "")
        val noLinks = noTags
            .replace(LINK_WITH_LABEL) { "[[ref ${it.groupValues[1]}]]" }
            .replace(LINK_WITHOUT_LABEL) { "[[ref ${it.groupValues[1].substringAfterLast('.')}]]" }
        return unescape(noLinks)
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Превращает содержимое врезки в текст: заголовок уходит в открывающий маркер. */
    private fun calloutHtmlToPlain(calloutHtml: String): String {
        val heading = HTML_HEADING_REGEX.find(calloutHtml)
        val title = heading?.let { stripTags(it.groupValues[1]) }.orEmpty()
        val body = heading?.let { calloutHtml.removeRange(it.range) } ?: calloutHtml
        // Тело врезки обрабатываем обычным путём, чтобы внутри работали списки и выделения.
        val plainBody = htmlToPlain(body)
        return if (title.isBlank()) "\n$plainBody" else " $title\n$plainBody"
    }

    /** Превращает содержимое `<ul>`/`<ol>` в строки с маркерами. */
    private fun htmlListToPlain(listHtml: String, numbered: Boolean): String =
        HTML_LIST_ITEM_REGEX.findAll(listHtml)
            .map { stripTags(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .mapIndexed { index, item -> if (numbered) "${index + 1}. $item" else "$BULLET_PREFIX$item" }
            .joinToString("\n")

    /** Заменяет `<strong>`/`<em>` на текстовые маркеры выделения. */
    private fun applyInlineMarkup(html: String): String = html
        .replace(HTML_BOLD_REGEX) { match ->
            val text = stripTags(match.groupValues[2])
            if (text.isBlank()) "" else "**$text**"
        }
        .replace(HTML_ITALIC_REGEX) { match ->
            val text = stripTags(match.groupValues[2])
            if (text.isBlank()) "" else "*$text*"
        }

    /** Убирает теги и лишние пробелы, оставляя чистый текст. */
    private fun stripTags(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Превращает содержимое `<table>` в строки с `|` и разделителем шапки. */
    private fun htmlTableToPlain(tableHtml: String): String {
        val rows = HTML_ROW_REGEX.findAll(tableHtml).map { rowMatch ->
            val attributes = rowMatch.groupValues[1]
            val body = rowMatch.groupValues[2]
            // dnd.su помечает шапку классом, другие источники — тегом `<th>`.
            val isHeader = attributes.contains("table_header", ignoreCase = true) ||
                body.contains("<th", ignoreCase = true)
            val cells = HTML_CELL_REGEX.findAll(body).map { cellMatch ->
                // Внутри ячейки встречаются `<br>` — склеиваем строки в одну, чтобы не ломать таблицу.
                val text = stripTags(applyInlineMarkup(cellMatch.groupValues[2]))
                // Вертикальная черта внутри текста разорвала бы разметку строки.
                unescape(text).replace('|', '/').trim()
            }.toList()
            isHeader to cells
        }.filter { it.second.isNotEmpty() }.toList()

        if (rows.isEmpty()) return ""
        val result = StringBuilder()
        rows.forEachIndexed { index, (isHeader, cells) ->
            result.append(buildTableRow(cells)).append('\n')
            // Разделитель ставим один раз — сразу после последней строки шапки.
            val nextIsBody = rows.getOrNull(index + 1)?.first == false
            if (isHeader && nextIsBody) {
                result.append(buildTableRow(cells.map { "---" })).append('\n')
            }
        }
        return result.toString().trimEnd('\n')
    }

    // endregion

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
