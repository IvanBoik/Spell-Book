package com.example.spellbook.util

/**
 * Склонение существительных по числу для подписей вроде «1 минута», «2 минуты», «10 минут».
 *
 * Формы задаются тройкой: для 1, для 2–4 и для 5 и более.
 */
object RussianPlurals {

    /** Формы слова: [one] — «минута», [few] — «минуты», [many] — «минут». */
    data class Forms(val one: String, val few: String, val many: String)

    /** Единицы времени и дистанции, встречающиеся в характеристиках заклинаний. */
    private val FORMS: Map<String, Forms> = mapOf(
        "minute" to Forms("минута", "минуты", "минут"),
        "hour" to Forms("час", "часа", "часов"),
        "day" to Forms("день", "дня", "дней"),
        "round" to Forms("раунд", "раунда", "раундов"),
        "turn" to Forms("ход", "хода", "ходов"),
        "ft" to Forms("фут", "фута", "футов"),
        "mi" to Forms("миля", "мили", "миль"),
        "action" to Forms("действие", "действия", "действий"),
        "bonus" to Forms("бонусное действие", "бонусных действия", "бонусных действий"),
        "reaction" to Forms("реакция", "реакции", "реакций"),
    )

    /** Есть ли для кода склоняемые формы. */
    fun hasForms(code: String): Boolean = code in FORMS

    /**
     * Подбирает форму слова под число.
     *
     * @return null, если для кода формы не заданы — тогда подпись берётся из справочника.
     */
    fun forCount(code: String, count: Int): String? {
        val forms = FORMS[code] ?: return null
        return forms.select(count)
    }

    /** Выбирает нужную форму по правилам русского языка. */
    private fun Forms.select(count: Int): String {
        val absolute = kotlin.math.abs(count)
        // Числа 11–14 всегда используют форму множественного числа: «11 минут».
        if (absolute % 100 in 11..14) return many
        return when (absolute % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }
}
