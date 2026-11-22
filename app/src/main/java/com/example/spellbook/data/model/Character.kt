package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Персонаж-владелец набора заклинаний. Заклинания хранятся в общей библиотеке,
 * а персонаж ссылается на них через таблицу связи [CharacterSpellCrossRef].
 *
 * [imageUri] — строковый URI выбранной из галереи картинки; если пусто,
 * показывается запасной аватар с инициалами.
 *
 * Подготовка заклинаний:
 * [canPrepareSpells] — умеет ли персонаж переподготавливать заклинания. Если да,
 * его известные заклинания делятся на «все известные» и «подготовленные» (флаг
 * [CharacterSpellCrossRef.prepared]), а [maxPreparedSpells] ограничивает их число.
 * Заговоры (уровень 0) подготовке не подлежат и ограничиваются отдельно [maxCantrips].
 *
 * Ресурсы персонажа:
 * [spellSlots] — доступно ячеек по уровням (1..9), [spellSlotsUsed] — потрачено;
 * [resources] — произвольные счётчики (очки, кости, заряды).
 */
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val canPrepareSpells: Boolean = false,
    val maxPreparedSpells: Int = 0,
    /** Сколько заговоров (уровень 0) доступно. 0 — без ограничения. */
    val maxCantrips: Int = 0,
    val spellSlots: Map<Int, Int> = emptyMap(),
    val spellSlotsUsed: Map<Int, Int> = emptyMap(),
    /** Произвольные восполняемые ресурсы: очки, кости, заряды и т. п. */
    val resources: List<CharacterResource> = emptyList(),
    /** Кошелёк: порядковый номер [CoinType] → количество монет. */
    val coins: Map<Int, Int> = emptyMap(),
    /** Максимальное число одновременно настроенных магических предметов. */
    val maxAttunedItems: Int = 3,
    /** Уровень персонажа: от него зависит бонус мастерства. */
    val level: Int = 1,
    /** Классы и уровни в формате `WIZARD:5` — задаются при создании персонажа. */
    val classes: List<String> = emptyList(),
    val maxHp: Int = 0,
    val currentHp: Int = 0,
    /** Временные хиты тратятся раньше обычных и не ограничены максимумом. */
    val tempHp: Int = 0,
    val armorClass: Int = 10,
    /** Скорость в футах. */
    val speed: Int = 30,
    /**
     * Формула бонуса инициативы, например `[dex] + 5`.
     *
     * Пустая строка — поведение по умолчанию: бонус равен модификатору Ловкости.
     * Хранится именно выражение, а не число: тогда бонус сам пересчитывается при росте
     * уровня и изменении характеристик. Вычисляется через `Character.initiativeBonus`.
     */
    val initiativeFormula: String = "",
    /** Значения характеристик: порядковый номер [AbilityType] → значение. */
    val abilityScores: Map<Int, Int> = emptyMap(),
    /** Владение спасбросками: номер [AbilityType] → множитель бонуса мастерства. */
    val saveProficiencies: Map<Int, Int> = emptyMap(),
    /** Владение навыками: номер [SkillType] → множитель (1 — владение, 2 — экспертиза). */
    val skillProficiencies: Map<Int, Int> = emptyMap(),
    /** Порядковые номера [ArmorProficiency], которыми владеет персонаж. */
    val armorProficiencies: List<String> = emptyList(),
    /** Порядковые номера [WeaponProficiency], которыми владеет персонаж. */
    val weaponProficiencies: List<String> = emptyList(),
    /** Описание прочих владений оружием — для [WeaponProficiency.OTHER]. */
    val otherWeaponProficiencies: String = "",
    /** Инструменты и языки задаются свободным текстом через запятую. */
    val toolProficiencies: String = "",
    val languages: String = "",
    /**
     * Происхождение персонажа текстом: раса, разновидность, архетип и предыстория.
     *
     * Хранятся названия, а не ссылки на библиотеку: запись библиотеки могут удалить или
     * переименовать, а персонаж должен сохранить своё происхождение. Пусто — не задано.
     */
    val race: String = "",
    val subrace: String = "",
    val subclass: String = "",
    val background: String = "",
    /**
     * Размер: код [CreatureSize] (`medium`). Берётся от расы; если раса даёт выбор
     * («Маленький или Средний»), его делают в мастере создания. Пусто — не задан.
     */
    val size: String = "",
    /**
     * Составляющие КД: доспех, щит, черты и т. п. Пусто — КД задан числом [armorClass]
     * (персонажи, созданные до появления составляющих). Итог считает `Character.effectiveArmorClass`.
     */
    val armorClassParts: List<StatPart> = emptyList(),
    /**
     * Составляющие скорости: скорость расы, прибавки от черт и умений класса.
     * Пусто — скорость задана числом [speed]. Итог считает `Character.effectiveSpeed`.
     */
    val speedParts: List<StatPart> = emptyList(),
    /**
     * Составляющие инициативы: основа (модификатор Ловкости) и прибавки от черт и умений
     * («Бдительный», «Волшебная прозорливость»). Пусто — действует [initiativeFormula].
     * Итог считает `Character.initiativeBonus`.
     */
    val initiativeParts: List<StatPart> = emptyList(),
    /**
     * Заметки и ресурсы, которые пользователь исключил в мастере создания или повышения уровня
     * (ключи `ContentExclusions`): при следующих повышениях они не добавляются заново.
     */
    val excludedContent: List<String> = emptyList(),
) {
    /** Происхождение одной строкой: «Высший эльф · Мудрец». */
    val originSummary: String
        get() = listOf(subrace.ifBlank { race }, background).filter { it.isNotBlank() }.joinToString(" · ")

    /** Разобранный список классов с уровнями. */
    val classLevels: List<CharacterClassLevel>
        get() = classes.mapNotNull(CharacterClassLevel::parse)

    /** Краткое описание классов, например «Волшебник 5 / Колдун 2». */
    val classSummary: String
        get() = classLevels.joinToString(" / ") { "${it.displayName} ${it.level}" }

    fun hasArmorProficiency(armor: ArmorProficiency): Boolean = armor.name in armorProficiencies

    fun hasWeaponProficiency(weapon: WeaponProficiency): Boolean = weapon.name in weaponProficiencies

    /** Количество доступных (не потраченных) ячеек указанного уровня. */
    fun availableSlots(level: Int): Int =
        (spellSlots[level] ?: 0) - (spellSlotsUsed[level] ?: 0)

    val proficiencyBonus: Int get() = proficiencyBonusFor(level)

    /** Значение характеристики; по умолчанию 10 (нулевой модификатор). */
    fun abilityScore(ability: AbilityType): Int = abilityScores[ability.ordinal] ?: DEFAULT_ABILITY_SCORE

    fun abilityModifierOf(ability: AbilityType): Int = abilityModifier(abilityScore(ability))

    fun saveProficiency(ability: AbilityType): ProficiencyLevel =
        proficiencyOf(saveProficiencies[ability.ordinal])

    fun skillProficiency(skill: SkillType): ProficiencyLevel =
        proficiencyOf(skillProficiencies[skill.ordinal])

    /** Итоговый бонус спасброска с учётом владения. */
    fun saveBonus(ability: AbilityType): Int =
        abilityModifierOf(ability) + proficiencyBonus * saveProficiency(ability).multiplier

    /** Итоговый бонус навыка: экспертиза добавляет бонус мастерства дважды. */
    fun skillBonus(skill: SkillType): Int =
        abilityModifierOf(skill.ability) + proficiencyBonus * skillProficiency(skill).multiplier

    private fun proficiencyOf(multiplier: Int?): ProficiencyLevel = when (multiplier) {
        ProficiencyLevel.EXPERTISE.multiplier -> ProficiencyLevel.EXPERTISE
        ProficiencyLevel.PROFICIENT.multiplier -> ProficiencyLevel.PROFICIENT
        else -> ProficiencyLevel.NONE
    }

    companion object {
        const val DEFAULT_ABILITY_SCORE = 10
    }
}
