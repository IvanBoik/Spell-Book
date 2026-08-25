package com.example.spellbook.data.model

/** Базовые характеристики D&D. Порядковый номер используется как ключ хранения. */
enum class AbilityType(val label: String, val shortLabel: String) {
    STRENGTH("Сила", "СИЛ"),
    DEXTERITY("Ловкость", "ЛОВ"),
    CONSTITUTION("Телосложение", "ТЕЛ"),
    INTELLIGENCE("Интеллект", "ИНТ"),
    WISDOM("Мудрость", "МУД"),
    CHARISMA("Харизма", "ХАР"),
}

/** Навык и характеристика, от которой он зависит. */
enum class SkillType(val label: String, val ability: AbilityType) {
    ACROBATICS("Акробатика", AbilityType.DEXTERITY),
    ANIMAL_HANDLING("Обращение с животными", AbilityType.WISDOM),
    ARCANA("Магия", AbilityType.INTELLIGENCE),
    ATHLETICS("Атлетика", AbilityType.STRENGTH),
    DECEPTION("Обман", AbilityType.CHARISMA),
    HISTORY("История", AbilityType.INTELLIGENCE),
    INSIGHT("Проницательность", AbilityType.WISDOM),
    INTIMIDATION("Запугивание", AbilityType.CHARISMA),
    INVESTIGATION("Анализ", AbilityType.INTELLIGENCE),
    MEDICINE("Медицина", AbilityType.WISDOM),
    NATURE("Природа", AbilityType.INTELLIGENCE),
    PERCEPTION("Внимательность", AbilityType.WISDOM),
    PERFORMANCE("Выступление", AbilityType.CHARISMA),
    PERSUASION("Убеждение", AbilityType.CHARISMA),
    RELIGION("Религия", AbilityType.INTELLIGENCE),
    SLEIGHT_OF_HAND("Ловкость рук", AbilityType.DEXTERITY),
    STEALTH("Скрытность", AbilityType.DEXTERITY),
    SURVIVAL("Выживание", AbilityType.WISDOM),
}

/** Уровень владения навыком или спасброском. */
enum class ProficiencyLevel(val label: String, val multiplier: Int) {
    NONE("Нет владения", 0),
    PROFICIENT("Владение", 1),
    EXPERTISE("Экспертиза", 2),
}

/** Что именно бросается — для подписи всплывающего результата. */
enum class RollKind(val label: String) {
    ABILITY("Проверка"),
    SAVE("Спасбросок"),
    SKILL("Навык"),
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
