package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Background
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.Race

/*
 * Повышение уровня персонажа на +1.
 *
 * Черновик мастера создания не хранится, поэтому всё восстанавливается из листа персонажа
 * и библиотек по названиям: раса, разновидность, предыстория, классы (`Character.classes`)
 * и архетип (`Character.subclass`). Уже выбранные варианты (воззвания, приёмы, стиль…)
 * восстанавливаются по заметкам: блок с заголовком выбора, абзацы с названиями вариантов.
 *
 * Поток для интерфейса:
 * 1. [LevelUpEngine.base] — один раз при открытии окна;
 * 2. [LevelUpEngine.initialDraft] — стартовый черновик;
 * 3. на каждое изменение черновика — [LevelUpEngine.context], затем [LevelUpEngine.steps],
 *    [LevelUpEngine.issues], [LevelUpEngine.choicesOn];
 * 4. на шаге итога и при сохранении — [LevelUpEngine.build] → [LevelUpResult]
 *    (сводка «было → стало» и изменения для `SpellBookRepository.applyLevelUp`).
 */

/**
 * Класс персонажа, сопоставленный с библиотекой.
 *
 * @param index позиция в `Character.classLevels`.
 * @param rules null — класс не найден в библиотеке: повышение без умений и выборов.
 * @param subclass архетип, восстановленный по названию из `Character.subclass`.
 * @param selections выборы-варианты ([ChoiceKind.OPTION]), восстановленные по заметкам.
 * @param optionalSwaps решения по опциональным заменам Tasha, восстановленные по заметкам.
 */
data class LevelUpClassEntry(
    val index: Int,
    val classLevel: CharacterClassLevel,
    val charClass: CharClass?,
    val rules: ClassRules?,
    val subclass: SubclassRules?,
    val selections: Map<String, List<String>> = emptyMap(),
    val optionalSwaps: Map<String, Boolean> = emptyMap(),
)

/** Что не удалось сопоставить с библиотекой — интерфейс показывает предупреждение. */
enum class LevelUpWarningKind { RACE_NOT_FOUND, BACKGROUND_NOT_FOUND, CLASS_NOT_FOUND, SUBCLASS_NOT_FOUND }

/** @param classIndex позиция в `Character.classLevels` для [LevelUpWarningKind.CLASS_NOT_FOUND]. */
data class LevelUpWarning(val kind: LevelUpWarningKind, val label: String = "", val classIndex: Int? = null)

/**
 * Персонаж и всё, что о нём известно, — считается один раз при открытии окна.
 *
 * @param knownSpellIds все связанные заклинания (включая заговоры).
 * @param defaultShowOptional / [defaultShowUnofficial] — значения переключателей по умолчанию.
 */
data class LevelUpBase(
    val character: Character,
    val notes: List<NoteBlock>,
    val knownSpellIds: Set<String>,
    val preparedSpellIds: Set<String>,
    val featIds: List<String>,
    val race: Race?,
    val subraceId: String?,
    val background: Background?,
    val classes: List<LevelUpClassEntry>,
    val defaultShowOptional: Boolean,
    val defaultShowUnofficial: Boolean,
    val warnings: List<LevelUpWarning>,
) {
    val oldLevel: Int get() = character.level
    val atMaxLevel: Boolean get() = character.level >= com.example.spellbook.data.model.MAX_CLASS_LEVEL
}

/** Замена при повышении уровня: что убрать ([oldId]) и что взять взамен ([newId]). */
data class LevelUpReplacement(val oldId: String? = null, val newId: String? = null) {
    val isComplete: Boolean get() = oldId != null && newId != null
    val isEmpty: Boolean get() = oldId == null && newId == null
}

/**
 * Решения пользователя в окне повышения уровня.
 *
 * Повышается либо существующий класс ([classIndex] — позиция в `Character.classLevels`), либо
 * берётся 1-й уровень нового класса ([newClassId] — id класса библиотеки). Ровно одно из двух.
 *
 * @param selections решения новых выборов (id — [ChoiceSpec.id] из [LevelUpContext.choices]):
 * только новые значения, без уже взятых раньше.
 * @param asi решение на уровне «Увеличения характеристик»; [AsiChoice.level] — новый уровень класса.
 * @param featId черта вместо увеличения ([AsiKind.FEAT]).
 * @param hpRoll выпавшее значение кости хитов ([HpMethod.ROLL]); [manualHp] — значение кости,
 * введённое вручную ([HpMethod.MANUAL]). К обоим добавляется модификатор Телосложения.
 * @param cantripIds / [spellIds] — новые заговоры и известные заклинания класса (у волшебника — в книгу).
 * @param replacements замены: id из [ReplacementSpec.id] → что меняется на что.
 */
data class LevelUpDraft(
    val classIndex: Int? = null,
    val newClassId: String? = null,
    val subclassId: String? = null,
    val selections: Map<String, List<String>> = emptyMap(),
    val optionalSwaps: Map<String, Boolean> = emptyMap(),
    val asi: AsiChoice? = null,
    val featId: String? = null,
    val hpMethod: HpMethod = HpMethod.AVERAGE,
    val hpRoll: Int? = null,
    val manualHp: Int? = null,
    val cantripIds: Set<String> = emptySet(),
    val spellIds: Set<String> = emptySet(),
    val replacements: Map<String, LevelUpReplacement> = emptyMap(),
    val showOptional: Boolean = false,
    val showUnofficial: Boolean = false,
    /**
     * Решения по ресурсам, удалённым вручную ([MissingResource.key] → вернуть ли).
     * Нет решения — ресурс не возвращается, но в итоге остаётся вопрос.
     */
    val resourceDecisions: Map<String, Boolean> = emptyMap(),
    /**
     * Переподготовка заклинаний повышаемого класса ([LevelUpPreparation]): подготовленные после повышения,
     * без всегда подготовленных. null — не менялась: действует [LevelUpPreparation.current].
     */
    val preparedIds: Set<String>? = null,
    /**
     * Исключённые заметки и ресурсы после повышения (ключи [ContentExclusions]) — полный набор,
     * включая исключённое раньше. null — не менялся: действует `Character.excludedContent`.
     */
    val excludedContent: Set<String>? = null,
)

/**
 * Переподготовка заклинаний при повышении уровня — у классов, которые готовят заклинания:
 * жрец, друид, паладин, изобретатель (из всего списка класса) и волшебник (из книги, включая новые
 * записи этого уровня). Шаг необязательный: текущая подготовка сохраняется, если её не менять.
 *
 * Подготовленные заклинания других классов мультикласса и полученные от расы, черт и умений сюда
 * не входят и не меняются.
 *
 * @param limit сколько заклинаний можно подготовить на новом уровне (всегда подготовленные не считаются).
 * @param oldLimit лимит на прежнем уровне класса (0 — класс ещё не готовил заклинания).
 * @param candidates что можно подготовить: заклинания класса доступных кругов или книга волшебника.
 * @param alwaysPrepared всегда подготовленные заклинания класса на новом уровне — отмечены без возможности снять.
 * @param current подготовленные сейчас заклинания этого класса (без всегда подготовленных).
 * @param selected итоговая подготовка: решение черновика или [current].
 */
data class LevelUpPreparation(
    val classCode: String,
    val spellbook: Boolean,
    val limit: Int,
    val oldLimit: Int,
    val candidates: List<String>,
    val alwaysPrepared: List<String>,
    val current: Set<String>,
    val selected: Set<String>,
) {
    /** Свободные места подготовки после выбора (отрицательно — подготовлено сверх лимита). */
    val free: Int get() = limit - selected.size
}

/**
 * Ресурс, который по правилам есть у персонажа и на этом уровне должен измениться,
 * но в листе его нет: пользователь удалил его вручную. Окно спрашивает, вернуть ли его.
 *
 * @param key ключ решения в [LevelUpDraft.resourceDecisions].
 * @param oldMaximum / [newMaximum] — максимум по правилам на старом и новом уровне.
 */
data class MissingResource(
    val key: String,
    val name: String,
    val oldName: String,
    val description: String,
    val oldMaximum: Int,
    val newMaximum: Int,
    internal val spec: com.example.spellbook.data.model.CharacterResource,
)

/** Что заменяется. */
enum class ReplacementKind {
    /** Известное заклинание класса (бард, чародей, колдун, следопыт, мистические рыцарь и ловкач). */
    SPELL,

    /** Заговор класса (Tasha, на уровнях «Увеличения характеристик»). */
    CANTRIP,

    /** Вариант выбора: воззвание, боевой приём, боевой стиль, метамагия, дар договора, инфузия. */
    OPTION,

    /**
     * Заклинание таинственного арканума колдуна (Tasha): другое заклинание колдуна того же круга.
     * Кандидаты зависят от заменяемого — см. [ReplacementSpec.candidatesFor].
     */
    ARCANUM,

    /**
     * Навык компетентности («Многогранность барда», Tasha): варианты — [ReplacementSpec.removableOptions]
     * и [ReplacementSpec.candidateOptions], id варианта — `SkillType.name`.
     */
    EXPERTISE,
}

/**
 * Доступная замена.
 *
 * @param id ключ в [LevelUpDraft.replacements]: `replace.spell`, `replace.cantrip`, `replace.option.<choiceId>`.
 * @param choiceId для [ReplacementKind.OPTION] — выбор, чей вариант меняется ([ChoiceSpec.id]).
 * @param removable что можно убрать: id заклинаний или [ChoiceOption.id].
 * @param candidates на что можно заменить: id заклинаний (для заклинаний) или варианты (для [ReplacementKind.OPTION]).
 * @param group замены одной группы взаимоисключающие («одно из следующих действий» у Tasha); null — независимая.
 * @param sourceText текст правила замены (умение «Универсальность…» или «Таинственные воззвания»).
 */
data class ReplacementSpec(
    val id: String,
    val kind: ReplacementKind,
    val title: String,
    val removable: List<String>,
    val candidateSpellIds: List<String> = emptyList(),
    val candidateOptions: List<ChoiceOption> = emptyList(),
    val removableOptions: List<ChoiceOption> = emptyList(),
    val choiceId: String? = null,
    val group: String? = null,
    val sourceText: String = "",
    /**
     * Кандидаты, зависящие от заменяемого: id заменяемого → id допустимых замен (арканум — тот же
     * круг; мистический рыцарь и ловкач — любая школа только вместо «свободного» заклинания).
     * Пусто — для любого заменяемого действует общий список ([candidateSpellIds] или [candidateOptions]).
     */
    val candidatesByRemovable: Map<String, List<String>> = emptyMap(),
) {
    /**
     * Замена заклинания (известное, заговор, арканум) показывается на шаге заклинаний, остальные
     * (варианты выборов, навык компетентности) — на шаге замен.
     */
    val onSpellStep: Boolean
        get() = kind == ReplacementKind.SPELL || kind == ReplacementKind.CANTRIP || kind == ReplacementKind.ARCANUM

    /** id допустимых замен для выбранного заменяемого (заклинания или [ChoiceOption.id]). */
    fun candidatesFor(removableId: String?): List<String> =
        removableId?.let { candidatesByRemovable[it] }
            ?: candidateSpellIds.ifEmpty { candidateOptions.map { it.id } }
}

/** Новое умение на этом уровне: показывается на шаге класса и дописывается в заметки. */
data class LevelUpFeature(
    val source: ChoiceSource,
    /** Заголовок блока заметок, куда допишется умение («Класс: Воин»). */
    val blockTitle: String,
    val feature: RuleFeature,
) {
    /** Заголовок абзаца — как в заметках мастера создания: «Дополнительная атака (5 ур.)». */
    val title: String get() = CharacterAssembler.levelTitle(feature)
}

/**
 * Новые заклинания класса на этом уровне.
 *
 * @param newCantrips сколько заговоров выбрать; [cantripCandidates] — id подходящих неизвестных заговоров.
 * @param newSpells сколько известных заклинаний (или заклинаний в книгу у волшебника, [spellbook]) выбрать.
 * @param addedFromClassList заклинания нового круга, которые классы с подготовкой из списка класса
 * узнают автоматически (жрец, друид, паладин, изобретатель).
 * @param alwaysPrepared новые всегда подготовленные заклинания (домен, клятва).
 */
data class LevelUpSpells(
    val plan: SpellcastingPlan?,
    val oldPlan: SpellcastingPlan?,
    val newCantrips: Int,
    val cantripCandidates: List<String>,
    val newSpells: Int,
    val spellCandidates: List<String>,
    val spellbook: Boolean,
    val addedFromClassList: List<String>,
    val alwaysPrepared: List<String>,
    /**
     * Мистический рыцарь и ловкач: сколько из новых заклинаний (и замены) могут быть любой
     * школы — свободные места 3/8/14/20 уровня, ещё не занятые известными заклинаниями
     * других школ. В [spellCandidates] тогда есть заклинания всех школ. 0 — только школы плана.
     */
    val anySchoolAllowance: Int = 0,
    /** id заклинаний не из школ плана среди кандидатов и известных — для проверки [anySchoolAllowance]. */
    val offSchoolSpellIds: Set<String> = emptySet(),
) {
    val hasPicks: Boolean get() = newCantrips > 0 || newSpells > 0
}

/**
 * Требования мультиклассирования.
 *
 * @param unmet класс → группы характеристик, где ни одна не достигает минимума
 * (названия классов — как в библиотеке; текущие классы тоже проверяются по правилам PHB).
 */
data class MulticlassCheck(
    val rules: MulticlassRules,
    val unmet: Map<String, List<Set<AbilityType>>>,
) {
    val isMet: Boolean get() = unmet.isEmpty()
}

/** Класс, в который можно взять мультикласс, с проверкой требований (для списка на шаге класса). */
data class NewClassOption(
    val charClass: CharClass,
    val rules: ClassRules,
    val multiclass: MulticlassRules,
    val check: MulticlassCheck,
)

/**
 * Вычисленное по черновику повышения: что нового даёт уровень и что нужно выбрать.
 *
 * @param rules / [subclass] — правила повышаемого класса после опциональных замен; null — класс не выбран
 * или не найден в библиотеке ([limited]).
 * @param subclassOptions архетипы на выбор, если [needsSubclass] (неофициальные — только с переключателем).
 * @param swaps новые на этом уровне выборы «базовое или опциональное умение» (решение — [LevelUpDraft.optionalSwaps]).
 * @param features новые умения класса и архетипа (без служебных) — для показа и заметок.
 * @param choices новые выборы: только появившиеся на этом уровне или с увеличившимся числом
 * (count — сколько добрать, уже взятые варианты исключены); выборы мультикласса и новой черты.
 * @param restoredSelections уже взятые варианты повышаемого класса (по заметкам).
 * @param knownSpellIds заклинания персонажа — интерфейс исключает их из выбора заклинаний умений.
 * @param newWizardDraft / [newWizardContext] — черновик и контекст мастера создания на новом уровне
 * класса: по ним работают `OptionRequirements` и компоненты выбора мастера создания.
 * @param limited класс не найден в библиотеке: только уровень, хиты, бонус мастерства и ячейки.
 */
data class LevelUpContext(
    val base: LevelUpBase,
    val draft: LevelUpDraft,
    val newLevel: Int,
    val entry: LevelUpClassEntry?,
    val newClass: CharClass?,
    val rules: ClassRules?,
    val subclass: SubclassRules?,
    val oldClassLevel: Int,
    val newClassLevel: Int,
    val needsSubclass: Boolean,
    val subclassOptions: List<SubclassRules>,
    val swaps: List<OptionalSwap>,
    val features: List<LevelUpFeature>,
    val choices: List<ChoiceSpec>,
    val isAsiLevel: Boolean,
    val spells: LevelUpSpells?,
    val replacements: List<ReplacementSpec>,
    val multiclass: MulticlassCheck?,
    val hitDie: Int,
    val scoresBefore: Map<AbilityType, Int>,
    val scoresAfter: Map<AbilityType, Int>,
    val proficientSkills: Set<com.example.spellbook.data.model.SkillType>,
    val expertiseSkills: Set<com.example.spellbook.data.model.SkillType>,
    val restoredSelections: Map<String, List<String>>,
    val knownSpellIds: Set<String>,
    val limited: Boolean,
    val newWizardDraft: CharacterDraft?,
    val newWizardContext: WizardContext?,
    internal val oldWizardContext: WizardContext?,
    internal val raceOld: WizardContext,
    internal val raceNew: WizardContext,
    internal val existingFeatHpPerLevel: Int,
    internal val mergedSelections: Map<String, List<String>>,
    /** Ресурсы, удалённые вручную, которые на этом уровне должны измениться: вернуть ли их. */
    val missingResources: List<MissingResource> = emptyList(),
    /** Переподготовка заклинаний повышаемого класса; null — класс заклинания не готовит. */
    val preparation: LevelUpPreparation? = null,
    /**
     * Замены, чьё новое значение уже взято на этом повышении как новое ([ReplacementSpec.id] → название):
     * взять копьё и тут же заменить на него старое заклинание — минус одно заклинание. Блокирует повышение.
     */
    val duplicatePicks: Map<String, String> = emptyMap(),
    /**
     * Что это повышение добавляет в заметки и ресурсы — для переключателей на итоговом шаге.
     * Исключённое раньше помечено `excludedEarlier` и может быть включено обратно.
     */
    val proposal: ContentProposal = ContentProposal(),
    /** Действующие исключения: [LevelUpDraft.excludedContent] или запомненные у персонажа. */
    val excludedContent: Set<String> = emptySet(),
) {
    /**
     * Заклинания, которые с этого уровня всегда подготовлены (домен, клятва, круг, специальность
     * изобретателя) — только новые на этом уровне.
     */
    val newAlwaysPrepared: List<String> get() = spells?.alwaysPrepared.orEmpty()

    val oldLevel: Int get() = base.oldLevel
    val classChosen: Boolean get() = entry != null || newClass != null
    val isNewClass: Boolean get() = newClass != null
}

/** Шаг окна повышения уровня. */
sealed interface LevelUpStep {
    val key: String

    /** Класс (существующий или новый), архетип, переключатели, новые умения, простые выборы. */
    data object Class : LevelUpStep { override val key = "class" }

    /** Отдельный экран выбора: воззвания, боевые приёмы, метамагия… */
    data class Choice(val choiceId: String) : LevelUpStep { override val key = "choice:$choiceId" }

    /** «Увеличение характеристик» или черта с её выборами. */
    data object Abilities : LevelUpStep { override val key = "abilities" }

    data object HitPoints : LevelUpStep { override val key = "hp" }

    /** Новые заговоры и заклинания, выборы заклинаний умений, замена заклинания или заговора. */
    data object Spells : LevelUpStep { override val key = "spells" }

    /** Замена воззвания, боевого приёма, стиля, метамагии, инфузии или навыка компетентности. */
    data object Replacements : LevelUpStep { override val key = "replacements" }

    /** Переподготовка заклинаний (необязательно): жрец, друид, паладин, изобретатель, волшебник. */
    data object Prepare : LevelUpStep { override val key = "prepare" }

    data object Summary : LevelUpStep { override val key = "summary" }
}

enum class LevelUpIssueKind {
    MAX_LEVEL,
    CLASS_MISSING,
    MULTICLASS_REQUIREMENTS,
    SUBCLASS_MISSING,
    CHOICE_INCOMPLETE,
    ASI_INCOMPLETE,
    FEAT_MISSING,
    HP_MISSING,
    CANTRIPS_INCOMPLETE,
    SPELLS_INCOMPLETE,
    REPLACEMENT_INCOMPLETE,

    /** Выбраны две замены из одной группы «одно из следующих действий». */
    REPLACEMENT_CONFLICT,

    /**
     * Мистический рыцарь или ловкач: заклинаний других школ больше, чем свободных мест
     * ([LevelUpSpells.anySchoolAllowance]); [LevelUpIssue.missing] — на сколько превышено.
     */
    SPELLS_SCHOOL_LIMIT,

    /** Не решено, возвращать ли удалённый ресурс ([MissingResource]); [LevelUpIssue.label] — название. */
    RESOURCE_DECISION,

    /** Лимит подготовки вырос, и остались свободные места ([LevelUpIssue.missing]) — напоминание, не блокирует. */
    PREPARED_FREE,

    /** Подготовлено больше нового лимита ([LevelUpIssue.missing] — на сколько). */
    PREPARED_OVER,

    /**
     * Замена на то, что уже взято новым на этом повышении ([LevelUpContext.duplicatePicks]);
     * [LevelUpIssue.label] — название. Блокирует.
     */
    DUPLICATE_PICK,
}

/**
 * Незавершённость шага.
 *
 * @param blocking без исправления повысить уровень нельзя (класс, требования мультикласса, хиты);
 * остальные — предупреждения, как в мастере создания.
 */
data class LevelUpIssue(
    val kind: LevelUpIssueKind,
    val label: String = "",
    val missing: Int = 0,
    val blocking: Boolean = false,
)

/** Изменение ресурса в сводке; null — ресурса не было (новый) или не стало (убран заменой). */
data class ResourceChange(
    val name: String,
    val oldMaximum: Int?,
    val newMaximum: Int?,
    /** Ресурс был удалён вручную и возвращается по решению пользователя. */
    val restored: Boolean = false,
)

/** Сводка «было → стало». */
data class LevelUpSummary(
    val oldLevel: Int,
    val newLevel: Int,
    val classTitle: String,
    val oldClassLevel: Int,
    val newClassLevel: Int,
    val isNewClass: Boolean,
    val oldProficiencyBonus: Int,
    val newProficiencyBonus: Int,
    val oldMaxHp: Int,
    val newMaxHp: Int,
    val hpGain: Int,
    val oldSlots: Map<Int, Int>,
    val newSlots: Map<Int, Int>,
    val resources: List<ResourceChange>,
    val features: List<LevelUpFeature>,
    val abilityChanges: Map<AbilityType, Pair<Int, Int>>,
    val addedSpellIds: List<String>,
    val removedSpellIds: List<String>,
    val newFeatIds: List<String>,
    val subclassTitle: String?,
    /** Новые варианты выборов: заголовок выбора → названия вариантов. */
    val newOptions: Map<String, List<String>>,
    /** Заменённые варианты и заклинания: «старое» → «новое» (названия). */
    val replaced: List<Pair<String, String>>,
    /** Владения, которых раньше не было. */
    val newProficiencies: LevelUpProficiencies,
    /** Лимит подготовки «было → стало»; null — не изменился. */
    val maxPreparedChange: Pair<Int, Int>?,
    /** Новые всегда подготовленные заклинания этого уровня (домен, клятва, специальность). */
    val newAlwaysPreparedIds: List<String> = emptyList(),
    /** Переподготовка: заклинания, которые станут подготовленными, и с которых подготовка снимается. */
    val preparedAddedIds: List<String> = emptyList(),
    val preparedRemovedIds: List<String> = emptyList(),
)

/** Новые владения после повышения уровня: подписи перечислений берёт интерфейс. */
data class LevelUpProficiencies(
    val armor: Set<com.example.spellbook.data.model.ArmorProficiency> = emptySet(),
    val weapons: Set<com.example.spellbook.data.model.WeaponProficiency> = emptySet(),
    val otherWeapons: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val skills: Set<com.example.spellbook.data.model.SkillType> = emptySet(),
    val expertise: Set<com.example.spellbook.data.model.SkillType> = emptySet(),
    val saves: Set<AbilityType> = emptySet(),
) {
    val isEmpty: Boolean
        get() = armor.isEmpty() && weapons.isEmpty() && otherWeapons.isEmpty() && tools.isEmpty() &&
            languages.isEmpty() && skills.isEmpty() && expertise.isEmpty() && saves.isEmpty()
}

/**
 * Всё, что нужно записать в базу: персонаж целиком и изменения связей.
 *
 * @param notes изменённые или новые блоки заметок (upsert).
 * @param addedSpellIds новые связи с заклинаниями; [preparedSpellIds] — какие из связей
 * (новых или существующих) отметить подготовленными; [removedSpellIds] — связи, которые убрать.
 */
data class LevelUpResult(
    val character: Character,
    val notes: List<NoteBlock>,
    val addedSpellIds: Set<String>,
    val preparedSpellIds: Set<String>,
    val removedSpellIds: Set<String>,
    val addedFeatIds: List<String>,
    val summary: LevelUpSummary,
    /** Уже известные заклинания, с которых снимается подготовка (переподготовка). */
    val unpreparedSpellIds: Set<String> = emptySet(),
)
