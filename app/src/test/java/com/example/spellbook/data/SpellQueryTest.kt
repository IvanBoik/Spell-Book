package com.example.spellbook.data

import com.example.spellbook.data.model.Components
import com.example.spellbook.testSpell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты поиска, фильтрации и сортировки библиотеки заклинаний. */
class SpellQueryTest {

    private val fireball = testSpell(
        id = "fireball",
        name = "Огненный шар",
        level = 3,
        school = "evo",
        activationType = "action",
        components = Components(vocal = true, somatic = true, material = true),
        classes = listOf("wizard", "sorcerer"),
        createdAt = 300,
    )
    private val shield = testSpell(
        id = "shield",
        name = "Щит",
        level = 1,
        school = "abj",
        activationType = "reaction",
        components = Components(somatic = true),
        classes = listOf("wizard"),
        createdAt = 200,
    )
    private val magicArrow = testSpell(
        id = "arrow",
        name = "волшебная стрела",
        level = 1,
        school = "evo",
        activationType = "action",
        components = Components(vocal = true, ritual = true, concentration = true),
        classes = listOf("bard"),
        createdAt = 100,
    )

    private val spells = listOf(fireball, shield, magicArrow)

    private fun query(
        text: String = "",
        filters: SpellFilters = SpellFilters(),
        sort: SpellSort = SpellSort.DATE_ADDED,
    ) = spells.filterSortSearch(text, filters, sort)

    // region Поиск

    @Test
    fun `search is case insensitive`() {
        assertEquals(listOf(fireball), query(text = "ОГНЕННЫЙ"))
    }

    @Test
    fun `search trims spaces around query`() {
        assertEquals(listOf(shield), query(text = "  Щит  "))
    }

    @Test
    fun `empty search returns all spells`() {
        assertEquals(spells.size, query().size)
    }

    @Test
    fun `search without matches returns empty list`() {
        assertTrue(query(text = "несуществующее").isEmpty())
    }

    // endregion

    // region Фильтры

    @Test
    fun `level filter keeps only requested levels`() {
        assertEquals(setOf(shield, magicArrow), query(filters = SpellFilters(levels = setOf(1))).toSet())
    }

    @Test
    fun `class filter matches any class of the spell`() {
        assertEquals(setOf(fireball, shield), query(filters = SpellFilters(classes = setOf("wizard"))).toSet())
    }

    @Test
    fun `school filter keeps only requested school`() {
        assertEquals(listOf(shield), query(filters = SpellFilters(schools = setOf("abj"))))
    }

    @Test
    fun `casting time filter uses activation code`() {
        assertEquals(listOf(shield), query(filters = SpellFilters(activationTypes = setOf("reaction"))))
    }

    @Test
    fun `components inside one category are combined with OR`() {
        val result = query(filters = SpellFilters(components = setOf(SpellComponent.VOCAL, SpellComponent.MATERIAL)))

        assertEquals(setOf(fireball, magicArrow), result.toSet())
    }

    @Test
    fun `material component filter keeps single spell`() {
        assertEquals(listOf(fireball), query(filters = SpellFilters(components = setOf(SpellComponent.MATERIAL))))
    }

    @Test
    fun `concentration and ritual flags filter the list`() {
        assertEquals(listOf(magicArrow), query(filters = SpellFilters(concentration = true)))
        assertEquals(listOf(magicArrow), query(filters = SpellFilters(ritual = true)))
    }

    @Test
    fun `different filter categories are combined with AND`() {
        val result = query(filters = SpellFilters(levels = setOf(1), classes = setOf("wizard")))

        assertEquals(listOf(shield), result)
    }

    @Test
    fun `filters and search are applied together`() {
        val result = query(text = "щ", filters = SpellFilters(levels = setOf(1)))

        assertEquals(listOf(shield), result)
    }

    @Test
    fun `contradictory filters produce empty result`() {
        val result = query(filters = SpellFilters(levels = setOf(3), schools = setOf("abj")))

        assertTrue(result.isEmpty())
    }

    // endregion

    // region Сортировка

    @Test
    fun `sorting by date added puts newest first`() {
        assertEquals(listOf(fireball, shield, magicArrow), query(sort = SpellSort.DATE_ADDED))
    }

    @Test
    fun `sorting by name is case insensitive`() {
        assertEquals(listOf(magicArrow, fireball, shield), query(sort = SpellSort.NAME))
    }

    @Test
    fun `sorting by level falls back to name inside level`() {
        assertEquals(listOf(magicArrow, shield, fireball), query(sort = SpellSort.LEVEL))
    }

    // endregion

    // region Вспомогательные структуры

    @Test
    fun `activeCount counts every active condition`() {
        val filters = SpellFilters(
            levels = setOf(1, 2),
            classes = setOf("wizard"),
            schools = setOf("evo"),
            components = setOf(SpellComponent.VOCAL),
            activationTypes = setOf("action"),
            concentration = true,
            ritual = true,
        )

        // 2 уровня + класс + школа + компонент + время + концентрация + ритуал.
        assertEquals(8, filters.activeCount)
        assertTrue(filters.isActive)
    }

    @Test
    fun `empty filters are not active`() {
        assertEquals(0, SpellFilters().activeCount)
        assertFalse(SpellFilters().isActive)
    }

    @Test
    fun `presentIn checks component presence in spell`() {
        assertTrue(SpellComponent.VOCAL.presentIn(fireball))
        assertTrue(SpellComponent.SOMATIC.presentIn(shield))
        assertFalse(SpellComponent.MATERIAL.presentIn(shield))
    }

    @Test
    fun `every sort variant has a label resource`() {
        assertTrue(SpellSort.entries.all { it.labelRes != 0 })
    }

    @Test
    fun `casting time codes match activation codes`() {
        val activationCodes = SpellOptions.activationTypes.map { it.code }

        assertTrue(castingTimeOptions.all { it.first in activationCodes })
    }

    // endregion
}
