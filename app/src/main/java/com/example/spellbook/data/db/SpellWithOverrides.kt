package com.example.spellbook.data.db

import androidx.room.Embedded
import com.example.spellbook.data.model.Spell

/**
 * Заклинание из библиотеки вместе с персональной правкой конкретного персонажа.
 *
 * Хранить правку прямо в [Spell] нельзя: одно и то же заклинание может быть
 * у нескольких персонажей, и у каждого свой текст. Поэтому библиотечная запись
 * читается целиком, а поверх неё накладываются переопределения из таблицы связи.
 */
data class SpellWithOverrides(
    @Embedded val spell: Spell,
    val nameOverride: String? = null,
    val descriptionOverride: String? = null,
) {
    /** Есть ли у персонажа собственная версия текста. */
    val hasPersonalEdit: Boolean get() = nameOverride != null || descriptionOverride != null

    /** Заклинание с применённой персональной правкой. */
    fun resolved(): Spell = if (!hasPersonalEdit) {
        spell
    } else {
        spell.copy(
            name = nameOverride ?: spell.name,
            description = descriptionOverride ?: spell.description,
        )
    }
}
