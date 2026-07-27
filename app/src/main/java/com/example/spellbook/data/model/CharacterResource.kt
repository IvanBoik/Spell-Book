package com.example.spellbook.data.model

import java.util.UUID

/**
 * Пользовательский восполняемый ресурс персонажа: очки чародейства,
 * кости боевых приёмов, заряды способности и т. п.
 */
data class CharacterResource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Пояснение механики ресурса; показывается по нажатию на карточку. */
    val description: String = "",
    val current: Int,
    val maximum: Int,
) {
    /** Нормализованное состояние, устойчивое к отрицательным и слишком большим значениям. */
    fun normalized(): CharacterResource {
        val safeMaximum = maximum.coerceAtLeast(1)
        return copy(
            name = name.trim(),
            description = description.trim(),
            maximum = safeMaximum,
            current = current.coerceIn(0, safeMaximum),
        )
    }
}
