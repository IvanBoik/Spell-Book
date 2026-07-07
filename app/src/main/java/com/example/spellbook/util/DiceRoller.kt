package com.example.spellbook.util

import kotlin.random.Random

/**
 * Бросок кубиков по формулам формата LSS вида `NdM` (например, `2d6`).
 *
 * В описаниях заклинаний кости размечаются токеном `[[/r 2d6]]`; для их поиска
 * используется [DICE_TOKEN_REGEX], а сам бросок выполняет [roll].
 */
object DiceRoller {

    /** Токен кости в тексте описания: `[[/r 2d6]]`. Группа 1 — формула `2d6`. */
    val DICE_TOKEN_REGEX = Regex("""\[\[/r\s*(.+?)\s*]]""")

    /** Необернутые кости в тексте: `1d6`, `5d10`, `2d8`. */
    private val PLAIN_DICE_REGEX = Regex("""\b(\d+)d(\d+)\b""", RegexOption.IGNORE_CASE)

    /** Формула кости `NdM`: количество (по умолчанию 1) и число граней. */
    private val DICE_FORMULA_REGEX = Regex("""(\d*)d(\d+)""", RegexOption.IGNORE_CASE)

    private const val MAX_DICE_COUNT = 1000

    /** Результат броска: исходная [formula], список выпавших значений [rolls] и их [total]. */
    data class Result(
        val formula: String,
        val rolls: List<Int>,
        val total: Int,
    )

    /**
     * Оборачивает в тексте необернутые кости `NdM` в токены `[[/r NdM]]`,
     * пропуская вхождения, которые уже внутри `[[/r ...]]` и не плодя двойных оберток.
     */
    fun wrapDiceTokens(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder()
        var lastIndex = 0
        DICE_TOKEN_REGEX.findAll(text).forEach { match ->
            out.append(wrapPlainDice(text.substring(lastIndex, match.range.first)))
            out.append(match.value)
            lastIndex = match.range.last + 1
        }
        out.append(wrapPlainDice(text.substring(lastIndex)))
        return out.toString()
    }

    private fun wrapPlainDice(text: String): String =
        PLAIN_DICE_REGEX.replace(text) { "[[/r ${it.value}]]" }

    /** Бросает кости по формуле `NdM`. Возвращает null, если формула не распознана. */
    fun roll(formula: String): Result? {
        val trimmed = formula.trim()
        val match = DICE_FORMULA_REGEX.matchEntire(trimmed) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        val sides = match.groupValues[2].toIntOrNull() ?: return null
        if (count !in 1..MAX_DICE_COUNT || sides < 1) return null
        val rolls = List(count) { Random.nextInt(1, sides + 1) }
        return Result(formula = trimmed, rolls = rolls, total = rolls.sum())
    }
}
