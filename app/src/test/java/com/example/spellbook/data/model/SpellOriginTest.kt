package com.example.spellbook.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты признака происхождения заклинания и правил его перезаписи. */
class SpellOriginTest {

    @Test
    fun `new spell is created by user by default`() {
        assertEquals(SpellOrigin.USER, Spell().spellOrigin)
    }

    @Test
    fun `unknown origin falls back to user`() {
        // Защищает от потери пользовательских заклинаний при проблемах с данными.
        assertEquals(SpellOrigin.USER, Spell(origin = "SOMETHING_ELSE").spellOrigin)
    }

    @Test
    fun `user spell is never replaced by official download`() {
        assertFalse(Spell(origin = SpellOrigin.USER.name).isReplaceableByOfficial)
    }

    @Test
    fun `imported and official spells can be refreshed`() {
        assertTrue(Spell(origin = SpellOrigin.IMPORTED.name).isReplaceableByOfficial)
        assertTrue(Spell(origin = SpellOrigin.OFFICIAL.name).isReplaceableByOfficial)
    }

    @Test
    fun `origin survives serialization to string`() {
        SpellOrigin.entries.forEach { origin ->
            assertEquals(origin, Spell(origin = origin.name).spellOrigin)
        }
    }
}
