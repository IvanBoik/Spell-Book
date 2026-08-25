package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Доступные размеры шрифта в заметках, в sp. */
val NOTE_FONT_SIZES = listOf(12, 14, 16, 20, 24)

/** Размер шрифта абзаца по умолчанию. */
const val NOTE_DEFAULT_FONT_SIZE = 16

/** Тип списка строки, в которой стоит каретка. */
enum class NoteListStyle(val label: String) {
    NONE("Обычный"),
    BULLET("Маркированный"),
    NUMBERED("Нумерованный"),
}

/**
 * Оформление участка текста `[start, end)`. Участки не пересекаются
 * и хранятся отсортированными по [start].
 */
data class NoteSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Размер шрифта в sp; [NOTE_DEFAULT_FONT_SIZE] — размер по умолчанию. */
    val fontSize: Int = NOTE_DEFAULT_FONT_SIZE,
) {
    val style: NoteCharStyle get() = NoteCharStyle(bold, italic, underline, fontSize)
}

/** Оформление одного символа: используется при пересчёте спанов. */
data class NoteCharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSize: Int = NOTE_DEFAULT_FONT_SIZE,
) {
    val isPlain: Boolean
        get() = !bold && !italic && !underline && fontSize == NOTE_DEFAULT_FONT_SIZE
}

/**
 * Абзац заметки: многострочный текст и оформление участков.
 * Маркеры списков хранятся как префикс конкретной строки внутри [text].
 */
data class NoteParagraph(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val spans: List<NoteSpan> = emptyList(),
) {
    /** Разворачивает спаны в постимвольные стили — так проще безопасно менять текст. */
    fun toCharStyles(): List<NoteCharStyle> {
        val styles = MutableList(text.length) { NoteCharStyle() }
        spans.forEach { span ->
            val from = span.start.coerceIn(0, text.length)
            val to = span.end.coerceIn(from, text.length)
            for (index in from until to) styles[index] = span.style
        }
        return styles
    }

    /**
     * Обновляет текст абзаца, сохраняя оформление неизменённых участков.
     * Вставленные символы наследуют стиль символа слева, иначе — справа.
     */
    fun withText(newText: String): NoteParagraph {
        if (newText == text) return this
        val oldStyles = toCharStyles()

        var prefix = 0
        val maxPrefix = minOf(text.length, newText.length)
        while (prefix < maxPrefix && text[prefix] == newText[prefix]) prefix++

        var suffix = 0
        val maxSuffix = minOf(text.length - prefix, newText.length - prefix)
        while (suffix < maxSuffix &&
            text[text.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }

        val insertedCount = newText.length - prefix - suffix
        val inheritedStyle = oldStyles.getOrNull(prefix - 1)
            ?: oldStyles.getOrNull(text.length - suffix)
            ?: NoteCharStyle()

        val updatedStyles = buildList {
            addAll(oldStyles.take(prefix))
            repeat(insertedCount.coerceAtLeast(0)) { add(inheritedStyle) }
            addAll(oldStyles.takeLast(suffix))
        }
        return copy(text = newText, spans = compressStyles(updatedStyles))
    }

    /** Применяет преобразование стиля к выделенному участку `[start, end)`. */
    fun applyStyle(start: Int, end: Int, transform: (NoteCharStyle) -> NoteCharStyle): NoteParagraph {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        if (from == to) return this
        val styles = toCharStyles().toMutableList()
        for (index in from until to) styles[index] = transform(styles[index])
        return copy(spans = compressStyles(styles))
    }

    /** Есть ли у всего выделения указанный признак — для подсветки кнопок панели. */
    fun rangeHas(start: Int, end: Int, predicate: (NoteCharStyle) -> Boolean): Boolean {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        if (from == to) return false
        val styles = toCharStyles()
        return (from until to).all { predicate(styles[it]) }
    }

    /** Размер шрифта выделения или null, если внутри он разный. */
    fun rangeFontSize(start: Int, end: Int): Int? {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        if (from == to) return null
        val styles = toCharStyles()
        val first = styles[from].fontSize
        return if ((from until to).all { styles[it].fontSize == first }) first else null
    }
}

/** Схлопывает посимвольные стили обратно в компактные спаны. */
fun compressStyles(styles: List<NoteCharStyle>): List<NoteSpan> {
    val spans = mutableListOf<NoteSpan>()
    var index = 0
    while (index < styles.size) {
        val current = styles[index]
        var end = index + 1
        while (end < styles.size && styles[end] == current) end++
        if (!current.isPlain) {
            spans += NoteSpan(
                start = index,
                end = end,
                bold = current.bold,
                italic = current.italic,
                underline = current.underline,
                fontSize = current.fontSize,
            )
        }
        index = end
    }
    return spans
}

/**
 * Блок заметок персонажа. Блоки можно сворачивать, чтобы длинные записи
 * не занимали весь экран.
 */
@Entity(
    tableName = "note_blocks",
    foreignKeys = [ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class NoteBlock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val title: String = "",
    /** Свёрнут ли блок; состояние сохраняется между запусками. */
    val collapsed: Boolean = false,
    val paragraphs: List<NoteParagraph> = listOf(NoteParagraph()),
    val createdAt: Long = System.currentTimeMillis(),
    /** Пользовательский порядок; по умолчанию новые блоки оказываются сверху. */
    val sortOrder: Long = -createdAt,
) {
    /** Короткая выжимка содержимого для свёрнутого состояния. */
    val preview: String
        get() = paragraphs.map { it.text.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}
