package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.effectiveSpeed
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.Spell
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Составляющие скорости, подготовка заклинаний от черт и шаг заклинаний в мастере. */
class SpeedAndPreparedTest {

    private val classes by lazy {
        ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "c$index") }
    }
    private val feats by lazy {
        val json = JSONArray(File("src/main/assets/feats-library.json").readText())
        (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            Feat(id = "f$index", name = item.getString("name"), description = item.getString("description"), book = item.optString("book"))
        }
    }
    private val library by lazy {
        WizardLibrary(
            races = RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
                .mapIndexed { index, race -> race.copy(id = "r$index") },
            classes = classes,
            backgrounds = BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "b$index") },
            feats = feats,
        )
    }

    private fun feat(name: String) = feats.first { it.name == name }

    @Test
    fun `mobile feat gives permanent speed bonus`() {
        val part = SpeedRules.featPart(feat("Подвижный"))!!
        assertEquals("10", part.formula)
    }

    @Test
    fun `monk unarmored movement grows with level`() {
        val monk = ClassRulesParser.parse(classes.first { it.name == "Монах" })
        assertTrue(SpeedRules.classParts(monk, null, 1, showOptional = false).isEmpty())
        val part = SpeedRules.classParts(monk, null, 6, showOptional = false).single()
        assertTrue(part.description.contains("доспех"))

        var draft = CharacterDraft(name = "Монах", level = 6)
        draft = WizardEngine.selectClass(draft, classes.first { it.name == "Монах" }.id)
        val context = WizardEngine.context(draft, library, WizardRulesCache())
        val character = CharacterAssembler.assemble(draft, context, emptyList(), feats).character
        // 30 (базовая) + 15 (движение без доспехов на 6-м уровне).
        assertEquals(45, character.effectiveSpeed)
        assertEquals(45, character.speed)
    }

    @Test
    fun `temporary speed boosts are not listed`() {
        // «Пока вы в ярости…», «до конца хода» — включаются в бою, а не в листе.
        classes.forEach { item ->
            val rules = ClassRulesParser.parse(item)
            rules.subclasses.filter { !it.extended }.forEach { sub ->
                SpeedRules.classParts(rules, sub, 20, showOptional = false).forEach { part ->
                    val lower = part.description.lowercase()
                    assertFalse("${item.name}/${sub.title}: ${part.name}", lower.contains("до конца") || lower.contains("ярост"))
                }
            }
        }
    }

    @Test
    fun `feat spells are always prepared and do not use the limit`() {
        val feyTouched = feat("Затронутый феями")
        val mistyStep = Spell(id = "misty", name = "Туманный шаг", level = 2, classes = listOf("sorcerer", "warlock", "wizard"))
        val bless = Spell(id = "bless", name = "Благословение", level = 1, classes = listOf("cleric", "paladin"))
        var draft = CharacterDraft(name = "Жрец", level = 4)
        draft = WizardEngine.selectClass(draft, classes.first { it.name == "Жрец" }.id)
        var context = WizardEngine.context(draft, library, WizardRulesCache())
        val domain = context.characterClass!!.subclasses.first { it.title == "Домен жизни" }
        draft = WizardEngine.selectSubclass(draft, domain.id).copy(
            asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)),
            featSlots = mapOf("asi.4" to feyTouched.id),
            poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap(),
        )
        context = WizardEngine.context(draft, library, WizardRulesCache())
        val creation = CharacterAssembler.assemble(draft, context, listOf(mistyStep, bless), feats)
        assertTrue("misty" in creation.spellIds)
        assertTrue("Туманный шаг от черты всегда подготовлен", "misty" in creation.preparedIds)
        // Лимит класса плюс всегда подготовленные: благословение домена и туманный шаг черты.
        val classLimit = context.spellcasting!!.preparedCount
        assertEquals(classLimit + 2, creation.character.maxPreparedSpells)
    }

    @Test
    fun `ranger without Tasha options has no optional resources or notes`() {
        var draft = CharacterDraft(name = "Следопыт", level = 3, showOptional = false)
        draft = WizardEngine.selectClass(draft, classes.first { it.name == "Следопыт" }.id)
        val context = WizardEngine.context(draft, library, WizardRulesCache())
        val names = context.resources.map { it.name }
        assertFalse(names.toString(), names.any { it.startsWith("Предпочтительный") || it.startsWith("Изначальная") })

        // С переключателем замена выбирается явно; если взять опциональное — заряды есть,
        // а таблица заклинаний в описании остаётся таблицей.
        val offered = WizardEngine.context(draft.copy(showOptional = true), library, WizardRulesCache())
        val withTasha = draft.copy(showOptional = true, optionalSwaps = offered.swaps.associate { it.id to true })
        val optional = WizardEngine.context(withTasha, library, WizardRulesCache())
        val awareness = optional.resources.first { it.name.startsWith("Изначальная") }
        assertTrue(awareness.description, awareness.description.lines().any { it.startsWith("| Уровень следопыта") })
        assertTrue(awareness.description.lines().any { it.trim().startsWith("| ---") })
    }

    @Test
    fun `non caster has no spell step`() {
        var draft = CharacterDraft(name = "Воин", level = 1)
        draft = WizardEngine.selectClass(draft, classes.first { it.name == "Воин" }.id)
        val fighter = WizardEngine.context(draft, library, WizardRulesCache())
        assertFalse(WizardStep.Spells in WizardEngine.steps(draft, fighter))

        // Паладин 1-го уровня: заклинательства ещё нет — шага тоже.
        var paladinDraft = CharacterDraft(name = "Паладин", level = 1)
        paladinDraft = WizardEngine.selectClass(paladinDraft, classes.first { it.name == "Паладин" }.id)
        val paladin = WizardEngine.context(paladinDraft, library, WizardRulesCache())
        assertFalse(WizardStep.Spells in WizardEngine.steps(paladinDraft, paladin))
    }
}
