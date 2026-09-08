package com.example.spellbook.data

import com.example.spellbook.data.model.Character
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты разбора выражений и отображения переменных. */
class StatFormulaTest {

    /** Сила 16 даёт модификатор +3, бонус мастерства на 5 уровне равен 3. */
    private val character = Character(level = 5, abilityScores = mapOf(0 to 16))

    @Test
    fun `humanize replaces variable codes with readable names`() {
        assertEquals("2d6 + Сила", StatFormula.humanize("2d6 + [str]"))
        assertEquals("Бонус мастерства × 2", StatFormula.humanize("[pb] × 2"))
        assertEquals("Уровень / 2", StatFormula.humanize("[level] / 2"))
    }

    @Test
    fun `humanize supports all ability variables`() {
        val humanized = StatFormula.humanize("[str] [dex] [con] [int] [wis] [cha]")

        assertEquals("Сила Ловкость Телосложение Интеллект Мудрость Харизма", humanized)
    }

    @Test
    fun `humanize keeps unknown variables untouched`() {
        assertEquals("2d6 + [unknown]", StatFormula.humanize("2d6 + [unknown]"))
    }

    @Test
    fun `humanize ignores case and spaces inside brackets`() {
        assertEquals("Сила", StatFormula.humanize("[ STR ]"))
    }

    @Test
    fun `humanize leaves plain expressions unchanged`() {
        assertEquals("2d6 + 5", StatFormula.humanize("2d6 + 5"))
    }

    @Test
    fun `variables are resolved from character`() {
        assertEquals(6, StatFormula.evaluateValue("[str] + [pb]", character))
    }

    @Test
    fun `division rounds down`() {
        // Уровень 5, деление округляется вниз — как в правилах D&D.
        assertEquals(2, StatFormula.evaluateValue("[level] / 2", character))
    }

    @Test
    fun `invalid expression is rejected`() {
        assertNull(StatFormula.evaluateValue("2d6 + [oops]", character))
        assertTrue(StatFormula.isValid("2d6 + [str]"))
    }
}
