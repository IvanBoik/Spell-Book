package com.example.spellbook.data

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

    private fun diceStep(
        id: String = "step-dice",
        count: Int = DICE_COUNT,
        sides: Int = DICE_SIDES,
        modifier: Int = MODIFIER,
        effect: String = "",
    ) = ComboStep(
        id = id,
        characterId = CHARACTER_ID,
        name = "Кости",
        type = ComboStepType.DICE.name,
        diceCount = count,
        diceSides = sides,
        modifier = modifier,
        effect = effect,
    )

    private fun constantStep(value: Int, modifier: Int = 100, effect: String = "") = ComboStep(
        id = "step-const",
        characterId = CHARACTER_ID,
        name = "Число",
        type = ComboStepType.CONSTANT.name,
        flatValue = value,
        modifier = modifier,
        effect = effect,
    )

    private fun roll(step: ComboStep, mode: ComboRollMode): ComboStepRollResult =
        ComboRoller.roll(ComboWithSteps(combo, listOf(step)), mode, Random(SEED)).steps.single()

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
    fun `constant step rolls no dice and ignores modifier`() {
        val result = roll(constantStep(value = 7), ComboRollMode.CRITICAL_CLASSIC)

        assertTrue(result.rolls.isEmpty())
        assertEquals(7, result.total)
    }

    @Test
    fun `invalid dice parameters are coerced to minimum allowed`() {
        val result = roll(diceStep(count = 0, sides = 1, modifier = 0), ComboRollMode.MAXIMUM)

        assertEquals(listOf(2), result.rolls)
    }

    @Test
    fun `combo result sums steps and collects effects`() {
        val steps = listOf(
            diceStep(id = "s1", count = 2, sides = 4, modifier = 1, effect = "Поджог"),
            constantStep(value = 5, effect = ""),
        )

        val result = ComboRoller.roll(ComboWithSteps(combo, steps), ComboRollMode.MAXIMUM, Random(SEED))

        assertEquals(2 * 4 + 1 + 5, result.total)
        assertEquals(listOf("Поджог"), result.effects)
        assertEquals(combo, result.combo)
        assertEquals(ComboRollMode.MAXIMUM, result.mode)
    }

    @Test
    fun `combo without steps gives zero total`() {
        val result = ComboRoller.roll(ComboWithSteps(combo, emptyList()), ComboRollMode.NORMAL, Random(SEED))

        assertEquals(0, result.total)
        assertTrue(result.effects.isEmpty())
    }
}
