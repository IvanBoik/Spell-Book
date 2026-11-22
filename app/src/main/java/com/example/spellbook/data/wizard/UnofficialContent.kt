package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.CharClass

/**
 * Признаки неофициального контента в библиотеках: Unearthed Arcana, homebrew, Plane Shift
 * и сторонние книги (Midgard). Общие для рас, архетипов и классов.
 */
object UnofficialContent {

    /** Подстроки (в нижнем регистре) названий книг и разделов каталога с неофициальными материалами. */
    val BOOK_MARKERS = listOf("unearthed", "homebrew", "unofficial", "plane shift", "midgard")

    /** Раздел пользовательских материалов dnd.su в ссылке. */
    private const val HOMEBREW_PATH = "/homebrew/"

    /** Книга или раздел каталога — неофициальные. */
    fun isUnofficialBook(text: String): Boolean {
        val lower = text.lowercase()
        return BOOK_MARKERS.any { lower.contains(it) }
    }

    /**
     * Неофициальный класс библиотеки — определяется автоматически:
     * - homebrew-страница dnd.su (`/homebrew/` в ссылке);
     * - книга из Unearthed Arcana или сторонняя ([BOOK_MARKERS]);
     * - класс создан пользователем: у встроенных и загруженных с dnd.su всегда есть ссылка.
     */
    fun isUnofficialClass(charClass: CharClass): Boolean =
        charClass.source.isBlank() ||
            charClass.source.contains(HOMEBREW_PATH, ignoreCase = true) ||
            isUnofficialBook(charClass.book)
}
