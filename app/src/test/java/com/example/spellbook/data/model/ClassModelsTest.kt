package com.example.spellbook.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты расчёта ячеек заклинаний по правилам мультикласса. */
class ClassModelsTest {

    private fun classLevel(characterClass: CharacterClass, level: Int) =
        CharacterClassLevel(characterClass, level)

    @Test
    fun `full caster gets slots from the table`() {
        val slots = spellSlotsFor(listOf(classLevel(CharacterClass.WIZARD, 5)))

        assertEquals(mapOf(1 to 4, 2 to 3, 3 to 2), slots)
    }

    @Test
    fun `non caster has no slots`() {
        val slots = spellSlotsFor(listOf(classLevel(CharacterClass.FIGHTER, 10)))

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `half caster rounds levels down`() {
        // Паладин 5 уровня даёт уровень заклинателя 2.
        val slots = spellSlotsFor(listOf(classLevel(CharacterClass.PALADIN, 5)))

        assertEquals(mapOf(1 to 3), slots)
    }

    @Test
    fun `artificer rounds levels up`() {
        // Изобретатель 1 уровня уже получает ячейки первого уровня.
        val slots = spellSlotsFor(listOf(classLevel(CharacterClass.ARTIFICER, 1)))

        assertEquals(mapOf(1 to 2), slots)
    }

    @Test
    fun `multiclass sums caster levels`() {
        // Волшебник 3 + паладин 4 даёт уровень заклинателя 5.
        val slots = spellSlotsFor(
            listOf(
                classLevel(CharacterClass.WIZARD, 3),
                classLevel(CharacterClass.PALADIN, 4),
            ),
        )

        assertEquals(mapOf(1 to 4, 2 to 3, 3 to 2), slots)
    }

    @Test
    fun `warlock slots are counted separately`() {
        val slots = spellSlotsFor(listOf(classLevel(CharacterClass.WARLOCK, 5)))

        assertEquals(mapOf(3 to 2), slots)
    }

    @Test
    fun `warlock slots are added on top of other classes`() {
        // Волшебник 3 даёт 4/2 ячейки, колдун 3 добавляет две ячейки второго уровня.
        val slots = spellSlotsFor(
            listOf(
                classLevel(CharacterClass.WIZARD, 3),
                classLevel(CharacterClass.WARLOCK, 3),
            ),
        )

        assertEquals(mapOf(1 to 4, 2 to 4), slots)
    }

    @Test
    fun `total level sums all classes`() {
        val total = totalLevel(
            listOf(
                classLevel(CharacterClass.ROGUE, 3),
                classLevel(CharacterClass.WIZARD, 2),
            ),
        )

        assertEquals(5, total)
    }

    @Test
    fun `preparing classes are detected`() {
        assertTrue(preparesSpells(listOf(classLevel(CharacterClass.CLERIC, 1))))
        assertFalse(preparesSpells(listOf(classLevel(CharacterClass.SORCERER, 5))))
    }

    @Test
    fun `class list preparing classes are detected`() {
        // Жрец, друид, паладин и изобретатель готовят из всего списка класса.
        assertTrue(CharacterClass.CLERIC.preparesFromClassList)
        assertTrue(CharacterClass.DRUID.preparesFromClassList)
        assertTrue(CharacterClass.PALADIN.preparesFromClassList)
        assertTrue(CharacterClass.ARTIFICER.preparesFromClassList)
    }

    @Test
    fun `wizard prepares from spellbook not from class list`() {
        // Волшебник готовит заклинания из личной книги, поэтому предложение ему не нужно.
        assertTrue(CharacterClass.WIZARD.preparesSpells)
        assertFalse(CharacterClass.WIZARD.preparesFromClassList)
    }

    @Test
    fun `non preparing classes are not offered class spells`() {
        assertFalse(CharacterClass.SORCERER.preparesFromClassList)
        assertFalse(CharacterClass.BARD.preparesFromClassList)
        assertFalse(CharacterClass.FIGHTER.preparesFromClassList)
    }

    @Test
    fun `class list limits are counted per class`() {
        val limits = classListPreparingLimits(
            listOf(
                classLevel(CharacterClass.CLERIC, 5),
                classLevel(CharacterClass.DRUID, 3),
                classLevel(CharacterClass.WIZARD, 2),
                classLevel(CharacterClass.FIGHTER, 1),
            ),
        )

        // Жрец 5 уровня — до 3 круга, друид 3 уровня — до 2 круга.
        assertEquals(mapOf("cleric" to 3, "druid" to 2), limits)
    }

    @Test
    fun `class level defines circle regardless of total level`() {
        // Друид 4 + чародей 1: общий уровень 5 даёт ячейки 3 круга,
        // но друид готовит заклинания только до 2 круга.
        val classes = listOf(
            classLevel(CharacterClass.DRUID, 4),
            classLevel(CharacterClass.SORCERER, 1),
        )

        assertEquals(3, spellSlotsFor(classes).keys.max())
        assertEquals(mapOf("druid" to 2), classListPreparingLimits(classes))
    }

    @Test
    fun `half casters get lower circle than full casters`() {
        // Паладин 5 уровня — заклинатель 2 уровня, то есть только 1 круг.
        assertEquals(1, maxSpellCircleFor(classLevel(CharacterClass.PALADIN, 5)))
        assertEquals(3, maxSpellCircleFor(classLevel(CharacterClass.CLERIC, 5)))
    }

    @Test
    fun `class without spell slots is not offered`() {
        // Паладин 1 уровня ещё не имеет ячеек, добавлять нечего.
        assertEquals(0, maxSpellCircleFor(classLevel(CharacterClass.PALADIN, 1)))
        assertTrue(classListPreparingLimits(listOf(classLevel(CharacterClass.PALADIN, 1))).isEmpty())
    }

    @Test
    fun `class list limits are empty without preparing classes`() {
        assertTrue(classListPreparingLimits(listOf(classLevel(CharacterClass.SORCERER, 5))).isEmpty())
    }

    @Test
    fun `custom class has no spell list code`() {
        assertEquals(null, CharacterClass.OTHER.spellListCode)
        assertFalse(CharacterClass.OTHER.preparesFromClassList)
    }

    @Test
    fun `spellcaster presence is detected`() {
        assertTrue(hasSpellcaster(listOf(classLevel(CharacterClass.WARLOCK, 1))))
        assertFalse(hasSpellcaster(listOf(classLevel(CharacterClass.BARBARIAN, 20))))
    }

    @Test
    fun `class level survives serialization`() {
        val entry = classLevel(CharacterClass.DRUID, 7)

        assertEquals(entry, CharacterClassLevel.parse(entry.serialize()))
    }

    @Test
    fun `unknown class is ignored on parse`() {
        assertEquals(null, CharacterClassLevel.parse("UNKNOWN:3"))
        assertEquals(null, CharacterClassLevel.parse("WIZARD"))
    }

    @Test
    fun `custom class keeps its name after serialization`() {
        val entry = CharacterClassLevel(CharacterClass.OTHER, 4, "Кровомаг")

        assertEquals(entry, CharacterClassLevel.parse(entry.serialize()))
    }

    @Test
    fun `custom class name may contain separator`() {
        val entry = CharacterClassLevel(CharacterClass.OTHER, 2, "Маг: огненный")

        assertEquals(entry, CharacterClassLevel.parse(entry.serialize()))
    }

    @Test
    fun `custom class gives no spell slots`() {
        val slots = spellSlotsFor(listOf(CharacterClassLevel(CharacterClass.OTHER, 10, "Кровомаг")))

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `custom class still counts toward total level`() {
        val total = totalLevel(
            listOf(
                CharacterClassLevel(CharacterClass.OTHER, 3, "Кровомаг"),
                classLevel(CharacterClass.WIZARD, 2),
            ),
        )

        assertEquals(5, total)
    }

    @Test
    fun `display name falls back to standard label`() {
        assertEquals("Волшебник", classLevel(CharacterClass.WIZARD, 1).displayName)
        assertEquals("Кровомаг", CharacterClassLevel(CharacterClass.OTHER, 1, "Кровомаг").displayName)
    }
}
