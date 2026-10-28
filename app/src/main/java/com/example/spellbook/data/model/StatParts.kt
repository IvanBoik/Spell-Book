package com.example.spellbook.data.model

import java.util.UUID

/**
 * Роль составляющей показателя (КД, скорости).
 */
enum class StatPartKind {
    /**
     * Основа: доспех и защита без доспехов для КД, скорость расы для скорости. Из включённых
     * основ берётся наибольшая — по правилам способы расчёта не складываются.
     */
    BASE,

    /**
     * Прибавка: щит и черты для КД, «Подвижный» и «Движение без доспехов» для скорости.
     * Включённые прибавки суммируются.
     */
    BONUS,
}

/**
 * Составляющая показателя персонажа: класса доспеха или скорости.
 *
 * @param formula выражение `StatFormula`: `11 + [dex]`, `14 + min([dex], 2)`, `2`, `30`.
 * @param enabled учитывается ли сейчас (например, щит снят или надет доспех, при котором
 * «Движение без доспехов» не действует — составляющая отключена, но не удалена, и её легко вернуть).
 * @param description пояснение: откуда взялась и при каком условии действует.
 */
data class StatPart(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val formula: String,
    val kind: StatPartKind = StatPartKind.BONUS,
    val enabled: Boolean = true,
    val description: String = "",
)

/** Основа КД по умолчанию, если доспех не надет: 10 + модификатор Ловкости. */
const val UNARMORED_AC_FORMULA = "10 + [dex]"

/** Основа скорости по умолчанию, если основы отключены: 30 футов. */
const val DEFAULT_SPEED_FORMULA = "30"
