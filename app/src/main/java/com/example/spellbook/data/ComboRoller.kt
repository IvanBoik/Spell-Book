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
        val count = step.diceCount.coerceAtLeast(1) * if (mode == ComboRollMode.CRITICAL) 2 else 1
        val sides = step.diceSides.coerceAtLeast(2)
        val rolls = List(count) {
            if (mode == ComboRollMode.MAXIMUM) sides else random.nextInt(1, sides + 1)
        }
        return ComboStepRollResult(step, rolls, rolls.sum() + step.modifier)
    }
}
