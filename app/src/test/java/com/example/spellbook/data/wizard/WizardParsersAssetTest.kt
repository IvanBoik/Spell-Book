package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.CharacterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Разбор встроенных библиотек для мастера создания персонажа.
 *
 * Проверяет ключевые случаи на настоящих текстах dnd.su: правила извлекаются
 * эвристически, и без таких тестов поломка обнаружилась бы только в интерфейсе.
 */
class WizardParsersAssetTest {

    private val races by lazy { RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText()) }
    private val classes by lazy { ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText()) }
    private val backgrounds by lazy {
        BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
    }

    private fun race(name: String, category: String = "Основные расы") =
        RaceRulesParser.parse(races.first { it.name == name && it.category == category })

    private fun charClass(name: String) = ClassRulesParser.parse(classes.first { it.name == name })

    @Test
    fun `elf has dexterity bonus and official subraces`() {
        val elf = race("Эльф")
        assertEquals(listOf(AbilityBonus(AbilityType.DEXTERITY, 2)), elf.abilityBonus.fixed)
        val high = elf.subraces.first { it.title == "Высший эльф" }
        assertEquals(listOf(AbilityBonus(AbilityType.INTELLIGENCE, 1)), high.abilityBonus?.fixed)
        assertTrue(high.choices.any { it.kind == ChoiceKind.CANTRIP && "wizard" in it.spellFilter!!.classCodes })
        assertTrue(high.choices.any { it.kind == ChoiceKind.LANGUAGE })
        assertTrue(elf.subraceRequired)
        assertTrue("Гругач — Unearthed Arcana", elf.subraces.first { it.title == "Гругач" }.extended)
        assertEquals(30, elf.grants.speed)
        assertTrue(elf.grants.skills.isNotEmpty())
    }

    @Test
    fun `human has variant with feat and free bonuses`() {
        val human = race("Человек")
        assertEquals(6, human.abilityBonus.fixed.size)
        val variant = human.subraces.first { it.grants.featSlots > 0 }
        assertTrue(variant.replacesBaseAbilityBonus)
        assertEquals(listOf(listOf(1, 1)), variant.abilityBonus?.patterns)
        assertTrue(variant.choices.any { it.kind == ChoiceKind.SKILL })
    }

    @Test
    fun `tasha style races offer flexible bonuses`() {
        val aarakocra = race("Ааракокра", "Mordenkainen Presents: Monsters of the Multiverse")
        assertEquals(listOf(listOf(2, 1), listOf(1, 1, 1)), aarakocra.abilityBonus.patterns)
    }

    @Test
    fun `half elf has fixed and free bonuses`() {
        val halfElf = race("Полуэльф")
        assertEquals(listOf(AbilityBonus(AbilityType.CHARISMA, 2)), halfElf.abilityBonus.fixed)
        assertEquals(listOf(listOf(1, 1)), halfElf.abilityBonus.patterns)
    }

    @Test
    fun `tabbed dwarf page becomes subraces`() {
        val dwarf = race("Дварф")
        assertTrue(dwarf.subraces.size >= 2)
        val hill = dwarf.subraces.first { it.title.startsWith("Холмов") }
        assertEquals(
            setOf(AbilityBonus(AbilityType.CONSTITUTION, 2), AbilityBonus(AbilityType.WISDOM, 1)),
            hill.abilityBonus?.fixed?.toSet(),
        )
        assertEquals(1, hill.grants.hpPerLevel)
        assertEquals(25, hill.grants.speed)
    }

    @Test
    fun `fighter rules`() {
        val fighter = charClass("Воин")
        assertEquals(CharacterClass.FIGHTER, fighter.type)
        assertEquals(10, fighter.hitDie)
        assertEquals(setOf(AbilityType.STRENGTH, AbilityType.CONSTITUTION), fighter.savingThrows)
        assertEquals(2, fighter.skillChoice?.count)
        assertEquals(8, fighter.skillChoice?.options?.size)
        assertEquals(4, fighter.equipment.size)
        assertTrue(fighter.equipment.all { it.hasChoice })
        assertEquals("5к4×10", fighter.startingGold)
        assertEquals(listOf(4, 6, 8, 12, 14, 16, 19), fighter.asiLevels)
        val style = fighter.feature("Боевой стиль")!!
        assertTrue(style.parts.size >= 6)
        assertTrue(fighter.optionList("приём").size >= 16)
        val master = fighter.subclasses.first { it.title == "Мастер боевых искусств" }
        assertTrue(master.features.any { it.title.lowercase().startsWith("боевое превосходство") && it.level == 3 })
        assertTrue(fighter.subclasses.first { it.title.startsWith("Застрельщик") }.extended)
        assertTrue(!master.extended)
    }

    @Test
    fun `battle master gets maneuvers step and superiority dice`() {
        val fighter = charClass("Воин")
        val master = fighter.subclasses.first { it.title == "Мастер боевых искусств" }
        val choices = ClassChoiceCatalog.choices(fighter, master, 3, emptyMap(), showOptional = false, showUnofficial = false)
        val maneuvers = choices.first { it.id == "subclass.maneuvers" }
        assertEquals(3, maneuvers.count)
        assertTrue(maneuvers.dedicatedStep)
        assertTrue(maneuvers.options.size >= 16)
        val resources = ClassChoiceCatalog.resources(fighter, master, 3, showOptional = false)
        assertTrue(resources.any { it.name.startsWith("Кости превосходства") && it.maximum == 4 })
        val style = choices.first { it.id == "class.fighting_style" }
        assertTrue("Опциональные стили скрыты", style.options.none { it.extended })
    }

    @Test
    fun `warlock invocations and pact`() {
        val warlock = charClass("Колдун")
        assertEquals("Известные воззвания", warlock.tableHeaders.last())
        val fiend = warlock.subclasses.first { it.title == "Исчадие" }
        val choices = ClassChoiceCatalog.choices(warlock, fiend, 5, emptyMap(), showOptional = false, showUnofficial = false)
        val invocations = choices.first { it.id == "class.invocations" }
        assertEquals(3, invocations.count)
        assertTrue(invocations.options.size > 20)
        // Воззвания выше уровня видны (недоступны в интерфейсе через OptionRequirements).
        assertTrue("Воззвания 7+ уровня в списке", invocations.options.any { (it.requiredLevel ?: 0) > 5 })
        assertTrue(choices.any { it.id == "class.pact" })
        val plan = ClassChoiceCatalog.spellcasting(warlock, fiend, 5, emptyMap())!!
        assertEquals(3, plan.cantrips)
        assertEquals(6, plan.knownSpells)
        assertTrue(plan.extraSpellNames.isNotEmpty())
    }

    @Test
    fun `cleric prepares and has domain spells`() {
        val cleric = charClass("Жрец")
        assertEquals(1, cleric.subclassLevel)
        val life = cleric.subclasses.first { it.title == "Домен жизни" }
        val plan = ClassChoiceCatalog.spellcasting(cleric, life, 3, mapOf(AbilityType.WISDOM to 16))!!
        assertEquals(3, plan.cantrips)
        assertEquals(6, plan.preparedCount)
        assertEquals(4, plan.alwaysPrepared.size)
    }

    @Test
    fun `every class parses basics`() {
        classes.forEach { charClass ->
            val rules = ClassRulesParser.parse(charClass)
            assertTrue("${charClass.name}: тип", rules.type != CharacterClass.OTHER)
            assertTrue("${charClass.name}: навыки", rules.skillChoice != null)
            assertTrue("${charClass.name}: спасброски", rules.savingThrows.size == 2)
            assertTrue("${charClass.name}: архетипы", rules.subclasses.count { !it.extended } >= 3)
            assertTrue("${charClass.name}: снаряжение", rules.equipment.isNotEmpty())
            assertTrue("${charClass.name}: умения", rules.features.size >= 5)
        }
    }

    @Test
    fun `backgrounds parse skills and equipment`() {
        val acolyte = BackgroundRulesParser.parse(backgrounds.first { it.name == "Благородный" })
        assertEquals(2, acolyte.skills.size)
        assertEquals(25, acolyte.gold)
        assertTrue(acolyte.choices.any { it.kind == ChoiceKind.LANGUAGE })
        assertTrue(acolyte.choices.any { it.kind == ChoiceKind.TOOL })
        assertTrue(acolyte.feature != null)
        val parsed = backgrounds.map(BackgroundRulesParser::parse)
        val withSkills = parsed.count { it.skills.isNotEmpty() || it.choices.any { c -> c.kind == ChoiceKind.SKILL } }
        assertTrue("Навыки разобраны у большинства: $withSkills/${parsed.size}", withSkills >= parsed.size * 3 / 4)
    }

    @Test
    fun `race bonus parse coverage`() {
        val parsed = races.map(RaceRulesParser::parse)
        val failed = parsed.filter { rules ->
            !rules.abilityBonus.parsed && rules.subraces.none { it.abilityBonus?.parsed == true }
        }
        println("Расы без разобранных бонусов: ${failed.map { it.race.name + " / " + it.race.category }}")
        assertTrue("Бонусы разобраны у большинства рас", failed.size <= races.size / 10)
    }
}
