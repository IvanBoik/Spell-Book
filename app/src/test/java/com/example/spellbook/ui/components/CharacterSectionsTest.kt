package com.example.spellbook.ui.components

import com.example.spellbook.data.SectionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты состава и порядка разделов персонажа.
 *
 * Ключевые правила: главная страница — характеристики (её и настройки скрыть нельзя),
 * а остальные разделы, включая заклинания, пользователь может отключить.
 */
class CharacterSectionsTest {

    @Test
    fun `home section is the stats page`() {
        assertEquals(CharacterSection.STATS, CharacterSection.HOME)
    }

    @Test
    fun `default order starts with stats and spells and ends with character settings`() {
        val sections = CharacterSection.ordered(SectionLayout.DEFAULT)

        assertEquals(CharacterSection.STATS, sections.first())
        assertEquals(CharacterSection.SPELLS, sections[1])
        assertEquals(CharacterSection.SETTINGS, sections.last())
    }

    @Test
    fun `default layout keeps declaration order`() {
        val sections = CharacterSection.ordered(SectionLayout.DEFAULT)

        assertEquals(CharacterSection.entries, sections)
    }

    @Test
    fun `custom order is applied and unknown sections go last`() {
        val layout = SectionLayout(order = listOf("NOTES", "STATS"))

        val sections = CharacterSection.ordered(layout)

        assertEquals(CharacterSection.NOTES, sections[0])
        assertEquals(CharacterSection.STATS, sections[1])
        // Остальные разделы не теряются — они просто уезжают в конец.
        assertEquals(CharacterSection.entries.size, sections.size)
    }

    @Test
    fun `home and settings sections never get hidden from the bar`() {
        val layout = SectionLayout(hidden = CharacterSection.entries.map { it.name }.toSet())

        val sections = CharacterSection.arrange(layout, showPrepare = true)

        assertEquals(listOf(CharacterSection.STATS, CharacterSection.SETTINGS), sections)
    }

    @Test
    fun `spells section can be hidden for characters without magic`() {
        val layout = SectionLayout(hidden = setOf("SPELLS"))

        val sections = CharacterSection.arrange(layout, showPrepare = true)

        assertFalse(CharacterSection.SPELLS in sections)
        // Главная страница остаётся доступной в любом случае.
        assertTrue(CharacterSection.HOME in sections)
    }

    @Test
    fun `current section stays visible even when hidden`() {
        val layout = SectionLayout(hidden = setOf("NOTES"))

        val sections = CharacterSection.arrange(layout, showPrepare = true, keepVisible = CharacterSection.NOTES)

        assertTrue(CharacterSection.NOTES in sections)
    }

    @Test
    fun `prepare section is unavailable for characters that do not prepare spells`() {
        val sections = CharacterSection.arrange(SectionLayout.DEFAULT, showPrepare = false)

        assertFalse(CharacterSection.PREPARE in sections)
    }

    @Test
    fun `hidden sections remain in the settings list so they can be turned back on`() {
        val layout = SectionLayout(hidden = setOf("SPELLS", "NOTES"))

        val sections = CharacterSection.ordered(layout)

        assertTrue(CharacterSection.SPELLS in sections)
        assertTrue(CharacterSection.NOTES in sections)
    }
}
