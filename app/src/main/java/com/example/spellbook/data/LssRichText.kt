package com.example.spellbook.data

import com.example.spellbook.data.model.NoteCharStyle
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.NoteSpan
import com.example.spellbook.data.model.compressStyles
import com.example.spellbook.util.HtmlUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Строка текста, развёрнутая из документа редактора LSS.
 *
 * Редактор LSS хранит текст деревом ProseMirror (абзацы, списки, спойлеры). Для разбора
 * блоков и перевода в заметки приложения дерево разворачивается в плоский список строк.
 *
 * @param text строка; у пунктов списка — с маркером «• » или «1. », как в заметках приложения.
 * @param spans оформление участков строки (жирный, курсив, подчёркивание).
 * @param isBreak разделитель: пустой абзац или горизонтальная черта.
 * @param listItem первая строка пункта списка.
 * @param continuation последующий абзац внутри пункта списка.
 */
data class RichLine(
    val text: String,
    val spans: List<NoteSpan> = emptyList(),
    val isBreak: Boolean = false,
    val listItem: Boolean = false,
    val continuation: Boolean = false,
) {
    /** Строка, целиком набранная жирным с подчёркиванием, — заголовок раздела, который пишет приложение. */
    val isSectionHeading: Boolean
        get() = !listItem && !continuation && coversText { it.bold && it.underline }

    /** Строка целиком выделена: жирным, курсивом или подчёркиванием. */
    val isFullyMarked: Boolean
        get() = coversText { it.bold || it.italic || it.underline }

    /** Выделенное начало строки: «**Адское сопротивление.** Вы получаете…» → «Адское сопротивление.». */
    val markedPrefix: String
        get() {
            val styles = NoteParagraph(text = text, spans = spans).toCharStyles()
            var end = 0
            while (end < text.length && (text[end].isWhitespace() || styles[end].isMarked)) end++
            return text.substring(0, end).trim()
        }

    private fun coversText(predicate: (NoteCharStyle) -> Boolean): Boolean {
        if (text.isBlank()) return false
        val styles = NoteParagraph(text = text, spans = spans).toCharStyles()
        return text.indices.filter { !text[it].isWhitespace() }.all { predicate(styles[it]) }
    }

    private val NoteCharStyle.isMarked: Boolean get() = bold || italic || underline
}

/**
 * Перевод между форматированным текстом редактора LSS и абзацами заметок приложения.
 *
 * Пишутся только узлы, которые LSS точно понимает: `paragraph`, `text` с отметками
 * `bold`/`italic`/`underline`, `bulletList`/`orderedList`/`listItem` и `resource`. Неизвестный
 * узел ломает отрисовку всего текстового блока LSS, поэтому другие типы не создаются.
 */
object LssRichText {

    private const val BULLET_PREFIX = "• "
    private const val TABLE_CELL_SEPARATOR = " — "
    private val NUMBERED_PREFIX = Regex("^(\\d+)\\.\\s")

    // region Запись

    /** Текстовый блок листа: `{ "value": { "data": <doc> }, "customLabel": … }`. */
    fun textBlock(content: List<JSONObject>, customLabel: String? = null): JSONObject =
        JSONObject().apply {
            put("value", JSONObject().put("data", doc(content)))
            if (!customLabel.isNullOrBlank()) put("customLabel", customLabel)
        }

    fun doc(content: List<JSONObject>): JSONObject {
        val nodes = content.ifEmpty { listOf(emptyParagraph()) }
        return JSONObject().put("type", "doc").put("content", JSONArray(nodes))
    }

    fun emptyParagraph(): JSONObject = JSONObject().put("type", "paragraph")

    /** Абзац, набранный жирным: заголовок умения или черты. */
    fun heading(text: String): JSONObject =
        styledParagraph(text, NoteCharStyle(bold = true))

    /**
     * Заголовок раздела внутри блока: жирный с подчёркиванием. По нему загрузка снова делит
     * блок на заметки — например, «Раса: …» и «Класс: …» в общем блоке способностей.
     */
    fun sectionHeading(text: String): JSONObject =
        styledParagraph(text, NoteCharStyle(bold = true, underline = true))

    /** Абзац простого текста, возможно с жирным началом «Языки: …». */
    fun labeledParagraph(label: String, value: String): JSONObject {
        val text = "$label: $value"
        val styles = List(text.length) { index -> NoteCharStyle(bold = index < label.length) }
        return paragraph(text, styles)
    }

    /** Абзацы заметки: каждая строка — абзац, строки с маркерами — пункты списков. */
    fun noteParagraphs(paragraphs: List<NoteParagraph>): List<JSONObject> {
        val nodes = mutableListOf<JSONObject>()
        paragraphs.filter { it.text.isNotBlank() }.forEachIndexed { index, paragraph ->
            // Абзацы заметки разделяются пустой строкой — так же их пишет и сам LSS.
            if (index > 0) nodes += emptyParagraph()
            nodes += paragraphNodes(paragraph)
        }
        return nodes
    }

    /** Простой текст (описание черты или предмета) в узлы документа. */
    fun plainText(text: String): List<JSONObject> = noteParagraphs(listOf(NoteParagraph(text = text)))

    /** Узел виджета ресурса LSS: счётчик из `data.resources`, стоящий в текстовом блоке. */
    fun resourceNode(resourceId: String, blockKey: String): JSONObject =
        JSONObject().put("type", "resource").put(
            "attrs",
            JSONObject().apply {
                put("id", resourceId)
                put("textName", blockKey)
                put("source", JSONObject.NULL)
                put("sourceRev", JSONObject.NULL)
                put("removed", false)
            },
        )

    private fun styledParagraph(text: String, style: NoteCharStyle): JSONObject =
        paragraph(text, List(text.length) { style })

    /**
     * Абзац заметки: строки идут подряд, строки с маркерами собираются в списки.
     *
     * Таблиц в редакторе LSS нет, поэтому таблица («| 3 | палящая кара |») пишется строками
     * «3 — палящая кара», а её шапка — жирным.
     */
    private fun paragraphNodes(paragraph: NoteParagraph): List<JSONObject> {
        val styles = paragraph.toCharStyles()
        val nodes = mutableListOf<JSONObject>()
        var list: JSONObject? = null
        var listKind: String? = null
        var tableRow = 0
        var offset = 0
        paragraph.text.split('\n').forEach { line ->
            val lineStyles = styles.subList(offset, offset + line.length)
            offset += line.length + 1
            if (HtmlUtils.isTableRow(line.trim())) {
                list = null
                listKind = null
                if (HtmlUtils.isTableSeparator(line.trim())) return@forEach
                val text = HtmlUtils.parseTableRow(line.trim()).joinToString(TABLE_CELL_SEPARATOR) { it.trim() }
                nodes += styledParagraph(text, NoteCharStyle(bold = tableRow == 0))
                tableRow++
                return@forEach
            }
            tableRow = 0
            val numbered = NUMBERED_PREFIX.find(line)
            val (kind, prefixLength) = when {
                line.startsWith(BULLET_PREFIX) -> "bulletList" to BULLET_PREFIX.length
                numbered != null -> "orderedList" to numbered.value.length
                else -> null to 0
            }
            if (kind == null) {
                list = null
                listKind = null
                nodes += if (line.isBlank()) emptyParagraph() else paragraph(line, lineStyles)
                return@forEach
            }
            if (list == null || listKind != kind) {
                listKind = kind
                list = JSONObject().put("type", kind).put("content", JSONArray()).also { created ->
                    if (kind == "orderedList") {
                        val start = numbered?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        created.put("attrs", JSONObject().put("start", start))
                    }
                    nodes += created
                }
            }
            val item = JSONObject().put("type", "listItem").put(
                "content",
                JSONArray().put(paragraph(line.substring(prefixLength), lineStyles.subList(prefixLength, line.length))),
            )
            list!!.getJSONArray("content").put(item)
        }
        return nodes
    }

    /** Абзац из текста и посимвольного оформления: соседние символы одного стиля — один узел. */
    private fun paragraph(text: String, styles: List<NoteCharStyle>): JSONObject {
        val node = JSONObject().put("type", "paragraph")
        if (text.isEmpty()) return node
        val content = JSONArray()
        var start = 0
        while (start < text.length) {
            val style = styles.getOrElse(start) { NoteCharStyle() }
            var end = start + 1
            while (end < text.length && styles.getOrElse(end) { NoteCharStyle() }.sameMarks(style)) end++
            content.put(textNode(text.substring(start, end), style))
            start = end
        }
        return node.put("content", content)
    }

    private fun textNode(text: String, style: NoteCharStyle): JSONObject {
        val node = JSONObject().put("type", "text").put("text", text)
        val marks = JSONArray()
        if (style.bold) marks.put(JSONObject().put("type", "bold"))
        if (style.italic) marks.put(JSONObject().put("type", "italic"))
        if (style.underline) marks.put(JSONObject().put("type", "underline"))
        if (marks.length() > 0) node.put("marks", marks)
        return node
    }

    /** Размер шрифта в LSS не переносится, поэтому сравниваются только отметки. */
    private fun NoteCharStyle.sameMarks(other: NoteCharStyle): Boolean =
        bold == other.bold && italic == other.italic && underline == other.underline

    // endregion

    // region Чтение

    /** Разворачивает документ редактора в строки; null или пустой документ — пустой список. */
    fun lines(doc: JSONObject?): List<RichLine> {
        val out = mutableListOf<RichLine>()
        doc?.optJSONArray("content")?.objects()?.forEach { block(it, out) }
        return out
    }

    /** Строки в абзацы заметки: абзацы разделяются пустыми строками и чертами. */
    fun toNoteParagraphs(lines: List<RichLine>): List<NoteParagraph> {
        val paragraphs = mutableListOf<NoteParagraph>()
        val text = StringBuilder()
        val styles = mutableListOf<NoteCharStyle>()
        fun flush() {
            if (text.isNotBlank()) {
                paragraphs += NoteParagraph(text = text.toString(), spans = compressStyles(styles))
            }
            text.clear()
            styles.clear()
        }
        lines.forEach { line ->
            if (line.isBreak) {
                flush()
                return@forEach
            }
            if (text.isNotEmpty()) {
                text.append('\n')
                styles += NoteCharStyle()
            }
            text.append(line.text)
            styles += NoteParagraph(text = line.text, spans = line.spans).toCharStyles()
        }
        flush()
        return paragraphs
    }

    /** Простой текст строк: абзацы — с новой строки. */
    fun toPlainText(lines: List<RichLine>): String =
        toNoteParagraphs(lines).joinToString("\n") { it.text }

    private fun block(node: JSONObject, out: MutableList<RichLine>) {
        when (node.optString("type")) {
            "paragraph" -> paragraphLines(node, out, forceBold = false)
            // Заголовков в LSS нет, но на всякий случай они читаются жирным абзацем.
            "heading", "spoilerSummary" -> paragraphLines(node, out, forceBold = true)
            "bulletList", "taskList" -> node.children().forEach { listItem(it, BULLET_PREFIX, out) }
            "orderedList" -> {
                val start = node.optJSONObject("attrs")?.optInt("start", 1) ?: 1
                node.children().forEachIndexed { index, item -> listItem(item, "${start + index}. ", out) }
            }
            "divider", "horizontalRule" -> out += RichLine("", isBreak = true)
            // Ресурс — счётчик из `data.resources`, текста у него нет.
            "resource" -> Unit
            else -> node.children().forEach { block(it, out) }
        }
    }

    private fun listItem(node: JSONObject, prefix: String, out: MutableList<RichLine>) {
        val inner = mutableListOf<RichLine>()
        node.children().forEach { block(it, inner) }
        var first = true
        inner.filterNot { it.isBreak }.forEach { line ->
            out += when {
                // Вложенный список остаётся самостоятельным пунктом.
                line.listItem -> line
                first -> RichLine(
                    text = prefix + line.text,
                    spans = line.spans.map { it.copy(start = it.start + prefix.length, end = it.end + prefix.length) },
                    listItem = true,
                )
                else -> line.copy(continuation = true)
            }
            first = false
        }
    }

    /** Строки абзаца: перенос строки внутри абзаца (`hardBreak`) начинает новую строку. */
    private fun paragraphLines(node: JSONObject, out: MutableList<RichLine>, forceBold: Boolean) {
        val inline = node.children()
        if (inline.isEmpty()) {
            out += RichLine("", isBreak = true)
            return
        }
        val text = StringBuilder()
        val styles = mutableListOf<NoteCharStyle>()
        var produced = false
        fun flush() {
            out += if (text.isBlank()) {
                RichLine("", isBreak = true)
            } else {
                RichLine(text.toString(), compressStyles(styles))
            }
            produced = true
            text.clear()
            styles.clear()
        }
        inline.forEach { child ->
            when (child.optString("type")) {
                "hardBreak" -> flush()
                "text" -> {
                    val style = styleOf(child.optJSONArray("marks"), forceBold)
                    child.optString("text").split('\n').forEachIndexed { index, part ->
                        if (index > 0) flush()
                        text.append(part)
                        repeat(part.length) { styles += style }
                    }
                }
                // Кубики, формулы и прочие встроенные виджеты: берём их подпись, если она есть.
                else -> atomText(child)?.let { atom ->
                    text.append(atom)
                    repeat(atom.length) { styles += NoteCharStyle(bold = forceBold) }
                }
            }
        }
        if (text.isNotEmpty() || !produced) flush()
    }

    private fun styleOf(marks: JSONArray?, forceBold: Boolean): NoteCharStyle {
        var style = NoteCharStyle(bold = forceBold)
        marks?.objects()?.forEach { mark ->
            style = when (mark.optString("type")) {
                "bold", "strong" -> style.copy(bold = true)
                "italic", "em" -> style.copy(italic = true)
                "underline" -> style.copy(underline = true)
                else -> style
            }
        }
        return style
    }

    private fun atomText(node: JSONObject): String? {
        val attrs = node.optJSONObject("attrs") ?: return null
        return ATOM_TEXT_KEYS.firstNotNullOfOrNull { key ->
            attrs.opt(key)?.takeIf { it is String && it.isNotBlank() } as String?
        }
    }

    private val ATOM_TEXT_KEYS = listOf("text", "label", "formula", "expression", "value")

    private fun JSONObject.children(): List<JSONObject> = optJSONArray("content")?.objects().orEmpty()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    // endregion
}
