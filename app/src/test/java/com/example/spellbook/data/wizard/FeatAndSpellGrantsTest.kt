package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.SkillType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сценарии из отзыва пользователя: повторные владения, заклинания архетипов,
 * ложные заклинания рас и эффекты черт.
 */
class FeatAndSpellGrantsTest {

    private val races by lazy { RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText()) }
    private val classes by lazy { ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText()) }
    private val backgrounds by lazy {
        BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
    }
    private val feats by lazy {
        val json = JSONArray(File("src/main/assets/feats-library.json").readText())
        (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            Feat(id = "f$index", name = item.getString("name"), description = item.getString("description"), book = item.optString("book"))
        }
    }

    private fun feat(name: String) = feats.first { it.name == name }

    @Test
    fun `centaur MOT version replaces base traits instead of doubling them`() {
        val library = WizardLibrary(
            races = races.mapIndexed { index, race -> race.copy(id = "r$index") },
            classes = classes.mapIndexed { index, item -> item.copy(id = "c$index") },
            backgrounds = backgrounds.mapIndexed { index, item -> item.copy(id = "b$index") },
        )
        val centaurId = library.races.first { it.name == "Кентавр" && it.category == "Основные расы" }.id
        val cache = WizardRulesCache()
        var draft = WizardEngine.selectRace(CharacterDraft(name = "Кентавр"), centaurId)
        val rules = WizardEngine.context(draft, library, cache).race!!
        // Разделы с именами и историей — не разновидности; версия MOT называется по своей книге.
        val mot = rules.subraces.single()
        assertEquals("Кентавр (MOT)", mot.title)
        assertTrue(mot.replacesBaseTraits)

        draft = WizardEngine.selectSubrace(draft, mot.id)
        val context = WizardEngine.context(draft, library, cache)
        // Бонусы только один раз: Сила +2, Мудрость +1.
        assertEquals(
            mapOf(AbilityType.STRENGTH to 2, AbilityType.WISDOM to 1),
            WizardEngine.racialBonuses(draft, context),
        )
        // Особенности и выбор навыка не дублируются.
        val creation = CharacterAssembler.assemble(draft, context, emptyList())
        val raceTitles = creation.notes.filter { it.title.startsWith("Раса") }
            .flatMap { block -> block.paragraphs.map { it.text.lineSequence().first() } }
        assertEquals(raceTitles.distinct(), raceTitles)
        assertEquals(1, raceTitles.count { it.startsWith("Разбег") })
        assertEquals(1, context.choices.count { it.kind == ChoiceKind.SKILL && it.source != ChoiceSource.CLASS })
    }

    @Test
    fun `hare jump note does not grant jump spell`() {
        val harengon = RaceRulesParser.parse(races.first { it.name == "Зайцегон" })
        assertTrue(harengon.grants.spells.isEmpty())
        // Примечание о прыжке не становится второй особенностью «Кроличий прыжок».
        assertEquals(1, harengon.traits.count { it.title.startsWith("Кроличий прыжок") })
        assertFalse(harengon.traits.any { MarkupText.plain(it.text).contains("Примечание") })
    }

    @Test
    fun `way of shadow grants its spells at level 3`() {
        val monk = ClassRulesParser.parse(classes.first { it.name == "Монах" })
        val shadow = monk.subclasses.first { it.title == "Путь тени" }
        val spells = ClassChoiceCatalog.featureSpells(monk, shadow, 3, showOptional = false).spells.map { it.name }
        listOf("бесследное передвижение", "тёмное зрение", "тишину", "тьму", "малая иллюзия").forEach { name ->
            assertTrue("Нет $name в $spells", name in spells)
        }
        // На 2-м уровне архетипа ещё нет — и заклинаний тоже.
        assertTrue(ClassChoiceCatalog.featureSpells(monk, null, 2, showOptional = false).spells.isEmpty())
    }

    @Test
    fun `heavy armor master feat gives strength and heavy armor`() {
        val effects = FeatRules.parse(feat("Знаток тяжёлых доспехов"), "asi.4")
        assertEquals(mapOf(AbilityType.STRENGTH to 1), effects.fixedAbilities)
        assertTrue(ArmorProficiency.HEAVY in effects.grants.armor)
    }

    @Test
    fun `fey touched offers mental ability and spell choices`() {
        val effects = FeatRules.parse(feat("Затронутый феями"), "asi.4")
        val ability = effects.choices.first { it.kind == ChoiceKind.ABILITY }
        assertEquals(
            setOf(AbilityType.INTELLIGENCE, AbilityType.WISDOM, AbilityType.CHARISMA).map { it.name }.toSet(),
            ability.options.map { it.id }.toSet(),
        )
        assertEquals(listOf("туманный шаг"), effects.spells)
        val spell = effects.choices.first { it.kind == ChoiceKind.SPELL }
        assertEquals(1, spell.spellFilter?.minLevel)
        assertEquals(setOf("div", "enc"), spell.spellFilter?.schools)
    }

    @Test
    fun `monk criminal stealth overlap is reported and can be kept`() {
        val library = WizardLibrary(
            races = races.mapIndexed { index, race -> race.copy(id = "r$index") },
            classes = classes.mapIndexed { index, item -> item.copy(id = "c$index") },
            backgrounds = backgrounds.mapIndexed { index, item -> item.copy(id = "b$index") },
        )
        val cache = WizardRulesCache()
        var draft = CharacterDraft(name = "Тень", level = 1)
        draft = WizardEngine.selectClass(draft, library.classes.first { it.name == "Монах" }.id)
        draft = WizardEngine.selectBackground(draft, library.backgrounds.first { it.name == "Преступник" }.id)
        draft = draft.copy(selections = draft.selections + ("class.skills" to listOf(SkillType.STEALTH.name, SkillType.ACROBATICS.name)))
        val context = WizardEngine.context(draft, library, cache)

        val overlaps = ProficiencyOverlaps.find(draft, context)
        val stealth = overlaps.single()
        assertEquals("class.skills", stealth.choice.id)
        assertEquals(SkillType.STEALTH.name, stealth.value)
        assertEquals(ChoiceSource.BACKGROUND, stealth.grantedBy?.source)

        // «Оставить как есть» скрывает уведомление.
        val kept = draft.copy(keptOverlaps = draft.keptOverlaps + stealth.key)
        assertTrue(ProficiencyOverlaps.find(kept, WizardEngine.context(kept, library, cache)).isEmpty())
    }

    @Test
    fun `feat bonuses reach the assembled character`() {
        val library = WizardLibrary(
            races = races.mapIndexed { index, race -> race.copy(id = "r$index") },
            classes = classes.mapIndexed { index, item -> item.copy(id = "c$index") },
            backgrounds = backgrounds.mapIndexed { index, item -> item.copy(id = "b$index") },
            feats = feats,
        )
        val cache = WizardRulesCache()
        val heavy = feat("Знаток тяжёлых доспехов")
        var draft = CharacterDraft(name = "Монах", level = 4)
        draft = WizardEngine.selectClass(draft, library.classes.first { it.name == "Монах" }.id)
        draft = draft.copy(
            asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)),
            featSlots = mapOf("asi.4" to heavy.id),
            poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap(),
        )
        val context = WizardEngine.context(draft, library, cache)
        val character = CharacterAssembler.assemble(draft, context, emptyList(), feats).character
        // Стандартный набор: Сила 15 + 1 от черты.
        assertEquals(16, character.abilityScores[AbilityType.STRENGTH.ordinal])
        assertTrue(ArmorProficiency.HEAVY.name in character.armorProficiencies)
    }
}
