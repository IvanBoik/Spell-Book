package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.FeatLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.model.spellSlotsFor
import com.example.spellbook.ui.wizard.filterSpells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Повышение уровня на настоящих библиотеках: персонаж собирается мастером создания,
 * восстанавливается из листа и заметок, затем повышается на +1.
 */
class LevelUpEngineTest {

    private val races by lazy {
        RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "r$index") }
    }
    private val classes by lazy {
        ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "c$index") }
    }
    private val backgrounds by lazy {
        BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "b$index") }
    }
    private val feats by lazy {
        FeatLibraryCodec.decodeList(File("src/main/assets/feats-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "f$index") }
    }
    private val spells: List<Spell> by lazy {
        SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "s$index") }
    }
    private val magicItems by lazy {
        com.example.spellbook.data.MagicItemLibraryCodec.decodeAll(File("src/main/assets/magic-items-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "m$index") }
    }
    private val library by lazy { WizardLibrary(races, classes, backgrounds, feats, spells, magicItems) }
    private val cache = WizardRulesCache()

    private val human by lazy { races.first { it.name == "Человек" && it.category == "Основные расы" } }
    private val soldier by lazy { backgrounds.first { it.name == "Солдат" } }

    // region Сборка персонажа мастером создания

    private fun classId(name: String) = classes.first { it.name == name }.id

    private fun fill(choice: ChoiceSpec, draft: CharacterDraft, context: WizardContext): List<String> = when (choice.kind) {
        ChoiceKind.CANTRIP, ChoiceKind.SPELL -> {
            val filter = choice.spellFilter ?: SpellFilter(maxLevel = if (choice.kind == ChoiceKind.CANTRIP) 0 else 9)
            val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
            filterSpells(spells, effective).map { it.id }.take(choice.count)
        }
        ChoiceKind.SKILL, ChoiceKind.EXPERTISE -> {
            val owned = WizardEngine.proficientSkills(draft, context)
            val pool = when {
                choice.kind == ChoiceKind.EXPERTISE -> owned.map { it.name }
                choice.options.isNotEmpty() -> choice.options.map { it.id }
                else -> SkillType.entries.map { it.name }
            }
            val fresh = pool.filter { name -> choice.kind == ChoiceKind.EXPERTISE || owned.none { it.name == name } }
            (fresh + pool).distinct().take(choice.count)
        }
        ChoiceKind.ABILITY -> choice.options.map { it.id }.ifEmpty { AbilityType.entries.map { it.name } }.take(choice.count)
        else -> choice.options.filter { OptionRequirements.unmet(it, draft, context, spells).isEmpty() }
            .filter { !it.extended }
            .map { it.id }.take(choice.count)
    }

    /** Персонаж мастера: человек-солдат, всё выбрано первыми доступными вариантами, увеличения — Сила +2. */
    private fun createDraft(
        className: String,
        level: Int,
        subclassPrefix: String? = null,
        scores: Map<AbilityType, Int>? = null,
        showOptional: Boolean = false,
    ): CharacterDraft {
        var draft = CharacterDraft(
            name = "Тест", level = level, raceId = human.id, backgroundId = soldier.id, showOptional = showOptional,
        )
        draft = WizardEngine.selectClass(draft, classId(className))
        var context = WizardEngine.context(draft, library, cache)
        if (WizardEngine.needsSubclass(draft, context)) {
            val rules = context.characterClass!!
            val sub = rules.subclasses.firstOrNull { subclassPrefix != null && it.title.startsWith(subclassPrefix) }
                ?: rules.subclasses.first { !it.extended }
            draft = WizardEngine.selectSubclass(draft, sub.id)
        }
        draft = if (scores != null) {
            draft.copy(abilityMethod = AbilityMethod.MANUAL, baseScores = scores)
        } else {
            draft.copy(
                abilityMethod = AbilityMethod.STANDARD_ARRAY,
                poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap(),
            )
        }
        context = WizardEngine.context(draft, library, cache)
        WizardEngine.bonusRule(context)?.let { rule ->
            val values = WizardEngine.bonusValues(draft, rule)
            draft = draft.copy(bonusTargets = AbilityType.entries.take(values.size))
        }
        context = WizardEngine.context(draft, library, cache)
        draft = draft.copy(
            asiChoices = WizardEngine.asiLevels(draft, context)
                .associateWith { AsiChoice(it, AsiKind.PLUS_TWO, listOf(AbilityType.STRENGTH)) },
        )
        repeat(3) {
            context = WizardEngine.context(draft, library, cache)
            val selections = draft.selections.toMutableMap()
            context.choices.forEach { choice ->
                if (WizardEngine.selectedCount(draft, choice) < choice.count) {
                    selections[choice.id] = fill(choice, draft.copy(selections = selections), context)
                }
            }
            draft = draft.copy(selections = selections)
        }
        context = WizardEngine.context(draft, library, cache)
        context.spellcasting?.let { plan ->
            val classFilter = SpellFilter(classCodes = setOf(plan.classCode), schools = plan.schools)
            val cantrips = filterSpells(spells, classFilter.copy(maxLevel = 0, schools = emptySet())).map { it.id }.take(plan.cantrips).toSet()
            val target = plan.knownSpells ?: plan.spellbookSpells ?: 0
            val known = if (plan.maxSpellLevel > 0) {
                filterSpells(spells, classFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel), plan.extraSpellNames)
                    .map { it.id }.take(target).toSet()
            } else {
                emptySet()
            }
            val prepared = if (plan.canPrepare) {
                val pool = if (plan.spellbookSpells != null) known.toList() else
                    filterSpells(spells, classFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel)).map { it.id }
                pool.take(plan.preparedCount).toSet()
            } else {
                emptySet()
            }
            draft = draft.copy(cantripIds = cantrips, spellIds = known, preparedIds = prepared)
        }
        return draft
    }

    private fun create(draft: CharacterDraft): CharacterCreation =
        CharacterAssembler.assemble(draft, WizardEngine.context(draft, library, cache), spells, feats)

    private fun baseOf(creation: CharacterCreation): LevelUpBase = LevelUpEngine.base(
        character = creation.character,
        notes = creation.notes,
        knownSpellIds = creation.spellIds,
        preparedSpellIds = creation.preparedIds,
        featIds = creation.featIds,
        library = library,
        cache = cache,
    )

    // endregion

    // region Заполнение повышения

    /** Заполняет все выборы повышения первыми доступными вариантами (как пользователь). */
    private fun fillLevelUp(base: LevelUpBase, start: LevelUpDraft): LevelUpDraft {
        var draft = start
        var context = LevelUpEngine.context(base, draft, library, cache)
        if (context.needsSubclass && context.subclass == null) {
            draft = LevelUpEngine.selectSubclass(draft, context.subclassOptions.first { !it.extended }.id)
        }
        if (context.isAsiLevel && draft.asi == null) {
            draft = draft.copy(asi = AsiChoice(context.newClassLevel, AsiKind.PLUS_TWO, listOf(AbilityType.STRENGTH)))
        }
        repeat(3) {
            context = LevelUpEngine.context(base, draft, library, cache)
            val selections = draft.selections.toMutableMap()
            context.choices.forEach { choice ->
                if (selections[choice.id].orEmpty().size >= choice.count) return@forEach
                selections[choice.id] = when (choice.kind) {
                    ChoiceKind.SKILL -> {
                        val pool = choice.options.map { it.id }.ifEmpty { SkillType.entries.map { it.name } }
                        pool.filter { name -> context.proficientSkills.none { it.name == name } }.take(choice.count)
                    }
                    ChoiceKind.EXPERTISE -> context.proficientSkills.filter { it !in context.expertiseSkills }
                        .map { it.name }.take(choice.count)
                    ChoiceKind.CANTRIP, ChoiceKind.SPELL -> {
                        val filter = choice.spellFilter ?: SpellFilter(maxLevel = 9)
                        val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
                        filterSpells(spells, effective).map { it.id }.filter { it !in context.knownSpellIds }.take(choice.count)
                    }
                    else -> choice.options.filter { !it.extended && (it.requiredLevel ?: 0) <= context.newClassLevel }
                        .map { it.id }.take(choice.count)
                }
            }
            draft = draft.copy(selections = selections)
        }
        context = LevelUpEngine.context(base, draft, library, cache)
        context.spells?.let { info ->
            draft = draft.copy(
                cantripIds = info.cantripCandidates.take(info.newCantrips).toSet(),
                spellIds = info.spellCandidates.take(info.newSpells).toSet(),
            )
        }
        return draft
    }

    private fun spellName(id: String?) = spells.first { it.id == id }.name

    private fun headings(notes: List<com.example.spellbook.data.model.NoteBlock>, prefix: String): Set<String> =
        notes.filter { it.title.startsWith(prefix) }
            .flatMap { block -> block.paragraphs.map { it.text.lineSequence().first().trim() } }
            .toSet()

    // endregion

    @Test
    fun `multiclass requirements parse or and and`() {
        fun parsed(name: String) = MulticlassRules.parse(cache.characterClass(classes.first { it.name == name }))
        assertEquals(listOf(setOf(AbilityType.STRENGTH, AbilityType.DEXTERITY)), parsed("Воин").requirements)
        assertEquals(listOf(setOf(AbilityType.STRENGTH), setOf(AbilityType.CHARISMA)), parsed("Паладин").requirements)
        assertEquals(listOf(setOf(AbilityType.WISDOM), setOf(AbilityType.DEXTERITY)), parsed("Монах").requirements)
        val rogue = parsed("Плут")
        assertEquals(setOf(ArmorProficiency.LIGHT), rogue.grants.armor)
        assertTrue(rogue.grants.tools.any { it.lowercase().contains("воровск") })
        assertEquals(1, rogue.choices.single { it.kind == ChoiceKind.SKILL }.count)
        assertTrue(parsed("Волшебник").grants.isEmpty)
        val bard = parsed("Бард")
        assertTrue(bard.choices.any { it.kind == ChoiceKind.TOOL })
        assertTrue(bard.choices.any { it.kind == ChoiceKind.SKILL })
    }

    @Test
    fun `warlock gets an invocation and may replace an invocation and a spell`() {
        val creation = create(createDraft("Колдун", 4))
        val base = baseOf(creation)
        val draftSelections = createDraft("Колдун", 4).selections
        val start = LevelUpEngine.initialDraft(base)
        val context0 = LevelUpEngine.context(base, start, library, cache)
        // Воззвания восстановлены по заметкам.
        assertEquals(draftSelections["class.invocations"]?.toSet(), context0.restoredSelections["class.invocations"]?.toSet())
        val invocations = context0.choices.single { it.id == "class.invocations" }
        assertEquals(1, invocations.count)
        assertTrue(invocations.options.none { it.id in draftSelections["class.invocations"].orEmpty() })
        assertEquals(1, context0.spells!!.newSpells)
        assertEquals(LevelUpStep.Choice("class.invocations"), LevelUpEngine.steps(context0)[1])

        val spellSpec = context0.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        val invocationSpec = context0.replacements.single { it.id == LevelUpEngine.REPLACE_OPTION_PREFIX + "class.invocations" }
        assertTrue(context0.replacements.none { it.kind == ReplacementKind.CANTRIP })

        var draft = fillLevelUp(base, start)
        val newInvocation = draft.selections.getValue("class.invocations").single()
        val oldOption = invocationSpec.removableOptions.first()
        val newOption = invocationSpec.candidateOptions.first { it.id != newInvocation }
        val oldSpell = spellSpec.removable.first()
        val newSpell = spellSpec.candidateSpellIds.first { it !in draft.spellIds }
        draft = draft.copy(
            replacements = mapOf(
                invocationSpec.id to LevelUpReplacement(oldOption.id, newOption.id),
                spellSpec.id to LevelUpReplacement(oldSpell, newSpell),
            ),
        )
        val context = LevelUpEngine.context(base, draft, library, cache)
        assertTrue(LevelUpEngine.allIssues(context).toString(), LevelUpEngine.allIssues(context).isEmpty())
        val result = LevelUpEngine.build(context, library)

        assertEquals(5, result.character.level)
        assertEquals(listOf("WARLOCK:5"), result.character.classes)
        assertEquals(3, result.summary.newProficiencyBonus)
        assertEquals(2, result.summary.oldProficiencyBonus)
        assertEquals(mapOf(3 to 2), result.character.spellSlots)
        assertTrue(oldSpell in result.removedSpellIds)
        assertTrue(newSpell in result.addedSpellIds)
        val block = result.notes.single { it.title == "Таинственные воззвания" }
        val titles = block.paragraphs.map { it.text.lineSequence().first() }
        assertEquals(3, titles.size)
        assertFalse(oldOption.title in titles)
        assertTrue(newOption.title in titles)
        val added = invocations.options.first { it.id == newInvocation }.title
        assertTrue(added in titles)
        assertTrue(result.summary.replaced.contains(oldOption.title to newOption.title))
        assertTrue(result.summary.replaced.contains(spellName(oldSpell) to spellName(newSpell)))
    }

    @Test
    fun `wizard writes two spells into the book and cannot replace`() {
        val base = baseOf(create(createDraft("Волшебник", 2)))
        val draft = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val context = LevelUpEngine.context(base, draft, library, cache)
        val info = context.spells!!
        assertTrue(info.spellbook)
        assertEquals(2, info.newSpells)
        assertTrue(info.spellCandidates.all { id -> spells.first { it.id == id }.level in 1..2 })
        assertTrue(context.replacements.none { it.kind == ReplacementKind.SPELL })
        val result = LevelUpEngine.build(context, library)
        assertEquals(2, result.addedSpellIds.count { id -> spells.first { it.id == id }.level > 0 })
        assertEquals(mapOf(1 to 4, 2 to 2), result.character.spellSlots)
    }

    @Test
    fun `cleric learns the new circle and domain spells without replacements`() {
        val creation = create(createDraft("Жрец", 2, subclassPrefix = "Домен жизни"))
        val base = baseOf(creation)
        assertNotNull(base.classes.single().subclass)
        val draft = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val context = LevelUpEngine.context(base, draft, library, cache)
        val info = context.spells!!
        assertTrue(info.addedFromClassList.isNotEmpty())
        assertTrue(info.addedFromClassList.all { id -> spells.first { it.id == id }.level == 2 })
        assertEquals(2, info.alwaysPrepared.size)
        assertTrue(context.replacements.isEmpty())
        assertEquals(0, info.newSpells)

        val result = LevelUpEngine.build(context, library)
        assertTrue(result.preparedSpellIds.containsAll(info.alwaysPrepared))
        val direct = create(createDraft("Жрец", 3, subclassPrefix = "Домен жизни"))
        assertEquals(direct.character.maxPreparedSpells, result.character.maxPreparedSpells)
        assertEquals(direct.character.spellSlots, result.character.spellSlots)
        val known = creation.spellIds + result.addedSpellIds - result.removedSpellIds
        assertEquals(direct.spellIds, known)
    }

    @Test
    fun `battle master gets an asi and tasha replacements of maneuver and style`() {
        val creation = create(createDraft("Воин", 3, subclassPrefix = "Мастер боевых", showOptional = true))
        val base = baseOf(creation)
        val start = LevelUpEngine.initialDraft(base).copy(showOptional = true)
        val context0 = LevelUpEngine.context(base, start, library, cache)
        assertTrue(context0.isAsiLevel)
        assertTrue(LevelUpStep.Abilities in LevelUpEngine.steps(context0))
        val maneuver = context0.replacements.single { it.choiceId == "subclass.maneuvers" }
        val style = context0.replacements.single { it.choiceId == "class.fighting_style" }
        assertEquals(maneuver.group, style.group)
        assertNotNull(maneuver.group)
        // Без переключателя Tasha заменять нельзя.
        val noTasha = LevelUpEngine.context(base, start.copy(showOptional = false), library, cache)
        assertTrue(noTasha.replacements.isEmpty())

        // Две замены из одной группы — конфликт.
        val both = start.copy(
            asi = AsiChoice(4, AsiKind.PLUS_TWO, listOf(AbilityType.STRENGTH)),
            replacements = mapOf(
                maneuver.id to LevelUpReplacement(maneuver.removable.first(), maneuver.candidateOptions.first().id),
                style.id to LevelUpReplacement(style.removable.first(), style.candidateOptions.first().id),
            ),
        )
        assertFalse(LevelUpEngine.canApply(LevelUpEngine.context(base, both, library, cache)))

        // Черта «Крепкий»: хиты задним числом за все уровни.
        val tough = feats.first { it.name == "Крепкий" }
        val draft = start.copy(
            asi = AsiChoice(4, AsiKind.FEAT),
            featId = tough.id,
            replacements = mapOf(maneuver.id to LevelUpReplacement(maneuver.removable.first(), maneuver.candidateOptions.first().id)),
        )
        val context = LevelUpEngine.context(base, draft, library, cache)
        assertTrue(LevelUpEngine.canApply(context))
        val result = LevelUpEngine.build(context, library)
        val con = abilityModifier(creation.character.abilityScore(AbilityType.CONSTITUTION))
        assertEquals(creation.character.maxHp + 6 + con + 2 * 4, result.character.maxHp)
        assertEquals(listOf(tough.id), result.addedFeatIds)
        val maneuvers = result.notes.single { it.title == "Боевые приёмы" }.paragraphs.map { it.text.lineSequence().first() }
        assertFalse(maneuver.removableOptions.first().title in maneuvers)
        assertTrue(maneuver.candidateOptions.first().title in maneuvers)

        // Увеличение характеристик +2.
        val asiDraft = start.copy(asi = AsiChoice(4, AsiKind.PLUS_TWO, listOf(AbilityType.DEXTERITY)))
        val asiResult = LevelUpEngine.build(LevelUpEngine.context(base, asiDraft, library, cache), library)
        val dex = creation.character.abilityScore(AbilityType.DEXTERITY)
        assertEquals(minOf(20, dex + 2), asiResult.character.abilityScore(AbilityType.DEXTERITY))
        assertEquals(creation.character.abilityScore(AbilityType.STRENGTH), asiResult.character.abilityScore(AbilityType.STRENGTH))
    }

    @Test
    fun `multiclass checks requirements and grants reduced proficiencies and slots`() {
        val scores = AbilityType.entries.associateWith { 14 }
        val base = baseOf(create(createDraft("Волшебник", 3, scores = scores)))
        val options = LevelUpEngine.newClassOptions(base, library, cache)
        val warlockOption = options.single { it.charClass.name == "Колдун" }
        assertTrue(warlockOption.check.isMet)
        assertTrue(options.none { it.charClass.name == "Волшебник" })

        var draft = LevelUpEngine.selectNewClass(LevelUpEngine.initialDraft(base), warlockOption.charClass.id)
        draft = fillLevelUp(base, draft)
        val context = LevelUpEngine.context(base, draft, library, cache)
        assertTrue(context.isNewClass)
        assertEquals(1, context.newClassLevel)
        assertTrue(context.needsSubclass)
        assertEquals(2, context.spells!!.newCantrips)
        assertEquals(2, context.spells!!.newSpells)
        assertTrue(context.choices.none { it.id == "class.skills" })
        assertTrue(LevelUpEngine.canApply(context))
        val result = LevelUpEngine.build(context, library)
        assertEquals(4, result.character.level)
        assertEquals(listOf("WIZARD:3", "WARLOCK:1"), result.character.classes)
        assertEquals(spellSlotsFor(listOf(CharacterClassLevel(CharacterClass.WIZARD, 3), CharacterClassLevel(CharacterClass.WARLOCK, 1))), result.character.spellSlots)
        assertEquals(mapOf(1 to 5, 2 to 2), result.character.spellSlots)
        assertTrue(ArmorProficiency.LIGHT.name in result.character.armorProficiencies)
        // Спасброски колдуна при мультиклассе не даются.
        assertEquals(base.character.saveProficiencies, result.character.saveProficiencies)
        assertTrue(result.character.subclass.contains(LevelUpEngine.SUBCLASS_SEPARATOR))
        // Хиты нового класса — кость колдуна (среднее), а не максимум.
        assertEquals(base.character.maxHp + 5 + abilityModifier(14), result.character.maxHp)

        // Харизма 8: чародея взять нельзя.
        val weak = baseOf(create(createDraft("Волшебник", 3, scores = scores + (AbilityType.CHARISMA to 8))))
        val sorcerer = LevelUpEngine.newClassOptions(weak, library, cache).single { it.charClass.name == "Чародей" }
        assertFalse(sorcerer.check.isMet)
        val blocked = LevelUpEngine.context(weak, LevelUpEngine.selectNewClass(LevelUpEngine.initialDraft(weak), sorcerer.charClass.id), library, cache)
        assertFalse(LevelUpEngine.canApply(blocked))
    }

    @Test
    fun `fighter multiclassing into rogue gets thieves tools and a rogue skill`() {
        val base = baseOf(create(createDraft("Воин", 3, subclassPrefix = "Чемпион")))
        val rogue = LevelUpEngine.newClassOptions(base, library, cache).single { it.charClass.name == "Плут" }
        assertTrue(rogue.check.isMet)
        val draft = fillLevelUp(base, LevelUpEngine.selectNewClass(LevelUpEngine.initialDraft(base), rogue.charClass.id))
        val context = LevelUpEngine.context(base, draft, library, cache)
        val skillChoice = context.choices.single { it.id == MulticlassRules.SKILLS_CHOICE_ID }
        assertEquals(1, skillChoice.count)
        val result = LevelUpEngine.build(context, library)
        assertTrue(result.character.toolProficiencies.lowercase().contains("воровск"))
        val skill = SkillType.valueOf(draft.selections.getValue(skillChoice.id).single())
        assertEquals(1, result.character.skillProficiencies[skill.ordinal])
        assertTrue(result.summary.newProficiencies.skills.contains(skill))
        // Компетентность плута 1-го уровня — новый выбор.
        assertTrue(context.choices.any { it.kind == ChoiceKind.EXPERTISE })
    }

    @Test
    fun `level up matches a character created directly at the next level`() {
        listOf(
            Triple("Варвар", 5, null),
            Triple("Жрец", 2, "Домен жизни"),
            Triple("Плут", 6, "Вор"),
            Triple("Монах", 4, null),
            Triple("Чародей", 5, null),
            Triple("Паладин", 4, null),
        ).forEach { (className, level, subclass) ->
            val creation = create(createDraft(className, level, subclassPrefix = subclass))
            val base = baseOf(creation)
            val context = LevelUpEngine.context(base, fillLevelUp(base, LevelUpEngine.initialDraft(base)), library, cache)
            val result = LevelUpEngine.build(context, library)
            val direct = create(createDraft(className, level + 1, subclassPrefix = subclass))
            val where = "$className $level→${level + 1}"
            assertEquals(where, direct.character.level, result.character.level)
            assertEquals(where, direct.character.classes, result.character.classes)
            assertEquals(where, direct.character.maxHp, result.character.maxHp)
            assertEquals(where, direct.character.spellSlots, result.character.spellSlots)
            assertEquals(
                where,
                direct.character.resources.associate { it.name to it.maximum },
                result.character.resources.associate { it.name to it.maximum },
            )
            val merged = (creation.notes.associateBy { it.id } + result.notes.associateBy { it.id }).values.toList()
            assertEquals(where, headings(direct.notes, "Класс:"), headings(merged, "Класс:"))
            assertEquals(where, headings(direct.notes, "Архетип:"), headings(merged, "Архетип:"))
            assertEquals(where, direct.character.speedParts.map { it.id to it.formula }.toSet().size, result.character.speedParts.size)
        }
    }

    @Test
    fun `class missing from the library still levels up hp and proficiency`() {
        val character = Character(
            name = "Кровомаг",
            level = 3,
            classes = listOf(CharacterClassLevel(CharacterClass.OTHER, 3, "Кровомаг").serialize()),
            maxHp = 20,
            currentHp = 15,
            abilityScores = mapOf(AbilityType.CONSTITUTION.ordinal to 14),
        )
        val base = LevelUpEngine.base(character, emptyList(), emptySet(), emptySet(), emptyList(), library, cache)
        assertTrue(base.warnings.any { it.kind == LevelUpWarningKind.CLASS_NOT_FOUND })
        val context = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base), library, cache)
        assertTrue(context.limited)
        assertEquals(listOf(LevelUpStep.Class, LevelUpStep.HitPoints, LevelUpStep.Summary), LevelUpEngine.steps(context))
        assertTrue(LevelUpEngine.canApply(context))
        val result = LevelUpEngine.build(context, library)
        assertEquals(4, result.character.level)
        assertEquals(listOf("OTHER:4:Кровомаг"), result.character.classes)
        assertEquals(20 + 5 + 2, result.character.maxHp)
        assertEquals(15 + 5 + 2, result.character.currentHp)

        // Ручной ввод кости хитов.
        val manual = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base).copy(hpMethod = HpMethod.MANUAL), library, cache)
        assertFalse(LevelUpEngine.canApply(manual))
        val manualResult = LevelUpEngine.build(
            LevelUpEngine.context(base, LevelUpEngine.initialDraft(base).copy(hpMethod = HpMethod.MANUAL, manualHp = 1), library, cache),
            library,
        )
        assertEquals(20 + 1 + 2, manualResult.character.maxHp)
    }

    @Test
    fun `bard versatility swaps an expertise skill on asi levels with tasha`() {
        val creation = create(createDraft("Бард", 3, showOptional = true))
        val base = baseOf(creation)
        val start = LevelUpEngine.initialDraft(base).copy(showOptional = true)
        val context0 = LevelUpEngine.context(base, start, library, cache)
        val spec = context0.replacements.single { it.id == LevelUpEngine.REPLACE_EXPERTISE_ID }
        assertEquals(ReplacementKind.EXPERTISE, spec.kind)
        assertEquals(2, spec.removable.size)
        assertNotNull(spec.group)
        // Замена заговора из той же группы «одно из следующих действий».
        assertEquals(spec.group, context0.replacements.single { it.kind == ReplacementKind.CANTRIP }.group)
        assertTrue(LevelUpEngine.context(base, start.copy(showOptional = false), library, cache).replacements
            .none { it.kind == ReplacementKind.EXPERTISE })
        // Компетентность — на шаге замен, заговор — на шаге заклинаний; группа проверяется поверх шагов.
        assertFalse(spec.onSpellStep)
        assertTrue(LevelUpStep.Replacements in LevelUpEngine.steps(context0))
        assertTrue(LevelUpEngine.issues(LevelUpStep.Replacements, context0).isEmpty())
        val cantrip = context0.replacements.single { it.kind == ReplacementKind.CANTRIP }
        val both = fillLevelUp(base, start).copy(
            replacements = mapOf(
                spec.id to LevelUpReplacement(spec.removable.first(), spec.candidateOptions.first().id),
                cantrip.id to LevelUpReplacement(cantrip.removable.first(), cantrip.candidateSpellIds.first()),
            ),
        )
        assertFalse(LevelUpEngine.canApply(LevelUpEngine.context(base, both, library, cache)))

        val from = spec.removable.first()
        val to = spec.candidateOptions.first().id
        val draft = fillLevelUp(base, start).copy(replacements = mapOf(spec.id to LevelUpReplacement(from, to)))
        val context = LevelUpEngine.context(base, draft, library, cache)
        assertTrue(LevelUpEngine.canApply(context))
        val result = LevelUpEngine.build(context, library)
        assertEquals(1, result.character.skillProficiencies[SkillType.valueOf(from).ordinal])
        assertEquals(2, result.character.skillProficiencies[SkillType.valueOf(to).ordinal])
        assertTrue(result.summary.replaced.contains(MarkupText.skillTitle(SkillType.valueOf(from)) to MarkupText.skillTitle(SkillType.valueOf(to))))
    }

    @Test
    fun `warlock versatility swaps pact boon and arcanum spell of the same circle`() {
        val creation = create(createDraft("Колдун", 11, showOptional = true))
        val base = baseOf(creation)
        val start = LevelUpEngine.initialDraft(base).copy(showOptional = true)
        val context0 = LevelUpEngine.context(base, start, library, cache)
        assertTrue(context0.isAsiLevel)
        val pact = context0.replacements.single { it.choiceId == "class.pact" }
        val arcanum = context0.replacements.single { it.id == LevelUpEngine.REPLACE_ARCANUM_ID }
        assertEquals(pact.group, arcanum.group)
        val old = arcanum.removable.single()
        assertEquals(6, spells.first { it.id == old }.level)
        assertTrue(arcanum.candidatesFor(old).all { id -> spells.first { it.id == id }.level == 6 })
        // Арканум не меняется как обычное известное заклинание.
        assertFalse(old in context0.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }.removable)

        val newArcanum = arcanum.candidatesFor(old).first()
        val draft = fillLevelUp(base, start).copy(replacements = mapOf(arcanum.id to LevelUpReplacement(old, newArcanum)))
        val result = LevelUpEngine.build(LevelUpEngine.context(base, draft, library, cache), library)
        assertTrue(old in result.removedSpellIds)
        assertTrue(newArcanum in result.addedSpellIds)

        // Дар договора: заметка переписывается.
        val pactDraft = fillLevelUp(base, start).copy(
            replacements = mapOf(pact.id to LevelUpReplacement(pact.removable.single(), pact.candidateOptions.first().id)),
        )
        val pactResult = LevelUpEngine.build(LevelUpEngine.context(base, pactDraft, library, cache), library)
        val block = pactResult.notes.single { it.title == pact.title }.paragraphs.map { it.text.lineSequence().first() }
        assertEquals(listOf(pact.candidateOptions.first().title), block)

        // До 12-го уровня арканум не заменяется (на 7-м арканума нет, но дар — да, 8 — ASI).
        val base7 = baseOf(create(createDraft("Колдун", 7, showOptional = true)))
        val context7 = LevelUpEngine.context(base7, LevelUpEngine.initialDraft(base7).copy(showOptional = true), library, cache)
        assertTrue(context7.replacements.none { it.id == LevelUpEngine.REPLACE_ARCANUM_ID })
        assertTrue(context7.replacements.any { it.choiceId == "class.pact" })
    }

    @Test
    fun `artificer may replace one infusion on any level`() {
        val creation = create(createDraft("Изобретатель", 2))
        val base = baseOf(creation)
        val start = LevelUpEngine.initialDraft(base)
        val context0 = LevelUpEngine.context(base, start, library, cache)
        val spec = context0.replacements.single { it.choiceId == "class.infusions" }
        assertEquals(4, spec.removable.size)
        assertTrue(LevelUpStep.Replacements in LevelUpEngine.steps(context0))
        val removed = spec.removableOptions.first()
        val added = spec.candidateOptions.first()
        val draft = fillLevelUp(base, start).copy(replacements = mapOf(spec.id to LevelUpReplacement(removed.id, added.id)))
        val result = LevelUpEngine.build(LevelUpEngine.context(base, draft, library, cache), library)
        val titles = result.notes.single { it.title == spec.title }.paragraphs.map { it.text.lineSequence().first() }
        assertEquals(4, titles.size)
        assertFalse(removed.title in titles)
        assertTrue(added.title in titles)
    }

    @Test
    fun `eldritch knight school limits allow any school only for free slots`() {
        val creation = create(createDraft("Воин", 4, subclassPrefix = "Мистический рыцарь"))
        assertTrue(creation.character.subclass.startsWith("Мистический рыцарь"))
        val schools = setOf("evo", "abj")
        val byId = spells.associateBy { it.id }
        val classSpells = creation.spellIds.filter { (byId[it]?.level ?: 0) >= 1 }
        assertTrue(classSpells.all { byId.getValue(it).school in schools })

        // Все известные — из своих школ: свободное место 3-го уровня не занято, замена на любую школу.
        val base = baseOf(creation)
        val context = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base), library, cache)
        assertEquals(1, context.spells!!.anySchoolAllowance)
        val spec = context.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        assertTrue(spec.candidatesFor(spec.removable.first()).any { byId.getValue(it).school !in schools })
        assertTrue(spec.candidateSpellIds.all { byId.getValue(it).school in schools })

        // Одно известное — другой школы (свободное): его можно заменить на любую школу, остальные — только на свои.
        val offSchool = spells.first { it.level == 1 && "wizard" in it.classes && it.school !in schools }
        val knownWithFree = creation.spellIds - classSpells.first() + offSchool.id
        val freeBase = baseOf(creation.copy(spellIds = knownWithFree))
        val freeContext = LevelUpEngine.context(freeBase, LevelUpEngine.initialDraft(freeBase), library, cache)
        assertEquals(0, freeContext.spells!!.anySchoolAllowance)
        val freeSpec = freeContext.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        assertTrue(freeSpec.candidatesFor(offSchool.id).any { byId.getValue(it).school !in schools })
        val inSchool = freeSpec.removable.first { it != offSchool.id }
        assertTrue(freeSpec.candidatesFor(inSchool).all { byId.getValue(it).school in schools })
        // Новое заклинание другой школы без свободного места — нельзя (на 5-м уровне новых нет, проверяем замену).
        val badSwap = LevelUpEngine.initialDraft(freeBase).copy(
            replacements = mapOf(freeSpec.id to LevelUpReplacement(inSchool, freeSpec.candidatesFor(offSchool.id).first { byId.getValue(it).school !in schools })),
        )
        assertFalse(LevelUpEngine.canApply(LevelUpEngine.context(freeBase, badSwap, library, cache)))

        // 7 → 8: новое свободное место, новое заклинание может быть любой школы.
        val base7 = baseOf(create(createDraft("Воин", 7, subclassPrefix = "Мистический рыцарь")))
        val context7 = LevelUpEngine.context(base7, LevelUpEngine.initialDraft(base7), library, cache)
        assertEquals(2, context7.spells!!.anySchoolAllowance)
        assertTrue(context7.spells!!.spellCandidates.any { byId.getValue(it).school !in schools })
    }

    @Test
    fun `arcane trickster knows one spell of any school from level 3 and one more at 8`() {
        val schools = setOf("enc", "ill")
        val byId = spells.associateBy { it.id }
        val draft3 = createDraft("Плут", 3, subclassPrefix = "Мистический ловкач")
        val context3 = WizardEngine.context(draft3, library, cache)
        assertTrue(context3.subclass!!.title.startsWith("Мистический ловкач"))
        val plan3 = context3.spellcasting!!
        assertEquals(3, plan3.knownSpells)
        assertEquals(1, plan3.anySchoolSpells)

        // Мастер создания: одно заклинание другой школы можно, два — уже нет.
        val offSchool = spells.filter { it.level == 1 && "wizard" in it.classes && it.school !in schools }.map { it.id }
        val inSchool = spells.filter { it.level == 1 && "wizard" in it.classes && it.school in schools }.map { it.id }
        val oneOff = draft3.copy(spellIds = setOf(offSchool[0], inSchool[0], inSchool[1]))
        assertTrue(WizardEngine.issues(WizardStep.Spells, oneOff, WizardEngine.context(oneOff, library, cache))
            .none { it.kind == IssueKind.SPELLS_SCHOOL_LIMIT })
        val twoOff = draft3.copy(spellIds = setOf(offSchool[0], offSchool[1], inSchool[0]))
        assertTrue(WizardEngine.issues(WizardStep.Spells, twoOff, WizardEngine.context(twoOff, library, cache))
            .any { it.kind == IssueKind.SPELLS_SCHOOL_LIMIT && it.missing == 1 })

        // Повышение 3 → 4 при занятом свободном месте: новое заклинание — только своих школ.
        val creation = create(oneOff)
        val base = baseOf(creation)
        val context = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base), library, cache)
        assertEquals(0, context.spells!!.anySchoolAllowance)
        assertTrue(context.spells!!.spellCandidates.all { byId.getValue(it).school in schools })
        // Свободное заклинание меняется на любую школу, своих школ — только на свои.
        val spec = context.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        assertTrue(spec.candidatesFor(offSchool[0]).any { byId.getValue(it).school !in schools })
        assertTrue(spec.candidatesFor(inSchool[0]).all { byId.getValue(it).school in schools })

        // 7 → 8: второе свободное место.
        val base7 = baseOf(create(createDraft("Плут", 7, subclassPrefix = "Мистический ловкач")))
        val context7 = LevelUpEngine.context(base7, LevelUpEngine.initialDraft(base7), library, cache)
        assertEquals(2, context7.spells!!.anySchoolAllowance)
        assertTrue(context7.spells!!.spellCandidates.any { byId.getValue(it).school !in schools })
    }

    @Test
    fun `deleted resource that changes on level up asks whether to restore it`() {
        val creation = create(createDraft("Варвар", 2))
        val direct = create(createDraft("Варвар", 3)).character.resources
        // Ярость: 2 → 3 на 3-м уровне.
        val rage = creation.character.resources.first { old ->
            direct.any { it.name == old.name && it.maximum != old.maximum }
        }
        val expected = direct.first { it.name == rage.name }.maximum
        val deleted = creation.copy(character = creation.character.copy(resources = creation.character.resources - rage))
        val base = baseOf(deleted)
        val start = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val context = LevelUpEngine.context(base, start, library, cache)
        val missing = context.missingResources.single()
        assertEquals(rage.name, missing.name)
        assertEquals(rage.maximum, missing.oldMaximum)
        assertEquals(expected, missing.newMaximum)
        // Вопрос — незавершённость итога, но не блокирующая.
        val issue = LevelUpEngine.issues(LevelUpStep.Summary, context).single()
        assertEquals(LevelUpIssueKind.RESOURCE_DECISION, issue.kind)
        assertFalse(issue.blocking)
        assertTrue(LevelUpEngine.canApply(context))
        assertTrue(LevelUpEngine.build(context, library).character.resources.none { it.name == rage.name })

        val restore = LevelUpEngine.context(base, start.copy(resourceDecisions = mapOf(missing.key to true)), library, cache)
        assertTrue(LevelUpEngine.issues(LevelUpStep.Summary, restore).isEmpty())
        val restored = LevelUpEngine.build(restore, library)
        val back = restored.character.resources.single { it.name == rage.name }
        assertEquals(expected, back.maximum)
        assertEquals(expected, back.current)
        assertTrue(restored.summary.resources.any { it.name == rage.name && it.restored })

        val skip = LevelUpEngine.context(base, start.copy(resourceDecisions = mapOf(missing.key to false)), library, cache)
        assertTrue(LevelUpEngine.issues(LevelUpStep.Summary, skip).isEmpty())
        assertTrue(LevelUpEngine.build(skip, library).character.resources.none { it.name == rage.name })

        // Ресурсы на месте — вопроса нет.
        val intact = baseOf(creation)
        assertTrue(LevelUpEngine.context(intact, LevelUpEngine.initialDraft(intact), library, cache).missingResources.isEmpty())
    }

    @Test
    fun `unofficial classes are detected automatically`() {
        val official = classes.first { it.name == "Воин" }
        assertFalse(cache.characterClass(official).extended)
        assertTrue(classes.none { UnofficialContent.isUnofficialClass(it) })
        assertTrue(UnofficialContent.isUnofficialClass(official.copy(source = "")))
        assertTrue(UnofficialContent.isUnofficialClass(official.copy(source = "https://dnd.su/homebrew/classes/1-x/")))
        assertTrue(UnofficialContent.isUnofficialClass(official.copy(book = "Unearthed Arcana")))
    }

    // region Копия магического предмета

    private fun copyOf(choice: ChoiceSpec, itemName: String) =
        choice.options.single { it.title == "Копия магического предмета: $itemName" }

    /** Изобретатель 6-го уровня с двумя копиями предметов среди инфузий. */
    private fun artificerWithCopies(): Pair<CharacterDraft, List<ChoiceOption>> {
        var draft = createDraft("Изобретатель", 6)
        val context = WizardEngine.context(draft, library, cache)
        val infusions = context.choices.single { it.id == WizardEngine.INFUSIONS_CHOICE_ID }
        val bag = copyOf(infusions, "Сумка хранения")
        val cloak = copyOf(infusions, "Эльфийский плащ")
        val others = infusions.options.filter { !ReplicateInfusion.isCopy(it.id) && !it.extended }.take(infusions.count - 2)
        draft = draft.copy(selections = draft.selections + (infusions.id to (others.map { it.id } + bag.id + cloak.id)))
        return draft to listOf(bag, cloak)
    }

    @Test
    fun `replicate magic item infusion expands into copies by item`() {
        val (draft, copies) = artificerWithCopies()
        val context = WizardEngine.context(draft, library, cache)
        val infusions = context.choices.single { it.id == WizardEngine.INFUSIONS_CHOICE_ID }
        // Сама инфузия без предмета выбрать нельзя — есть только копии.
        assertTrue(infusions.options.none { it.title == "Копия магического предмета" })
        val allCopies = infusions.options.filter { ReplicateInfusion.isCopy(it.id) }
        assertTrue(allCopies.size > 40)
        assertTrue(allCopies.all { it.group == "Копия магического предмета" && it.groupText.isNotBlank() })
        assertTrue(allCopies.none { it.groupText.contains("|") })
        // Описание — ссылка на предмет библиотеки и строка предмета; полный текст — в окне предмета.
        val bag = copies[0]
        assertTrue(bag.text, bag.text.contains("Сумка хранения [bag of holding]"))
        assertTrue(bag.text.contains("настройка не требуется"))
        assertTrue(bag.text, !bag.text.contains("500 фунтов"))
        val links = com.example.spellbook.data.LibraryLinkIndex(spells, magicItems).find(bag.text)
        assertEquals("Сумка хранения", (links.single() as com.example.spellbook.data.LibraryLink.ToItem).item.name)
        assertEquals(2, bag.requiredLevel)
        // Предмет 10-го уровня изобретателя на 6-м недоступен.
        val brooch = copyOf(infusions, "Брошь защиты")
        assertEquals(10, brooch.requiredLevel)
        assertEquals(listOf(OptionRequirements.Unmet.Level(10)), OptionRequirements.unmet(brooch, draft, context, spells))
        assertTrue(OptionRequirements.unmet(bag, draft, context, spells).isEmpty())
        // В заметках — отдельный абзац на каждую копию с описанием предмета.
        val creation = create(draft)
        val block = creation.notes.single { it.title == "Инфузии" }
        val bagNote = block.paragraphs.single { it.text.startsWith("Копия магического предмета: Сумка хранения") }
        // В заметке название предмета — ссылка на окно предмета.
        assertTrue(bagNote.text, bagNote.text.contains("Сумка хранения [bag of holding]"))
        assertTrue(block.paragraphs.any { it.text.startsWith("Копия магического предмета: Эльфийский плащ") })
    }

    @Test
    fun `taken copies are restored on level up and excluded from new picks`() {
        val (draft, copies) = artificerWithCopies()
        val base = baseOf(create(draft))
        val context = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base), library, cache)
        val restored = context.restoredSelections[WizardEngine.INFUSIONS_CHOICE_ID].orEmpty()
        assertTrue(copies.all { it.id in restored })
        assertEquals(draft.selections.getValue(WizardEngine.INFUSIONS_CHOICE_ID).toSet(), restored.toSet())
        // Замена инфузии: можно заменить конкретную копию, взятые копии в кандидатах не повторяются.
        val spec = context.replacements.single { it.choiceId == WizardEngine.INFUSIONS_CHOICE_ID }
        assertTrue(copies.all { copy -> spec.removableOptions.any { it.id == copy.id } })
        assertTrue(spec.candidateOptions.none { it.id in restored })
        val newCopy = spec.candidateOptions.first { ReplicateInfusion.isCopy(it.id) && (it.requiredLevel ?: 0) <= 7 }
        val replaced = LevelUpEngine.initialDraft(base).copy(
            replacements = mapOf(spec.id to LevelUpReplacement(copies[0].id, newCopy.id)),
        )
        val result = LevelUpEngine.build(LevelUpEngine.context(base, replaced, library, cache), library)
        val titles = result.notes.single { it.title == "Инфузии" }.paragraphs.map { it.text.lineSequence().first() }
        assertFalse(copies[0].title in titles)
        assertTrue(copies[1].title in titles)
        assertTrue(newCopy.title in titles)

        // На 10-м уровне новые инфузии — без уже взятых копий.
        val nine = createDraft("Изобретатель", 9)
        val nineContext = WizardEngine.context(nine, library, cache)
        val nineInfusions = nineContext.choices.single { it.id == WizardEngine.INFUSIONS_CHOICE_ID }
        val bag = copyOf(nineInfusions, "Сумка хранения")
        val nineTaken = nine.selections.getValue(nineInfusions.id).filter { it != bag.id }.take(nineInfusions.count - 1)
        val withBag = nine.copy(selections = nine.selections + (nineInfusions.id to (nineTaken + bag.id)))
        val nineBase = baseOf(create(withBag))
        val tenth = LevelUpEngine.context(nineBase, LevelUpEngine.initialDraft(nineBase), library, cache)
        val newInfusions = tenth.choices.single { it.id == WizardEngine.INFUSIONS_CHOICE_ID }
        assertEquals(2, newInfusions.count)
        assertTrue(newInfusions.options.none { it.id == bag.id })
        assertTrue(newInfusions.options.any { it.title == "Копия магического предмета: Брошь защиты" })
    }

    // endregion

    // region Переподготовка

    @Test
    fun `cleric may reprepare spells within the new limit`() {
        val creation = create(createDraft("Жрец", 2, subclassPrefix = "Домен жизни"))
        val base = baseOf(creation)
        val start = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val context = LevelUpEngine.context(base, start, library, cache)
        val prep = context.preparation!!
        assertTrue(LevelUpStep.Prepare in LevelUpEngine.steps(context))
        assertEquals(prep.oldLimit + 1, prep.limit)
        assertEquals(prep.current, prep.selected)
        assertTrue(prep.current.isNotEmpty())
        // Всегда подготовленные не занимают лимит и не входят в текущие.
        assertTrue(prep.alwaysPrepared.isNotEmpty())
        assertTrue(prep.current.none { it in prep.alwaysPrepared })
        assertTrue(prep.candidates.any { id -> spells.first { it.id == id }.level == 2 })
        // Лимит вырос — напоминание, не блокирует.
        val reminder = LevelUpEngine.issues(LevelUpStep.Prepare, context)
        assertEquals(listOf(LevelUpIssueKind.PREPARED_FREE), reminder.map { it.kind })
        assertFalse(reminder.single().blocking)
        assertTrue(LevelUpEngine.canApply(context))

        // Замена подготовленного и занятие нового места заклинанием 2-го круга.
        val dropped = prep.current.first()
        // Занимаем все свободные места и место снятого заклинания.
        val added = prep.candidates.filter { it !in prep.current }.take(prep.limit - prep.current.size + 1)
        val prepared = prep.current - dropped + added
        val draft = start.copy(preparedIds = prepared)
        val changed = LevelUpEngine.context(base, draft, library, cache)
        assertEquals(prepared, changed.preparation!!.selected)
        assertTrue(LevelUpEngine.issues(LevelUpStep.Prepare, changed).isEmpty())
        val result = LevelUpEngine.build(changed, library)
        assertTrue(dropped in result.unpreparedSpellIds)
        assertTrue(result.preparedSpellIds.containsAll(added))
        assertEquals(added.toSet(), result.summary.preparedAddedIds.toSet())
        assertEquals(listOf(dropped), result.summary.preparedRemovedIds)

        // Превышение лимита блокирует.
        val over = start.copy(preparedIds = prep.candidates.take(prep.limit + 1).toSet())
        val overContext = LevelUpEngine.context(base, over, library, cache)
        assertTrue(LevelUpEngine.issues(LevelUpStep.Prepare, overContext).any { it.kind == LevelUpIssueKind.PREPARED_OVER && it.blocking })
        assertFalse(LevelUpEngine.canApply(overContext))
    }

    @Test
    fun `wizard may prepare the new spells of the book`() {
        val base = baseOf(create(createDraft("Волшебник", 2)))
        val start = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val context = LevelUpEngine.context(base, start, library, cache)
        val prep = context.preparation!!
        assertTrue(prep.spellbook)
        // Кандидаты — книга вместе с двумя новыми записями.
        assertTrue(prep.candidates.containsAll(start.spellIds))
        assertTrue(prep.candidates.all { id -> id in base.knownSpellIds || id in start.spellIds })
        val newSpell = start.spellIds.first()
        val draft = start.copy(preparedIds = prep.selected + newSpell)
        val result = LevelUpEngine.build(LevelUpEngine.context(base, draft, library, cache), library)
        assertTrue(newSpell in result.addedSpellIds)
        assertTrue(newSpell in result.preparedSpellIds)
    }

    @Test
    fun `artificer specialist spells are reported as new always prepared`() {
        val base = baseOf(create(createDraft("Изобретатель", 4, subclassPrefix = "Алхимик")))
        val context = LevelUpEngine.context(base, fillLevelUp(base, LevelUpEngine.initialDraft(base)), library, cache)
        assertEquals(2, context.newAlwaysPrepared.size)
        assertTrue(context.newAlwaysPrepared.all { id -> spells.first { it.id == id }.level == 2 })
        assertTrue(context.preparation!!.alwaysPrepared.containsAll(context.newAlwaysPrepared))
        val result = LevelUpEngine.build(context, library)
        assertEquals(context.newAlwaysPrepared.toSet(), result.summary.newAlwaysPreparedIds.toSet())
        assertTrue(result.preparedSpellIds.containsAll(context.newAlwaysPrepared))
        // На уровне без новых заклинаний специальности — пусто.
        val fifth = baseOf(create(createDraft("Изобретатель", 5, subclassPrefix = "Алхимик")))
        val sixth = LevelUpEngine.context(fifth, fillLevelUp(fifth, LevelUpEngine.initialDraft(fifth)), library, cache)
        assertTrue(sixth.newAlwaysPrepared.isEmpty())
    }

    // endregion

    @Test
    fun `a spell or option taken as new cannot also be taken as a replacement`() {
        val base = baseOf(create(createDraft("Колдун", 4)))
        val draft = fillLevelUp(base, LevelUpEngine.initialDraft(base))
        val newSpell = draft.spellIds.single()
        val newInvocation = draft.selections.getValue("class.invocations").single()
        val context = LevelUpEngine.context(base, draft, library, cache)
        val spellSpec = context.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        val invocationSpec = context.replacements.single { it.id == LevelUpEngine.REPLACE_OPTION_PREFIX + "class.invocations" }
        // Взятое новым не предлагается заменой.
        assertFalse(newSpell in spellSpec.candidateSpellIds)
        assertTrue(spellSpec.candidatesByRemovable.values.none { newSpell in it })
        assertTrue(invocationSpec.candidateOptions.none { it.id == newInvocation })

        // И наоборот: взятое заменой не предлагается новым.
        val start = LevelUpEngine.initialDraft(base)
        val bare = LevelUpEngine.context(base, start, library, cache)
        val bareSpell = bare.replacements.single { it.id == LevelUpEngine.REPLACE_SPELL_ID }
        val bareInvocation = bare.replacements.single { it.id == LevelUpEngine.REPLACE_OPTION_PREFIX + "class.invocations" }
        val swapped = start.copy(
            replacements = mapOf(
                bareSpell.id to LevelUpReplacement(bareSpell.removable.first(), bareSpell.candidateSpellIds.first()),
                bareInvocation.id to LevelUpReplacement(bareInvocation.removable.first(), bareInvocation.candidateOptions.first().id),
            ),
        )
        val swappedContext = LevelUpEngine.context(base, swapped, library, cache)
        assertFalse(bareSpell.candidateSpellIds.first() in swappedContext.spells!!.spellCandidates)
        assertTrue(
            swappedContext.choices.single { it.id == "class.invocations" }.options
                .none { it.id == bareInvocation.candidateOptions.first().id },
        )

        // Устаревший черновик с повтором блокируется.
        val duplicate = draft.copy(
            replacements = mapOf(
                spellSpec.id to LevelUpReplacement(spellSpec.removable.first(), newSpell),
                invocationSpec.id to LevelUpReplacement(invocationSpec.removable.first(), newInvocation),
            ),
        )
        val duplicateContext = LevelUpEngine.context(base, duplicate, library, cache)
        assertEquals(setOf(spellSpec.id, invocationSpec.id), duplicateContext.duplicatePicks.keys)
        assertTrue(
            LevelUpEngine.allIssues(duplicateContext).flatMap { it.second }.count { it.kind == LevelUpIssueKind.DUPLICATE_PICK } == 2,
        )
        assertFalse(LevelUpEngine.canApply(duplicateContext))
    }

    // region Исключение заметок и ресурсов

    private fun proposalOf(draft: CharacterDraft): ContentProposal =
        CharacterAssembler.proposal(draft, WizardEngine.context(draft, library, cache), spells)

    @Test
    fun `wizard excludes a background block, a class paragraph and a resource and remembers them`() {
        val draft = createDraft("Варвар", 2)
        val proposal = proposalOf(draft)
        val background = proposal.blocks.single { it.title.startsWith("Предыстория:") }
        val classBlock = proposal.blocks.single { it.title.startsWith("Класс:") }
        val paragraph = classBlock.paragraphs.first()
        val rage = proposal.resources.first { it.name.startsWith("Ярост") }
        val excluded = setOf(background.key, paragraph.key, rage.key)
        val creation = create(draft.copy(excludedContent = excluded))

        assertTrue(creation.notes.none { it.title == background.title })
        val classNote = creation.notes.single { it.title == classBlock.title }
        assertFalse(paragraph.heading in headings(listOf(classNote), "Класс:"))
        assertEquals(classBlock.paragraphs.size - 1, classNote.paragraphs.size)
        assertTrue(creation.character.resources.none { it.name == rage.name })
        assertEquals(excluded, creation.character.excludedContent.toSet())
    }

    @Test
    fun `blocks of chosen options cannot be excluded`() {
        val draft = createDraft("Воин", 1)
        val locked = proposalOf(draft).blocks.first { it.locked }
        val creation = create(draft.copy(excludedContent = setOf(locked.key) + locked.paragraphs.map { it.key }))
        assertTrue(creation.notes.any { it.title == locked.title })
        assertTrue(creation.character.excludedContent.isEmpty())
    }

    @Test
    fun `level up respects remembered exclusions and can include them back`() {
        // Исключённая «Ярость» (2 → 3 на 3-м уровне) не спрашивается и не добавляется.
        val rageDraft = createDraft("Варвар", 2)
        val rage = proposalOf(rageDraft).resources.first { it.name.startsWith("Ярост") }
        val rageBase = baseOf(create(rageDraft.copy(excludedContent = setOf(rage.key))))
        val rageStart = fillLevelUp(rageBase, LevelUpEngine.initialDraft(rageBase))
        val rageContext = LevelUpEngine.context(rageBase, rageStart, library, cache)
        assertTrue(rageContext.missingResources.isEmpty())
        val offered = rageContext.proposal.resources.single { it.key == rage.key }
        assertTrue(offered.excludedEarlier)
        val kept = LevelUpEngine.build(rageContext, library)
        assertTrue(kept.character.resources.none { it.name.startsWith("Ярост") })
        assertTrue(rage.key in kept.character.excludedContent)
        // Включённая обратно — с полным максимумом нового уровня, и исключение забывается.
        val back = LevelUpEngine.context(rageBase, LevelUpEngine.toggleExcluded(rageContext, rage.key), library, cache)
        val restored = LevelUpEngine.build(back, library)
        val expected = create(createDraft("Варвар", 3)).character.resources.first { it.name.startsWith("Ярост") }.maximum
        assertEquals(expected, restored.character.resources.single { it.name.startsWith("Ярост") }.maximum)
        assertFalse(rage.key in restored.character.excludedContent)

        // Исключённый целиком блок класса не создаётся заново ради новых умений 5-го уровня.
        val classDraft = createDraft("Варвар", 4)
        val classBlock = proposalOf(classDraft).blocks.single { it.title.startsWith("Класс:") }
        val classBase = baseOf(create(classDraft.copy(excludedContent = setOf(classBlock.key))))
        assertTrue(classBase.notes.none { it.title == classBlock.title })
        val classStart = fillLevelUp(classBase, LevelUpEngine.initialDraft(classBase))
        val classContext = LevelUpEngine.context(classBase, classStart, library, cache)
        val proposed = classContext.proposal.blocks.single { it.title == classBlock.title }
        assertTrue(proposed.excludedEarlier)
        assertTrue(proposed.paragraphs.isNotEmpty())
        assertTrue(LevelUpEngine.build(classContext, library).notes.none { it.title == classBlock.title })
        val included = LevelUpEngine.context(classBase, LevelUpEngine.toggleExcluded(classContext, classBlock.key), library, cache)
        val result = LevelUpEngine.build(included, library)
        val note = result.notes.single { it.title == classBlock.title }
        assertEquals(proposed.paragraphs.map { it.heading }.toSet(), headings(listOf(note), "Класс:"))
        assertFalse(classBlock.key in result.character.excludedContent)
    }

    // endregion

    @Test
    fun `max level cannot be raised`() {
        val character = Character(level = 20, classes = listOf("FIGHTER:20"))
        val base = LevelUpEngine.base(character, emptyList(), emptySet(), emptySet(), emptyList(), library, cache)
        val context = LevelUpEngine.context(base, LevelUpEngine.initialDraft(base), library, cache)
        assertFalse(LevelUpEngine.canApply(context))
        assertNull(context.newClass)
    }
}
