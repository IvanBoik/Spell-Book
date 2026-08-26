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
    val maxHp: Int = 0,
    val currentHp: Int = 0,
    /** Временные хиты тратятся раньше обычных и не ограничены максимумом. */
    val tempHp: Int = 0,
    val armorClass: Int = 10,
    /** Скорость в футах. */
    val speed: Int = 30,
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
) {
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
