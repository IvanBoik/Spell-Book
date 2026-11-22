package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType

/** Способ заполнения базовых значений характеристик. */
enum class AbilityMethod { MANUAL, STANDARD_ARRAY, POINT_BUY, ROLL }

/** Способ расчёта хитов. */
enum class HpMethod { AVERAGE, ROLL, MANUAL }

/**
 * Режим бонусов характеристик от расы.
 *
 * [RULES] — строго по тексту расы: фиксированные бонусы стоят на своих местах,
 * а свободные (по выбранному шаблону) распределяются через [CharacterDraft.bonusTargets];
 * [FREE] — все величины бонусов (фиксированные и шаблонные) можно перенести на любые
 * разные характеристики (вариант «Настройки происхождения» из Tasha). Тогда
 * [CharacterDraft.bonusTargets] покрывает все значения: сначала фиксированные, затем шаблонные.
 */
enum class BonusMode { RULES, FREE }

/** Что взято на уровне «Увеличения характеристик». */
enum class AsiKind { PLUS_TWO, PLUS_ONE_ONE, FEAT }

/**
 * Выбор на одном уровне «Увеличения характеристик».
 *
 * @param abilities для [AsiKind.PLUS_TWO] — одна характеристика, для [AsiKind.PLUS_ONE_ONE] — две разные.
 * @param featId для [AsiKind.FEAT] — выбранная черта (см. также [CharacterDraft.featSlots]).
 */
data class AsiChoice(
    val level: Int,
    val kind: AsiKind = AsiKind.PLUS_TWO,
    val abilities: List<AbilityType> = emptyList(),
)

/**
 * Результат броска 4к6 для одной характеристики.
 *
 * @param dice все четыре кости; наименьшая отбрасывается.
 */
data class RolledScore(val dice: List<Int>) {
    val dropped: Int get() = dice.minOrNull() ?: 0
    val total: Int get() = dice.sum() - dropped
}

/**
 * Черновик персонажа в мастере создания: всё, что выбрал пользователь.
 *
 * Хранит только решения пользователя (id и значения); правила рас и классов
 * разбираются отдельно и подставляются при отображении и сборке.
 */
data class CharacterDraft(
    val name: String = "",
    val imageUri: String? = null,
    val level: Int = 1,

    val raceId: String? = null,
    /** id разновидности из [SubraceRules.id]. */
    val subraceId: String? = null,
    val classId: String? = null,
    val subclassId: String? = null,
    val backgroundId: String? = null,

    /**
     * Значения выборов: [ChoiceSpec.id] → выбранные значения (см. [ChoiceKind]).
     *
     * id выборов начинаются с префикса источника (`race.`, `subrace.`, `class.`,
     * `subclass.`, `background.`), чтобы при смене расы или класса можно было
     * сбросить устаревшие решения через [WizardEngine.clearSelections].
     */
    val selections: Map<String, List<String>> = emptyMap(),

    /** Стартовое снаряжение класса: индекс строки → индекс выбранного варианта. */
    val equipmentChoices: Map<Int, Int> = emptyMap(),
    /**
     * Оружие на выбор в снаряжении: ключ `строка.вариант.слот` → id оружия из
     * [EquipmentCatalog] по числу оружия в слоте («два воинских оружия» — два id).
     */
    val weaponPicks: Map<String, List<String>> = emptyMap(),
    /** Взять золото вместо стартового снаряжения класса. */
    val takeClassGold: Boolean = false,
    /** Выпавшее золото, если [takeClassGold]; null — ещё не брошено. */
    val classGold: Int? = null,

    val abilityMethod: AbilityMethod = AbilityMethod.STANDARD_ARRAY,
    /** Базовые значения для ручного ввода и покупки за очки. */
    val baseScores: Map<AbilityType, Int> = emptyMap(),
    /** Значения из стандартного набора или броска: характеристика → индекс значения в пуле. */
    val poolAssignment: Map<AbilityType, Int> = emptyMap(),
    /** Шесть бросков 4к6; пусто — ещё не бросали. */
    val rolledScores: List<RolledScore> = emptyList(),

    val bonusMode: BonusMode = BonusMode.RULES,
    /** Индекс выбранного шаблона [AbilityBonusRule.patterns]. */
    val bonusPatternIndex: Int = 0,
    /**
     * Цели бонусов по порядку значений (см. [WizardEngine.bonusValues]);
     * null — значение ещё не распределено.
     */
    val bonusTargets: List<AbilityType?> = emptyList(),

    /** Решения на уровнях «Увеличения характеристик»: уровень → выбор. */
    val asiChoices: Map<Int, AsiChoice> = emptyMap(),
    /**
     * Черты: слот → id черты. Слоты: `race.0`, `race.1`… (альтернативный человек)
     * и `asi.<уровень>` (вместо увеличения характеристик).
     */
    val featSlots: Map<String, String> = emptyMap(),

    /** Выбранные заговоры класса (id заклинаний). */
    val cantripIds: Set<String> = emptySet(),
    /** Известные заклинания или заклинания в книге волшебника (id). */
    val spellIds: Set<String> = emptySet(),
    /** Подготовленные заклинания (id). */
    val preparedIds: Set<String> = emptySet(),

    val hpMethod: HpMethod = HpMethod.AVERAGE,
    /** Броски кости хитов за уровни после первого. */
    val hpRolls: List<Int> = emptyList(),
    val manualHp: Int? = null,

    /** Показывать неофициальный контент (UA, сторонние книги). */
    val showUnofficial: Boolean = false,
    /** Показывать опциональные умения (Tasha's Cauldron of Everything). */
    val showOptional: Boolean = false,

    /**
     * Повторные владения, которые пользователь решил оставить как есть
     * (ключи [ProficiencyOverlap.key]): уведомление о них больше не показывается.
     */
    val keptOverlaps: Set<String> = emptySet(),
    /**
     * Решения по опциональным умениям, заменяющим базовые ([OptionalSwap.id] → брать опциональное).
     * Нет решения — действует базовое умение из «Книги игрока».
     */
    val optionalSwaps: Map<String, Boolean> = emptyMap(),
    /**
     * Исключённые на итоговом шаге заметки и ресурсы (ключи [ContentExclusions]): блоки целиком,
     * абзацы внутри блоков и ресурсы. Заметки выбранных вариантов исключить нельзя.
     */
    val excludedContent: Set<String> = emptySet(),
)

/**
 * Всё, что нужно для сборки персонажа: черновик плюс разобранные правила выбранных
 * расы, класса и предыстории и вычисленные по ним выборы, ресурсы и заклинательство.
 *
 * @param choices все активные выборы (раса, подраса, класс, подкласс, предыстория).
 * @param classGrants владения без выбора из умений класса и архетипа («Бонусное владение» и т. п.).
 * @param featureSpells заклинания из умений класса и архетипа на текущем уровне («Техника тени» монаха,
 * «Дополнительный заговор» домена света) — фрагменты названий для [MarkupText.matchesSpellName].
 * @param featEffects эффекты черт, выбранных в слотах: id слота → эффекты.
 */
data class WizardContext(
    val race: RaceRules?,
    val subrace: SubraceRules?,
    val characterClass: ClassRules?,
    val subclass: SubclassRules?,
    val background: BackgroundRules?,
    val choices: List<ChoiceSpec>,
    val resources: List<ResourceSpec>,
    val spellcasting: SpellcastingPlan?,
    val classGrants: FeatureGrants = FeatureGrants(),
    val featureSpells: List<String> = emptyList(),
    val featEffects: Map<String, FeatEffects> = emptyMap(),
    /** Опциональные умения, взаимоисключающие с базовыми: выбор между ними на шаге класса. */
    val swaps: List<OptionalSwap> = emptyList(),
    /**
     * Мистические рыцарь и ловкач: заклинания списка класса не из школ архетипа. Их можно
     * взять не больше [SpellcastingPlan.anySchoolSpells]; пусто — ограничения школ нет.
     */
    val offSchoolSpellIds: Set<String> = emptySet(),
) {
    /** Владения без выбора от всех выбранных черт («Знаток тяжёлых доспехов»). */
    val featGrants: FeatureGrants
        get() = featEffects.values.fold(FeatureGrants()) { acc, effects -> acc + effects.grants }
}
