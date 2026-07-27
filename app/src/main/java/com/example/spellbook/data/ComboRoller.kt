package com.example.spellbook.data

import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepRollResult
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.data.model.ComboWithSteps
import kotlin.random.Random

/** Выполняет все шаги комбинации и сохраняет детализацию каждого броска. */
object ComboRoller {
    fun roll(
        combo: ComboWithSteps,
        mode: ComboRollMode,
        random: Random = Random.Default,
    ): ComboRollResult = ComboRollResult(
        combo = combo.combo,
        mode = mode,
        steps = combo.steps.map { rollStep(it, mode, random) },
    )

    private fun rollStep(step: ComboStep, mode: ComboRollMode, random: Random): ComboStepRollResult {
        if (step.stepType == ComboStepType.CONSTANT) {
            return ComboStepRollResult(step, emptyList(), step.flatValue)
        }
        val count = step.diceCount.coerceAtLeast(1)
        val sides = step.diceSides.coerceAtLeast(2)
        fun randomDice(): List<Int> = List(count) { random.nextInt(1, sides + 1) }
        fun maximumDice(): List<Int> = List(count) { sides }

        val rolls = when (mode) {
            ComboRollMode.NORMAL -> randomDice()
            ComboRollMode.MAXIMUM -> maximumDice()
            // Все кости пробрасываются два раза, плоский модификатор добавляется один раз.
            ComboRollMode.CRITICAL_CLASSIC -> randomDice() + randomDice()
            // Максимум на основном наборе костей + ещё один обычный бросок всех костей.
            ComboRollMode.CRITICAL_HOMEBREW -> maximumDice() + randomDice()
        }
        return ComboStepRollResult(step, rolls, rolls.sum() + step.modifier)
    }
}
