package com.example.spellbook.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Доменная модель заклинания.
 *
 * Структура повторяет формат LSS (FoundryVTT dnd5e), используемый на сайте LSS:
 * корневые поля [name]/[type]/`system`/`classes` и вложенные секции внутри `system`.
 * Часть служебных полей формата (uses, consume, cover и т.п.) не редактируется
 * пользователем и заполняется значениями по умолчанию при сериализации.
 *
 * [id] — локальный идентификатор записи, в чистый LSS-экспорт не попадает.
 * [createdAt] — момент добавления в библиотеку (для сортировки «по дате добавления»).
 */
@Entity(tableName = "spells")
data class Spell(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val name: String = "",
    /** Описание в виде простого текста; абзацы разделяются переводом строки. */
    val description: String = "",
    val source: String = "HB",
    /** Круг заклинания: 0 — заговор (cantrip). */
    val level: Int = 1,
    /** Школа магии, код LSS: abj, con, div, enc, evo, ill, nec, trs. */
    val school: String = "evo",
    /** Тип активации: action, bonus, reaction, minute, hour, day, special. */
    val activationType: String = "action",
    val activationCost: Int? = 1,
    val activationCondition: String = "",
    val durationValue: Int? = null,
    /** Единицы длительности: inst, turn, round, minute, hour, day, perm, spec. */
    val durationUnits: String = "inst",
    val rangeValue: Int? = null,
    /** Единицы дистанции: ft, mi, self, touch, spec, any. */
    val rangeUnits: String = "self",
    @Embedded(prefix = "target_") val target: Target = Target(),
    @Embedded(prefix = "comp_") val components: Components = Components(),
    @Embedded(prefix = "mat_") val materials: Materials = Materials(),
    /** Тип действия: util, msak, rsak, save, heal, abil, other. */
    val actionType: String = "util",
    /** Базовая характеристика заклинателя: "" или str/dex/con/int/wis/cha. */
    val ability: String = "",
    val attackBonus: Int = 0,
    @Embedded(prefix = "save_") val save: Save = Save(),
    val damageParts: List<DamagePart> = emptyList(),
    val scalingMode: String = "none",
    val scalingFormula: String = "",
    /** Классы, которым доступно заклинание (коды LSS): wizard, warlock, ... */
    val classes: List<String> = emptyList(),
)

data class Target(
    val value: Int? = null,
    val width: Int? = null,
    val units: String = "",
    val type: String = "",
)

data class Components(
    val vocal: Boolean = false,
    val somatic: Boolean = false,
    val material: Boolean = false,
    val ritual: Boolean = false,
    val concentration: Boolean = false,
    val value: String = "",
)

data class Materials(
    val value: String = "",
    val consumed: Boolean = false,
    val cost: Int = 0,
    val supply: Int = 0,
)

data class Save(
    /** Характеристика спасброска: "" или str/dex/con/int/wis/cha. */
    val ability: String = "",
    val dc: Int? = null,
    val scaling: String = "spell",
)

data class DamagePart(
    val formula: String = "",
    /** Тип урона: fire, cold, necrotic, ... либо тип лечения healing. */
    val type: String = "",
)
