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
