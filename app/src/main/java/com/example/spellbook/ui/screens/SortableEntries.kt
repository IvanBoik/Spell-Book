package com.example.spellbook.ui.screens

import com.example.spellbook.util.HtmlUtils

/**
 * Способ упорядочивания списка записей раздела — те же два варианта, что на dnd.su.
 */
enum class EntrySortOrder {
    /** По алфавиту. */
    NAME,

    /** По требуемому уровню; записи без требования доступны сразу и идут первыми. */
    LEVEL,
}

/**
 * Одна запись сортируемого списка: инфузия изобретателя, воззвание колдуна и т. п.
 *
 * @param title заголовок записи (подзаголовок `### ` в описании).
 * @param body текст записи вместе с требованиями.
 * @param level минимальный уровень из требования; null — требования по уровню нет.
 */
data class SortableEntry(val title: String, val body: String, val level: Int?)

/**
 * Раздел со списком записей, который на dnd.su можно сортировать.
 *
 * @param intro текст до первой записи.
 * @param entries записи в порядке сайта.
 */
data class SortableSection(val intro: String, val entries: List<SortableEntry>) {

    /**
     * Записи в выбранном порядке.
     *
     * Названия сравниваются без учёта регистра и с «е» вместо «ё»: на сайте часть
     * заголовков набрана капсом, и без этого «Аспект луны» оказывался после «ВОР ПЯТИ СУДЕБ».
     */
    fun sorted(order: EntrySortOrder): List<SortableEntry> = when (order) {
        EntrySortOrder.NAME -> entries.sortedBy { it.sortKey() }
        EntrySortOrder.LEVEL -> entries.sortedWith(compareBy({ it.level ?: 0 }, { it.sortKey() }))
    }
}

private fun SortableEntry.sortKey(): String = title.lowercase().replace('ё', 'е')

/**
 * Маркер блока сортировки: на dnd.su это выпадающий список над перечнем записей.
 *
 * В текст описания он попадает подписью и двумя пунктами («По уровню», «По названию»),
 * которые сами по себе бесполезны — вместо них показываются рабочие кнопки.
 */
private const val SORT_MARKER = "Сортировка:"

/**
 * Требование по уровню: «Требование: 14-й уровень изобретателя», «6 уровень».
 * Ищется только в строке требования, иначе в уровень попадали бы числа из текста
 * записи вроде «заклинание 3 уровня».
 */
private val LEVEL_REGEX = Regex("(\\d+)(?:\\s*-\\s*[а-яё]+)?\\s+уровень", RegexOption.IGNORE_CASE)

/** Строка с требованием записи. */
private const val REQUIREMENT_LABEL = "Требование"

/**
 * Разбирает тело раздела на сортируемые записи.
 *
 * Возвращает null, если раздел сортировку не предполагает: признаком служит
 * оставшийся от сайта маркер [SORT_MARKER]. Так сортировка появляется ровно там,
 * где она есть в оригинале, — у инфузий изобретателя и воззваний колдуна.
 */
fun parseSortableSection(body: String): SortableSection? {
    val lines = body.split("\n")
    val markerIndex = lines.indexOfFirst { it.trim().startsWith(SORT_MARKER) }
    if (markerIndex < 0) return null

    val intro = lines.take(markerIndex).joinToString("\n").trim()

    // Пункты сразу после маркера — это варианты сортировки с сайта, они не нужны.
    var index = markerIndex + 1
    while (index < lines.size && HtmlUtils.isBulletItem(lines[index].trim())) index++

    val entries = mutableListOf<SortableEntry>()
    var title: String? = null
    val entryBody = mutableListOf<String>()

    fun flush() {
        val currentTitle = title ?: return
        val text = entryBody.joinToString("\n").trim()
        entries += SortableEntry(title = currentTitle, body = text, level = requiredLevel(text))
        entryBody.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        if (HtmlUtils.isSubheading(line.trim())) {
            flush()
            title = HtmlUtils.subheadingText(line.trim())
        } else {
            entryBody += line
        }
        index++
    }
    flush()

    // Без записей сортировать нечего — раздел рисуется как обычно.
    return if (entries.isEmpty()) null else SortableSection(intro = intro, entries = entries)
}

/** Уровень из строки требования записи; null — требования по уровню нет. */
private fun requiredLevel(text: String): Int? =
    text.lineSequence()
        .firstOrNull { it.contains(REQUIREMENT_LABEL, ignoreCase = true) }
        ?.let { LEVEL_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
