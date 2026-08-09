package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты справочников кодов формата LSS. */
class SpellOptionsTest {

    @Test
    fun `labelFor returns label for known code`() {
        assertEquals("Воплощение", SpellOptions.labelFor(SpellOptions.schools, "evo"))
        assertEquals("Реакция", SpellOptions.labelFor(SpellOptions.activationTypes, "reaction"))
    }

    @Test
    fun `labelFor returns the code itself for unknown value`() {
        assertEquals("unknown", SpellOptions.labelFor(SpellOptions.schools, "unknown"))
    }

    @Test
    fun `labelFor supports empty code as not set`() {
        assertEquals("Не задано", SpellOptions.labelFor(SpellOptions.abilities, ""))
    }

    @Test
    fun `dictionary codes are unique`() {
        val dictionaries = mapOf(
            "schools" to SpellOptions.schools,
            "activationTypes" to SpellOptions.activationTypes,
            "durationUnits" to SpellOptions.durationUnits,
            "rangeUnits" to SpellOptions.rangeUnits,
            "targetTypes" to SpellOptions.targetTypes,
            "targetUnits" to SpellOptions.targetUnits,
            "actionTypes" to SpellOptions.actionTypes,
            "abilities" to SpellOptions.abilities,
            "damageTypes" to SpellOptions.damageTypes,
            "classes" to SpellOptions.classes,
        )

        dictionaries.forEach { (name, options) ->
            val codes = options.map { it.first }
            assertEquals("Duplicated codes in dictionary $name", codes.distinct(), codes)
            assertTrue("Dictionary $name is empty", codes.isNotEmpty())
        }
    }

    @Test
    fun `spell levels cover range from cantrip to ninth level`() {
        assertEquals((0..9).toList(), SpellOptions.levels.map { it.first })
        assertEquals("Заговор", SpellOptions.levels.first().second)
    }
}
