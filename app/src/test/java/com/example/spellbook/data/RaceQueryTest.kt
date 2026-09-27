package com.example.spellbook.data

import com.example.spellbook.data.model.Race
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты поиска и фильтра библиотеки рас. */
class RaceQueryTest {

    private fun race(name: String, book: String) = Race(name = name, book = book)

    private val library = listOf(
        race("Человек", "Player's Handbook"),
        race("Эльф", "Player's Handbook"),
        race("Голиаф", "Volo's guide to monsters"),
        race("Минотавр", "Plane Shift: Amonkhet"),
        race("Своя раса", ""),
    )

    @Test
    fun `available books are distinct and sorted`() {
        assertEquals(
            listOf("Plane Shift: Amonkhet", "Player's Handbook", "Volo's guide to monsters"),
            library.availableBooks(),
        )
    }

    @Test
    fun `races without a book are not offered as filter option`() {
        // Пустое значение в списке фильтров выглядело бы как безымянная кнопка.
        assertFalse(library.availableBooks().any { it.isBlank() })
    }

    @Test
    fun `empty filters keep everything`() {
        assertEquals(library, library.filterSearch("", RaceFilters()))
    }

    @Test
    fun `filter keeps only races from the selected book`() {
        val result = library.filterSearch("", RaceFilters(books = setOf("Player's Handbook")))

        assertEquals(listOf("Человек", "Эльф"), result.map { it.name })
    }

    @Test
    fun `several books are combined with or`() {
        val filters = RaceFilters(books = setOf("Player's Handbook", "Plane Shift: Amonkhet"))

        val result = library.filterSearch("", filters)

        assertEquals(listOf("Человек", "Эльф", "Минотавр"), result.map { it.name })
    }

    @Test
    fun `search and filter are combined with and`() {
        val filters = RaceFilters(books = setOf("Player's Handbook"))

        val result = library.filterSearch("эль", filters)

        assertEquals(listOf("Эльф"), result.map { it.name })
    }

    @Test
    fun `search ignores case`() {
        assertEquals(listOf("Голиаф"), library.filterSearch("ГОЛИ", RaceFilters()).map { it.name })
    }

    @Test
    fun `active count reflects selected books`() {
        val filters = RaceFilters(books = setOf("Player's Handbook", "Volo's guide to monsters"))

        assertEquals(2, filters.activeCount)
        assertTrue(filters.isActive)
        assertFalse(RaceFilters().isActive)
    }
}
