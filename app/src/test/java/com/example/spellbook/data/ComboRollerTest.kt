package com.example.spellbook.data

import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepRollResult
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.data.model.ComboWithSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Тесты броска комбинаций во всех режимах. */
class ComboRollerTest {

    private companion object {
        const val CHARACTER_ID = "char-1"
        const val SEED = 42
        const val DICE_COUNT = 3
        const val DICE_SIDES = 6
        const val MODIFIER = 2
    }

    private val combo = Combo(id = "combo-1", characterId = CHARACTER_ID, name = "Атака")

    /** Персонаж для вычисления переменных: бонус мастерства 2, модификатор силы +3. */
    private val character = Character(
        id = CHARACTER_ID,
        name = "Тест",
        level = 1,
        abilityScores = mapOf(0 to 16),
    )

    /** Шаг с выражением вида `3d6 + 2`. */
    private fun diceStep(
        id: String = "step-dice",
        count: Int = DICE_COUNT,
        sides: Int = DICE_SIDES,
        modifier: Int = MODIFIER,
        effect: String = "",
    ): ComboStep {
        val sign = if (modifier < 0) " - ${-modifier}" else if (modifier > 0) " + $modifier" else ""
        return step(id = id, expression = "${count}d$sides$sign", effect = effect)
    }

    private fun constantStep(value: Int, effect: String = "") =
        step(id = "step-const", expression = value.toString(), effect = effect)

    private fun step(id: String, expression: String, effect: String = "") = ComboStep(
        id = id,
        characterId = CHARACTER_ID,
        name = "Шаг",
        expression = expression,
        effect = effect,
    )

    private fun roll(step: ComboStep, mode: ComboRollMode): ComboStepRollResult =
        ComboRoller.roll(ComboWithSteps(combo, listOf(step)), mode, character, Random(SEED)).steps.single()

    @Test
    fun `normal roll produces requested dice count and applies modifier`() {
        val result = roll(diceStep(), ComboRollMode.NORMAL)

        assertEquals(DICE_COUNT, result.rolls.size)
        assertTrue("Values out of range: ${result.rolls}", result.rolls.all { it in 1..DICE_SIDES })
        assertEquals(result.rolls.sum() + MODIFIER, result.total)
    }

    @Test
    fun `roll with the same seed is reproducible`() {
        val first = roll(diceStep(), ComboRollMode.NORMAL)
        val second = roll(diceStep(), ComboRollMode.NORMAL)

        assertEquals(first.rolls, second.rolls)
    }

    @Test
    fun `maximum mode returns max value on every die`() {
        val result = roll(diceStep(), ComboRollMode.MAXIMUM)

        assertEquals(List(DICE_COUNT) { DICE_SIDES }, result.rolls)
        assertEquals(DICE_COUNT * DICE_SIDES + MODIFIER, result.total)
    }

    @Test
    fun `classic critical doubles dice and keeps single modifier`() {
        val result = roll(diceStep(), ComboRollMode.CRITICAL_CLASSIC)

        assertEquals(DICE_COUNT * 2, result.rolls.size)
        assertTrue(result.rolls.all { it in 1..DICE_SIDES })
        assertEquals(result.rolls.sum() + MODIFIER, result.total)
    }

    @Test
    fun `homebrew critical returns maximum plus a normal roll`() {
        val result = roll(diceStep(), ComboRollMode.CRITICAL_HOMEBREW)

        assertEquals(DICE_COUNT * 2, result.rolls.size)
        assertEquals(List(DICE_COUNT) { DICE_SIDES }, result.rolls.take(DICE_COUNT))
        assertTrue(result.rolls.drop(DICE_COUNT).all { it in 1..DICE_SIDES })
        assertEquals(result.rolls.sum() + MODIFIER, result.total)
    }

    @Test
    fun `negative modifier is subtracted from total`() {
        val result = roll(diceStep(modifier = -3), ComboRollMode.MAXIMUM)

        assertEquals(DICE_COUNT * DICE_SIDES - 3, result.total)
    }

    @Test
    fun `constant step rolls no dice`() {
        val result = roll(constantStep(value = 7), ComboRollMode.CRITICAL_CLASSIC)

        assertTrue(result.rolls.isEmpty())
        assertEquals(7, result.total)
    }

    @Test
    fun `variables are resolved from character stats`() {
        // Сила 16 даёт модификатор +3, бонус мастерства на 1 уровне равен 2.
        val result = roll(step(id = "s-var", expression = "[str] + [pb]"), ComboRollMode.NORMAL)

        assertEquals(5, result.total)
    }

    @Test
    fun `dice and variables can be combined in one expression`() {
        val result = roll(step(id = "s-mix", expression = "2d6 + [str]"), ComboRollMode.MAXIMUM)

        assertEquals(listOf(6, 6), result.rolls)
        assertEquals(15, result.total)
    }

    @Test
    fun `breakdown shows each dice and constant separately`() {
        val result = roll(step(id = "s-break", expression = "2d6 + 5"), ComboRollMode.MAXIMUM)

        assertEquals("6 + 6 + 5", result.breakdown)
        assertEquals(17, result.total)
    }

    @Test
    fun `breakdown replaces variables with their values`() {
        // Сила 16 даёт модификатор +3.
        val result = roll(step(id = "s-var-break", expression = "[str]"), ComboRollMode.NORMAL)

        assertEquals("3", result.breakdown)
    }

    @Test
    fun `legacy step breakdown includes modifier`() {
        val legacy = ComboStep(
            id = "legacy-break",
            characterId = CHARACTER_ID,
            name = "Старый шаг",
            type = ComboStepType.DICE.name,
            diceCount = 2,
            diceSides = 4,
            modifier = 1,
        )

        val result = roll(legacy, ComboRollMode.MAXIMUM)

        assertEquals("4 + 4 + 1", result.breakdown)
    }

    @Test
    fun `invalid expression gives zero without crashing`() {
        val result = roll(step(id = "s-bad", expression = "2d6 + [unknown]"), ComboRollMode.NORMAL)

        assertTrue(result.rolls.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun `legacy step without expression uses stored numbers`() {
        val legacy = ComboStep(
            id = "legacy",
            characterId = CHARACTER_ID,
            name = "Старый шаг",
            type = ComboStepType.DICE.name,
            diceCount = 2,
            diceSides = 4,
            modifier = 1,
        )

        val result = roll(legacy, ComboRollMode.MAXIMUM)

        assertEquals(listOf(4, 4), result.rolls)
        assertEquals(2 * 4 + 1, result.total)
    }

    @Test
    fun `combo result sums steps and collects effects`() {
        val steps = listOf(
            diceStep(id = "s1", count = 2, sides = 4, modifier = 1, effect = "Поджог"),
            constantStep(value = 5),
        )

        val result = ComboRoller.roll(
            ComboWithSteps(combo, steps),
            ComboRollMode.MAXIMUM,
            character,
            Random(SEED),
        )

        assertEquals(2 * 4 + 1 + 5, result.total)
        assertEquals(listOf("Поджог"), result.effects)
        assertEquals(combo, result.combo)
        assertEquals(ComboRollMode.MAXIMUM, result.mode)
    }

    @Test
    fun `combo without steps gives zero total`() {
        val result = ComboRoller.roll(
            ComboWithSteps(combo, emptyList()),
            ComboRollMode.NORMAL,
            character,
            Random(SEED),
        )

        assertEquals(0, result.total)
        assertTrue(result.effects.isEmpty())
    }
}
