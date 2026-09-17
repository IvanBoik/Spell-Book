package com.example.spellbook.data.model

import androidx.annotation.StringRes
import com.example.spellbook.R

/** Базовые характеристики D&D. Порядковый номер используется как ключ хранения. */
enum class AbilityType(
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
) {
    STRENGTH(R.string.ability_strength, R.string.ability_short_str),
    DEXTERITY(R.string.ability_dexterity, R.string.ability_short_dex),
    CONSTITUTION(R.string.ability_constitution, R.string.ability_short_con),
    INTELLIGENCE(R.string.ability_intelligence, R.string.ability_short_int),
    WISDOM(R.string.ability_wisdom, R.string.ability_short_wis),
    CHARISMA(R.string.ability_charisma, R.string.ability_short_cha),
}

/** Навык и характеристика, от которой он зависит. */
enum class SkillType(@param:StringRes val labelRes: Int, val ability: AbilityType) {
    ACROBATICS(R.string.skill_acrobatics, AbilityType.DEXTERITY),
    ANIMAL_HANDLING(R.string.skill_animal_handling, AbilityType.WISDOM),
    ARCANA(R.string.skill_arcana, AbilityType.INTELLIGENCE),
    ATHLETICS(R.string.skill_athletics, AbilityType.STRENGTH),
    DECEPTION(R.string.skill_deception, AbilityType.CHARISMA),
    HISTORY(R.string.skill_history, AbilityType.INTELLIGENCE),
    INSIGHT(R.string.skill_insight, AbilityType.WISDOM),
    INTIMIDATION(R.string.skill_intimidation, AbilityType.CHARISMA),
    INVESTIGATION(R.string.skill_investigation, AbilityType.INTELLIGENCE),
    MEDICINE(R.string.skill_medicine, AbilityType.WISDOM),
    NATURE(R.string.skill_nature, AbilityType.INTELLIGENCE),
    PERCEPTION(R.string.skill_perception, AbilityType.WISDOM),
    PERFORMANCE(R.string.skill_performance, AbilityType.CHARISMA),
    PERSUASION(R.string.skill_persuasion, AbilityType.CHARISMA),
    RELIGION(R.string.skill_religion, AbilityType.INTELLIGENCE),
    SLEIGHT_OF_HAND(R.string.skill_sleight_of_hand, AbilityType.DEXTERITY),
    STEALTH(R.string.skill_stealth, AbilityType.DEXTERITY),
    SURVIVAL(R.string.skill_survival, AbilityType.WISDOM),
}

/** Виды доспехов, владение которыми отмечается в настройках персонажа. */
enum class ArmorProficiency(@param:StringRes val labelRes: Int) {
    LIGHT(R.string.armor_light),
    MEDIUM(R.string.armor_medium),
    HEAVY(R.string.armor_heavy),
    SHIELDS(R.string.armor_shields),
}

/** Категории оружия, владение которыми отмечается в настройках персонажа. */
enum class WeaponProficiency(@param:StringRes val labelRes: Int) {
    SIMPLE(R.string.weapon_simple),
    MARTIAL(R.string.weapon_martial),

    /** При выборе открывается поле с подробным описанием владений. */
    OTHER(R.string.action_type_other),
}

/** Уровень владения навыком или спасброском. */
enum class ProficiencyLevel(@param:StringRes val labelRes: Int, val multiplier: Int) {
    NONE(R.string.proficiency_none, 0),
    PROFICIENT(R.string.proficiency_proficient, 1),
    EXPERTISE(R.string.proficiency_expertise, 2),
}

/** Что именно бросается — для подписи всплывающего результата. */
enum class RollKind(@param:StringRes val labelRes: Int) {
    ABILITY(R.string.roll_kind_ability),
    SAVE(R.string.action_type_save),
    SKILL(R.string.roll_kind_skill),
}

/** Результат броска d20 с бонусом, показывается небольшой плашкой. */
data class D20RollResult(
    val title: String,
    val kind: RollKind,
    val roll: Int,
    val bonus: Int,
    val id: Long = System.currentTimeMillis(),
) {
    val total: Int get() = roll + bonus
    val isCriticalSuccess: Boolean get() = roll == 20
    val isCriticalFailure: Boolean get() = roll == 1
}

/** Модификатор характеристики по её значению: (score − 10) / 2 с округлением вниз. */
fun abilityModifier(score: Int): Int = Math.floorDiv(score - 10, 2)

/** Бонус мастерства для уровня 1..20: 2 + (уровень − 1) / 4. */
fun proficiencyBonusFor(level: Int): Int = 2 + (level.coerceIn(1, 20) - 1) / 4

/** Знаковая запись модификатора: +3, 0, −1. */
fun formatModifier(value: Int): String = if (value >= 0) "+$value" else "−${-value}"
