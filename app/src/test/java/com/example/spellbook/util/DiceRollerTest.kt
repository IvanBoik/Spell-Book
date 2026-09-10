package com.example.spellbook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты разметки и броска костей формата `NdM` / `[[/r NdM]]`. */
class DiceRollerTest {

    // region wrapDiceTokens

    @Test
    fun `wrapDiceTokens wraps plain dice into tokens`() {
        assertEquals("Урон [[/r 2d6]] огнём", DiceRoller.wrapDiceTokens("Урон 2d6 огнём"))
    }

    @Test
    fun `wrapDiceTokens does not create double wrapping`() {
        assertEquals(
            "Урон [[/r 2d6]] и [[/r 1d4]]",
            DiceRoller.wrapDiceTokens("Урон [[/r 2d6]] и 1d4"),
        )
    }

    @Test
    fun `wrapDiceTokens keeps already marked text unchanged`() {
        val text = "Урон [[/r 2d6]] огнём"
        assertEquals(text, DiceRoller.wrapDiceTokens(text))
    }

    @Test
    fun `wrapDiceTokens wraps several dice in a row`() {
        assertEquals(
            "[[/r 1d4]] + [[/r 5d10]]",
            DiceRoller.wrapDiceTokens("1d4 + 5d10"),
        )
    }

    @Test
    fun `wrapDiceTokens ignores formula without dice count`() {
        assertEquals("бросок d20", DiceRoller.wrapDiceTokens("бросок d20"))
    }

    @Test
    fun `wrapDiceTokens returns empty string for empty input`() {
        assertEquals("", DiceRoller.wrapDiceTokens(""))
    }

    @Test
    fun `dice token regex extracts formula from token`() {
        val match = DiceRoller.DICE_TOKEN_REGEX.find("текст [[/r 3d8]] далее")
        assertEquals("3d8", match?.groupValues?.get(1))
    }

    // endregion

    // region roll

    @Test
    fun `roll returns requested dice count within allowed range`() {
        val result = requireNotNull(DiceRoller.roll("2d6"))

        assertEquals("2d6", result.formula)
        assertEquals(2, result.rolls.size)
        assertTrue("All values must be in 1..6: ${result.rolls}", result.rolls.all { it in 1..6 })
        assertEquals(result.rolls.sum(), result.total)
    }

    @Test
    fun `roll without count throws a single die`() {
        val result = requireNotNull(DiceRoller.roll("d20"))

        assertEquals(1, result.rolls.size)
        assertTrue(result.rolls.single() in 1..20)
    }

    @Test
    fun `roll trims spaces around formula`() {
        assertEquals("2d4", DiceRoller.roll("  2d4  ")?.formula)
    }

    @Test
    fun `roll of d1 always returns one per die`() {
        assertEquals(3, DiceRoller.roll("3d1")?.total)
    }

    @Test
    fun `roll returns null for invalid formulas`() {
        listOf("", "  ", "abc", "0d6", "2d0", "1001d6", "d").forEach { formula ->
            assertNull("Formula '$formula' must be rejected", DiceRoller.roll(formula))
        }
    }

    @Test
    fun `roll supports russian dice letter`() {
        // В русских описаниях заклинаний кости записаны через «к».
        val result = requireNotNull(DiceRoller.roll("6к10"))

        assertEquals(6, result.rolls.size)
        assertTrue(result.rolls.all { it in 1..10 })
        assertEquals(result.rolls.sum(), result.total)
    }

    @Test
    fun `roll adds positive modifier to total`() {
        val result = requireNotNull(DiceRoller.roll("10к6 + 40"))

        assertEquals(10, result.rolls.size)
        assertEquals(40, result.modifier)
        assertEquals(result.rolls.sum() + 40, result.total)
    }

    @Test
    fun `roll subtracts negative modifier from total`() {
        val result = requireNotNull(DiceRoller.roll("2d6-3"))

        assertEquals(-3, result.modifier)
        assertEquals(result.rolls.sum() - 3, result.total)
    }

    @Test
    fun `plain dice regex finds dice written in text`() {
        val found = DiceRoller.PLAIN_DICE_REGEX.findAll("Урон 6к10 и 10к6 + 40 огнём")
            .map { it.value }
            .toList()

        assertEquals(listOf("6к10", "10к6 + 40"), found)
    }

    @Test
    fun `plain dice regex does not capture unrelated numbers`() {
        // Число после кости без знака модификатором не является.
        val found = DiceRoller.PLAIN_DICE_REGEX.find("урон 2d6 в течение 10 минут")

        assertEquals("2d6", found?.value)
    }

    // endregion
}
