package com.example.spellbook.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты распознавания ссылок на страницу заклинания dnd.su. */
class DndSuLoaderTest {

    @Test
    fun `spell url is recognized`() {
        assertTrue(DndSuLoader.isSpellUrl("https://dnd.su/spells/123-fireball/"))
    }

    @Test
    fun `case and spaces do not affect recognition`() {
        assertTrue(DndSuLoader.isSpellUrl("  HTTPS://DND.SU/SPELLS/123-Fireball/  "))
    }

    @Test
    fun `url to another site section is rejected`() {
        assertFalse(DndSuLoader.isSpellUrl("https://dnd.su/items/123-sword/"))
    }

    @Test
    fun `url to another host is rejected`() {
        assertFalse(DndSuLoader.isSpellUrl("https://example.com/spells/fireball"))
    }

    @Test
    fun `blank string is not a spell url`() {
        assertFalse(DndSuLoader.isSpellUrl(""))
        assertFalse(DndSuLoader.isSpellUrl("   "))
    }
}
