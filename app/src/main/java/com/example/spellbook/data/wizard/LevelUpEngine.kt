package com.example.spellbook.data.wizard

import com.example.spellbook.data.initiativePartsOrDefault
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.MAX_CLASS_LEVEL
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.Race
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.model.proficiencyBonusFor
import com.example.spellbook.data.model.spellSlotsFor
import com.example.spellbook.data.withRecalculatedResources

/**
 * Чистые вычисления окна повышения уровня (см. описание потока в LevelUpModels.kt).
 *
 * Правила класса считаются через [WizardEngine.context] по «черновику класса» — без расы и
 * предыстории, с текущими характеристиками персонажа как базовыми значениями (бонусы расы
 * в них уже учтены и повторно не прибавляются). Новое на уровне — разница контекстов
 * на старом и новом уровне класса: умения, выборы, ресурсы, владения, заклинания.
 * Особенности расы зависят от общего уровня персонажа и считаются отдельным контекстом.
 */
object LevelUpEngine {

    /** Префикс id выбора черты, взятой на этом уровне: `feat.asi.<уровень класса>.…`. */
    private const val ASI_SLOT_PREFIX = "asi."

    const val REPLACE_SPELL_ID = "replace.spell"
    const val REPLACE_CANTRIP_ID = "replace.cantrip"
    const val REPLACE_OPTION_PREFIX = "replace.option."
    const val REPLACE_ARCANUM_ID = "replace.arcanum"
    const val REPLACE_EXPERTISE_ID = "replace.expertise"

    /** Замены, которые меняют связи с заклинаниями. */
    private val SPELL_REPLACEMENT_IDS = setOf("replace.spell", "replace.cantrip", "replace.arcanum")

    /** Разделитель архетипов разных классов в `Character.subclass` у мультиклассовых персонажей. */
    const val SUBCLASS_SEPARATOR = " / "

    // region Восстановление персонажа

    /**
     * Сопоставляет персонажа с библиотекой: раса, предыстория, классы, архетипы,
     * уже выбранные варианты (по заметкам) и решения по опциональным заменам Tasha.
     */
    fun base(
        character: Character,
        notes: List<NoteBlock>,
        knownSpellIds: Set<String>,
        preparedSpellIds: Set<String>,
        featIds: List<String>,
        library: WizardLibrary,
        cache: WizardRulesCache,
    ): LevelUpBase {
        val warnings = mutableListOf<LevelUpWarning>()
        val subclassTitles = character.subclass.split(SUBCLASS_SEPARATOR.trim()).map(::norm).filter { it.isNotEmpty() }
        val race = findRace(character, library, cache)
        if (character.race.isNotBlank() && race == null) {
            warnings += LevelUpWarning(LevelUpWarningKind.RACE_NOT_FOUND, character.race)
        }
        val raceRules = race?.let(cache::race)
        val subrace = raceRules?.subraces?.firstOrNull { norm(it.title) == norm(character.subrace) }
        val background = character.background.takeIf { it.isNotBlank() }
            ?.let { name -> library.backgrounds.firstOrNull { norm(it.name) == norm(name) } }
        if (character.background.isNotBlank() && background == null) {
            warnings += LevelUpWarning(LevelUpWarningKind.BACKGROUND_NOT_FOUND, character.background)
        }
        val scores = currentScores(character)
        val classes = character.classLevels.mapIndexed { index, classLevel ->
            val charClass = findClass(classLevel, subclassTitles, library, cache)
            val rules = charClass?.let(cache::characterClass)
            if (rules == null) {
                warnings += LevelUpWarning(
                    LevelUpWarningKind.CLASS_NOT_FOUND,
                    classLevel.displayName ?: classLevel.characterClass.name,
                    classIndex = index,
                )
            }
            val subclass = rules?.subclasses?.firstOrNull { norm(it.title) in subclassTitles }
            if (rules != null && subclass == null && subclassTitles.isNotEmpty() &&
                classLevel.level >= rules.subclassLevel && rules.subclasses.isNotEmpty() &&
                character.classLevels.size == 1
            ) {
                warnings += LevelUpWarning(LevelUpWarningKind.SUBCLASS_NOT_FOUND, character.subclass, classIndex = index)
            }
            if (charClass == null || rules == null) {
                LevelUpClassEntry(index, classLevel, charClass, rules, subclass)
            } else {
                val selections = restoreSelections(notes, charClass, subclass, classLevel.level, scores, library, cache)
                val swaps = restoreSwaps(notes, rules, subclass, classLevel.level)
                LevelUpClassEntry(index, classLevel, charClass, rules, subclass, selections, swaps)
            }
        }

        // Переключатели по умолчанию — по тому, что у персонажа уже есть.
        val showOptional = classes.any { entry ->
            val rules = entry.rules ?: return@any false
            val titles = classBlockTitles(notes, rules)
            entry.optionalSwaps.values.any { it } ||
                rules.features.filter { it.extended }.any { feature -> titles.any { it.startsWith(norm(feature.title)) } } ||
                restoredOptions(entry, library, cache, scores).any { it.extended && !it.id.startsWith("ua.") }
        }
        val showUnofficial = raceRules?.extended == true || subrace?.extended == true ||
            classes.any { entry ->
                entry.rules?.extended == true || entry.subclass?.extended == true ||
                    entry.selections.values.flatten().any { it.startsWith("ua.") }
            }

        return LevelUpBase(
            character = character,
            notes = notes,
            knownSpellIds = knownSpellIds,
            preparedSpellIds = preparedSpellIds,
            featIds = featIds,
            race = race,
            subraceId = subrace?.id,
            background = background,
            classes = classes,
            defaultShowOptional = showOptional,
            defaultShowUnofficial = showUnofficial,
            warnings = warnings,
        )
    }

    /** Стартовый черновик: повышается единственный (первый) класс, переключатели — по персонажу. */
    fun initialDraft(base: LevelUpBase): LevelUpDraft = LevelUpDraft(
        classIndex = base.classes.firstOrNull()?.index,
        showOptional = base.defaultShowOptional,
        showUnofficial = base.defaultShowUnofficial,
    )

    /** Выбор существующего класса: решения, зависящие от класса, сбрасываются. */
    fun selectClass(draft: LevelUpDraft, classIndex: Int): LevelUpDraft =
        if (draft.classIndex == classIndex && draft.newClassId == null) draft else resetClassDecisions(draft).copy(classIndex = classIndex)

    /** Выбор нового класса (мультикласс): решения, зависящие от класса, сбрасываются. */
    fun selectNewClass(draft: LevelUpDraft, classId: String): LevelUpDraft =
        if (draft.newClassId == classId) draft else resetClassDecisions(draft).copy(classIndex = null, newClassId = classId)

    fun selectSubclass(draft: LevelUpDraft, subclassId: String?): LevelUpDraft =
        if (draft.subclassId == subclassId) {
            draft
        } else {
            draft.copy(
                subclassId = subclassId,
                selections = draft.selections.filterKeys { !it.startsWith("subclass.") },
            )
        }

    private fun resetClassDecisions(draft: LevelUpDraft) = draft.copy(
        subclassId = null,
        selections = emptyMap(),
        optionalSwaps = emptyMap(),
        asi = null,
        featId = null,
        hpRoll = null,
        cantripIds = emptySet(),
        spellIds = emptySet(),
        replacements = emptyMap(),
    )

    /** Классы библиотеки, которые можно взять мультиклассом, с проверкой требований. */
    fun newClassOptions(base: LevelUpBase, library: WizardLibrary, cache: WizardRulesCache): List<NewClassOption> {
        val takenIds = base.classes.mapNotNull { it.charClass?.id }.toSet()
        val takenTypes = base.classes.map { it.classLevel.characterClass }.filter { it != CharacterClass.OTHER }.toSet()
        val scores = currentScores(base.character)
        return library.classes.filter { it.id !in takenIds }.mapNotNull { charClass ->
            val rules = cache.characterClass(charClass)
            if (rules.type != CharacterClass.OTHER && rules.type in takenTypes) return@mapNotNull null
            val multiclass = MulticlassRules.parse(rules)
            NewClassOption(charClass, rules, multiclass, multiclassCheck(base, charClass, multiclass, scores))
        }
    }

    /** Черты, которые ещё не взяты персонажем. */
    fun availableFeats(base: LevelUpBase, library: WizardLibrary): List<Feat> =
        library.feats.filter { it.id !in base.featIds }

    private fun multiclassCheck(
        base: LevelUpBase,
        newClass: CharClass,
        rules: MulticlassRules,
        scores: Map<AbilityType, Int>,
    ): MulticlassCheck {
        val unmet = linkedMapOf<String, List<Set<AbilityType>>>()
        // Без классов у персонажа это не мультикласс, а первый класс.
        if (base.character.classLevels.isEmpty()) return MulticlassCheck(rules, unmet)
        rules.unmet(scores).takeIf { it.isNotEmpty() }?.let { unmet[newClass.name] = it }
        base.classes.forEach { entry ->
            val classRules = entry.rules ?: return@forEach
            MulticlassRules.parse(classRules).unmet(scores).takeIf { it.isNotEmpty() }?.let {
                unmet[classRules.charClass.name] = it
            }
        }
        return MulticlassCheck(rules, unmet)
    }

    private fun findRace(character: Character, library: WizardLibrary, cache: WizardRulesCache): Race? {
        if (character.race.isBlank()) return null
        val candidates = library.races.filter { norm(it.name) == norm(character.race) }
        if (candidates.isEmpty()) return null
        if (character.subrace.isNotBlank()) {
            candidates.firstOrNull { race -> cache.race(race).subraces.any { norm(it.title) == norm(character.subrace) } }
                ?.let { return it }
        }
        return candidates.firstOrNull { !cache.race(it).extended } ?: candidates.first()
    }

    private fun findClass(
        classLevel: CharacterClassLevel,
        subclassTitles: List<String>,
        library: WizardLibrary,
        cache: WizardRulesCache,
    ): CharClass? {
        val candidates = library.classes.filter { charClass ->
            if (classLevel.characterClass == CharacterClass.OTHER) {
                classLevel.customName.isNotBlank() && norm(charClass.name) == norm(classLevel.customName)
            } else {
                ClassRulesParser.classType(charClass.name) == classLevel.characterClass
            }
        }
        return candidates.firstOrNull { charClass ->
            cache.characterClass(charClass).subclasses.any { norm(it.title) in subclassTitles }
        } ?: candidates.firstOrNull()
    }

    /**
     * Выбранные варианты по заметкам: блок с заголовком выбора, абзацы с названиями вариантов.
     * Два прохода — выборы зависят друг от друга (книга теней появляется после договора гримуара).
     */
    private fun restoreSelections(
        notes: List<NoteBlock>,
        charClass: CharClass,
        subclass: SubclassRules?,
        level: Int,
        scores: Map<AbilityType, Int>,
        library: WizardLibrary,
        cache: WizardRulesCache,
    ): Map<String, List<String>> {
        var selections = emptyMap<String, List<String>>()
        repeat(RESTORE_PASSES) {
            val draft = classDraft(charClass.id, subclass?.id, level, selections, emptyMap(), true, true, scores)
            val context = WizardEngine.context(draft, library, cache)
            selections = context.choices.filter { it.kind == ChoiceKind.OPTION }.mapNotNull { choice ->
                val block = notes.firstOrNull { norm(it.title) == norm(choice.title) } ?: return@mapNotNull null
                val titles = block.paragraphs.map { norm(firstLine(it.text)) }.toSet()
                val ids = choice.options.filter { norm(it.title) in titles }.map { it.id }.distinct()
                (choice.id to ids).takeIf { ids.isNotEmpty() }
            }.toMap()
        }
        return selections
    }

    private fun restoredOptions(
        entry: LevelUpClassEntry,
        library: WizardLibrary,
        cache: WizardRulesCache,
        scores: Map<AbilityType, Int>,
    ): List<ChoiceOption> {
        val charClass = entry.charClass ?: return emptyList()
        if (entry.selections.isEmpty()) return emptyList()
        val draft = classDraft(charClass.id, entry.subclass?.id, entry.classLevel.level, entry.selections, emptyMap(), true, true, scores)
        val context = WizardEngine.context(draft, library, cache)
        return context.choices.flatMap { choice ->
            entry.selections[choice.id].orEmpty().mapNotNull { id -> choice.options.firstOrNull { it.id == id } }
        }
    }

    /** Выбранные опциональные умения, заменяющие базовые: опциональное есть в заметках класса. */
    private fun restoreSwaps(
        notes: List<NoteBlock>,
        rules: ClassRules,
        subclass: SubclassRules?,
        level: Int,
    ): Map<String, Boolean> {
        val titles = classBlockTitles(notes, rules)
        return WizardEngine.optionalSwaps(rules, subclass, level).associate { swap ->
            swap.id to titles.any { it == norm(CharacterAssembler.levelTitle(swap.optional)) || it.startsWith(norm(swap.optional.title)) }
        }.filterValues { it }
    }

    private fun classBlockTitles(notes: List<NoteBlock>, rules: ClassRules): List<String> =
        notes.firstOrNull { norm(it.title) == norm(classBlockTitle(rules)) }
            ?.paragraphs?.map { norm(firstLine(it.text)) }
            .orEmpty()

    // endregion

    // region Контекст

    /** Всё, что даёт повышение по текущему черновику. */
    fun context(base: LevelUpBase, draft: LevelUpDraft, library: WizardLibrary, cache: WizardRulesCache): LevelUpContext {
        val character = base.character
        val newLevel = (base.oldLevel + 1).coerceAtMost(MAX_CLASS_LEVEL)
        val newClass = draft.newClassId?.let { id -> library.classes.firstOrNull { it.id == id } }
        val entry = if (newClass == null) draft.classIndex?.let { index -> base.classes.firstOrNull { it.index == index } } else null
        val scoresBefore = currentScores(character)

        val raceOld = WizardEngine.context(raceDraft(base, base.oldLevel, draft), library, cache)
        val raceNew = WizardEngine.context(raceDraft(base, newLevel, draft), library, cache)

        val charClass = newClass ?: entry?.charClass
        val parsed = if (newClass != null) cache.characterClass(newClass) else entry?.rules
        val limited = entry != null && entry.rules == null
        val oldClassLevel = entry?.classLevel?.level ?: 0
        val newClassLevel = (oldClassLevel + 1).coerceAtMost(MAX_CLASS_LEVEL)
        val restored = entry?.selections.orEmpty()
        val restoredSwaps = entry?.optionalSwaps.orEmpty()
        val subclassId = entry?.subclass?.id ?: draft.subclassId
        val isAsiLevel = parsed != null && newClassLevel in parsed.asiLevels

        val oldDraft = if (entry != null && parsed != null && charClass != null) {
            classDraft(charClass.id, entry.subclass?.id, oldClassLevel, restored, restoredSwaps, draft.showOptional, draft.showUnofficial, scoresBefore)
        } else {
            null
        }
        val oldCtx = oldDraft?.let { WizardEngine.context(it, library, cache) }

        val merged = mergeSelections(restored, draft)
        val newDraft = if (parsed != null && charClass != null) {
            classDraft(
                classId = charClass.id,
                subclassId = subclassId,
                level = newClassLevel,
                selections = merged,
                swaps = restoredSwaps + draft.optionalSwaps,
                showOptional = draft.showOptional,
                showUnofficial = draft.showUnofficial,
                scores = scoresBefore,
                asi = draft.asi.takeIf { isAsiLevel },
                featId = draft.featId.takeIf { isAsiLevel },
            )
        } else {
            null
        }
        val newCtx = newDraft?.let { WizardEngine.context(it, library, cache) }
        val scoresAfter = if (newDraft != null && newCtx != null) WizardEngine.finalScores(newDraft, newCtx) else scoresBefore

        val rules = newCtx?.characterClass
        val subclass = newCtx?.subclass
        val needsSubclass = parsed != null && entry?.subclass == null &&
            newClassLevel >= parsed.subclassLevel && parsed.subclasses.isNotEmpty()

        val multiclass = if (newClass != null && parsed != null) {
            multiclassCheck(base, newClass, MulticlassRules.parse(parsed), scoresBefore)
        } else {
            null
        }

        val choices = if (newCtx != null) {
            newChoices(newCtx, oldCtx, restored, newClass != null, multiclass, base, draft)
        } else {
            emptyList()
        }
        val features = newFeatures(base, newCtx, oldCtx, raceOld, raceNew, newClassLevel, oldClassLevel, draft.showOptional)
        val swaps = newCtx?.swaps.orEmpty().filter { swap -> oldCtx?.swaps.orEmpty().none { it.id == swap.id } }
        val granted = if (oldCtx != null) grantedSpellIds(base, oldCtx, raceOld, library) else emptySet()
        val freedom = newCtx?.spellcasting?.let { plan ->
            schoolFreedom(plan, newCtx.subclass, newClassLevel, base.knownSpellIds, granted, library.spells)
        }
        val spells = newCtx?.spellcasting?.let { plan ->
            levelUpSpells(plan, oldCtx?.spellcasting, base.knownSpellIds, library.spells, freedom, replacementSpellPicks(draft))
        }
        val replacements = if (newCtx != null && oldCtx != null && rules != null) {
            replacements(base, draft, newCtx, oldCtx, granted, freedom, restored, isAsiLevel, newClassLevel, library)
        } else {
            emptyList()
        }
        val skillLevels = character.skillProficiencies.mapNotNull { (ordinal, level) ->
            SkillType.entries.getOrNull(ordinal)?.let { it to level }
        }
        val existingFeatHp = base.featIds.mapNotNull { id -> library.feats.firstOrNull { it.id == id } }
            .sumOf { FeatRules.parse(it, "existing").hpPerLevel }
        val preparation = newCtx?.spellcasting?.takeIf { it.canPrepare }?.let { plan ->
            preparation(plan, oldCtx?.spellcasting, base, draft, granted, library.spells)
        }
        val missingResources = missingResources(
            character = character,
            oldSpecs = oldCtx?.resources.orEmpty() + raceOld.resources,
            newSpecs = newCtx?.resources.orEmpty() + raceNew.resources,
            newLevel = newLevel,
            scoresAfter = scoresAfter,
            spells = library.spells,
        ).filterNot { ContentExclusions.isResourceExcluded(it.name, character.excludedContent.toSet()) }
        val excludedContent = draft.excludedContent ?: character.excludedContent.toSet()

        val built = LevelUpContext(
            base = base,
            draft = draft,
            newLevel = newLevel,
            entry = entry,
            newClass = newClass,
            rules = rules,
            subclass = subclass,
            oldClassLevel = oldClassLevel,
            newClassLevel = newClassLevel,
            needsSubclass = needsSubclass,
            subclassOptions = if (needsSubclass) parsed?.subclasses.orEmpty().filter { !it.extended || draft.showUnofficial } else emptyList(),
            swaps = swaps,
            features = features,
            choices = choices,
            isAsiLevel = isAsiLevel,
            spells = spells,
            replacements = replacements,
            multiclass = multiclass,
            hitDie = parsed?.hitDie ?: hitDieFor(entry?.classLevel?.characterClass),
            scoresBefore = scoresBefore,
            scoresAfter = scoresAfter,
            proficientSkills = skillLevels.filter { it.second >= 1 }.map { it.first }.toSet(),
            expertiseSkills = skillLevels.filter { it.second >= 2 }.map { it.first }.toSet(),
            restoredSelections = restored,
            knownSpellIds = base.knownSpellIds,
            limited = limited,
            newWizardDraft = newDraft,
            newWizardContext = newCtx,
            oldWizardContext = oldCtx,
            raceOld = raceOld,
            raceNew = raceNew,
            existingFeatHpPerLevel = existingFeatHp,
            mergedSelections = merged,
            missingResources = missingResources,
            preparation = preparation,
            duplicatePicks = duplicatePicks(draft, choices, replacements, library.spells),
            excludedContent = excludedContent,
        )
        return built.copy(
            proposal = proposal(
                context = built,
                oldSpecs = oldCtx?.resources.orEmpty() + raceOld.resources,
                newSpecs = newCtx?.resources.orEmpty() + raceNew.resources,
                newLevel = newLevel,
                scoresAfter = scoresAfter,
                spells = library.spells,
            ),
        )
    }

    // region Исключения заметок и ресурсов

    /**
     * Что повышение добавляет в заметки и ресурсы: новые умения по блокам, новые и заменённые
     * варианты (закреплены), новые ресурсы уровня и ресурсы, исключённые раньше (их можно включить
     * обратно). Обновление максимума существующего ресурса сюда не входит.
     */
    private fun proposal(
        context: LevelUpContext,
        oldSpecs: List<ResourceSpec>,
        newSpecs: List<ResourceSpec>,
        newLevel: Int,
        scoresAfter: Map<AbilityType, Int>,
        spells: List<Spell>,
    ): ContentProposal {
        val character = context.base.character
        val earlier = character.excludedContent.toSet()
        val notes = context.base.notes
        fun existingBlock(title: String) = notes.firstOrNull { norm(it.title) == norm(title) }
        fun hasParagraph(title: String, heading: String) =
            existingBlock(title)?.paragraphs?.any { norm(firstLine(it.text)) == norm(heading) } == true
        fun proposedBlock(title: String, items: List<Pair<String, String>>, locked: Boolean): ProposedBlock {
            val blockKey = ContentExclusions.blockKey(title)
            return ProposedBlock(
                key = blockKey,
                title = title,
                paragraphs = items.map { (heading, text) ->
                    val key = ContentExclusions.paragraphKey(title, heading)
                    ProposedParagraph(key = key, heading = heading, text = text, excludedEarlier = !locked && key in earlier)
                },
                locked = locked,
                existing = existingBlock(title) != null,
                excludedEarlier = !locked && blockKey in earlier,
            )
        }

        val featureBlocks = context.features
            .filterNot { hasParagraph(it.blockTitle, it.title) }
            .groupBy { it.blockTitle }
            .map { (title, features) -> proposedBlock(title, features.map { it.title to it.feature.text }, locked = false) }
        val optionItems = linkedMapOf<String, MutableList<Pair<String, String>>>()
        newOptionSelections(context).forEach { (choice, options) ->
            optionItems.getOrPut(choice.title) { mutableListOf() } += options.map { it.title to it.text }
        }
        optionReplacements(context).forEach { (removed, added) ->
            val spec = context.replacements.first { spec -> spec.removableOptions.any { it.id == removed.id } }
            optionItems.getOrPut(spec.title) { mutableListOf() } += added.title to added.text
        }
        val optionBlocks = optionItems.map { (title, items) -> proposedBlock(title, items, locked = true) }

        val oldResolved = resolvedResources(oldSpecs, spells)
        val newResolved = resolvedResources(newSpecs, spells)
        val leveled = character.copy(
            level = newLevel,
            abilityScores = character.abilityScores + scoresAfter.mapKeys { it.key.ordinal },
        )
        fun present(name: String) = character.resources.any { norm(it.name) == norm(name) || resourceKey(it.name) == resourceKey(name) }
        val resources = newResolved.mapNotNull { spec ->
            val previous = oldResolved.firstOrNull { resourceKey(it.name) == resourceKey(spec.name) }
            val key = ContentExclusions.resourceKey(spec.name)
            when {
                present(previous?.name ?: spec.name) || present(spec.name) -> null
                // Новый ресурс уровня.
                previous == null -> ProposedResource(
                    key = key,
                    name = spec.name,
                    maximum = resourceMaximum(spec, leveled),
                    description = spec.description,
                    excludedEarlier = key in earlier,
                )
                // Был и раньше, но исключён: предлагаем включить обратно.
                key in earlier -> ProposedResource(
                    key = key,
                    name = spec.name,
                    maximum = resourceMaximum(spec, leveled),
                    description = spec.description,
                    excludedEarlier = true,
                    spec = spec,
                )
                else -> null
            }
        }.distinctBy { it.key }
        return ContentProposal(featureBlocks + optionBlocks, resources)
    }

    /** Ключи закреплённых блоков вариантов: исключить их нельзя. */
    private fun lockedKeys(proposal: ContentProposal): Set<String> =
        proposal.blocks.filter { it.locked }.flatMap { block -> listOf(block.key) + block.paragraphs.map { it.key } }.toSet()

    /** Исключить или включить обратно блок, абзац или ресурс ([ContentExclusions]); закреплённое не меняется. */
    fun toggleExcluded(context: LevelUpContext, key: String): LevelUpDraft {
        if (key in lockedKeys(context.proposal)) return context.draft
        return context.draft.copy(excludedContent = ContentExclusions.toggle(context.excludedContent, key))
    }

    // endregion

    // region Повторы между новым и заменой

    /** Заклинания, взятые как новые на этом повышении: заговоры, известные/книга и выборы заклинаний умений. */
    private fun newSpellPicks(draft: LevelUpDraft, choices: List<ChoiceSpec>): Set<String> {
        val spellChoiceIds = choices.filter { it.kind == ChoiceKind.CANTRIP || it.kind == ChoiceKind.SPELL }.map { it.id }.toSet()
        return draft.cantripIds + draft.spellIds +
            draft.selections.filterKeys { it in spellChoiceIds }.values.flatten().filter { it.isNotBlank() }
    }

    /** Заклинания, взятые взамен старых (известное, заговор, арканум): их нельзя выбрать ещё раз новыми. */
    private fun replacementSpellPicks(draft: LevelUpDraft): Set<String> =
        draft.replacements.filterKeys { it in SPELL_REPLACEMENT_IDS }.values.mapNotNull { it.newId }.toSet()

    /**
     * Замены, чьё новое значение совпадает с взятым новым на этом же повышении: заклинание,
     * вариант того же выбора (воззвание, приём, копия инфузии) или навык новой компетентности.
     * Кандидаты уже отфильтрованы в обе стороны, так что повтор возможен только в устаревшем
     * черновике — тогда повышение блокируется ([LevelUpIssueKind.DUPLICATE_PICK]).
     */
    private fun duplicatePicks(
        draft: LevelUpDraft,
        choices: List<ChoiceSpec>,
        replacements: List<ReplacementSpec>,
        spells: List<Spell>,
    ): Map<String, String> {
        val spellPicks = newSpellPicks(draft, choices)
        val expertisePicks = choices.filter { it.kind == ChoiceKind.EXPERTISE }
            .flatMap { draft.selections[it.id].orEmpty() }.toSet()
        return replacements.mapNotNull { spec ->
            val newId = draft.replacements[spec.id]?.newId ?: return@mapNotNull null
            val duplicate = when {
                spec.id in SPELL_REPLACEMENT_IDS -> newId in spellPicks
                spec.kind == ReplacementKind.EXPERTISE -> newId in expertisePicks
                spec.choiceId != null -> newId in draft.selections[spec.choiceId].orEmpty()
                else -> false
            }
            if (!duplicate) return@mapNotNull null
            val title = spells.firstOrNull { it.id == newId }?.name
                ?: spec.candidateOptions.firstOrNull { it.id == newId }?.title
                ?: newId
            spec.id to title
        }.toMap()
    }

    // endregion

    /**
     * Переподготовка повышаемого класса. Заклинания класса — из его списка (у волшебника — из
     * книги и новых записей этого уровня); полученные от расы, черт, умений и всегда
     * подготовленные не входят. Подготовленные заклинания списков других классов не трогаются.
     */
    private fun preparation(
        plan: SpellcastingPlan,
        oldPlan: SpellcastingPlan?,
        base: LevelUpBase,
        draft: LevelUpDraft,
        granted: Set<String>,
        spells: List<Spell>,
    ): LevelUpPreparation {
        val byId = spells.associateBy { it.id }
        val always = CharacterAssembler.matchSpells(plan.alwaysPrepared, spells).filter { (byId[it]?.level ?: 1) > 0 }
        val excluded = granted + always
        fun ofClass(spell: Spell) = spell.level in 1..plan.maxSpellLevel && plan.classCode in spell.classes && spell.id !in excluded
        val spellbook = plan.spellbookSpells != null
        val candidates = if (spellbook) {
            (base.knownSpellIds + draft.spellIds).mapNotNull(byId::get).filter(::ofClass)
        } else {
            spells.filter(::ofClass)
        }.distinctBy { it.id }.sortedWith(compareBy({ it.level }, { it.name.lowercase() })).map { it.id }
        val candidateSet = candidates.toSet()
        // Новый класс ещё ничего не готовил: подготовленные заклинания других классов ему не приписываются.
        val current = if (oldPlan?.canPrepare != true) emptySet() else base.preparedSpellIds.filter { id ->
            val spell = byId[id] ?: return@filter false
            spell.level >= 1 && plan.classCode in spell.classes && id !in excluded
        }.toSet()
        val selected = draft.preparedIds?.filter { it in candidateSet }?.toSet() ?: current.filter { it in candidateSet }.toSet()
        return LevelUpPreparation(
            classCode = plan.classCode,
            spellbook = spellbook,
            limit = plan.preparedCount,
            oldLimit = oldPlan?.preparedCount ?: 0,
            candidates = candidates,
            alwaysPrepared = always,
            current = current,
            selected = selected,
        )
    }

    /** Ключ ресурса без уточнения в скобках: «Кости превосходства (к8)» и «(к10)» — один ресурс. */
    private fun resourceKey(name: String) = norm(name.substringBefore(" ("))

    /** Спецификации ресурсов, разрешённые в ресурсы персонажа (название заклинания — из библиотеки). */
    private fun resolvedResources(specs: List<ResourceSpec>, spells: List<Spell>): List<CharacterResource> {
        val byId = spells.associateBy { it.id }
        return specs.map { CharacterAssembler.resourceOf(it, spells, byId) }.distinctBy { resourceKey(it.name) }
    }

    /** Максимум ресурса для персонажа: по формуле («[pb]»), иначе число из таблицы. */
    private fun resourceMaximum(resource: CharacterResource, character: Character): Int =
        resource.maximumFormula.takeIf { it.isNotBlank() }
            ?.let { com.example.spellbook.data.StatFormula.evaluateValue(it, character) }
            ?.coerceAtLeast(1)
            ?: resource.maximum

    /**
     * Ресурсы, удалённые пользователем, которые на этом уровне должны были измениться: по правилам
     * ресурс был на старом уровне и остаётся на новом, его максимум или название меняется, а в листе
     * его нет. Новые ресурсы уровня и ресурсы, убранные заменой варианта, сюда не попадают;
     * удалённый ресурс, который не меняется, — тоже: повода спрашивать нет.
     */
    private fun missingResources(
        character: Character,
        oldSpecs: List<ResourceSpec>,
        newSpecs: List<ResourceSpec>,
        newLevel: Int,
        scoresAfter: Map<AbilityType, Int>,
        spells: List<Spell>,
    ): List<MissingResource> {
        val oldResolved = resolvedResources(oldSpecs, spells)
        val newResolved = resolvedResources(newSpecs, spells)
        val leveled = character.copy(
            level = newLevel,
            abilityScores = character.abilityScores + scoresAfter.mapKeys { it.key.ordinal },
        )
        return newResolved.mapNotNull { spec ->
            val previous = oldResolved.firstOrNull { resourceKey(it.name) == resourceKey(spec.name) } ?: return@mapNotNull null
            val present = character.resources.any {
                norm(it.name) == norm(previous.name) || norm(it.name) == norm(spec.name) ||
                    resourceKey(it.name) == resourceKey(spec.name)
            }
            if (present) return@mapNotNull null
            val oldMax = resourceMaximum(previous, character)
            val newMax = resourceMaximum(spec, leveled)
            if (oldMax == newMax && previous.name == spec.name) return@mapNotNull null
            MissingResource(
                key = resourceKey(spec.name),
                name = spec.name,
                oldName = previous.name,
                description = spec.description,
                oldMaximum = oldMax,
                newMaximum = newMax,
                spec = spec,
            )
        }
    }

    /** Уже выбранное плюс новые решения черновика, с применёнными заменами вариантов. */
    private fun mergeSelections(restored: Map<String, List<String>>, draft: LevelUpDraft): Map<String, List<String>> {
        val merged = restored.toMutableMap()
        draft.selections.forEach { (id, values) ->
            merged[id] = (merged[id].orEmpty() + values.filter { it.isNotBlank() }).distinct()
        }
        draft.replacements.forEach { (id, replacement) ->
            if (!id.startsWith(REPLACE_OPTION_PREFIX) || !replacement.isComplete) return@forEach
            val choiceId = id.removePrefix(REPLACE_OPTION_PREFIX)
            merged[choiceId] = (merged[choiceId].orEmpty() - replacement.oldId!!) + replacement.newId!!
        }
        return merged
    }

    /**
     * Новые выборы: появившиеся на этом уровне или с выросшим числом (count — сколько добрать,
     * уже взятые варианты исключены). У нового класса нет выбора навыков и инструментов класса —
     * вместо них владения мультиклассирования.
     */
    private fun newChoices(
        newCtx: WizardContext,
        oldCtx: WizardContext?,
        restored: Map<String, List<String>>,
        isNewClass: Boolean,
        multiclass: MulticlassCheck?,
        base: LevelUpBase,
        draft: LevelUpDraft,
    ): List<ChoiceSpec> = buildList {
        // Вариант, взятый взамен старого (воззвание, копия инфузии), выбрать ещё раз новым нельзя;
        // навык, на который переносится компетентность, — взять новой компетентностью.
        fun replacedTo(choiceId: String): Set<String> =
            draft.replacements[REPLACE_OPTION_PREFIX + choiceId]?.newId?.let(::setOf).orEmpty()
        val expertiseTarget = draft.replacements[REPLACE_EXPERTISE_ID]?.newId
        fun withoutReplaced(choice: ChoiceSpec): ChoiceSpec {
            val excluded = replacedTo(choice.id) +
                if (choice.kind == ChoiceKind.EXPERTISE) setOfNotNull(expertiseTarget) else emptySet()
            if (excluded.isEmpty() || choice.options.isEmpty()) return choice
            return choice.copy(options = choice.options.filterNot { it.id in excluded })
        }
        newChoicesRaw(newCtx, oldCtx, restored, isNewClass, multiclass, base).forEach { add(withoutReplaced(it)) }
    }

    private fun newChoicesRaw(
        newCtx: WizardContext,
        oldCtx: WizardContext?,
        restored: Map<String, List<String>>,
        isNewClass: Boolean,
        multiclass: MulticlassCheck?,
        base: LevelUpBase,
    ): List<ChoiceSpec> = buildList {
        newCtx.choices.forEach { choice ->
            if (choice.source == ChoiceSource.FEAT) {
                add(choice)
                return@forEach
            }
            if (isNewClass && (choice.id == "class.skills" || choice.id == "class.tools")) return@forEach
            val old = oldCtx?.choices?.firstOrNull { it.id == choice.id }
                ?: oldCtx?.choices?.firstOrNull {
                    it.kind == choice.kind && it.source == choice.source && norm(it.title) == norm(choice.title)
                }
            if (old == null) {
                add(choice)
            } else {
                val delta = choice.count - old.count
                if (delta > 0) {
                    val taken = restored[choice.id].orEmpty()
                    add(choice.copy(count = delta, options = choice.options.filterNot { it.id in taken }))
                }
            }
        }
        if (isNewClass && base.character.classLevels.isNotEmpty()) multiclass?.rules?.choices?.let(::addAll)
    }

    private fun newFeatures(
        base: LevelUpBase,
        newCtx: WizardContext?,
        oldCtx: WizardContext?,
        raceOld: WizardContext,
        raceNew: WizardContext,
        newClassLevel: Int,
        oldClassLevel: Int,
        showOptional: Boolean,
    ): List<LevelUpFeature> = buildList {
        fun diff(new: List<RuleFeature>, old: List<RuleFeature>): List<RuleFeature> =
            new.filter { feature -> old.none { norm(it.title) == norm(feature.title) } }

        newCtx?.characterClass?.let { rules ->
            val classChoices = newCtx.choices.filter { it.source == ChoiceSource.CLASS }
            val new = FeatureRules.noteFeatures(rules.features, newClassLevel, showOptional, rules, classChoices)
            val old = oldCtx?.characterClass?.let { oldRules ->
                FeatureRules.noteFeatures(
                    oldRules.features, oldClassLevel, showOptional, oldRules,
                    oldCtx.choices.filter { it.source == ChoiceSource.CLASS },
                )
            }.orEmpty()
            diff(new, old).forEach { add(LevelUpFeature(ChoiceSource.CLASS, classBlockTitle(rules), it)) }
        }
        newCtx?.subclass?.let { sub ->
            val new = FeatureRules.noteFeatures(
                sub.features, newClassLevel, showOptional = true, rules = null,
                choices = newCtx.choices.filter { it.source == ChoiceSource.SUBCLASS },
            )
            val old = oldCtx?.subclass?.let { oldSub ->
                FeatureRules.noteFeatures(
                    oldSub.features, oldClassLevel, showOptional = true, rules = null,
                    choices = oldCtx.choices.filter { it.source == ChoiceSource.SUBCLASS },
                )
            }.orEmpty()
            diff(new, old).forEach { add(LevelUpFeature(ChoiceSource.SUBCLASS, subclassBlockTitle(sub), it)) }
        }
        raceNew.race?.let { race ->
            val traits = race.traits + raceNew.subrace?.traits.orEmpty()
            val new = FeatureRules.noteFeatures(traits, base.oldLevel + 1, showOptional = true)
            val oldTraits = raceOld.race?.traits.orEmpty() + raceOld.subrace?.traits.orEmpty()
            val old = FeatureRules.noteFeatures(oldTraits, base.oldLevel, showOptional = true)
            diff(new, old).forEach { add(LevelUpFeature(ChoiceSource.RACE, raceBlockTitle(race, raceNew.subrace), it)) }
        }
    }

    /** Заклинания, которые даёт не умение «Использование заклинаний»: раса, умения, черты, домен. */
    private fun grantedSpellIds(
        base: LevelUpBase,
        oldCtx: WizardContext,
        raceOld: WizardContext,
        library: WizardLibrary,
    ): Set<String> {
        val spells = library.spells
        val featSpells = base.featIds.mapNotNull { id -> library.feats.firstOrNull { it.id == id } }
            .flatMap { FeatRules.parse(it, "existing").spells }
        return (
            CharacterAssembler.matchSpells(oldCtx.featureSpells, spells) +
                CharacterAssembler.matchSpells(oldCtx.spellcasting?.alwaysPrepared.orEmpty(), spells) +
                CharacterAssembler.matchSpells(
                    (raceOld.race?.grants?.spells.orEmpty() + raceOld.subrace?.grants?.spells.orEmpty()).map { it.name },
                    spells,
                ) +
                CharacterAssembler.matchSpells(featSpells, spells)
            ).toSet()
    }

    /**
     * Свобода школ мистического рыцаря и ловкача.
     *
     * @param freeNew сколько известных заклинаний могут быть любой школы на новом уровне.
     * @param offSchool сколько известных заклинаний класса уже не из школ плана (черновик не хранится —
     * «свободными» считаются именно они).
     */
    private data class SchoolFreedom(val freeNew: Int, val offSchool: Int, val schools: Set<String>) {
        val spare: Int get() = (freeNew - offSchool).coerceAtLeast(0)
    }

    private fun schoolFreedom(
        plan: SpellcastingPlan,
        subclass: SubclassRules?,
        classLevel: Int,
        known: Set<String>,
        granted: Set<String>,
        spells: List<Spell>,
    ): SchoolFreedom? {
        if (plan.schools.isEmpty() || subclass == null) return null
        val offSchool = spells.count { spell ->
            spell.id in known && spell.id !in granted && spell.level >= 1 &&
                plan.classCode in spell.classes && spell.school !in plan.schools
        }
        // Свободные места — по одному на 3, 8, 14 и 20 уровне (см. [SpellcastingPlan.anySchoolSpells]).
        return SchoolFreedom(ClassChoiceCatalog.MYSTIC_FREE_LEVELS.count { it <= classLevel }, offSchool, plan.schools)
    }

    /** Заклинания класса на новом уровне: прирост заговоров и известных, новые круги, домен. */
    private fun levelUpSpells(
        plan: SpellcastingPlan,
        oldPlan: SpellcastingPlan?,
        known: Set<String>,
        spells: List<Spell>,
        freedom: SchoolFreedom?,
        /** Взятые взамен старых — новыми их уже не выбрать. */
        replacedTo: Set<String> = emptySet(),
    ): LevelUpSpells {
        val known = known + replacedTo
        fun bySchool(spell: Spell) = plan.schools.isEmpty() || spell.school in plan.schools
        val anySchool = freedom?.spare ?: 0
        fun bySchoolOrFree(spell: Spell) = anySchool > 0 || bySchool(spell)
        val newCantrips = (plan.cantrips - (oldPlan?.cantrips ?: 0)).coerceAtLeast(0)
        val cantripCandidates = spells
            .filter { it.level == 0 && plan.classCode in it.classes && it.id !in known }
            .sortedBy { it.name.lowercase() }.map { it.id }
        val spellbook = plan.spellbookSpells != null
        val newSpells = if (spellbook) {
            (plan.spellbookSpells ?: 0) - (oldPlan?.spellbookSpells ?: 0)
        } else {
            (plan.knownSpells ?: 0) - (oldPlan?.knownSpells ?: 0)
        }.coerceAtLeast(0)
        val spellCandidates = if (plan.maxSpellLevel > 0) {
            spells.filter { spell ->
                spell.level in 1..plan.maxSpellLevel && spell.id !in known && bySchoolOrFree(spell) &&
                    (plan.classCode in spell.classes || plan.extraSpellNames.any { MarkupText.matchesSpellName(it, spell.name) })
            }.sortedWith(compareBy({ it.level }, { it.name.lowercase() })).map { it.id }
        } else {
            emptyList()
        }
        val offSchool = if (freedom != null) {
            spells.filter { it.level >= 1 && plan.classCode in it.classes && it.school !in freedom.schools }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val addedFromList = if (plan.preparesFromClassList && plan.maxSpellLevel > (oldPlan?.maxSpellLevel ?: 0)) {
            val from = (oldPlan?.maxSpellLevel ?: 0) + 1
            spells.filter { it.level in from..plan.maxSpellLevel && plan.classCode in it.classes && it.id !in known }.map { it.id }
        } else {
            emptyList()
        }
        // Заклинания домена часто есть и в списке класса (уже известны) — всё равно становятся подготовленными.
        val oldAlways = CharacterAssembler.matchSpells(oldPlan?.alwaysPrepared.orEmpty(), spells).toSet()
        val always = CharacterAssembler.matchSpells(plan.alwaysPrepared, spells).filter { it !in oldAlways }
        return LevelUpSpells(
            plan = plan,
            oldPlan = oldPlan,
            newCantrips = newCantrips,
            cantripCandidates = cantripCandidates,
            newSpells = newSpells,
            spellCandidates = spellCandidates,
            spellbook = spellbook,
            addedFromClassList = addedFromList,
            alwaysPrepared = always,
            anySchoolAllowance = anySchool,
            offSchoolSpellIds = offSchool,
        )
    }

    /**
     * Замены при повышении уровня:
     * - известное заклинание — у классов, которые «знают» заклинания (не готовят из списка и не волшебник);
     * - воззвание колдуна — на любом уровне, если текст умения это разрешает;
     * - опциональные умения Tasha «Универсальность…» — только на уровнях «Увеличения характеристик»:
     *   заговор, боевой стиль, боевой приём, метамагия (варианты разбираются из текста умения).
     */
    private fun replacements(
        base: LevelUpBase,
        draft: LevelUpDraft,
        newCtx: WizardContext,
        oldCtx: WizardContext,
        granted: Set<String>,
        freedom: SchoolFreedom?,
        restored: Map<String, List<String>>,
        isAsiLevel: Boolean,
        newClassLevel: Int,
        library: WizardLibrary,
    ): List<ReplacementSpec> {
        val result = mutableListOf<ReplacementSpec>()
        val spells = library.spells
        val known = base.knownSpellIds
        val plan = newCtx.spellcasting
        val oldPlan = oldCtx.spellcasting
        val rules = newCtx.characterClass ?: return emptyList()
        val byId = spells.associateBy { it.id }
        // Взятое новым на этом повышении нельзя взять ещё раз взамен старого: иначе заклинаний
        // (вариантов, компетентностей) станет на одно меньше.
        val notCandidate = known + newSpellPicks(draft, newCtx.choices)
        val newExpertise = newCtx.choices.filter { it.kind == ChoiceKind.EXPERTISE }
            .flatMap { draft.selections[it.id].orEmpty() }.toSet()
        fun bySchool(spell: Spell) = plan == null || plan.schools.isEmpty() || spell.school in plan.schools
        fun inClassList(spell: Spell) = plan != null &&
            (plan.classCode in spell.classes || plan.extraSpellNames.any { MarkupText.matchesSpellName(it, spell.name) })

        // Известное заклинание класса (арканум колдуна 6–9 круга — не «известное», меняется отдельно).
        if (plan != null && oldPlan != null && plan.knownSpells != null && plan.spellbookSpells == null &&
            !plan.preparesFromClassList && oldPlan.maxSpellLevel > 0
        ) {
            val removable = known.mapNotNull(byId::get).filter { spell ->
                spell.level in 1..oldPlan.maxSpellLevel && spell.id !in granted && inClassList(spell)
            }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
            // Мистический рыцарь и ловкач: заклинание любой школы — только вместо «свободного» (другой
            // школы) или если свободных мест больше, чем известных заклинаний других школ.
            val pool = spells.filter { spell ->
                spell.level in 1..plan.maxSpellLevel && spell.id !in notCandidate && inClassList(spell)
            }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
            val inSchool = pool.filter(::bySchool).map { it.id }
            val anyAllowed = pool.map { it.id }
            val byRemovable = if (freedom != null) {
                val newOffSchool = draft.spellIds.count { id -> byId[id]?.let { !bySchool(it) } == true }
                val spareAfterNew = freedom.spare - newOffSchool
                removable.associate { spell ->
                    spell.id to if (!bySchool(spell) || spareAfterNew > 0) anyAllowed else inSchool
                }
            } else {
                emptyMap()
            }
            if (removable.isNotEmpty() && inSchool.isNotEmpty()) {
                result += ReplacementSpec(
                    id = REPLACE_SPELL_ID,
                    kind = ReplacementKind.SPELL,
                    title = "Замена известного заклинания",
                    removable = removable.map { it.id },
                    candidateSpellIds = inSchool,
                    sourceText = rules.feature("Использование заклинаний")?.text
                        ?: rules.feature("Магия договора")?.text
                        ?: newCtx.subclass?.features?.firstOrNull { it.title.lowercase().startsWith("использование заклинаний") }
                            ?.text.orEmpty(),
                    candidatesByRemovable = byRemovable,
                )
            }
        }



        fun optionReplacement(choiceId: String, group: String?, sourceText: String) {
            val oldChoice = oldCtx.choices.firstOrNull { it.id == choiceId } ?: return
            val newChoice = newCtx.choices.firstOrNull { it.id == choiceId } ?: return
            val taken = restored[choiceId].orEmpty()
            val removable = oldChoice.options.filter { it.id in taken }
            if (removable.isEmpty()) return
            val candidates = newChoice.options.filter { it.id !in taken && it.id !in draft.selections[choiceId].orEmpty() }
            if (candidates.isEmpty()) return
            val id = REPLACE_OPTION_PREFIX + choiceId
            if (result.any { it.id == id }) return
            result += ReplacementSpec(
                id = id,
                kind = ReplacementKind.OPTION,
                title = oldChoice.title,
                removable = removable.map { it.id },
                removableOptions = removable,
                candidateOptions = candidates,
                choiceId = choiceId,
                group = group,
                sourceText = sourceText,
            )
        }

        // Воззвания колдуна: «когда вы получаете новый уровень этого класса, вы можете выбрать
        // одно известное вам воззвание и заменить его другим».
        rules.feature("Таинственные воззвания")?.let { feature ->
            val lower = MarkupText.plain(feature.text).lowercase()
            if (LEVEL_REPLACE.containsMatchIn(lower) && lower.contains("воззвани")) {
                optionReplacement("class.invocations", null, feature.text)
            }
        }

        // Инфузии изобретателя: «Всякий раз, когда вы получаете уровень в этом классе, можете
        // заменить одну из уже изученных инфузий на новую».
        // Правило стоит в подразделе умения («Известные инфузии»), поэтому смотрим и части.
        rules.features.filter { it.title.lowercase().startsWith("инфузи") }.forEach { feature ->
            val texts = listOf(feature.text) + feature.parts.map { it.text }
            texts.firstOrNull { text ->
                val lower = MarkupText.plain(text).lowercase()
                LEVEL_REPLACE.containsMatchIn(lower) && lower.contains("инфузи")
            }?.let { optionReplacement("class.infusions", null, it) }
        }

        // Tasha: «Всякий раз, когда вы достигаете уровня с «Увеличением характеристик», вы можете…».
        if (draft.showOptional && isAsiLevel) {
            rules.features.filter { feature ->
                val lower = MarkupText.plain(feature.text).lowercase()
                feature.extended && feature.level <= newClassLevel &&
                    lower.contains("увеличение характеристик") && lower.contains("замен")
            }.forEach { feature ->
                val bullets = feature.text.lines().map { it.trim() }.filter { it.startsWith("- ") }
                val items = bullets.ifEmpty { listOf(feature.text) }.map { MarkupText.plain(it).lowercase() }
                val group = if (items.size >= 2) feature.title else null
                items.forEach { item ->
                    when {
                        item.contains("заговор") && plan != null && plan.cantrips > 0 -> {
                            if (result.any { it.id == REPLACE_CANTRIP_ID }) return@forEach
                            val removable = known.mapNotNull(byId::get).filter { spell ->
                                spell.level == 0 && plan.classCode in spell.classes && spell.id !in granted
                            }.sortedBy { it.name.lowercase() }.map { it.id }
                            val candidates = spells.filter { spell ->
                                spell.level == 0 && plan.classCode in spell.classes && spell.id !in notCandidate
                            }.sortedBy { it.name.lowercase() }.map { it.id }
                            if (removable.isNotEmpty() && candidates.isNotEmpty()) {
                                result += ReplacementSpec(
                                    id = REPLACE_CANTRIP_ID,
                                    kind = ReplacementKind.CANTRIP,
                                    title = "Замена заговора",
                                    removable = removable,
                                    candidateSpellIds = candidates,
                                    group = group,
                                    sourceText = feature.text,
                                )
                            }
                        }
                        // «Замените дар, полученный вами от умения «Предмет договора»…» (колдун).
                        item.contains("предмет договора") -> optionReplacement("class.pact", group, feature.text)
                        // «Если вы имеете 12-й уровень… замените одно заклинание от умения «Таинственный
                        // арканум» другим заклинанием колдуна того же уровня».
                        item.contains("арканум") && plan != null -> {
                            val minLevel = Regex("(\\d+)\\s*-?\\S*\\s+уров").find(item)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            if (newClassLevel < minLevel) return@forEach
                            val arcanum = known.mapNotNull(byId::get).filter { spell ->
                                spell.level in ARCANUM_CIRCLES && plan.classCode in spell.classes && spell.id !in granted
                            }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
                            val byRemovable = arcanum.associate { old ->
                                old.id to spells.filter { it.level == old.level && plan.classCode in it.classes && it.id !in notCandidate }
                                    .sortedBy { it.name.lowercase() }.map { it.id }
                            }.filterValues { it.isNotEmpty() }
                            if (byRemovable.isNotEmpty()) {
                                result += ReplacementSpec(
                                    id = REPLACE_ARCANUM_ID,
                                    kind = ReplacementKind.ARCANUM,
                                    title = "Замена заклинания таинственного арканума",
                                    removable = byRemovable.keys.toList(),
                                    candidateSpellIds = byRemovable.values.flatten().distinct(),
                                    group = group,
                                    sourceText = feature.text,
                                    candidatesByRemovable = byRemovable,
                                )
                            }
                        }
                        // «Замените один из навыков, выбранный вами с помощью умения «Компетентность»,
                        // одним из ваших других навыков, который не имеет бонуса от этого умения» (бард).
                        item.contains("компетентност") && item.contains("навык") -> {
                            val levels = base.character.skillProficiencies
                            fun skillOption(skill: SkillType) = ChoiceOption(skill.name, MarkupText.skillTitle(skill))
                            val expert = SkillType.entries.filter { (levels[it.ordinal] ?: 0) >= 2 }
                            val others = SkillType.entries.filter { (levels[it.ordinal] ?: 0) == 1 && it.name !in newExpertise }
                            if (expert.isNotEmpty() && others.isNotEmpty()) {
                                result += ReplacementSpec(
                                    id = REPLACE_EXPERTISE_ID,
                                    kind = ReplacementKind.EXPERTISE,
                                    title = "Замена навыка компетентности",
                                    removable = expert.map { it.name },
                                    removableOptions = expert.map(::skillOption),
                                    candidateOptions = others.map(::skillOption),
                                    group = group,
                                    sourceText = feature.text,
                                )
                            }
                        }
                        item.contains("боевой стиль") -> optionReplacement("class.fighting_style", group, feature.text)
                        item.contains("приём") || item.contains("прием") -> optionReplacement("subclass.maneuvers", group, feature.text)
                        item.contains("метамаги") -> optionReplacement("class.metamagic", group, feature.text)
                    }
                }
            }
        }
        return result
    }


    // endregion

    // region Шаги и проверки

    /**
     * Шаги окна: класс → отдельные выборы → характеристики (уровень «Увеличения характеристик»)
     * → хиты → заклинания (если есть что выбрать или заменить) → замены вариантов → итог.
     */
    fun steps(context: LevelUpContext): List<LevelUpStep> = buildList {
        add(LevelUpStep.Class)
        if (context.classChosen && !context.base.atMaxLevel) {
            addAll(context.choices.filter { choiceStepKey(it, context) is LevelUpStep.Choice }.map { LevelUpStep.Choice(it.id) })
            if (context.isAsiLevel) add(LevelUpStep.Abilities)
            add(LevelUpStep.HitPoints)
            if (hasSpellStep(context)) add(LevelUpStep.Spells)
            if (context.preparation != null) add(LevelUpStep.Prepare)
            if (context.replacements.any { !it.onSpellStep }) add(LevelUpStep.Replacements)
        }
        add(LevelUpStep.Summary)
    }

    /** На каком шаге показывается выбор. */
    fun choiceStepKey(choice: ChoiceSpec, context: LevelUpContext): LevelUpStep = when {
        choice.source == ChoiceSource.FEAT -> LevelUpStep.Abilities
        (choice.kind == ChoiceKind.CANTRIP || choice.kind == ChoiceKind.SPELL) -> LevelUpStep.Spells
        choice.dedicatedStep || choice.kind == ChoiceKind.EXPERTISE -> LevelUpStep.Choice(choice.id)
        else -> LevelUpStep.Class
    }

    /** Выборы, которые показываются на шаге. */
    fun choicesOn(step: LevelUpStep, context: LevelUpContext): List<ChoiceSpec> =
        context.choices.filter { choiceStepKey(it, context).key == step.key }

    private fun hasSpellStep(context: LevelUpContext): Boolean =
        context.spells?.hasPicks == true ||
            context.choices.any { it.kind == ChoiceKind.CANTRIP || it.kind == ChoiceKind.SPELL } ||
            context.replacements.any { it.onSpellStep }

    fun issues(step: LevelUpStep, context: LevelUpContext): List<LevelUpIssue> {
        val draft = context.draft
        fun choiceIssues(choices: List<ChoiceSpec>) = choices.mapNotNull { choice ->
            val missing = choice.count - draft.selections[choice.id].orEmpty().count { it.isNotBlank() }
            LevelUpIssue(LevelUpIssueKind.CHOICE_INCOMPLETE, choice.title, missing).takeIf { missing > 0 }
        }
        fun replacementIssues(specs: List<ReplacementSpec>) = buildList {
            specs.forEach { spec ->
                val replacement = draft.replacements[spec.id] ?: return@forEach
                if (!replacement.isEmpty && !replacement.isComplete) {
                    add(LevelUpIssue(LevelUpIssueKind.REPLACEMENT_INCOMPLETE, spec.title))
                }
                context.duplicatePicks[spec.id]?.let { title ->
                    add(LevelUpIssue(LevelUpIssueKind.DUPLICATE_PICK, title, blocking = true))
                }
            }
            specs.filter { it.group != null && draft.replacements[it.id]?.isEmpty == false }
                .groupBy { it.group }
                .filterValues { it.size > 1 }
                .keys.forEach { add(LevelUpIssue(LevelUpIssueKind.REPLACEMENT_CONFLICT, it.orEmpty(), blocking = true)) }
        }
        return when (step) {
            LevelUpStep.Class -> buildList {
                if (context.base.atMaxLevel) add(LevelUpIssue(LevelUpIssueKind.MAX_LEVEL, blocking = true))
                if (!context.classChosen) add(LevelUpIssue(LevelUpIssueKind.CLASS_MISSING, blocking = true))
                context.multiclass?.takeIf { !it.isMet }?.let {
                    add(LevelUpIssue(LevelUpIssueKind.MULTICLASS_REQUIREMENTS, it.unmet.keys.joinToString(", "), blocking = true))
                }
                if (context.needsSubclass && context.subclass == null) {
                    add(LevelUpIssue(LevelUpIssueKind.SUBCLASS_MISSING, context.rules?.subclassGroupTitle.orEmpty()))
                }
                addAll(choiceIssues(choicesOn(step, context)))
            }
            is LevelUpStep.Choice -> choiceIssues(choicesOn(step, context))
            LevelUpStep.Abilities -> buildList {
                val asi = draft.asi
                val complete = when (asi?.kind) {
                    null -> false
                    AsiKind.PLUS_TWO -> asi.abilities.isNotEmpty()
                    AsiKind.PLUS_ONE_ONE -> asi.abilities.distinct().size >= 2
                    AsiKind.FEAT -> true
                }
                if (!complete) add(LevelUpIssue(LevelUpIssueKind.ASI_INCOMPLETE, context.newClassLevel.toString()))
                if (asi?.kind == AsiKind.FEAT && draft.featId == null) add(LevelUpIssue(LevelUpIssueKind.FEAT_MISSING))
                addAll(choiceIssues(choicesOn(step, context)))
            }
            LevelUpStep.HitPoints -> listOfNotNull(
                LevelUpIssue(LevelUpIssueKind.HP_MISSING, blocking = true).takeIf {
                    (draft.hpMethod == HpMethod.ROLL && draft.hpRoll == null) ||
                        (draft.hpMethod == HpMethod.MANUAL && draft.manualHp == null)
                },
            )
            LevelUpStep.Spells -> buildList {
                context.spells?.let { spells ->
                    val cantrips = spells.newCantrips - draft.cantripIds.size
                    if (cantrips > 0) add(LevelUpIssue(LevelUpIssueKind.CANTRIPS_INCOMPLETE, missing = cantrips))
                    val known = spells.newSpells - draft.spellIds.size
                    if (known > 0) add(LevelUpIssue(LevelUpIssueKind.SPELLS_INCOMPLETE, missing = known))
                    // Мистические рыцарь и ловкач: новые заклинания и замена вместе не должны занять больше
                    // свободных мест для заклинаний других школ.
                    if (spells.offSchoolSpellIds.isNotEmpty()) {
                        val swap = draft.replacements[REPLACE_SPELL_ID]?.takeIf { it.isComplete }
                        val swapDelta = if (swap == null) 0 else {
                            (if (swap.newId in spells.offSchoolSpellIds) 1 else 0) - (if (swap.oldId in spells.offSchoolSpellIds) 1 else 0)
                        }
                        val over = draft.spellIds.count { it in spells.offSchoolSpellIds } + swapDelta - spells.anySchoolAllowance
                        if (over > 0) add(LevelUpIssue(LevelUpIssueKind.SPELLS_SCHOOL_LIMIT, missing = over, blocking = true))
                    }
                }
                addAll(choiceIssues(choicesOn(step, context)))
                addAll(replacementIssues(context.replacements.filter { it.onSpellStep }))
            }
            LevelUpStep.Replacements -> replacementIssues(context.replacements.filter { !it.onSpellStep })
            // Переподготовка необязательна: свободные места выросшего лимита — напоминание,
            // превышение лимита — единственная блокирующая незавершённость.
            LevelUpStep.Prepare -> buildList {
                val prep = context.preparation ?: return@buildList
                val free = minOf(prep.free, (prep.candidates.size - prep.selected.size).coerceAtLeast(0))
                if (prep.free < 0) add(LevelUpIssue(LevelUpIssueKind.PREPARED_OVER, missing = -prep.free, blocking = true))
                if (free > 0 && prep.limit > prep.oldLimit) add(LevelUpIssue(LevelUpIssueKind.PREPARED_FREE, missing = free))
            }
            // Удалённые вручную ресурсы: пока нет ответа «вернуть / не возвращать» — незавершённость,
            // но не блокирующая: без ответа ресурс не возвращается.
            LevelUpStep.Summary -> context.missingResources
                .filter { it.key !in draft.resourceDecisions }
                .map { LevelUpIssue(LevelUpIssueKind.RESOURCE_DECISION, it.name) }
        }
    }

    /** Все незавершённости по шагам (для итога). */
    fun allIssues(context: LevelUpContext): List<Pair<LevelUpStep, List<LevelUpIssue>>> {
        val steps = steps(context)
        val perStep = steps.map { it to issues(it, context) }
        // Конфликт групп замен проверяется по всем заменам сразу (заговор и стиль — на разных шагах).
        val conflicts = context.replacements.filter { it.group != null && context.draft.replacements[it.id]?.isEmpty == false }
            .groupBy { it.group }.filterValues { it.size > 1 }.keys
            .map { LevelUpIssue(LevelUpIssueKind.REPLACEMENT_CONFLICT, it.orEmpty(), blocking = true) }
        val withConflicts = if (conflicts.isEmpty() || perStep.any { (_, list) -> list.any { it.kind == LevelUpIssueKind.REPLACEMENT_CONFLICT } }) {
            perStep
        } else {
            perStep.map { (step, list) -> if (step == LevelUpStep.Summary) step to (list + conflicts) else step to list }
        }
        return withConflicts.filter { it.second.isNotEmpty() }
    }

    /** Можно ли применить повышение: нет блокирующих незавершённостей. */
    fun canApply(context: LevelUpContext): Boolean =
        allIssues(context).none { (_, list) -> list.any { it.blocking } }

    // endregion

    // region Хиты

    /** Средний результат кости хитов по правилам. */
    fun averageHitDie(hitDie: Int): Int = hitDie / 2 + 1

    /** Значение кости хитов по выбранному способу (без модификатора Телосложения). */
    fun hitDieValue(context: LevelUpContext): Int {
        val draft = context.draft
        val average = averageHitDie(context.hitDie)
        return when (draft.hpMethod) {
            HpMethod.AVERAGE -> average
            HpMethod.ROLL -> draft.hpRoll ?: average
            HpMethod.MANUAL -> draft.manualHp ?: average
        }
    }

    /**
     * Прибавка к максимуму хитов: кость + модификатор Телосложения (минимум 1), хиты за уровень
     * от расы и уже взятых черт («Крепкий»). Если на этом уровне вырос модификатор Телосложения или
     * взята черта с хитами за уровень, прибавка за прошлые уровни начисляется задним числом (PHB).
     */
    fun hpGain(context: LevelUpContext): Int {
        val conBefore = abilityModifier(context.scoresBefore[AbilityType.CONSTITUTION] ?: DEFAULT_SCORE)
        val conAfter = abilityModifier(context.scoresAfter[AbilityType.CONSTITUTION] ?: DEFAULT_SCORE)
        val raceHp = (context.raceNew.race?.grants?.hpPerLevel ?: 0) + (context.raceNew.subrace?.grants?.hpPerLevel ?: 0)
        val newFeatHp = context.newWizardContext?.featEffects?.values?.sumOf { it.hpPerLevel } ?: 0
        val perLevel = maxOf(1, hitDieValue(context) + conAfter)
        return perLevel + raceHp + context.existingFeatHpPerLevel +
            newFeatHp * context.newLevel + (conAfter - conBefore) * context.oldLevel
    }

    /** Кость хитов класса, которого нет в библиотеке. */
    private fun hitDieFor(type: CharacterClass?): Int = when (type) {
        CharacterClass.BARBARIAN -> 12
        CharacterClass.FIGHTER, CharacterClass.PALADIN, CharacterClass.RANGER -> 10
        CharacterClass.SORCERER, CharacterClass.WIZARD -> 6
        else -> 8
    }

    // endregion

    // region Применение

    /** Итог повышения: персонаж, изменения связей и сводка «было → стало». */
    fun build(context: LevelUpContext, library: WizardLibrary): LevelUpResult {
        val base = context.base
        val old = base.character
        val draft = context.draft
        val spells = library.spells
        val byId = spells.associateBy { it.id }
        val newCtx = context.newWizardContext
        val oldCtx = context.oldWizardContext
        val wizardDraft = CharacterDraft(selections = draft.selections)
        fun values(kind: ChoiceKind) = context.choices.filter { it.kind == kind }.flatMap { choice ->
            draft.selections[choice.id].orEmpty().filter { it.isNotBlank() }
        }

        // Классы и уровень.
        val classLevels = old.classLevels.toMutableList()
        when {
            context.entry != null -> classLevels[context.entry.index] = context.entry.classLevel.copy(level = context.newClassLevel)
            context.newClass != null && context.rules != null -> classLevels += CharacterClassLevel(
                characterClass = context.rules.type,
                level = 1,
                customName = if (context.rules.type == CharacterClass.OTHER) context.newClass.name else "",
            )
        }
        val plan = newCtx?.spellcasting
        val oldPlan = oldCtx?.spellcasting
        val slots = if (classLevels.size <= 1 && plan != null) plan.slots else spellSlotsFor(classLevels)

        // Хиты.
        val gain = hpGain(context)

        // Заклинания.
        val known = base.knownSpellIds
        val replacedSpells = draft.replacements.filterKeys { it in SPELL_REPLACEMENT_IDS }
            .filter { (id, replacement) -> replacement.isComplete && context.replacements.any { it.id == id } }
            .values
        val raceSpells = { ctx: WizardContext, level: Int ->
            (ctx.race?.grants?.spells.orEmpty() + ctx.subrace?.grants?.spells.orEmpty()).filter { it.minLevel <= level }.map { it.name }
        }
        val grantedOld = CharacterAssembler.matchSpells(oldCtx?.featureSpells.orEmpty(), spells).toSet()
        val grantedNew = CharacterAssembler.matchSpells(
            newCtx?.featureSpells.orEmpty() + newCtx?.featEffects?.values?.flatMap { it.spells }.orEmpty(),
            spells,
        )
        val raceOldSpells = CharacterAssembler.matchSpells(raceSpells(context.raceOld, context.oldLevel), spells).toSet()
        val raceNewSpells = CharacterAssembler.matchSpells(raceSpells(context.raceNew, context.newLevel), spells)
        val choiceSpells = values(ChoiceKind.SPELL)
        val newGranted = (grantedNew.filter { it !in grantedOld } + raceNewSpells.filter { it !in raceOldSpells } + choiceSpells).distinct()
        val lostGranted = grantedOld.filter { it !in grantedNew }
        val spellInfo = context.spells
        val addedSpells = linkedSetOf<String>().apply {
            addAll(draft.cantripIds)
            addAll(draft.spellIds)
            addAll(values(ChoiceKind.CANTRIP))
            addAll(choiceSpells)
            addAll(spellInfo?.addedFromClassList.orEmpty())
            addAll(spellInfo?.alwaysPrepared.orEmpty())
            addAll(newGranted)
            replacedSpells.forEach { add(it.newId!!) }
            removeAll(known)
        }
        // Переподготовка: новые подготовленные и снятые (только если пользователь её менял).
        val prep = context.preparation
        val preparedAdded = prep?.let { it.selected - it.current }.orEmpty()
        val preparedRemoved = if (prep != null && draft.preparedIds != null) prep.current - prep.selected else emptySet()
        addedSpells += preparedAdded.filter { it !in known }
        val removedSpells = (replacedSpells.mapNotNull { it.oldId } + lostGranted)
            .filter { it in known && it !in addedSpells }.toSet()
        val leveledPrepares = plan?.canPrepare == true
        val canPrepare = old.canPrepareSpells || leveledPrepares
        val newlyAlwaysPrepared = if (leveledPrepares) {
            (spellInfo?.alwaysPrepared.orEmpty() + newGranted).distinct().filter { (byId[it]?.level ?: 1) > 0 }
        } else {
            emptyList()
        }
        val lostPrepared = if (leveledPrepares) lostGranted.filter { it in known && (byId[it]?.level ?: 1) > 0 } else emptyList()
        val maxPrepared = if (leveledPrepares) {
            (old.maxPreparedSpells + plan!!.preparedCount - (oldPlan?.preparedCount ?: 0) +
                newlyAlwaysPrepared.size - lostPrepared.size).coerceAtLeast(0)
        } else {
            old.maxPreparedSpells
        }
        val cantripsBefore = known.count { byId[it]?.level == 0 }
        val cantripsAfter = cantripsBefore + addedSpells.count { byId[it]?.level == 0 } - removedSpells.count { byId[it]?.level == 0 }
        val maxCantrips = when {
            old.maxCantrips > 0 -> (old.maxCantrips + cantripsAfter - cantripsBefore).coerceAtLeast(0)
            cantripsBefore == 0 -> cantripsAfter
            else -> 0
        }

        // Владения.
        val classGrants = minus(newCtx?.classGrants ?: FeatureGrants(), oldCtx?.classGrants ?: FeatureGrants())
        val multiclassGrants = if (context.isNewClass && old.classLevels.isNotEmpty()) context.multiclass?.rules?.grants else null
        val grants = classGrants + (newCtx?.featGrants ?: FeatureGrants()) + (multiclassGrants ?: FeatureGrants())
        val chosenSkills = context.choices.flatMap { WizardEngine.chosenSkills(wizardDraft, it) }
        val chosenTools = context.choices.flatMap { WizardEngine.chosenTools(wizardDraft, it) }
            .map { id -> ToolCatalog.byId(id)?.name ?: id }
        val expertise = values(ChoiceKind.EXPERTISE).mapNotNull { runCatching { SkillType.valueOf(it) }.getOrNull() }
        val skills = old.skillProficiencies.toMutableMap()
        (grants.skills + chosenSkills).forEach { skills[it.ordinal] = maxOf(skills[it.ordinal] ?: 0, 1) }
        expertise.forEach { skills[it.ordinal] = 2 }
        // «Многогранность барда»: компетентность переносится с одного навыка на другой, владение остаётся.
        expertiseReplacement(context)?.let { (from, to) ->
            skills[from.ordinal] = 1
            skills[to.ordinal] = 2
        }
        val featSaves = if (newDraftOf(context) != null && newCtx != null) {
            CharacterAssembler.featSaves(newDraftOf(context)!!, newCtx)
        } else {
            emptySet()
        }
        val saves = old.saveProficiencies.toMutableMap()
        (grants.saves + featSaves).forEach { saves[it.ordinal] = maxOf(saves[it.ordinal] ?: 0, 1) }
        val otherWeaponsAdded = grants.otherWeapons + values(ChoiceKind.WEAPON).mapNotNull { EquipmentCatalog.weaponById(it)?.name }
        val weapons = (old.weaponProficiencies.mapNotNull { runCatching { WeaponProficiency.valueOf(it) }.getOrNull() } + grants.weapons)
            .toMutableSet()
        if (otherWeaponsAdded.isNotEmpty()) weapons += WeaponProficiency.OTHER
        val armor = old.armorProficiencies.mapNotNull { runCatching { ArmorProficiency.valueOf(it) }.getOrNull() }.toSet() + grants.armor
        val toolsText = appendList(old.toolProficiencies, grants.tools + chosenTools)
        val languagesText = appendList(old.languages, grants.languages + values(ChoiceKind.LANGUAGE))
        val otherWeaponsText = appendList(old.otherWeaponProficiencies, otherWeaponsAdded)

        // Ресурсы: новые, обновлённые максимумы, убранные вместе с заменённым вариантом.
        val restoredResources = restoredResources(context)
        val excluded = context.excludedContent
        // Ресурсы, исключённые раньше и включённые обратно: с полным максимумом нового уровня.
        val reincluded = context.proposal.resources.filter { it.spec != null && it.key !in excluded }.mapNotNull { it.spec }
        val resources = mergeResources(
            old.resources,
            oldCtx?.resources.orEmpty() + context.raceOld.resources,
            newCtx?.resources.orEmpty() + context.raceNew.resources,
            spells,
            excluded,
        ) + restoredResources.map { it.spec } + reincluded

        // Архетип.
        val newSubclass = context.subclass?.takeIf { context.entry?.subclass == null && context.needsSubclass }
        val subclassText = when {
            newSubclass == null -> old.subclass
            old.subclass.isBlank() -> newSubclass.title
            else -> old.subclass + SUBCLASS_SEPARATOR + newSubclass.title
        }

        val feat = draft.featId?.takeIf { context.isAsiLevel && draft.asi?.kind == AsiKind.FEAT }
            ?.let { id -> library.feats.firstOrNull { it.id == id } }

        var character = old.copy(
            level = context.newLevel,
            classes = classLevels.map { it.serialize() },
            maxHp = old.maxHp + gain,
            currentHp = old.currentHp + gain,
            abilityScores = old.abilityScores + context.scoresAfter.mapKeys { it.key.ordinal },
            spellSlots = slots,
            resources = resources,
            canPrepareSpells = canPrepare,
            maxPreparedSpells = maxPrepared,
            maxCantrips = maxCantrips,
            skillProficiencies = skills,
            saveProficiencies = saves,
            armorProficiencies = ArmorProficiency.entries.filter { it in armor }.map { it.name },
            weaponProficiencies = WeaponProficiency.entries.filter { it in weapons }.map { it.name },
            otherWeaponProficiencies = otherWeaponsText,
            toolProficiencies = toolsText,
            languages = languagesText,
            subclass = subclassText,
            // Заметки выбранных вариантов исключить нельзя: их ключи не запоминаются.
            excludedContent = (excluded - lockedKeys(context.proposal)).sorted(),
        )
        character = withStatParts(character, context, feat)
        character = character.withRecalculatedResources()

        val notes = updatedNotes(context, old.id, excluded)
        val summary = summary(context, old, character, gain, addedSpells, removedSpells, feat, newSubclass, armor, weapons, skills, saves, library)
            .copy(
                newAlwaysPreparedIds = spellInfo?.alwaysPrepared.orEmpty().filter { (byId[it]?.level ?: 1) > 0 },
                preparedAddedIds = preparedAdded.toList(),
                preparedRemovedIds = preparedRemoved.toList(),
            )
        return LevelUpResult(
            character = character,
            notes = notes,
            addedSpellIds = addedSpells,
            preparedSpellIds = newlyAlwaysPrepared.toSet() + preparedAdded,
            removedSpellIds = removedSpells,
            addedFeatIds = listOfNotNull(feat?.id),
            summary = summary,
            unpreparedSpellIds = preparedRemoved - removedSpells,
        )
    }

    private fun newDraftOf(context: LevelUpContext): CharacterDraft? = context.newWizardDraft

    /** Удалённые ресурсы, которые пользователь решил вернуть: с полным максимумом нового уровня. */
    private fun restoredResources(context: LevelUpContext): List<MissingResource> =
        context.missingResources.filter { context.draft.resourceDecisions[it.key] == true }

    /** Замена навыка компетентности: (было, стало); null — не выбрана или недопустима. */
    private fun expertiseReplacement(context: LevelUpContext): Pair<SkillType, SkillType>? {
        val spec = context.replacements.firstOrNull { it.id == REPLACE_EXPERTISE_ID } ?: return null
        val replacement = context.draft.replacements[spec.id]?.takeIf { it.isComplete } ?: return null
        if (replacement.oldId !in spec.removable || spec.candidateOptions.none { it.id == replacement.newId }) return null
        val from = runCatching { SkillType.valueOf(replacement.oldId!!) }.getOrNull() ?: return null
        val to = runCatching { SkillType.valueOf(replacement.newId!!) }.getOrNull() ?: return null
        return from to to
    }

    /** Составляющие КД, скорости и инициативы от новых умений, черты и боевого стиля. */
    private fun withStatParts(start: Character, context: LevelUpContext, feat: Feat?): Character {
        var character = start
        val draft = context.draft
        val newCtx = context.newWizardContext
        val oldCtx = context.oldWizardContext
        val rules = newCtx?.characterClass
        if (rules != null) {
            val oldSpeed = oldCtx?.characterClass?.let { SpeedRules.classParts(it, oldCtx.subclass, context.oldClassLevel, draft.showOptional) }
                .orEmpty().map { it.id }.toSet()
            val speedAdded = SpeedRules.classParts(rules, newCtx.subclass, context.newClassLevel, draft.showOptional)
                .filter { it.id !in oldSpeed && character.speedParts.none { part -> part.id == it.id } }
            if (speedAdded.isNotEmpty()) {
                val baseParts = character.speedParts.ifEmpty {
                    listOf(StatPart(name = "", formula = character.speed.toString(), kind = StatPartKind.BASE))
                }
                character = character.copy(speedParts = baseParts + speedAdded)
            }
            val oldInit = oldCtx?.characterClass?.let { InitiativeRules.classParts(it, oldCtx.subclass, context.oldClassLevel, draft.showOptional) }
                .orEmpty().map { it.id }.toSet()
            val initAdded = InitiativeRules.classParts(rules, newCtx.subclass, context.newClassLevel, draft.showOptional)
                .filter { it.id !in oldInit && character.initiativeParts.none { part -> part.id == it.id } }
            if (initAdded.isNotEmpty()) character = character.copy(initiativeParts = character.initiativePartsOrDefault + initAdded)
        }
        if (feat != null) {
            character = ArmorClassRules.withFeat(character, feat)
            character = SpeedRules.withFeat(character, feat)
            character = InitiativeRules.withFeat(character, feat)
        }
        // Боевой стиль «Оборона» и т. п.: новые и заменённые варианты.
        val removedOptions = optionReplacements(context).map { it.first }
        val addedOptions = newOptionSelections(context).flatMap { it.second } + optionReplacements(context).map { it.second }
        if (removedOptions.isNotEmpty()) {
            val names = removedOptions.map { norm(it.title) }.toSet()
            character = character.copy(armorClassParts = character.armorClassParts.filterNot { norm(it.name) in names })
        }
        addedOptions.mapNotNull { ArmorClassRules.bonusPart(it.title, it.text) }.forEach { part ->
            if (character.armorClassParts.any { norm(it.name) == norm(part.name) }) return@forEach
            val baseParts = character.armorClassParts.ifEmpty {
                listOf(StatPart(name = "", formula = character.armorClass.toString(), kind = StatPartKind.BASE))
            }
            character = character.copy(armorClassParts = baseParts + part)
        }
        return character
    }

    /** Новые варианты выборов этого уровня: выбор → выбранные варианты. */
    private fun newOptionSelections(context: LevelUpContext): List<Pair<ChoiceSpec, List<ChoiceOption>>> =
        context.choices.filter { it.kind == ChoiceKind.OPTION }.mapNotNull { choice ->
            val selected = context.draft.selections[choice.id].orEmpty()
                .mapNotNull { id -> choice.options.firstOrNull { it.id == id } }
            (choice to selected).takeIf { selected.isNotEmpty() }
        }

    /** Заменённые варианты: (старый, новый). */
    private fun optionReplacements(context: LevelUpContext): List<Pair<ChoiceOption, ChoiceOption>> =
        context.replacements.filter { it.kind == ReplacementKind.OPTION }.mapNotNull { spec ->
            val replacement = context.draft.replacements[spec.id]?.takeIf { it.isComplete } ?: return@mapNotNull null
            val removed = spec.removableOptions.firstOrNull { it.id == replacement.oldId } ?: return@mapNotNull null
            val added = spec.candidateOptions.firstOrNull { it.id == replacement.newId } ?: return@mapNotNull null
            removed to added
        }

    /**
     * Заметки: новые умения дописываются в блоки «Класс: …», «Архетип: …», «Раса: …» (удалённый
     * блок создаётся заново), новые варианты — в блок выбора, заменённые варианты и базовые умения,
     * заменённые опциональными, убираются.
     */
    private fun updatedNotes(context: LevelUpContext, characterId: String, excluded: Set<String> = emptySet()): List<NoteBlock> {
        val blocks = context.base.notes.associateBy { it.id }.toMutableMap()
        val created = mutableListOf<NoteBlock>()
        val changed = mutableSetOf<String>()
        var nextOrder = (context.base.notes.maxOfOrNull { it.sortOrder } ?: 0L) + 1
        val now = System.currentTimeMillis()

        fun blockFor(title: String): NoteBlock {
            blocks.values.firstOrNull { norm(it.title) == norm(title) }?.let { return it }
            created.firstOrNull { norm(it.title) == norm(title) }?.let { return it }
            val block = NoteBlock(
                characterId = characterId,
                title = title,
                collapsed = true,
                paragraphs = emptyList(),
                createdAt = now,
                sortOrder = nextOrder++,
            )
            created += block
            return block
        }
        fun save(block: NoteBlock) {
            val index = created.indexOfFirst { it.id == block.id }
            if (index >= 0) created[index] = block else {
                blocks[block.id] = block
                changed += block.id
            }
        }
        fun append(title: String, paragraph: NoteParagraph) {
            val block = blockFor(title)
            val heading = norm(firstLine(paragraph.text))
            if (block.paragraphs.any { norm(firstLine(it.text)) == heading }) return
            // Пустой абзац нового блока по умолчанию не нужен.
            val existing = block.paragraphs.filterNot { it.text.isBlank() }
            save(block.copy(paragraphs = existing + paragraph))
        }
        fun remove(title: String, heading: String) {
            val block = blocks.values.firstOrNull { norm(it.title) == norm(title) } ?: return
            val kept = block.paragraphs.filterNot { norm(firstLine(it.text)) == norm(heading) }
            if (kept.size != block.paragraphs.size) save(block.copy(paragraphs = kept))
        }

        // Базовые умения, заменённые выбранными опциональными (Tasha).
        context.rules?.let { rules ->
            context.swaps.filter { context.draft.optionalSwaps[it.id] == true }.forEach { swap ->
                swap.base.forEach { feature ->
                    remove(classBlockTitle(rules), CharacterAssembler.levelTitle(feature))
                    context.subclass?.let { remove(subclassBlockTitle(it), CharacterAssembler.levelTitle(feature)) }
                }
            }
        }
        // Исключённый блок или абзац не создаётся и не дописывается; заметки вариантов — всегда.
        context.features
            .filterNot { ContentExclusions.isParagraphExcluded(it.blockTitle, it.title, excluded) }
            .forEach { feature ->
                append(feature.blockTitle, CharacterAssembler.paragraph(feature.title, feature.feature.text))
            }
        optionReplacements(context).forEach { (removed, _) ->
            val spec = context.replacements.first { spec -> spec.removableOptions.any { it.id == removed.id } }
            remove(spec.title, removed.title)
        }
        newOptionSelections(context).forEach { (choice, options) ->
            options.forEach { append(choice.title, CharacterAssembler.paragraph(it.title, it.text)) }
        }
        optionReplacements(context).forEach { (removed, added) ->
            val spec = context.replacements.first { spec -> spec.removableOptions.any { it.id == removed.id } }
            append(spec.title, CharacterAssembler.paragraph(added.title, added.text))
        }
        return changed.mapNotNull { blocks[it] } + created.filter { it.paragraphs.isNotEmpty() }
    }

    /**
     * Ресурсы после повышения. Спецификации старого и нового уровня сопоставляются по названию
     * без уточнения в скобках («Кости превосходства (к8)» → «(к10)»): у существующего ресурса
     * обновляются название и табличный максимум (заряды растут на прирост), формульные
     * пересчитываются потом. Новые добавляются полными, исчезнувшие (заменённый вариант) убираются.
     * Ресурс, который пользователь удалил, заново не создаётся.
     */
    private fun mergeResources(
        current: List<CharacterResource>,
        oldSpecs: List<ResourceSpec>,
        newSpecs: List<ResourceSpec>,
        spells: List<Spell>,
        excluded: Set<String> = emptySet(),
    ): List<CharacterResource> {
        fun key(name: String) = resourceKey(name)
        val oldResolved = resolvedResources(oldSpecs, spells)
        val newResolved = resolvedResources(newSpecs, spells)
        val result = current.toMutableList()
        fun indexOf(name: String) = result.indexOfFirst { norm(it.name) == norm(name) }
            .takeIf { it >= 0 } ?: result.indexOfFirst { key(it.name) == key(name) }

        newResolved.forEach { spec ->
            val previous = oldResolved.firstOrNull { key(it.name) == key(spec.name) }
            val index = indexOf(previous?.name ?: spec.name)
            when {
                // Одноимённый ресурс другого класса или созданный вручную не трогаем.
                previous == null && index >= 0 -> Unit
                index >= 0 -> {
                    val existing = result[index]
                    val updated = if (spec.maximumFormula.isBlank() && existing.maximumFormula.isBlank()) {
                        val delta = spec.maximum - (previous?.maximum ?: existing.maximum)
                        existing.copy(
                            name = spec.name,
                            maximum = maxOf(existing.maximum + delta, 1),
                            current = (existing.current + maxOf(delta, 0)).coerceAtLeast(0),
                        )
                    } else {
                        existing.copy(name = spec.name)
                    }
                    result[index] = updated.normalized()
                }
                // Ресурса не было на прошлом уровне — новый (если не исключён); был, но персонаж его
                // удалил, — возвращается только по решению пользователя (см. [restoredResources]).
                previous == null && !ContentExclusions.isResourceExcluded(spec.name, excluded) -> result += spec
            }
        }
        oldResolved.filter { old -> newResolved.none { key(it.name) == key(old.name) } }.forEach { gone ->
            val index = result.indexOfFirst { norm(it.name) == norm(gone.name) }
            if (index >= 0) result.removeAt(index)
        }
        return result
    }

    private fun summary(
        context: LevelUpContext,
        old: Character,
        new: Character,
        gain: Int,
        added: Set<String>,
        removed: Set<String>,
        feat: Feat?,
        subclass: SubclassRules?,
        armor: Set<ArmorProficiency>,
        weapons: Set<WeaponProficiency>,
        skills: Map<Int, Int>,
        saves: Map<Int, Int>,
        library: WizardLibrary,
    ): LevelUpSummary {
        val byId = library.spells.associateBy { it.id }
        val oldResources = old.resources.associateBy { norm(it.name) }
        val newResources = new.resources.associateBy { norm(it.name) }
        val restoredKeys = restoredResources(context).map { it.key }.toSet()
        val resourceChanges = buildList {
            new.resources.forEach { resource ->
                val before = oldResources[norm(resource.name)]
                    ?: old.resources.firstOrNull { norm(it.name.substringBefore(" (")) == norm(resource.name.substringBefore(" (")) }
                if (before == null || before.maximum != resource.maximum || before.name != resource.name) {
                    val restored = before == null && resourceKey(resource.name) in restoredKeys
                    add(ResourceChange(resource.name, before?.maximum, resource.maximum, restored = restored))
                }
            }
            old.resources.filter { resource ->
                norm(resource.name) !in newResources &&
                    new.resources.none { norm(it.name.substringBefore(" (")) == norm(resource.name.substringBefore(" (")) }
            }.forEach { add(ResourceChange(it.name, it.maximum, null)) }
        }
        val abilityChanges = AbilityType.entries.mapNotNull { ability ->
            val before = context.scoresBefore[ability] ?: DEFAULT_SCORE
            val after = context.scoresAfter[ability] ?: before
            (ability to (before to after)).takeIf { before != after }
        }.toMap()
        val replaced = buildList {
            optionReplacements(context).forEach { (from, to) -> add(from.title to to.title) }
            context.draft.replacements.filterKeys { it in SPELL_REPLACEMENT_IDS }
                .filter { (id, replacement) -> replacement.isComplete && context.replacements.any { it.id == id } }.values
                .forEach { add((byId[it.oldId]?.name ?: it.oldId!!) to (byId[it.newId]?.name ?: it.newId!!)) }
            expertiseReplacement(context)?.let { (from, to) ->
                add(MarkupText.skillTitle(from) to MarkupText.skillTitle(to))
            }
        }
        val oldSkills = old.skillProficiencies
        val proficiencies = LevelUpProficiencies(
            armor = armor - old.armorProficiencies.mapNotNull { runCatching { ArmorProficiency.valueOf(it) }.getOrNull() }.toSet(),
            weapons = weapons - old.weaponProficiencies.mapNotNull { runCatching { WeaponProficiency.valueOf(it) }.getOrNull() }.toSet(),
            otherWeapons = listItems(new.otherWeaponProficiencies) - listItems(old.otherWeaponProficiencies).toSet(),
            tools = listItems(new.toolProficiencies) - listItems(old.toolProficiencies).toSet(),
            languages = listItems(new.languages) - listItems(old.languages).toSet(),
            skills = skills.filter { (ordinal, level) -> level >= 1 && (oldSkills[ordinal] ?: 0) < 1 }
                .keys.mapNotNull { SkillType.entries.getOrNull(it) }.toSet(),
            expertise = skills.filter { (ordinal, level) -> level >= 2 && (oldSkills[ordinal] ?: 0) < 2 }
                .keys.mapNotNull { SkillType.entries.getOrNull(it) }.toSet(),
            saves = saves.filter { (ordinal, level) -> level >= 1 && (old.saveProficiencies[ordinal] ?: 0) < 1 }
                .keys.mapNotNull { AbilityType.entries.getOrNull(it) }.toSet(),
        )
        return LevelUpSummary(
            oldLevel = old.level,
            newLevel = new.level,
            classTitle = context.rules?.charClass?.name
                ?: context.entry?.classLevel?.let { it.displayName ?: it.characterClass.name }.orEmpty(),
            oldClassLevel = context.oldClassLevel,
            newClassLevel = context.newClassLevel,
            isNewClass = context.isNewClass,
            oldProficiencyBonus = proficiencyBonusFor(old.level),
            newProficiencyBonus = proficiencyBonusFor(new.level),
            oldMaxHp = old.maxHp,
            newMaxHp = new.maxHp,
            hpGain = gain,
            oldSlots = old.spellSlots,
            newSlots = new.spellSlots,
            resources = resourceChanges,
            features = context.features,
            abilityChanges = abilityChanges,
            addedSpellIds = added.toList(),
            removedSpellIds = removed.toList(),
            newFeatIds = listOfNotNull(feat?.id),
            subclassTitle = subclass?.title,
            newOptions = newOptionSelections(context).associate { (choice, options) -> choice.title to options.map { it.title } },
            replaced = replaced,
            newProficiencies = proficiencies,
            maxPreparedChange = (old.maxPreparedSpells to new.maxPreparedSpells).takeIf { it.first != it.second },
        )
    }

    // endregion

    // region Вспомогательное

    private fun classDraft(
        classId: String,
        subclassId: String?,
        level: Int,
        selections: Map<String, List<String>>,
        swaps: Map<String, Boolean>,
        showOptional: Boolean,
        showUnofficial: Boolean,
        scores: Map<AbilityType, Int>,
        asi: AsiChoice? = null,
        featId: String? = null,
    ) = CharacterDraft(
        level = level,
        classId = classId,
        subclassId = subclassId,
        selections = selections,
        abilityMethod = AbilityMethod.MANUAL,
        baseScores = scores,
        showOptional = showOptional,
        showUnofficial = showUnofficial,
        optionalSwaps = swaps,
        asiChoices = asi?.let { mapOf(level to it.copy(level = level)) }.orEmpty(),
        featSlots = if (asi?.kind == AsiKind.FEAT && featId != null) mapOf("$ASI_SLOT_PREFIX$level" to featId) else emptyMap(),
    )

    private fun raceDraft(base: LevelUpBase, level: Int, draft: LevelUpDraft) = CharacterDraft(
        level = level,
        raceId = base.race?.id,
        subraceId = base.subraceId,
        abilityMethod = AbilityMethod.MANUAL,
        showOptional = draft.showOptional,
        showUnofficial = draft.showUnofficial,
    )

    /** Текущие значения характеристик персонажа (с бонусами расы и увеличениями). */
    fun currentScores(character: Character): Map<AbilityType, Int> =
        AbilityType.entries.associateWith { character.abilityScore(it) }

    fun classBlockTitle(rules: ClassRules): String = "Класс: ${rules.charClass.name}"

    fun subclassBlockTitle(subclass: SubclassRules): String = "Архетип: ${subclass.title}"

    private fun raceBlockTitle(race: RaceRules, subrace: SubraceRules?): String =
        "Раса: " + listOfNotNull(race.race.name, subrace?.title).joinToString(", ")

    private fun minus(a: FeatureGrants, b: FeatureGrants) = FeatureGrants(
        skills = a.skills - b.skills,
        armor = a.armor - b.armor,
        weapons = a.weapons - b.weapons,
        otherWeapons = a.otherWeapons.filter { item -> b.otherWeapons.none { norm(it) == norm(item) } },
        tools = a.tools.filter { item -> b.tools.none { norm(it) == norm(item) } },
        languages = a.languages.filter { item -> b.languages.none { norm(it) == norm(item) } },
        saves = a.saves - b.saves,
    )

    /** Дописывает элементы в список через запятую без повторов (без учёта регистра). */
    private fun appendList(text: String, items: List<String>): String {
        val existing = listItems(text)
        val added = items.map { it.trim() }.filter { it.isNotEmpty() }
            .filter { item -> existing.none { norm(it) == norm(item) } }
            .distinctBy { norm(it) }
        return (existing + added).joinToString(", ")
    }

    private fun listItems(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun firstLine(text: String): String = text.lineSequence().firstOrNull()?.trim().orEmpty()

    private fun norm(text: String): String = text.lowercase().replace('ё', 'е').trim()

    /** «когда вы получаете (новый) уровень … заменить». */
    private val LEVEL_REPLACE = Regex("(?:получаете|повышаете)\\s+(?:новый\\s+)?уровень[^.]*замени")

    private const val RESTORE_PASSES = 2

    /** Круги таинственного арканума. */
    private val ARCANUM_CIRCLES = 6..9

    private const val DEFAULT_SCORE = 10

    // endregion
}
