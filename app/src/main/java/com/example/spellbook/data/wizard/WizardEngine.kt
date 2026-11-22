package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Background
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.Race
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.abilityModifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Кэш разобранных правил: разбор длинных описаний не бесплатен, а мастер
 * пересчитывает контекст при каждом изменении черновика.
 *
 * Ключ включает хэш описания — если запись в библиотеке отредактировали,
 * правила разберутся заново.
 */
class WizardRulesCache {
    private val races = ConcurrentHashMap<String, RaceRules>()
    private val classes = ConcurrentHashMap<String, ClassRules>()
    private val backgrounds = ConcurrentHashMap<String, BackgroundRules>()

    fun race(race: Race): RaceRules =
        races.getOrPut(race.id + ":" + race.description.hashCode()) { RaceRulesParser.parse(race) }

    fun characterClass(charClass: CharClass): ClassRules =
        classes.getOrPut(charClass.id + ":" + charClass.description.hashCode()) { ClassRulesParser.parse(charClass) }

    fun background(background: Background): BackgroundRules =
        backgrounds.getOrPut(background.id + ":" + background.description.hashCode()) {
            BackgroundRulesParser.parse(background)
        }
}

/** Библиотеки, из которых выбирает мастер. */
data class WizardLibrary(
    val races: List<Race>,
    val classes: List<CharClass>,
    val backgrounds: List<Background>,
    /** Черты: нужны, чтобы их бонусы и выборы учитывались в характеристиках и владениях. */
    val feats: List<Feat> = emptyList(),
    /**
     * Заклинания библиотеки: выбор заклинания хранит id, а ресурсу «раз до отдыха»
     * для выбранного по черте заклинания нужно его название.
     */
    val spells: List<com.example.spellbook.data.model.Spell> = emptyList(),
    /**
     * Магические предметы: описания предметов для копий инфузии «Копия магического предмета».
     * Пусто — копии описываются только названием и настройкой из таблицы инфузии.
     */
    val magicItems: List<com.example.spellbook.data.model.MagicItem> = emptyList(),
)

/** Шаг мастера. Дополнительные шаги появляются сразу после шага своего источника. */
sealed interface WizardStep {
    val key: String

    data object Basics : WizardStep { override val key = "basics" }
    data object Race : WizardStep { override val key = "race" }
    data object Class : WizardStep { override val key = "class" }

    /** Стартовое снаряжение класса: доспехи, оружие на выбор или золото. */
    data object Equipment : WizardStep { override val key = "equipment" }

    /** Отдельный экран выбора: воззвания, боевые приёмы, боевой стиль и т. п. */
    data class Choice(val choiceId: String) : WizardStep { override val key = "choice:$choiceId" }

    /** Заговоры, известные заклинания или книга волшебника, подготовка. */
    data object Spells : WizardStep { override val key = "spells" }
    data object Background : WizardStep { override val key = "background" }
    data object Abilities : WizardStep { override val key = "abilities" }
    data object Feats : WizardStep { override val key = "feats" }
    data object Summary : WizardStep { override val key = "summary" }
}

/** Что не доделано на шаге; текст собирает интерфейс по [kind]. */
enum class IssueKind {
    NAME_MISSING,
    RACE_MISSING,
    SUBRACE_MISSING,

    /** Раса позволяет выбрать размер («Маленький или Средний»), а он не выбран. */
    SIZE_MISSING,
    CLASS_MISSING,
    SUBCLASS_MISSING,
    BACKGROUND_MISSING,
    CHOICE_INCOMPLETE,
    CANTRIPS_INCOMPLETE,
    SPELLS_INCOMPLETE,
    PREPARED_INCOMPLETE,
    SCORES_UNASSIGNED,
    POINT_BUY_OVER_BUDGET,
    BONUS_UNASSIGNED,
    ASI_INCOMPLETE,
    FEAT_MISSING,
    WEAPON_MISSING,

    /**
     * Мистический рыцарь или ловкач: заклинаний других школ больше, чем свободных мест
     * ([SpellcastingPlan.anySchoolSpells]); [WizardIssue.missing] — на сколько превышено.
     */
    SPELLS_SCHOOL_LIMIT,
}

/**
 * Незавершённость на шаге.
 *
 * @param label уточнение: название выбора или черты.
 * @param missing сколько ещё нужно выбрать (0 — неприменимо).
 */
data class WizardIssue(val kind: IssueKind, val label: String = "", val missing: Int = 0)

/**
 * Слот черты: от расы (альтернативный человек), предыстории (Разорённый) или
 * вместо увеличения характеристик.
 *
 * @param allowedNames имена допустимых черт; пусто — любая черта из библиотеки.
 */
data class FeatSlot(
    val id: String,
    val source: ChoiceSource,
    val level: Int,
    val allowedNames: List<String> = emptyList(),
)

/** Чистые вычисления мастера: контекст правил, характеристики, шаги и проверки. */
object WizardEngine {

    /** Стандартный набор значений. */
    val STANDARD_ARRAY = listOf(15, 14, 13, 12, 10, 8)

    /** Бюджет покупки характеристик. */
    const val POINT_BUY_BUDGET = 27
    const val POINT_BUY_MIN = 8
    const val POINT_BUY_MAX = 15

    /** Значение по умолчанию при ручном вводе. */
    const val MANUAL_DEFAULT = 10

    /** Максимум характеристики после увеличений по уровню. */
    const val ASI_CAP = 20

    /** Запасной шаблон, если бонусы расы разобрать не удалось. */
    val FALLBACK_PATTERNS = listOf(listOf(2, 1), listOf(1, 1, 1))

    /** Стоимость значения при покупке за очки; null — значение недопустимо. */
    fun pointBuyCost(score: Int): Int? = when (score) {
        8 -> 0
        9 -> 1
        10 -> 2
        11 -> 3
        12 -> 4
        13 -> 5
        14 -> 7
        15 -> 9
        else -> null
    }

    fun pointBuySpent(draft: CharacterDraft): Int =
        AbilityType.entries.sumOf { pointBuyCost(draft.baseScores[it] ?: POINT_BUY_MIN) ?: 0 }

    /** id выбора инфузий изобретателя. */
    const val INFUSIONS_CHOICE_ID = "class.infusions"

    // region Контекст

    fun context(draft: CharacterDraft, library: WizardLibrary, cache: WizardRulesCache): WizardContext {
        val parsedRace = draft.raceId?.let { id -> library.races.firstOrNull { it.id == id } }?.let(cache::race)
        val subrace = parsedRace?.subraces?.firstOrNull { it.id == draft.subraceId }
        // Выбрана полная версия расы из другой книги («Кентавр (MOT)»): основные особенности,
        // владения, выборы и бонусы расы не применяются, иначе они задвоятся.
        val race = when {
            subrace?.replacesBaseTraits == true -> parsedRace.copy(
                traits = emptyList(),
                grants = RaceGrants(speed = parsedRace.grants.speed, size = parsedRace.grants.size),
                choices = emptyList(),
                abilityBonus = AbilityBonusRule(parsed = false),
            )
            // Одноимённая особенность разновидности — её версия и заменяет особенность расы:
            // «Дьявольское наследие» Асмодея, «Тёмное зрение» и «Языки» дома Тарашк.
            subrace != null && parsedRace != null -> withoutReplacedTraits(parsedRace, subrace)
            else -> parsedRace
        }
        val parsedClass = draft.classId
            ?.let { id -> library.classes.firstOrNull { it.id == id } }
            ?.let(cache::characterClass)
        val parsedSubclass = parsedClass
            ?.takeIf { draft.level >= it.subclassLevel }
            ?.subclasses
            ?.firstOrNull { it.id == draft.subclassId }
        // Опциональные умения, заменяющие базовые («Предпочтительный противник» вместо «Избранного
        // врага»): дальше всё считается по итоговому набору — одно из двух, не оба сразу.
        val swaps = if (draft.showOptional && parsedClass != null) {
            optionalSwaps(parsedClass, parsedSubclass, draft.level)
        } else {
            emptyList()
        }
        val (characterClass, subclass) = applySwaps(parsedClass, parsedSubclass, swaps, draft)
        val background = draft.backgroundId
            ?.let { id -> library.backgrounds.firstOrNull { it.id == id } }
            ?.let(cache::background)

        val classChoices = characterClass?.let {
            ClassChoiceCatalog.choices(
                rules = it,
                subclass = subclass,
                level = draft.level,
                selections = draft.selections,
                showOptional = draft.showOptional,
                showUnofficial = draft.showUnofficial,
            )
        }.orEmpty().map { choice ->
            // «Копия магического предмета» берётся несколько раз — по копии на каждый предмет.
            if (choice.kind == ChoiceKind.OPTION && choice.id == INFUSIONS_CHOICE_ID) {
                choice.copy(options = ReplicateInfusion.expand(choice.options, library.magicItems))
            } else {
                choice
            }
        }

        // Заклинания из умений класса и архетипа: «Техника тени», «Дополнительный заговор»…
        val featureSpells = characterClass
            ?.let { ClassChoiceCatalog.featureSpells(it, subclass, draft.level, draft.showOptional) }
            ?: FeatureSpells.Parsed(emptyList(), emptyList())

        val baseChoices = buildList {
            race?.let { addAll(it.choices) }
            subrace?.let { addAll(it.choices) }
            characterClass?.skillChoice?.let(::add)
            addAll(classChoices)
            addAll(featureSpells.choices)
            background?.let { addAll(it.choices) }
        }

        val raceTraits = race?.traits.orEmpty().map { it to ChoiceSource.RACE } +
            subrace?.traits.orEmpty().map { it to ChoiceSource.SUBRACE }
        val resources = buildList {
            raceTraits.forEach { (trait, source) -> addAll(FeatureResources.detect(trait, source, draft.level)) }
            characterClass?.let { addAll(ClassChoiceCatalog.resources(it, subclass, draft.level, draft.showOptional)) }
        }.distinctBy { it.name.lowercase() }

        val withoutFeats = WizardContext(
            swaps = swaps,
            race = race,
            subrace = subrace,
            characterClass = characterClass,
            subclass = subclass,
            background = background,
            choices = baseChoices,
            resources = resources,
            spellcasting = null,
            classGrants = characterClass
                ?.let { ClassChoiceCatalog.featureGrants(it, subclass, draft.level, draft.showOptional) }
                ?: FeatureGrants(),
            featureSpells = featureSpells.spells.filter { it.minLevel <= draft.level }.map { it.name } +
                // Выбранная склонность божественной души и т. п. у классов, которые заклинания не готовят.
                if (characterClass != null && !characterClass.type.preparesFromClassList) {
                    ClassChoiceCatalog.variantSpells(subclass, draft.level, draft.selections)
                } else {
                    emptyList()
                },
        )

        // Черты: эффекты и выборы выбранных в слотах черт.
        val featEffects = featSlots(draft, withoutFeats).mapNotNull { slot ->
            val feat = draft.featSlots[slot.id]?.let { id -> library.feats.firstOrNull { it.id == id } }
            feat?.let { slot.id to FeatRules.parse(it, slot.id) }
        }.toMap()

        val choices = (baseChoices + featEffects.values.flatMap { it.choices })
            .map(::withToolOptions)
            .map(::withLanguageOptions)

        // Выбранные варианты (воззвания и т. п.) со своими заклинаниями и ограничениями «раз до отдыха».
        val (optionResources, optionSpells) = ClassChoiceCatalog.selectedOptionEffects(choices, draft.selections, draft.level)

        // Черты тоже дают заклинания «раз до отдыха»: туманный шаг у «Затронутого феями»,
        // а выбранное по черте заклинание — тем же правилом, что и названное в тексте.
        val featResources = featSlots(draft, withoutFeats).flatMap { slot ->
            val feat = draft.featSlots[slot.id]?.let { id -> library.feats.firstOrNull { it.id == id } }
                ?: return@flatMap emptyList()
            val named = FeatureResources.detect(RuleFeature(title = feat.name, text = feat.description), ChoiceSource.FEAT, draft.level)
            named + chosenFeatSpellResources(slot.id, featEffects[slot.id], draft, named, library.spells)
        }

        val partial = withoutFeats.copy(
            choices = choices,
            featEffects = featEffects,
            resources = (resources + optionResources + featResources).distinctBy { it.name.lowercase().replace('ё', 'е') },
            featureSpells = (withoutFeats.featureSpells + optionSpells).distinctBy { it.lowercase() },
        )
        val spellcasting = characterClass?.let {
            ClassChoiceCatalog.spellcasting(it, subclass, draft.level, finalScores(draft, partial), draft.selections)
        }
        // Заклинания списка класса вне школ мистического рыцаря или ловкача — для проверки свободных мест.
        val offSchool = spellcasting?.takeIf { it.schools.isNotEmpty() }?.let { plan ->
            library.spells.filter { it.level >= 1 && plan.classCode in it.classes && it.school !in plan.schools }
                .map { it.id }.toSet()
        }.orEmpty()
        return partial.copy(spellcasting = spellcasting, offSchoolSpellIds = offSchool)
    }

    /**
     * Ресурсы на заклинания, выбранные по черте.
     *
     * «Затронутый феями» и «Затронутый тенью» разрешают накладывать без ячейки «каждое из этих
     * заклинаний» раз до отдыха — и названное, и выбранное. Разбор текста видит только названное
     * (туманный шаг), поэтому выбранные заклинания получают такой же ресурс, если черта даёт ресурс
     * хотя бы одному своему заклинанию. Заговоры накладываются неограниченно и ресурса не дают.
     *
     * @param named ресурсы, уже найденные в тексте черты: по ним видно, что черта ограничивает
     * свои заклинания поштучно, и откуда взять описание.
     */
    private fun chosenFeatSpellResources(
        slotId: String,
        effects: FeatEffects?,
        draft: CharacterDraft,
        named: List<ResourceSpec>,
        spells: List<com.example.spellbook.data.model.Spell>,
    ): List<ResourceSpec> {
        val template = named.firstOrNull { it.spellName.isNotBlank() } ?: return emptyList()
        val choices = effects?.choices.orEmpty().filter { it.kind == ChoiceKind.SPELL && it.id.startsWith("feat.$slotId") }
        val spellIds = choices.flatMap { draft.selections[it.id].orEmpty() }.distinct()
        return spellIds.mapNotNull { id ->
            val spell = spells.firstOrNull { it.id == id } ?: return@mapNotNull null
            template.copy(name = spell.name, spellName = spell.name)
        }
    }

    /**
     * Убирает из расы особенности, которые разновидность переопределяет своей версией, вместе
     * с их заклинаниями и выборами. Языки разновидности тоже заменяют языки расы.
     *
     * Кроме одноимённых убираются особенности, которые текст явно называет заменёнными ([SubraceRules.replacedTraits]):
     * «Наследие Авернуса» Зариэль заменяет «Дьявольское наследие» — иначе тифлинг получал и адское
     * возмездие с тьмой, и палящую кару с клеймящей карой.
     */
    private fun withoutReplacedTraits(race: RaceRules, subrace: SubraceRules): RaceRules {
        fun norm(text: String) = text.lowercase().replace('ё', 'е').trim()
        val replaced = (subrace.traits.map { it.title } + subrace.replacedTraits).map(::norm).toSet()
        if (replaced.none { title -> race.traits.any { norm(it.title) == title } }) return race
        val languagesReplaced = "языки" in replaced
        val replacedTitles = race.traits.filter { norm(it.title) in replaced }.map { it.title }.toSet()
        return race.copy(
            traits = race.traits.filterNot { norm(it.title) in replaced },
            grants = race.grants.copy(
                spells = race.grants.spells.filterNot { it.source in replacedTitles },
                languages = if (languagesReplaced) emptyList() else race.grants.languages,
            ),
            // Выборы заменённых особенностей (язык на выбор) тоже уходят.
            choices = race.choices.filterNot { choice -> choice.title in replacedTitles },
        )
    }

    // region Опциональные замены

    /**
     * Опциональные умения класса, которые заменяют базовые на текущем уровне.
     * Замена считается, только если заменяемое умение действительно есть — у жреца оно
     * зависит от домена («Божественный удар» или «Могущественное колдовство»).
     */
    fun optionalSwaps(rules: ClassRules, subclass: SubclassRules?, level: Int): List<OptionalSwap> {
        val pool = rules.features + subclass?.features.orEmpty()
        return rules.features
            .filter { it.extended && it.replaces.isNotEmpty() && it.level <= level }
            .mapNotNull { optional ->
                val base = pool.filter { feature ->
                    !feature.extended && feature.level <= level &&
                        optional.replaces.any { sameFeatureTitle(it, feature.title) }
                }
                OptionalSwap(optional.title, optional, base).takeIf { base.isNotEmpty() }
            }
    }

    /**
     * Итоговый набор умений: выбранное опциональное становится обычным умением, а базовое
     * убирается; невыбранное заменяющее умение убирается само. Остальные опциональные
     * умения (дополняющие, а не заменяющие) по-прежнему зависят от переключателя.
     */
    private fun applySwaps(
        rules: ClassRules?,
        subclass: SubclassRules?,
        swaps: List<OptionalSwap>,
        draft: CharacterDraft,
    ): Pair<ClassRules?, SubclassRules?> {
        if (rules == null) return null to subclass
        val swapIds = swaps.map { it.id }.toSet()
        val chosenIds = swaps.filter { draft.optionalSwaps[it.id] == true }.map { it.id }.toSet()
        val dropped = swaps.filter { it.id in chosenIds }.flatMap { swap -> swap.base.map { it.title } }.toSet()
        // Заменяющее умение без выбора или при выключенном переключателе не попадает никуда.
        val features = rules.features.mapNotNull { feature ->
            when {
                feature.extended && feature.title in swapIds ->
                    if (feature.title in chosenIds) feature.copy(extended = false) else null
                feature.extended && feature.replaces.isNotEmpty() -> null
                !feature.extended && feature.title in dropped -> null
                else -> feature
            }
        }
        val subFeatures = subclass?.features?.filterNot { !it.extended && it.title in dropped }
        return rules.copy(features = features) to subclass?.copy(features = subFeatures.orEmpty())
    }

    /** Названия умений совпадают без учёта регистра и «ё». */
    private fun sameFeatureTitle(a: String, b: String): Boolean {
        fun norm(text: String) = text.lowercase().replace('ё', 'е').trim()
        return norm(a) == norm(b)
    }

    // endregion

    /**
     * Выбор инструмента всегда идёт из справочника: варианты подбираются по тексту правила
     * («один музыкальный инструмент» → музыкальные), а у каждого есть описание владения.
     * id варианта — [ToolInfo.id]; название подставляется при сборке персонажа.
     */
    private fun withToolOptions(choice: ChoiceSpec): ChoiceSpec {
        if (choice.kind != ChoiceKind.TOOL) return choice
        val ruleText = listOf(choice.sourceText, choice.hint).filter { it.isNotBlank() }.joinToString(" ")
        val tools = ToolCatalog.candidates(ruleText)
        return choice.copy(options = tools.map { toolOption(it) })
    }

    /**
     * Языки выбираются только чипами: если правило не перечисляет варианты,
     * предлагаются все стандартные и экзотические языки из «Книги игрока».
     */
    private fun withLanguageOptions(choice: ChoiceSpec): ChoiceSpec {
        if (choice.kind != ChoiceKind.LANGUAGE || choice.options.isNotEmpty()) return choice
        return choice.copy(options = LanguageCatalog.all.map { ChoiceOption(it, it) })
    }

    fun toolOption(tool: ToolInfo): ChoiceOption = ChoiceOption(
        id = tool.id,
        title = tool.name,
        text = "${tool.description}\n\n**Владение.** ${tool.uses}",
    )

    /** Языки, которые персонаж уже знает: от расы, предыстории и сделанных выборов. */
    fun knownLanguages(draft: CharacterDraft, context: WizardContext): Set<String> {
        val fixed = context.race?.grants?.languages.orEmpty() + context.subrace?.grants?.languages.orEmpty() +
            context.background?.languages?.split(',')?.map { it.trim() }.orEmpty() + context.classGrants.languages +
            context.featGrants.languages
        val chosen = context.choices.filter { it.kind == ChoiceKind.LANGUAGE }.flatMap { draft.selections[it.id].orEmpty() }
        return (fixed + chosen).filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    }

    // endregion

    // region Характеристики

    /** Пул значений для распределения: стандартный набор или броски. */
    fun scorePool(draft: CharacterDraft): List<Int> = when (draft.abilityMethod) {
        AbilityMethod.STANDARD_ARRAY -> STANDARD_ARRAY
        AbilityMethod.ROLL -> draft.rolledScores.map { it.total }
        AbilityMethod.MANUAL, AbilityMethod.POINT_BUY -> emptyList()
    }

    /** Базовые значения до бонусов; null — значение ещё не распределено. */
    fun baseScores(draft: CharacterDraft): Map<AbilityType, Int?> = AbilityType.entries.associateWith { ability ->
        when (draft.abilityMethod) {
            AbilityMethod.MANUAL -> draft.baseScores[ability] ?: MANUAL_DEFAULT
            AbilityMethod.POINT_BUY -> draft.baseScores[ability] ?: POINT_BUY_MIN
            AbilityMethod.STANDARD_ARRAY, AbilityMethod.ROLL ->
                draft.poolAssignment[ability]?.let { scorePool(draft).getOrNull(it) }
        }
    }

    /**
     * Итоговое правило бонусов расы с учётом разновидности.
     *
     * Если разновидность заменяет бонусы основной расы (альтернативный человек),
     * берётся только её правило. Иначе фиксированные бонусы складываются, а шаблоны
     * свободных бонусов берутся у той части, где они есть. Если ничего не разобрано,
     * действует запасное свободное распределение.
     */
    fun bonusRule(context: WizardContext): AbilityBonusRule? {
        val race = context.race ?: return null
        val subrace = context.subrace
        val raceRule = race.abilityBonus
        val subRule = subrace?.abilityBonus
        val combined = when {
            subrace != null && subrace.replacesBaseAbilityBonus && subRule != null -> subRule
            subRule == null -> raceRule
            else -> AbilityBonusRule(
                fixed = raceRule.fixed + subRule.fixed,
                patterns = raceRule.patterns.ifEmpty { subRule.patterns },
                excludeFixed = raceRule.excludeFixed && subRule.excludeFixed,
                parsed = raceRule.parsed || subRule.parsed,
                sourceText = listOf(raceRule.sourceText, subRule.sourceText).filter { it.isNotBlank() }.joinToString("\n"),
            )
        }
        return if (!combined.parsed && combined.fixed.isEmpty() && combined.patterns.isEmpty()) {
            combined.copy(patterns = FALLBACK_PATTERNS)
        } else {
            combined
        }
    }

    /**
     * Величины бонусов, которые распределяет пользователь, по порядку [CharacterDraft.bonusTargets].
     *
     * В режиме [BonusMode.RULES] — только значения выбранного шаблона, в [BonusMode.FREE] —
     * сначала фиксированные, затем шаблонные.
     */
    fun bonusValues(draft: CharacterDraft, rule: AbilityBonusRule): List<Int> {
        val pattern = rule.patterns.getOrNull(draft.bonusPatternIndex) ?: rule.patterns.firstOrNull().orEmpty()
        return when (draft.bonusMode) {
            BonusMode.RULES -> pattern
            BonusMode.FREE -> rule.fixed.map { it.amount } + pattern
        }
    }

    /** Бонусы расы по характеристикам с учётом режима и распределения. */
    fun racialBonuses(draft: CharacterDraft, context: WizardContext): Map<AbilityType, Int> {
        val rule = bonusRule(context) ?: return emptyMap()
        val result = mutableMapOf<AbilityType, Int>()
        if (draft.bonusMode == BonusMode.RULES) {
            rule.fixed.forEach { result.merge(it.ability, it.amount, Int::plus) }
        }
        bonusValues(draft, rule).forEachIndexed { index, amount ->
            val target = draft.bonusTargets.getOrNull(index) ?: return@forEachIndexed
            result.merge(target, amount, Int::plus)
        }
        return result
    }

    /** Уровни «Увеличения характеристик», доступные на уровне персонажа. */
    fun asiLevels(draft: CharacterDraft, context: WizardContext): List<Int> =
        context.characterClass?.asiLevels.orEmpty().filter { it <= draft.level }

    /**
     * Прибавки от черт: фиксированные («Сила +1») и выбранные пользователем
     * («Интеллект, Мудрость или Харизма +1»).
     */
    fun featBonuses(draft: CharacterDraft, context: WizardContext): Map<AbilityType, Int> {
        val result = mutableMapOf<AbilityType, Int>()
        context.featEffects.values.forEach { effects ->
            effects.fixedAbilities.forEach { (ability, amount) -> result.merge(ability, amount, Int::plus) }
            effects.choices.filter { it.kind == ChoiceKind.ABILITY }.forEach { choice ->
                val amount = Regex("\\+(\\d)").find(choice.title)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                draft.selections[choice.id].orEmpty()
                    .mapNotNull { runCatching { AbilityType.valueOf(it) }.getOrNull() }
                    .forEach { result.merge(it, amount, Int::plus) }
            }
        }
        return result
    }

    /** Прибавки от «Увеличения характеристик» (без черт). */
    fun asiBonuses(draft: CharacterDraft, context: WizardContext): Map<AbilityType, Int> {
        val result = mutableMapOf<AbilityType, Int>()
        asiLevels(draft, context).forEach { level ->
            val choice = draft.asiChoices[level] ?: return@forEach
            when (choice.kind) {
                AsiKind.PLUS_TWO -> choice.abilities.firstOrNull()?.let { result.merge(it, 2, Int::plus) }
                AsiKind.PLUS_ONE_ONE -> choice.abilities.take(2).forEach { result.merge(it, 1, Int::plus) }
                AsiKind.FEAT -> Unit
            }
        }
        return result
    }

    /**
     * Итоговые значения характеристик: база + раса + увеличения по уровню.
     * Увеличения по уровню не поднимают значение выше [ASI_CAP]. Нераспределённое
     * базовое значение считается за 10, чтобы сводка оставалась осмысленной.
     */
    fun finalScores(draft: CharacterDraft, context: WizardContext): Map<AbilityType, Int> {
        val base = baseScores(draft)
        val racial = racialBonuses(draft, context)
        val asi = asiBonuses(draft, context)
        val feats = featBonuses(draft, context)
        return AbilityType.entries.associateWith { ability ->
            val beforeAsi = (base[ability] ?: MANUAL_DEFAULT) + (racial[ability] ?: 0)
            // Увеличения по уровню и черты не поднимают значение выше 20 («при максимуме 20»).
            val bonus = (asi[ability] ?: 0) + (feats[ability] ?: 0)
            if (bonus == 0) beforeAsi else maxOf(beforeAsi, minOf(ASI_CAP, beforeAsi + bonus))
        }
    }

    // endregion

    // region Черты и хиты

    fun featSlots(draft: CharacterDraft, context: WizardContext): List<FeatSlot> = buildList {
        val raceSlots = (context.race?.grants?.featSlots ?: 0) + (context.subrace?.grants?.featSlots ?: 0)
        repeat(raceSlots) { add(FeatSlot("race.$it", ChoiceSource.RACE, 1)) }
        context.background?.featOptions?.takeIf { it.isNotEmpty() }?.let { names ->
            add(FeatSlot("background.0", ChoiceSource.BACKGROUND, 1, names))
        }
        asiLevels(draft, context)
            .filter { draft.asiChoices[it]?.kind == AsiKind.FEAT }
            .forEach { add(FeatSlot("asi.$it", ChoiceSource.CLASS, it)) }
    }

    /**
     * Черты, допустимые в слоте. Имена из текста предыстории сравниваются без учёта
     * регистра и «ё»; если ни одна не нашлась в библиотеке, доступны все — иначе выбор
     * оказался бы невозможен.
     */
    fun featsForSlot(slot: FeatSlot?, feats: List<Feat>): List<Feat> {
        val names = slot?.allowedNames.orEmpty()
        if (names.isEmpty()) return feats
        fun norm(text: String) = text.lowercase().replace('ё', 'е').trim()
        val wanted = names.map(::norm).toSet()
        return feats.filter { norm(it.name) in wanted }.ifEmpty { feats }
    }

    /** Средний результат кости хитов по правилам: половина + 1. */
    fun averageHitDie(hitDie: Int): Int = hitDie / 2 + 1

    /** Максимум хитов по выбранному способу; null — класс не выбран (кроме ручного ввода). */
    fun maxHp(draft: CharacterDraft, context: WizardContext): Int? {
        if (draft.hpMethod == HpMethod.MANUAL) return draft.manualHp
        val featHp = context.featEffects.values.sumOf { it.hpPerLevel } * draft.level
        return classHp(draft, context)?.plus(featHp)
    }

    private fun classHp(draft: CharacterDraft, context: WizardContext): Int? {
        val hitDie = context.characterClass?.hitDie ?: return null
        val con = abilityModifier(finalScores(draft, context)[AbilityType.CONSTITUTION] ?: MANUAL_DEFAULT)
        var total = hitDie + con
        for (index in 0 until draft.level - 1) {
            val roll = when (draft.hpMethod) {
                HpMethod.ROLL -> draft.hpRolls.getOrNull(index) ?: averageHitDie(hitDie)
                else -> averageHitDie(hitDie)
            }
            total += maxOf(1, roll + con)
        }
        return total.coerceAtLeast(1)
    }

    // endregion

    // region Шаги и проверки

    /**
     * Шаги мастера: дополнительные экраны выбора идут сразу после экрана источника.
     *
     * Исключение — компетентность: она выбирается из навыков, которыми персонаж уже
     * владеет, поэтому её экран стоит после предыстории — когда все навыки уже известны.
     */
    fun steps(draft: CharacterDraft, context: WizardContext): List<WizardStep> {
        fun dedicated(vararg sources: ChoiceSource) = context.choices
            .filter { it.dedicatedStep && it.source in sources && it.kind != ChoiceKind.EXPERTISE }
            .map { WizardStep.Choice(it.id) }

        // Шаги, зависящие от других решений, идут после них:
        // компетентность — после всех источников навыков, заклинания — после характеристик
        // и черт (число подготовленных зависит от модификатора), хиты — на шаге характеристик.
        return buildList {
            add(WizardStep.Basics)
            add(WizardStep.Race)
            addAll(dedicated(ChoiceSource.RACE, ChoiceSource.SUBRACE))
            add(WizardStep.Class)
            addAll(dedicated(ChoiceSource.CLASS, ChoiceSource.SUBCLASS))
            if (context.characterClass != null) add(WizardStep.Equipment)
            add(WizardStep.Background)
            addAll(dedicated(ChoiceSource.BACKGROUND))
            addAll(
                context.choices.filter { it.kind == ChoiceKind.EXPERTISE && it.source != ChoiceSource.FEAT }
                    .map { WizardStep.Choice(it.id) },
            )
            add(WizardStep.Abilities)
            if (featSlots(draft, context).isNotEmpty()) add(WizardStep.Feats)
            if (hasSpellStep(context)) add(WizardStep.Spells)
            add(WizardStep.Summary)
        }
    }

    /**
     * На каком шаге показывается выбор ([WizardStep.key]).
     *
     * Заговоры и заклинания класса (арканум, книга теней, тайны магии) живут на шаге
     * заклинаний, а не на шаге класса: там уже есть список заклинаний с фильтрами.
     */
    fun choiceStepKey(choice: ChoiceSpec, context: WizardContext): String = when {
        // Выборы черт — прямо под чертой на шаге черт, включая заклинания и компетентность.
        choice.source == ChoiceSource.FEAT -> WizardStep.Feats.key
        choice.dedicatedStep || choice.kind == ChoiceKind.EXPERTISE -> WizardStep.Choice(choice.id).key
        (choice.kind == ChoiceKind.CANTRIP || choice.kind == ChoiceKind.SPELL) &&
            (choice.source == ChoiceSource.CLASS || choice.source == ChoiceSource.SUBCLASS) &&
            hasSpellStep(context) -> WizardStep.Spells.key
        else -> when (choice.source) {
            ChoiceSource.RACE, ChoiceSource.SUBRACE -> WizardStep.Race.key
            ChoiceSource.CLASS, ChoiceSource.SUBCLASS -> WizardStep.Class.key
            ChoiceSource.BACKGROUND -> WizardStep.Background.key
            ChoiceSource.FEAT -> WizardStep.Feats.key
        }
    }

    /** Выборы, которые показываются на шаге. */
    fun choicesOn(step: WizardStep, context: WizardContext): List<ChoiceSpec> =
        context.choices.filter { choiceStepKey(it, context) == step.key }

    /** Навыки, которыми персонаж владеет по текущим решениям (без компетентности). */
    fun proficientSkills(draft: CharacterDraft, context: WizardContext): Set<SkillType> {
        val granted = fixedSkills(context)
        val chosen = context.choices.flatMap { chosenSkills(draft, it) }
        return granted + chosen
    }

    /** Инструменты класса без частей «…на ваш выбор»: «Воровские инструменты». */
    fun fixedClassTools(rules: ClassRules): String =
        MarkupText.plain(rules.tools)
            .split(',', ';')
            .map { it.trim().removePrefix("а также").trim().trimEnd('.') }
            .filter { part ->
                val lower = part.lowercase()
                part.isNotBlank() && !lower.contains("выбор") && !lower.contains("выберите") && lower != "нет"
            }
            .joinToString(", ")

    /** Навыки, которые даются без выбора: от расы, разновидности, умений класса, предыстории и черт. */
    fun fixedSkills(context: WizardContext): Set<SkillType> =
        fixedSkillSources(context).flatMap { it.second }.toSet()

    /** Навыки без выбора с источником — для уведомлений о повторном владении. */
    fun fixedSkillSources(context: WizardContext): List<Pair<GrantSource, Set<SkillType>>> = listOfNotNull(
        context.race?.let { GrantSource(ChoiceSource.RACE, it.race.name) to it.grants.skills },
        context.subrace?.let { GrantSource(ChoiceSource.SUBRACE, it.title) to it.grants.skills },
        context.characterClass?.let { GrantSource(ChoiceSource.CLASS, it.charClass.name) to context.classGrants.skills },
        context.background?.let { GrantSource(ChoiceSource.BACKGROUND, it.background.name) to it.skills },
        GrantSource(ChoiceSource.FEAT, "") to context.featGrants.skills,
    ).filter { it.second.isNotEmpty() }

    /** Навыки, выбранные в выборе (включая «навыки или инструменты» черты «Одарённый»). */
    fun chosenSkills(draft: CharacterDraft, choice: ChoiceSpec): List<SkillType> = when (choice.kind) {
        ChoiceKind.SKILL -> draft.selections[choice.id].orEmpty()
        ChoiceKind.PROFICIENCY -> draft.selections[choice.id].orEmpty()
            .filter { it.startsWith(SKILL_PREFIX) }.map { it.removePrefix(SKILL_PREFIX) }
        else -> emptyList()
    }.mapNotNull { runCatching { SkillType.valueOf(it) }.getOrNull() }

    /** Инструменты, выбранные в выборе (`ToolInfo.id`). */
    fun chosenTools(draft: CharacterDraft, choice: ChoiceSpec): List<String> = when (choice.kind) {
        ChoiceKind.TOOL -> draft.selections[choice.id].orEmpty()
        ChoiceKind.PROFICIENCY -> draft.selections[choice.id].orEmpty()
            .filter { it.startsWith(TOOL_PREFIX) }.map { it.removePrefix(TOOL_PREFIX) }
        else -> emptyList()
    }

    const val SKILL_PREFIX = "skill:"
    const val TOOL_PREFIX = "tool:"

    /**
     * Нужен ли шаг заклинаний: только если на нём есть что выбирать. У паладина и следопыта
     * 1-го уровня заклинательство есть, но заклинаний ещё нет — шаг не показывается.
     */
    fun hasSpellStep(context: WizardContext): Boolean {
        val plan = context.spellcasting ?: return false
        val hasClassChoices = context.choices.any { choice ->
            (choice.kind == ChoiceKind.CANTRIP || choice.kind == ChoiceKind.SPELL) &&
                (choice.source == ChoiceSource.CLASS || choice.source == ChoiceSource.SUBCLASS) &&
                !choice.dedicatedStep
        }
        return plan.cantrips > 0 || (plan.knownSpells ?: 0) > 0 || (plan.spellbookSpells ?: 0) > 0 ||
            plan.canPrepare || hasClassChoices
    }

    /** Сколько выбрано в выборе (пустые строки свободного ввода не считаются). */
    fun selectedCount(draft: CharacterDraft, choice: ChoiceSpec): Int =
        draft.selections[choice.id].orEmpty().count { it.isNotBlank() }

    /**
     * Незавершённые выборы и навыки, выбранные дважды: если предыстория даёт навык,
     * уже взятый на шаге класса, по правилам вместо него выбирается другой.
     */
    private fun choiceIssues(
        draft: CharacterDraft,
        choices: List<ChoiceSpec>,
        context: WizardContext? = null,
    ): List<WizardIssue> {
        // Повторные владения не считаются незавершённостью: о них сообщает отдельное
        // уведомление (ProficiencyOverlaps), и пользователь может оставить их как есть.
        return choices.mapNotNull { choice ->
            val missing = choice.count - selectedCount(draft, choice)
            WizardIssue(IssueKind.CHOICE_INCOMPLETE, choice.title, missing).takeIf { missing > 0 }
        }
    }

    /** Незавершённости шага; пустой список — шаг готов. */
    fun issues(step: WizardStep, draft: CharacterDraft, context: WizardContext): List<WizardIssue> = when (step) {
        WizardStep.Basics -> listOfNotNull(
            WizardIssue(IssueKind.NAME_MISSING).takeIf { draft.name.isBlank() },
        )

        WizardStep.Race -> buildList {
            val race = context.race
            if (race == null) {
                add(WizardIssue(IssueKind.RACE_MISSING))
            } else {
                if (race.subraceRequired && context.subrace == null) add(WizardIssue(IssueKind.SUBRACE_MISSING))
                if (RaceSizes.options(context).size > 1 && RaceSizes.resolve(draft, context).isEmpty()) {
                    add(WizardIssue(IssueKind.SIZE_MISSING))
                }
                addAll(choiceIssues(draft, choicesOn(step, context), context))
            }
        }

        WizardStep.Class -> buildList {
            val characterClass = context.characterClass
            if (characterClass == null) {
                add(WizardIssue(IssueKind.CLASS_MISSING))
            } else {
                if (needsSubclass(draft, context) && context.subclass == null) {
                    add(WizardIssue(IssueKind.SUBCLASS_MISSING, characterClass.subclassGroupTitle))
                }
                addAll(choiceIssues(draft, choicesOn(step, context), context))
            }
        }

        is WizardStep.Choice -> choiceIssues(draft, choicesOn(step, context), context)

        WizardStep.Spells -> buildList {
            addAll(choiceIssues(draft, choicesOn(step, context)))
            val plan = context.spellcasting ?: return@buildList
            val cantripsMissing = plan.cantrips - draft.cantripIds.size
            if (cantripsMissing > 0) add(WizardIssue(IssueKind.CANTRIPS_INCOMPLETE, missing = cantripsMissing))
            val spellTarget = plan.knownSpells ?: plan.spellbookSpells ?: 0
            val spellsMissing = spellTarget - draft.spellIds.size
            if (spellsMissing > 0) add(WizardIssue(IssueKind.SPELLS_INCOMPLETE, missing = spellsMissing))
            // Мистические рыцарь и ловкач: заклинаний других школ не больше свободных мест.
            val offSchoolOver = draft.spellIds.count { it in context.offSchoolSpellIds } - plan.anySchoolSpells
            if (plan.schools.isNotEmpty() && offSchoolOver > 0) {
                add(WizardIssue(IssueKind.SPELLS_SCHOOL_LIMIT, missing = offSchoolOver))
            }
            if (plan.canPrepare) {
                val preparedMissing = plan.preparedCount - draft.preparedIds.size
                if (preparedMissing > 0) add(WizardIssue(IssueKind.PREPARED_INCOMPLETE, missing = preparedMissing))
            }
        }

        WizardStep.Background -> buildList {
            if (context.background == null) {
                add(WizardIssue(IssueKind.BACKGROUND_MISSING))
            } else {
                addAll(choiceIssues(draft, choicesOn(step, context), context))
            }
        }

        WizardStep.Abilities -> buildList {
            val unassigned = baseScores(draft).count { it.value == null }
            if (unassigned > 0) add(WizardIssue(IssueKind.SCORES_UNASSIGNED, missing = unassigned))
            if (draft.abilityMethod == AbilityMethod.POINT_BUY && pointBuySpent(draft) > POINT_BUY_BUDGET) {
                add(WizardIssue(IssueKind.POINT_BUY_OVER_BUDGET))
            }
            bonusRule(context)?.let { rule ->
                val values = bonusValues(draft, rule)
                val missing = values.indices.count { draft.bonusTargets.getOrNull(it) == null }
                if (missing > 0) add(WizardIssue(IssueKind.BONUS_UNASSIGNED, missing = missing))
            }
            asiLevels(draft, context).forEach { level ->
                val choice = draft.asiChoices[level]
                val complete = when (choice?.kind) {
                    null -> false
                    AsiKind.PLUS_TWO -> choice.abilities.isNotEmpty()
                    AsiKind.PLUS_ONE_ONE -> choice.abilities.distinct().size >= 2
                    AsiKind.FEAT -> true
                }
                if (!complete) add(WizardIssue(IssueKind.ASI_INCOMPLETE, label = level.toString()))
            }
        }

        WizardStep.Feats -> featSlots(draft, context)
            .filter { draft.featSlots[it.id] == null }
            .map { WizardIssue(IssueKind.FEAT_MISSING, label = it.id) } +
            choiceIssues(draft, choicesOn(step, context), context)

        WizardStep.Equipment -> buildList {
            val missing = missingWeaponPicks(draft, context)
            if (missing > 0) add(WizardIssue(IssueKind.WEAPON_MISSING, missing = missing))
        }

        WizardStep.Summary -> emptyList()
    }

    /** Нужен ли архетип на текущем уровне. */
    fun needsSubclass(draft: CharacterDraft, context: WizardContext): Boolean {
        val rules = context.characterClass ?: return false
        return draft.level >= rules.subclassLevel && rules.subclasses.isNotEmpty()
    }

    /** Все незавершённости по шагам (для сводки). */
    fun allIssues(draft: CharacterDraft, context: WizardContext): List<Pair<WizardStep, List<WizardIssue>>> =
        steps(draft, context).map { it to issues(it, draft, context) }.filter { it.second.isNotEmpty() }

    /** Без имени и класса создать персонажа нельзя; остальное — предупреждения. */
    fun canCreate(draft: CharacterDraft, context: WizardContext): Boolean =
        draft.name.isNotBlank() && context.characterClass != null

    // endregion

    // region Изменения черновика

    /** Сбрасывает решения выборов с указанными префиксами (`race.`, `class.`…). */
    fun clearSelections(draft: CharacterDraft, vararg prefixes: String): CharacterDraft =
        draft.copy(selections = draft.selections.filterKeys { key -> prefixes.none { key.startsWith(it) } })

    /** Выбор расы: сбрасывает разновидность, её выборы, распределение бонусов и расовые черты. */
    fun selectRace(draft: CharacterDraft, raceId: String?): CharacterDraft {
        if (draft.raceId == raceId) return draft
        return clearSelections(draft, "race.", "subrace.").copy(
            raceId = raceId,
            subraceId = null,
            size = "",
            bonusPatternIndex = 0,
            bonusTargets = emptyList(),
            featSlots = draft.featSlots.filterKeys { !it.startsWith("race.") },
        )
    }

    fun selectSubrace(draft: CharacterDraft, subraceId: String?): CharacterDraft {
        if (draft.subraceId == subraceId) return draft
        return clearSelections(draft, "subrace.").copy(
            subraceId = subraceId,
            size = "",
            bonusPatternIndex = 0,
            bonusTargets = emptyList(),
            featSlots = draft.featSlots.filterKeys { !it.startsWith("race.") },
        )
    }

    /** Выбор класса: сбрасывает подкласс, выборы класса, снаряжение, заклинания и увеличения. */
    fun selectClass(draft: CharacterDraft, classId: String?): CharacterDraft {
        if (draft.classId == classId) return draft
        return clearSelections(draft, "class.", "subclass.").copy(
            classId = classId,
            subclassId = null,
            equipmentChoices = emptyMap(),
            weaponPicks = emptyMap(),
            takeClassGold = false,
            classGold = null,
            cantripIds = emptySet(),
            spellIds = emptySet(),
            preparedIds = emptySet(),
            asiChoices = emptyMap(),
            featSlots = draft.featSlots.filterKeys { !it.startsWith("asi.") },
            hpRolls = emptyList(),
        )
    }

    fun selectSubclass(draft: CharacterDraft, subclassId: String?): CharacterDraft {
        if (draft.subclassId == subclassId) return draft
        return clearSelections(draft, "subclass.").copy(subclassId = subclassId)
    }

    fun selectBackground(draft: CharacterDraft, backgroundId: String?): CharacterDraft {
        if (draft.backgroundId == backgroundId) return draft
        return clearSelections(draft, "background.").copy(
            backgroundId = backgroundId,
            featSlots = draft.featSlots.filterKeys { !it.startsWith("background.") },
        )
    }

    // endregion

    // region Снаряжение

    /** Ключ слота оружия в [CharacterDraft.weaponPicks]. */
    fun weaponSlotKey(group: Int, option: Int, slot: Int) = "$group.$option.$slot"

    /** Разобранный выбранный вариант каждой строки снаряжения класса: строка → (вариант, разбор). */
    fun chosenEquipment(draft: CharacterDraft, context: WizardContext): List<Triple<Int, Int, ParsedEquipment>> {
        val rules = context.characterClass ?: return emptyList()
        if (draft.takeClassGold) return emptyList()
        return rules.equipment.mapIndexedNotNull { index, group ->
            val optionIndex = (draft.equipmentChoices[index] ?: 0).coerceIn(0, (group.options.size - 1).coerceAtLeast(0))
            val option = group.options.getOrNull(optionIndex) ?: return@mapIndexedNotNull null
            Triple(index, optionIndex, EquipmentCatalog.parseOption(option.label))
        }
    }

    /** Сколько оружия на выбор ещё не выбрано. */
    fun missingWeaponPicks(draft: CharacterDraft, context: WizardContext): Int =
        chosenEquipment(draft, context).sumOf { (group, option, parsed) ->
            parsed.slots.withIndex().sumOf { (slotIndex, slot) ->
                val picked = draft.weaponPicks[weaponSlotKey(group, option, slotIndex)].orEmpty().size
                (slot.count - picked).coerceAtLeast(0)
            }
        }

    /**
     * Итоговое снаряжение класса: конкретные предметы выбранных вариантов и оружие,
     * выбранное в слотах. Невыбранный слот попадает в инвентарь исходной подписью.
     */
    fun classEquipment(draft: CharacterDraft, context: WizardContext): List<EquipmentEntry> =
        chosenEquipment(draft, context).flatMap { (group, option, parsed) ->
            parsed.entries + parsed.slots.withIndex().flatMap { (slotIndex, slot) ->
                val picks = draft.weaponPicks[weaponSlotKey(group, option, slotIndex)].orEmpty()
                    .mapNotNull(EquipmentCatalog::weaponById)
                val chosen = picks.groupBy { it.id }.map { (_, same) -> EquipmentEntry(same.first().name, same.size, weapon = same.first()) }
                val missing = slot.count - picks.size
                if (missing > 0) chosen + EquipmentEntry(slot.label, missing) else chosen
            }
        }

    // endregion
}
