package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.model.Spell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Заклинания «раз до отдыха» от особенностей и воззваний: отдельный ресурс на каждое
 * заклинание и само заклинание в списке персонажа.
 */
class SpellResourcesTest {

    private val library by lazy {
        WizardLibrary(
            races = RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "r$index") },
            classes = ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "c$index") },
            backgrounds = BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
                .mapIndexed { index, item -> item.copy(id = "b$index") },
        )
    }
    private val feats by lazy {
        com.example.spellbook.data.FeatLibraryCodec.decodeList(File("src/main/assets/feats-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "f$index") }
    }
    private val libraryWithFeats by lazy { library.copy(feats = feats, spells = spells) }

    private val spells: List<Spell> by lazy {
        SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "s$index") }
    }

    private fun tieflingDraft(level: Int): Pair<CharacterDraft, WizardRulesCache> {
        val cache = WizardRulesCache()
        val race = library.races.first { it.name == "Тифлинг" && it.category == "Основные расы" }
        val fighter = library.classes.first { it.name == "Воин" }
        var draft = CharacterDraft(name = "Тифлинг", level = level)
        draft = WizardEngine.selectRace(draft, race.id)
        draft = WizardEngine.selectClass(draft, fighter.id)
        val asmodeus = WizardEngine.context(draft, library, cache).race!!.subraces.first { it.title.startsWith("Асмодей") }
        return WizardEngine.selectSubrace(draft, asmodeus.id) to cache
    }

    private fun spellNames(ids: Set<String>): Set<String> =
        spells.filter { it.id in ids }.map { it.name.lowercase() }.toSet()

    @Test
    fun `tiefling heritage gives a separate resource per spell by level`() {
        val (draft3, cache3) = tieflingDraft(level = 3)
        val creation3 = CharacterAssembler.assemble(draft3, WizardEngine.context(draft3, library, cache3), spells)
        val names3 = creation3.character.resources.map { it.name }
        assertTrue("Адское возмездие на 3 уровне: $names3", names3.contains("Адское возмездие"))
        assertFalse("Тьма раньше 5 уровня: $names3", names3.any { it.equals("Тьма", ignoreCase = true) })
        assertFalse("Общий ресурс особенности не нужен: $names3", names3.contains("Дьявольское наследие"))

        val (draft5, cache5) = tieflingDraft(level = 5)
        val creation5 = CharacterAssembler.assemble(draft5, WizardEngine.context(draft5, library, cache5), spells)
        val resources5 = creation5.character.resources.filter { it.name == "Адское возмездие" || it.name == "Тьма" }
        assertEquals(2, resources5.size)
        assertTrue(resources5.all { it.maximum == 1 })
        assertTrue(resources5.all { it.description.contains("Дьявольское наследие") })
        assertTrue(spellNames(creation5.spellIds).containsAll(setOf("адское возмездие", "тьма")))
    }

    @Test
    fun `invocation thief of five fates gives bane and a once per rest resource`() {
        val cache = WizardRulesCache()
        val warlock = library.classes.first { it.name == "Колдун" }
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Колдун", level = 5), warlock.id)
        val context = WizardEngine.context(draft, library, cache)
        val invocations = context.choices.first { it.id == "class.invocations" }
        val thief = invocations.options.first { it.title.equals("Вор пяти судеб", ignoreCase = true) }
        draft = draft.copy(selections = draft.selections + (invocations.id to listOf(thief.id)))

        val creation = CharacterAssembler.assemble(draft, WizardEngine.context(draft, library, cache), spells)
        val bane = creation.character.resources.firstOrNull { it.name == "Порча" }
        assertTrue("Ресурс порчи: ${creation.character.resources.map { it.name }}", bane != null && bane.maximum == 1)
        assertTrue("Порча в списке заклинаний", "порча" in spellNames(creation.spellIds))
    }

    @Test
    fun `mask of many faces gives disguise self at will without a resource`() {
        val cache = WizardRulesCache()
        val warlock = library.classes.first { it.name == "Колдун" }
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Колдун", level = 5), warlock.id)
        val context = WizardEngine.context(draft, library, cache)
        val invocations = context.choices.first { it.id == "class.invocations" }
        val mask = invocations.options.first { it.title.equals("Маска многих лиц", ignoreCase = true) }
        draft = draft.copy(selections = draft.selections + (invocations.id to listOf(mask.id)))

        val creation = CharacterAssembler.assemble(draft, WizardEngine.context(draft, library, cache), spells)
        assertTrue("маскировка" in spellNames(creation.spellIds))
        assertFalse(creation.character.resources.any { it.name.equals("Маскировка", ignoreCase = true) })
    }

    @Test
    fun `fey touched feat gives misty step as a once per rest resource`() {
        val cache = WizardRulesCache()
        val cleric = libraryWithFeats.classes.first { it.name == "Жрец" }
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Жрец", level = 4), cleric.id)
        draft = draft.copy(asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)))
        val feyTouched = feats.first { it.name == "Затронутый феями" }
        draft = draft.copy(featSlots = mapOf("asi.4" to feyTouched.id))

        val creation = CharacterAssembler.assemble(draft, WizardEngine.context(draft, libraryWithFeats, cache), spells)
        val names = creation.character.resources.map { it.name }
        val misty = creation.character.resources.firstOrNull { it.name == "Туманный шаг" }
        assertTrue("Ресурс туманного шага: $names", misty != null && misty.maximum == 1)
        assertTrue(misty!!.description.contains("Затронутый феями"))
        assertFalse("Общий ресурс черты не нужен: $names", names.contains("Затронутый феями"))
    }

    @Test
    fun `shadow touched gives a resource for invisibility and for the chosen spell`() {
        val cache = WizardRulesCache()
        val cleric = libraryWithFeats.classes.first { it.name == "Жрец" }
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Жрец", level = 4), cleric.id)
        val shadowTouched = feats.first { it.name == "Затронутый тенью" }
        draft = draft.copy(
            asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)),
            featSlots = mapOf("asi.4" to shadowTouched.id),
        )
        val choice = WizardEngine.context(draft, libraryWithFeats, cache).choices
            .first { it.id.startsWith("feat.asi.4") && it.kind == ChoiceKind.SPELL }
        val falseLife = spells.first { it.name == "Псевдожизнь" }
        draft = draft.copy(selections = draft.selections + (choice.id to listOf(falseLife.id)))

        val creation = CharacterAssembler.assemble(draft, WizardEngine.context(draft, libraryWithFeats, cache), spells)
        val names = creation.character.resources.map { it.name }
        assertTrue("Ресурс невидимости: $names", names.contains("Невидимость"))
        val chosen = creation.character.resources.firstOrNull { it.name == "Псевдожизнь" }
        assertTrue("Ресурс выбранного заклинания: $names", chosen != null && chosen.maximum == 1)
        assertTrue(chosen!!.description.contains("Затронутый тенью"))
        assertTrue("псевдожизнь" in spellNames(creation.spellIds))
    }

    @Test
    fun `unlimited invocation gives the spell without a resource`() {
        val cache = WizardRulesCache()
        val warlock = library.classes.first { it.name == "Колдун" }
        var draft = WizardEngine.selectClass(CharacterDraft(name = "Колдун", level = 15), warlock.id)
        val context = WizardEngine.context(draft, library, cache)
        val invocations = context.choices.first { it.id == "class.invocations" }
        val visions = invocations.options.first { it.title.equals("Видения далёких королевств", ignoreCase = true) }
        draft = draft.copy(selections = draft.selections + (invocations.id to listOf(visions.id)))

        val creation = CharacterAssembler.assemble(draft, WizardEngine.context(draft, library, cache), spells)
        assertTrue("магический глаз" in spellNames(creation.spellIds))
        assertFalse(creation.character.resources.any { it.name.equals("Магический глаз", ignoreCase = true) })
    }
}
