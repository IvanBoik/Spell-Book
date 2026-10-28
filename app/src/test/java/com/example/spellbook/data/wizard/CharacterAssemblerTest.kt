package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.effectiveArmorClass
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.testSpell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Сквозная сборка персонажа из черновика мастера на настоящих библиотеках. */
class CharacterAssemblerTest {

    private val library by lazy {
        WizardLibrary(
            races = RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
                .mapIndexed { index, race -> race.copy(id = "r$index") },
            classes = ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "c$index") },
            backgrounds = BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "b$index") },
        )
    }
    private val cache = WizardRulesCache()

    private fun raceId(name: String) = library.races.first { it.name == name && it.category == "Основные расы" }.id
    private fun classId(name: String) = library.classes.first { it.name == name }.id
    private fun backgroundId(name: String) = library.backgrounds.first { it.name == name }.id

    @Test
    fun `battle master hill dwarf soldier`() {
        var draft = CharacterDraft(name = "Торин", level = 3)
        draft = WizardEngine.selectRace(draft, raceId("Дварф"))
        var context = WizardEngine.context(draft, library, cache)
        val hill = context.race!!.subraces.first { it.title.startsWith("Холмов") }
        draft = WizardEngine.selectSubrace(draft, hill.id)
        draft = WizardEngine.selectClass(draft, classId("Воин"))
        context = WizardEngine.context(draft, library, cache)
        val master = context.characterClass!!.subclasses.first { it.title == "Мастер боевых искусств" }
        draft = WizardEngine.selectSubclass(draft, master.id)
        draft = WizardEngine.selectBackground(draft, backgroundId("Солдат"))
        context = WizardEngine.context(draft, library, cache)

        val maneuvers = context.choices.first { it.id == "subclass.maneuvers" }
        val style = context.choices.first { it.id == "class.fighting_style" }
        draft = draft.copy(
            selections = draft.selections +
                ("class.skills" to listOf(SkillType.ATHLETICS.name, SkillType.PERCEPTION.name)) +
                (maneuvers.id to maneuvers.options.take(3).map { it.id }) +
                (style.id to listOf(style.options.first().id)),
            poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap(),
        )
        context = WizardEngine.context(draft, library, cache)

        // Шаг боевых приёмов идёт сразу после класса.
        val steps = WizardEngine.steps(draft, context)
        val classIndex = steps.indexOf(WizardStep.Class)
        assertTrue(steps[classIndex + 1] is WizardStep.Choice)

        val creation = CharacterAssembler.assemble(draft, context, emptyList())
        val character = creation.character
        assertEquals(3, character.level)
        assertEquals("Дварф", character.race)
        assertEquals("Мастер боевых искусств", character.subclass)
        // Стандартный набор: Сила 15, Телосложение 13 + 2 (дварф).
        assertEquals(15, character.abilityScores[AbilityType.STRENGTH.ordinal])
        assertEquals(15, character.abilityScores[AbilityType.CONSTITUTION.ordinal])
        assertEquals(25, character.speed)
        // Хиты: 10 + 2×(6) + 3×модификатор Тел (+2) + холмовой дварф 3.
        assertEquals(10 + 6 + 6 + 2 * 3 + 3, character.maxHp)
        assertTrue(character.resources.any { it.name.startsWith("Кости превосходства") && it.maximum == 4 })
        assertTrue(character.resources.any { it.name == "Второе дыхание" })
        assertTrue(character.skillProficiencies[SkillType.ATHLETICS.ordinal] == 1)
        // Выбранные приёмы и стиль — отдельными заметками.
        assertEquals(3, creation.notes.first { it.title == "Боевые приёмы" }.paragraphs.size)
        assertEquals(1, creation.notes.first { it.title == style.title }.paragraphs.size)
        // В заметках класса — только настоящие способности, без служебных умений и выбранного стиля.
        val classTitles = creation.notes.first { it.title.startsWith("Класс") }.paragraphs.map { it.text.lineSequence().first() }
        assertTrue(classTitles.any { it.startsWith("Второе дыхание") })
        assertTrue(classTitles.none { it.startsWith("Боевой стиль") || it.startsWith("Воинский архетип") })
        // Ресурс — текст умения без дописанного «Восстанавливается…».
        assertTrue(character.resources.none { it.description.contains("Восстанавливается после") })
        assertTrue(creation.items.isNotEmpty())
    }

    @Test
    fun `wizard prepares from spellbook`() {
        var draft = CharacterDraft(name = "Эльминстер", level = 1)
        draft = WizardEngine.selectClass(draft, classId("Волшебник"))
        val spells = listOf(
            testSpell(id = "s1", name = "Волшебная стрела", level = 1, classes = listOf("wizard")),
            testSpell(id = "s2", name = "Щит", level = 1, classes = listOf("wizard")),
            testSpell(id = "c1", name = "Огненный снаряд", level = 0, classes = listOf("wizard")),
        )
        draft = draft.copy(spellIds = setOf("s1", "s2"), preparedIds = setOf("s1"), cantripIds = setOf("c1"))
        val context = WizardEngine.context(draft, library, cache)
        val plan = context.spellcasting!!
        assertEquals(3, plan.cantrips)
        assertEquals(6, plan.spellbookSpells)
        assertTrue(WizardEngine.steps(draft, context).contains(WizardStep.Spells))
        val creation = CharacterAssembler.assemble(draft, context, spells)
        assertEquals(setOf("s1", "s2", "c1"), creation.spellIds)
        assertEquals(setOf("s1"), creation.preparedIds)
        assertTrue(creation.character.canPrepareSpells)
        assertEquals(mapOf(1 to 2), creation.character.spellSlots)
    }

    @Test
    fun `racial bonus distribution respects rules`() {
        var draft = CharacterDraft(name = "Лира")
        draft = WizardEngine.selectRace(draft, raceId("Полуэльф"))
        draft = draft.copy(bonusTargets = listOf(AbilityType.DEXTERITY, AbilityType.WISDOM))
        val context = WizardEngine.context(draft, library, cache)
        val bonuses = WizardEngine.racialBonuses(draft, context)
        assertEquals(mapOf(AbilityType.CHARISMA to 2, AbilityType.DEXTERITY to 1, AbilityType.WISDOM to 1), bonuses)
        // В свободном режиме фиксированный +2 тоже переносится.
        val free = draft.copy(bonusMode = BonusMode.FREE, bonusTargets = listOf(AbilityType.STRENGTH, AbilityType.DEXTERITY, AbilityType.CONSTITUTION))
        assertEquals(
            mapOf(AbilityType.STRENGTH to 2, AbilityType.DEXTERITY to 1, AbilityType.CONSTITUTION to 1),
            WizardEngine.racialBonuses(free, WizardEngine.context(free, library, cache)),
        )
    }

    @Test
    fun `ruined background grants feat choice`() {
        val ruined = BackgroundRulesParser.parse(library.backgrounds.first { it.name == "Разорённый" })
        assertEquals(listOf("Бдительный", "Одарённый", "Крепкий"), ruined.featOptions)
        val draft = WizardEngine.selectBackground(CharacterDraft(name = "Икс"), backgroundId("Разорённый"))
        val context = WizardEngine.context(draft, library, cache)
        val slot = WizardEngine.featSlots(draft, context).single()
        assertEquals(ChoiceSource.BACKGROUND, slot.source)
        assertTrue(WizardEngine.steps(draft, context).contains(WizardStep.Feats))
        // Обычная предыстория черты не даёт.
        assertTrue(BackgroundRulesParser.parse(library.backgrounds.first { it.name == "Солдат" }).featOptions.isEmpty())
    }

    @Test
    fun `fighter armor and dual wielder feat give armor class parts`() {
        var draft = CharacterDraft(name = "Броня", level = 1)
        draft = WizardEngine.selectClass(draft, classId("Воин"))
        // Стандартный набор по порядку: Ловкость 14.
        draft = draft.copy(poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap())
        val context = WizardEngine.context(draft, library, cache)
        val dualWielder = com.example.spellbook.data.model.Feat(
            id = "dw",
            name = "Использование двух оружий",
            description = "- Вы получаете бонус +1 к КД, когда держите в каждой руке по рукопашному оружию.",
        )
        val creation = CharacterAssembler.assemble(draft.copy(featSlots = mapOf("race.0" to "dw")), context, emptyList(), listOf(dualWielder))
        val character = creation.character
        // Первый вариант воина — кольчуга (16), плюс +1 от черты.
        assertTrue(character.armorClassParts.any { it.name == "Кольчуга" && it.enabled })
        val featPart = character.armorClassParts.first { it.id == "feat:dw" }
        assertEquals("1", featPart.formula)
        assertTrue(featPart.description.startsWith("Когда держите"))
        // По умолчанию взяты «воинское оружие и щит»: 16 + 2 (щит) + 1 (черта).
        assertTrue(character.armorClassParts.any { it.name == "Щит" && it.formula == "2" })
        assertEquals(19, character.effectiveArmorClass)
        // Отключённый бонус не учитывается.
        val withoutBonus = character.copy(armorClassParts = character.armorClassParts.map { if (it.id == "feat:dw") it.copy(enabled = false) else it })
        assertEquals(18, withoutBonus.effectiveArmorClass)
        assertTrue(creation.items.any { it.name == "Кольчуга" && it.description.contains("КД 16") })
    }

    @Test
    fun `medium armor formula caps dexterity`() {
        val character = com.example.spellbook.data.model.Character(
            abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 18),
            armorClassParts = listOf(
                com.example.spellbook.data.model.StatPart(
                    name = "Чешуйчатый доспех",
                    formula = "14 + min([dex], 2)",
                    kind = com.example.spellbook.data.model.StatPartKind.BASE,
                ),
            ),
        )
        assertEquals(16, character.effectiveArmorClass)
    }

    @Test
    fun `tool choices come from catalog`() {
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Бард"), classId("Бард"))
        val context = WizardEngine.context(draft, library, cache)
        val tools = context.choices.first { it.kind == ChoiceKind.TOOL }
        assertTrue(tools.options.isNotEmpty())
        assertTrue(tools.options.all { ToolCatalog.byId(it.id)?.category == ToolCategory.MUSICAL })
    }

    @Test
    fun `cleric notes skip meta features and spells come after abilities`() {
        var draft = CharacterDraft(name = "Жрица", level = 1)
        draft = WizardEngine.selectClass(draft, classId("Жрец"))
        var context = WizardEngine.context(draft, library, cache)
        val storm = context.characterClass!!.subclasses.first { it.title == "Домен бури" }
        draft = WizardEngine.selectSubclass(draft, storm.id)
        context = WizardEngine.context(draft, library, cache)
        // Бонусное владение домена бури — в владениях, а не в заметках.
        assertTrue(com.example.spellbook.data.model.ArmorProficiency.HEAVY in context.classGrants.armor)
        val steps = WizardEngine.steps(draft, context)
        assertTrue(steps.indexOf(WizardStep.Spells) > steps.indexOf(WizardStep.Abilities))
        val creation = CharacterAssembler.assemble(draft, context, emptyList())
        val titles = creation.notes.flatMap { block -> block.paragraphs.map { it.text.lineSequence().first() } }
        listOf("Использование заклинаний", "Дополнительные заклинания", "Божественный домен", "Заклинания домена", "Бонусное владение")
            .forEach { meta -> assertTrue(meta, titles.none { it.startsWith(meta) }) }
        assertTrue("HEAVY" in creation.character.armorProficiencies)
    }

    @Test
    fun `languages are chips without known ones and fixed skills are visible`() {
        var draft = WizardEngine.selectRace(CharacterDraft(name = "Леголас"), raceId("Эльф"))
        var context = WizardEngine.context(draft, library, cache)
        val high = context.race!!.subraces.first { it.title == "Высший эльф" }
        draft = WizardEngine.selectSubrace(draft, high.id)
        context = WizardEngine.context(draft, library, cache)
        val language = context.choices.first { it.kind == ChoiceKind.LANGUAGE }
        assertTrue(language.options.isNotEmpty())
        val known = WizardEngine.knownLanguages(draft, context)
        assertTrue("общий" in known && "эльфийский" in known)
        // «Обострённые чувства» — фиксированный навык и не заметка.
        assertTrue(SkillType.PERCEPTION in WizardEngine.fixedSkills(context))
        val creation = CharacterAssembler.assemble(draft, context, emptyList())
        val raceNotes = creation.notes.first { it.title.startsWith("Раса") }.paragraphs.map { it.text.lineSequence().first() }
        assertTrue(raceNotes.none { it.startsWith("Обострённые чувства") || it == "Языки" })
        assertTrue(raceNotes.any { it.startsWith("Наследие фей") })
    }

    @Test
    fun `choices are limited to what the rules allow`() {
        // «Первобытное знание» варвара (Tasha) — только навыки варвара 1-го уровня.
        var draft = CharacterDraft(name = "Конан", level = 3, showOptional = true)
        draft = WizardEngine.selectClass(draft, classId("Варвар"))
        var context = WizardEngine.context(draft, library, cache)
        val primal = context.choices.first { it.title.startsWith("Первобытное знание") }
        assertEquals(
            context.characterClass!!.skillChoice!!.options.map { it.id }.toSet(),
            primal.options.map { it.id }.toSet(),
        )

        // Сатир: два навыка сразу, инструмент — только из музыкальных.
        draft = WizardEngine.selectRace(CharacterDraft(name = "Пан"), library.races.first { it.name == "Сатир" }.id)
        context = WizardEngine.context(draft, library, cache)
        assertTrue(WizardEngine.fixedSkills(context).containsAll(setOf(SkillType.PERFORMANCE, SkillType.PERSUASION)))
        assertTrue(context.choices.none { it.kind == ChoiceKind.SKILL })
        val tool = context.choices.single { it.kind == ChoiceKind.TOOL }
        assertTrue(tool.options.all { ToolCatalog.byId(it.id)?.category == ToolCategory.MUSICAL })

        // Языки-альтернативы предыстории: только названные.
        val lost = BackgroundRulesParser.parse(library.backgrounds.first { it.name == "Потерявшийся в царстве фей" })
        assertEquals(
            listOf("Эльфийский", "Гномий", "Гоблинский", "Сильван"),
            lost.choices.first { it.kind == ChoiceKind.LANGUAGE }.options.map { it.title },
        )
    }

    @Test
    fun `dwarf tabs keep traits apart`() {
        val dwarf = RaceRulesParser.parse(library.races.first { it.name == "Дварф" && it.category == "Основные расы" })
        val hill = dwarf.subraces.first { it.title.startsWith("Холмов") }
        // Инструмент на выбор — своей особенностью, языки без выбора.
        assertTrue(hill.choices.any { it.kind == ChoiceKind.TOOL && it.title.startsWith("Владение инструментами") })
        assertTrue(hill.choices.none { it.kind == ChoiceKind.LANGUAGE })
        assertEquals(1, hill.grants.hpPerLevel)
    }

    @Test
    fun `invocation requirements are checked`() {
        var draft = CharacterDraft(name = "Варлок", level = 5)
        draft = WizardEngine.selectClass(draft, classId("Колдун"))
        val context = WizardEngine.context(draft, library, cache)
        val invocations = context.choices.first { it.id == "class.invocations" }
        // Варианты выше уровня видны, но недоступны.
        val high = invocations.options.first { (it.requiredLevel ?: 0) > 5 }
        assertTrue(OptionRequirements.unmet(high, draft, context, emptyList()).any { it is OptionRequirements.Unmet.Level })
        // Воззвание договора недоступно, пока договор не выбран.
        val bladeOnly = invocations.options.first { it.requirement.contains("Договор клинка") && (it.requiredLevel ?: 0) <= 5 }
        assertTrue(OptionRequirements.unmet(bladeOnly, draft, context, emptyList()).any { it is OptionRequirements.Unmet.Feature })
        val pact = context.choices.first { it.id == "class.pact" }
        val blade = pact.options.first { it.title.contains("клинка", ignoreCase = true) }
        val withPact = draft.copy(selections = draft.selections + (pact.id to listOf(blade.id)))
        val withPactContext = WizardEngine.context(withPact, library, cache)
        assertTrue(OptionRequirements.unmet(bladeOnly, withPact, withPactContext, emptyList()).isEmpty())
    }

    @Test
    fun `point buy costs`() {
        val draft = CharacterDraft(
            abilityMethod = AbilityMethod.POINT_BUY,
            baseScores = mapOf(AbilityType.STRENGTH to 15, AbilityType.DEXTERITY to 15, AbilityType.CONSTITUTION to 15),
        )
        assertEquals(27, WizardEngine.pointBuySpent(draft))
    }
}
