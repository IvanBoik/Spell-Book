package com.example.spellbook.data

import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepRollResult
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.data.model.ComboWithSteps
import kotlin.random.Random

/** Выполняет все шаги комбинации и сохраняет детализацию каждого броска. */
object ComboRoller {
    /**
     * @param character нужен для вычисления переменных в выражениях шагов
     * (бонус мастерства, модификаторы характеристик).
     */
    fun roll(
        combo: ComboWithSteps,
        mode: ComboRollMode,
        character: Character? = null,
        random: Random = Random.Default,
    ): ComboRollResult = ComboRollResult(
        combo = combo.combo,
        mode = mode,
        steps = combo.steps.map { rollStep(it, mode, character, random) },
    )

    private fun rollStep(
        step: ComboStep,
        mode: ComboRollMode,
        character: Character?,
        random: Random,
    ): ComboStepRollResult {
        // Новые шаги хранят единое выражение вида `6d8 + [int]`.
        if (step.expression.isNotBlank()) {
            val result = StatFormula.evaluate(step.expression, character ?: Character(), mode, random)
                ?: return ComboStepRollResult(step, emptyList(), 0)
            return ComboStepRollResult(step, result.rolls, result.total, result.breakdown)
        }
        return rollLegacyStep(step, mode, random)
    }

    /** Шаги, созданные до перехода на выражения, считаются по числовым полям. */
    private fun rollLegacyStep(step: ComboStep, mode: ComboRollMode, random: Random): ComboStepRollResult {
        if (step.stepType == ComboStepType.CONSTANT) {
            return ComboStepRollResult(step, emptyList(), step.flatValue, step.flatValue.toString())
        }
        val count = step.diceCount.coerceAtLeast(1)
        val sides = step.diceSides.coerceAtLeast(2)
        fun randomDice(): List<Int> = List(count) { random.nextInt(1, sides + 1) }
        fun maximumDice(): List<Int> = List(count) { sides }

        val rolls = when (mode) {
            ComboRollMode.NORMAL -> randomDice()
            ComboRollMode.MAXIMUM -> maximumDice()
            ComboRollMode.CRITICAL_CLASSIC -> randomDice() + randomDice()
            ComboRollMode.CRITICAL_HOMEBREW -> maximumDice() + randomDice()
        }
        val breakdown = buildString {
            append(rolls.joinToString(" + "))
            if (step.modifier > 0) append(" + ").append(step.modifier)
            if (step.modifier < 0) append(" - ").append(-step.modifier)
        }
        return ComboStepRollResult(step, rolls, rolls.sum() + step.modifier, breakdown)
    }
}
