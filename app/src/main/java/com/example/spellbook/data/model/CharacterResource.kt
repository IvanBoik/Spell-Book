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
    /**
     * Формула максимума, например `бм` или `инт + 1`. Если задана,
     * [maximum] пересчитывается автоматически при изменении уровня и характеристик.
     */
    val maximumFormula: String = "",
    /** Когда ресурс восстанавливается; по умолчанию не отмечено ничего. */
    val recovery: ResourceRecovery = ResourceRecovery(),
) {
    /** Нормализованное состояние, устойчивое к отрицательным и слишком большим значениям. */
    fun normalized(): CharacterResource {
        val safeMaximum = maximum.coerceAtLeast(1)
        return copy(
            name = name.trim(),
            description = description.trim(),
            maximumFormula = maximumFormula.trim(),
            maximum = safeMaximum,
            current = current.coerceIn(0, safeMaximum),
            recovery = recovery.normalized(),
        )
    }
}

/**
 * Условия восстановления ресурса. Флаги независимы: умение может восстанавливаться
 * и после короткого, и после долгого отдыха.
 *
 * @param other особое восстановление, которое не сводится к отдыху: [otherText] описывает его
 * словами («после 1к4 долгих отдыхов» у «Ограниченного желания» колдуна Гения). В листе LSS
 * такого варианта нет, поэтому при выгрузке он считается долгим отдыхом.
 */
data class ResourceRecovery(
    val shortRest: Boolean = false,
    val longRest: Boolean = false,
    val other: Boolean = false,
    val otherText: String = "",
) {
    /** Отмечено ли хоть одно условие. */
    val isSet: Boolean get() = shortRest || longRest || other

    /** Текст особого восстановления хранится только вместе с флагом [other]. */
    fun normalized(): ResourceRecovery =
        copy(otherText = if (other) otherText.trim() else "")
}
