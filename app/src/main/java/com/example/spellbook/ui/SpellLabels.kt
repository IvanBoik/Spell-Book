package com.example.spellbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.FormulaVariable
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.SpellOptions.SpellOption
import com.example.spellbook.data.StatFormula

/**
 * Подписи справочников заклинаний на языке интерфейса.
 *
 * Коды LSS хранятся в базе как есть, а пользователю показывается перевод: так смена
 * языка не затрагивает сами данные заклинаний.
 */
@Composable
fun spellOptionLabel(options: List<SpellOption>, code: String): String =
    SpellOptions.optionFor(options, code)?.let { stringResource(it.labelRes) } ?: code

/** Пары «код — подпись» для выпадающих списков и фильтров. */
@Composable
fun spellOptionPairs(options: List<SpellOption>): List<Pair<String, String>> {
    val labels = options.map { stringResource(it.labelRes) }
    return remember(options, labels) { options.mapIndexed { index, option -> option.code to labels[index] } }
}

/** Подпись круга заклинания: «Заговор» либо «N круг». */
@Composable
fun spellLevelLabel(level: Int): String =
    if (level == 0) stringResource(R.string.spell_level_cantrip) else stringResource(R.string.spell_level_n, level)

/** Пары «круг — подпись» для фильтров и формы. */
@Composable
fun spellLevelPairs(): List<Pair<Int, String>> = SpellOptions.levels.map { it to spellLevelLabel(it) }

/**
 * Счётные формы единиц измерения для кодов LSS.
 * Склонением занимается Android: в разных языках разное число форм.
 */
private val MEASURE_PLURALS: Map<String, Int> = mapOf(
    "minute" to R.plurals.measure_minute,
    "hour" to R.plurals.measure_hour,
    "day" to R.plurals.measure_day,
    "round" to R.plurals.measure_round,
    "turn" to R.plurals.measure_turn,
    "ft" to R.plurals.measure_feet,
    "mi" to R.plurals.measure_mile,
    "action" to R.plurals.measure_action,
    "bonus" to R.plurals.measure_bonus_action,
    "reaction" to R.plurals.measure_reaction,
)

/**
 * Подпись вида «1 минута» / «10 минут». Если у кода нет счётных форм
 * («Мгновенная», «Касание» и т. п.), берётся обычная подпись из справочника.
 */
/** Подпись переменной формулы в подсказках. */
@Composable
fun formulaVariableLabel(variable: FormulaVariable): String =
    if (variable.abilityLabelRes != null) {
        stringResource(variable.labelRes, stringResource(variable.abilityLabelRes))
    } else {
        stringResource(variable.labelRes)
    }

/**
 * Формула с читаемыми названиями переменных: `2d6 + [str]` → `2d6 + Сила`.
 * Подписи берутся из ресурсов, а сама формула в базе остаётся в кодах.
 */
@Composable
fun humanizeFormula(expression: String): String {
    val names = StatFormula.VARIABLE_NAME_RES.mapValues { (_, res) -> stringResource(res) }
    return StatFormula.humanize(expression, names)
}

@Composable
fun measureLabel(code: String, value: Int?, options: List<SpellOption>): String {
    val pluralRes = MEASURE_PLURALS[code] ?: return spellOptionLabel(options, code)
    // Единица по умолчанию — одна: «1 действие» читается лучше, чем просто «Действие».
    val count = value?.takeIf { it > 0 } ?: 1
    return pluralStringResource(pluralRes, count, count)
}
