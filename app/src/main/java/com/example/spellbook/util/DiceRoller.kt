package com.example.spellbook.util

import kotlin.random.Random

/**
 * Бросок кубиков по формулам вида `NdM` или `NкM` с необязательным модификатором:
 * `2d6`, `6к10`, `10к6 + 40`.
 *
 * Русская «к» обязательна: именно так кости записаны в русских описаниях заклинаний.
 * Кости могут быть размечены токеном `[[/r 2d6]]` ([DICE_TOKEN_REGEX]) либо встречаться
 * в тексте как есть ([PLAIN_DICE_REGEX]) — кликабельны оба варианта.
 */
object DiceRoller {

    /** Токен кости в тексте описания: `[[/r 2d6]]`. Группа 1 — формула `2d6`. */
    val DICE_TOKEN_REGEX = Regex("""\[\[/r\s*(.+?)\s*]]""")

    /**
     * Кости в обычном тексте: `1d6`, `6к10`, `10к6 + 40`.
     * Модификатор захватывается только вместе со знаком сразу после кости.
     */
    val PLAIN_DICE_REGEX = Regex(
        // Негативный lookahead не даёт забрать в модификатор начало следующей кости:
        // в тексте `1d4 + 5d10` это два броска, а не один с модификатором.
        """\b\d+[dк]\d+(?:\s*[+−–-]\s*\d+(?![dк]\d))?""",
        RegexOption.IGNORE_CASE,
    )

    /** Формула кости: количество (по умолчанию 1), число граней и модификатор. */
    private val DICE_FORMULA_REGEX = Regex(
        """(\d*)[dк](\d+)(?:\s*([+−–-])\s*(\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_DICE_COUNT = 1000

    /** Знаки вычитания, встречающиеся в текстах: обычный дефис, минус и тире. */
    private const val MINUS_SIGNS = "-−–"

    /**
     * Результат броска: исходная [formula], выпавшие значения [rolls],
     * прибавленный [modifier] и итог [total].
     */
    data class Result(
        val formula: String,
        val rolls: List<Int>,
        val total: Int,
        val modifier: Int = 0,
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

    /**
     * Бросает кости по формуле `NdM` / `NкM` с необязательным модификатором.
     * Возвращает null, если формула не распознана.
     */
    fun roll(formula: String): Result? {
        val trimmed = formula.trim()
        val match = DICE_FORMULA_REGEX.matchEntire(trimmed) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: 1
        val sides = match.groupValues[2].toIntOrNull() ?: return null
        if (count !in 1..MAX_DICE_COUNT || sides < 1) return null

        val sign = match.groupValues[3]
        val amount = match.groupValues[4].toIntOrNull() ?: 0
        val modifier = if (sign.isNotEmpty() && sign[0] in MINUS_SIGNS) -amount else amount

        val rolls = List(count) { Random.nextInt(1, sides + 1) }
        return Result(
            formula = trimmed,
            rolls = rolls,
            total = rolls.sum() + modifier,
            modifier = modifier,
        )
    }
}
