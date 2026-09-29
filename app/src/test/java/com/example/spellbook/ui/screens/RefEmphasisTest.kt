package com.example.spellbook.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки настроек экрана статьи: шапка и выделение терминов-ссылок. */
class RefEmphasisTest {

    @Test
    fun `header is kept while it has something to show`() {
        // Расы и классы: есть и книга, и меню разделов.
        assertTrue(needsHeader(book = "Player's Handbook", showSectionMenu = true))
        // Предыстории: меню выключено, но книгу показать надо.
        assertTrue(needsHeader(book = "Player's Handbook", showSectionMenu = false))
        // Запись без источника, но с меню разделов.
        assertTrue(needsHeader(book = "", showSectionMenu = true))
    }

    @Test
    fun `header is dropped when it would be empty`() {
        // Иначе пустой блок оставил бы лишний отступ над текстом.
        assertFalse(needsHeader(book = "   ", showSectionMenu = false))
    }

    @Test
    fun `both emphasis modes are available`() {
        // Порядок фиксируем: STRONG — поведение по умолчанию для остальных разделов.
        assertEquals(
            listOf(RefEmphasis.STRONG, RefEmphasis.SUBTLE),
            RefEmphasis.entries.toList(),
        )
    }
}
