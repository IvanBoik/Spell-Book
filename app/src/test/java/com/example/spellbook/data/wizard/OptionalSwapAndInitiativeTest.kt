package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.initiativeBonus
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Feat
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Взаимоисключающие опциональные умения Tasha и составляющие инициативы. */
class OptionalSwapAndInitiativeTest {

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

    private fun rangerContext(draft: CharacterDraft): WizardContext =
        WizardEngine.context(draft, library, WizardRulesCache())

    private fun rangerDraft(showOptional: Boolean, swaps: Map<String, Boolean> = emptyMap()): CharacterDraft {
        val ranger = classes.first { it.name == "Следопыт" }
        return WizardEngine.selectClass(CharacterDraft(name = "Арагорн", level = 3, showOptional = showOptional), ranger.id)
            .copy(optionalSwaps = swaps)
    }

    private fun titles(context: WizardContext) = context.characterClass!!.features.map { it.title }

    @Test
    fun `ranger optional features are mutually exclusive with base ones`() {
        // Без опций Tasha — только базовые умения.
        val base = rangerContext(rangerDraft(showOptional = false))
        assertTrue(base.swaps.isEmpty())
        assertTrue(titles(base).any { it.startsWith("Избранный враг") })
        assertFalse(titles(base).any { it.startsWith("Предпочтительный противник") })

        // С опциями — предлагается выбор, по умолчанию базовые правила.
        val offered = rangerContext(rangerDraft(showOptional = true))
        val swap = offered.swaps.first { it.optional.title.startsWith("Предпочтительный противник") }
        assertTrue(swap.base.any { it.title.startsWith("Избранный враг") })
        assertTrue(titles(offered).any { it.startsWith("Избранный враг") })
        assertFalse(titles(offered).any { it.startsWith("Предпочтительный противник") })
        assertTrue(offered.choices.any { it.id == "class.favored_enemy" })

        // Выбрано опциональное — базовое и его выбор пропадают.
        val chosen = rangerContext(rangerDraft(showOptional = true, swaps = mapOf(swap.id to true)))
        assertTrue(titles(chosen).any { it.startsWith("Предпочтительный противник") })
        assertFalse(titles(chosen).any { it.startsWith("Избранный враг") })
        assertFalse(chosen.choices.any { it.id == "class.favored_enemy" })
        assertTrue(chosen.resources.any { it.name.startsWith("Предпочтительный противник") })
    }

    @Test
    fun `alert feat adds initiative part`() {
        val alert = InitiativeRules.featPart(feats.first { it.name == "Бдительный" })!!
        assertEquals("5", alert.formula)
        val character = InitiativeRules.withFeat(
            Character(name = "Т", abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 14)),
            feats.first { it.name == "Бдительный" },
        )
        assertEquals(2 + 5, character.initiativeBonus)
        assertEquals(2, InitiativeRules.withoutFeat(character, feats.first { it.name == "Бдительный" }.id).initiativeBonus)
    }

    @Test
    fun `old initiative formula becomes base`() {
        val character = Character(
            name = "Т",
            abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 14),
            initiativeFormula = "[dex] + 3",
        )
        assertEquals(5, character.initiativeBonus)
        val withAlert = InitiativeRules.withFeat(character, feats.first { it.name == "Бдительный" })
        assertEquals(5 + 5, withAlert.initiativeBonus)
    }

    @Test
    fun `divination wizard adds wisdom to initiative but temporary effects do not`() {
        val texts = mapOf(
            "Тактический ум" to "Вы можете добавить к броскам инициативы модификатор Мудрости.",
            "Разовое" to "Вы получаете бонус +5 к броскам инициативы до конца следующего хода.",
        )
        assertEquals("[wis]", InitiativeRules.bonusPart("Тактический ум", texts.getValue("Тактический ум"))?.formula)
        assertNull(InitiativeRules.bonusPart("Разовое", texts.getValue("Разовое")))
    }
}
