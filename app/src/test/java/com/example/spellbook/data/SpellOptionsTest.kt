package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты справочников кодов формата LSS.
 *
 * Проверяются только «канонические» имена источника dnd.su: подписи интерфейса
 * лежат в строковых ресурсах и зависят от выбранного языка.
 */
class SpellOptionsTest {

    @Test
    fun `canonical label returns source name for known code`() {
        assertEquals("Воплощение", SpellOptions.canonicalLabel(SpellOptions.schools, "evo"))
        assertEquals("Реакция", SpellOptions.canonicalLabel(SpellOptions.activationTypes, "reaction"))
    }

    @Test
    fun `canonical label returns the code itself for unknown value`() {
        assertEquals("unknown", SpellOptions.canonicalLabel(SpellOptions.schools, "unknown"))
    }

    @Test
    fun `empty code is a valid not set option`() {
        assertEquals("Не задано", SpellOptions.canonicalLabel(SpellOptions.abilities, ""))
    }

    @Test
    fun `optionFor returns null for unknown code`() {
        assertNull(SpellOptions.optionFor(SpellOptions.schools, "unknown"))
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
            val codes = options.map { it.code }
            assertEquals("Duplicated codes in dictionary $name", codes.distinct(), codes)
            assertTrue("Dictionary $name is empty", codes.isNotEmpty())
        }
    }

    @Test
    fun `spell levels cover range from cantrip to ninth level`() {
        assertEquals((0..MAX_SPELL_LEVEL).toList(), SpellOptions.levels)
    }
}
