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

    /**
     * Подписи переменных задаются явно: в приложении они берутся из ресурсов,
     * а в JVM-тестах ресурсы недоступны.
     */
    private val names = mapOf(
        "pb" to "Бонус мастерства",
        "level" to "Уровень",
        "str" to "Сила",
        "dex" to "Ловкость",
        "con" to "Телосложение",
        "int" to "Интеллект",
        "wis" to "Мудрость",
        "cha" to "Харизма",
    )

    @Test
    fun `humanize replaces variable codes with readable names`() {
        assertEquals("2d6 + Сила", StatFormula.humanize("2d6 + [str]", names))
        assertEquals("Бонус мастерства × 2", StatFormula.humanize("[pb] × 2", names))
        assertEquals("Уровень / 2", StatFormula.humanize("[level] / 2", names))
    }

    @Test
    fun `humanize supports all ability variables`() {
        val humanized = StatFormula.humanize("[str] [dex] [con] [int] [wis] [cha]", names)

        assertEquals("Сила Ловкость Телосложение Интеллект Мудрость Харизма", humanized)
    }

    @Test
    fun `humanize keeps unknown variables untouched`() {
        assertEquals("2d6 + [unknown]", StatFormula.humanize("2d6 + [unknown]", names))
    }

    @Test
    fun `humanize ignores case and spaces inside brackets`() {
        assertEquals("Сила", StatFormula.humanize("[ STR ]", names))
    }

    @Test
    fun `humanize leaves plain expressions unchanged`() {
        assertEquals("2d6 + 5", StatFormula.humanize("2d6 + 5", names))
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
