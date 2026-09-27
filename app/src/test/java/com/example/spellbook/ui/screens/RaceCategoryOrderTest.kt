package com.example.spellbook.ui.screens

import com.example.spellbook.data.RaceFilters
import com.example.spellbook.data.filterSearch
import com.example.spellbook.data.model.Race
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты порядка блоков в библиотеке рас.
 *
 * Порядок повторяет разделы каталога dnd.su и не должен зависеть ни от числа рас,
 * ни от поиска: блоки не должны прыгать при фильтрации.
 */
class RaceCategoryOrderTest {

    private val other = "Прочее"

    private fun race(name: String, category: String) =
        Race(name = name, category = category)

    /** По одной расе в каждом разделе, специально в перемешанном порядке. */
    private val library = listOf(
        race("Минотавр", "Plane Shift: Amonkhet"),
        race("Дампир", "Mordenkainen Presents: Monsters of the Multiverse"),
        race("Древорождённый", "Midgard Heroes Handbook"),
        race("Человек", "Основные расы"),
        race("Гитьянки", "Unearthed Arcana"),
        race("Призрак", "Plane Shift: Innistrad"),
        race("Марк тени", "Происхождения"),
    )

    @Test
    fun `blocks follow the catalog order`() {
        val groups = groupRacesByBook(library, library, other)

        assertEquals(
            listOf(
                "Основные расы",
                "Происхождения",
                "Mordenkainen Presents: Monsters of the Multiverse",
                "Unearthed Arcana",
                "Plane Shift: Amonkhet",
                "Plane Shift: Innistrad",
                "Midgard Heroes Handbook",
            ),
            groups.map { it.title },
        )
    }

    @Test
    fun `order does not depend on block size`() {
        // Даже если дополнение крупнее основного раздела, основные расы остаются первыми.
        val skewed = library + List(20) { race("Раса $it", "Midgard Heroes Handbook") }

        val groups = groupRacesByBook(skewed, skewed, other)

        assertEquals("Основные расы", groups.first().title)
        assertEquals("Midgard Heroes Handbook", groups.last().title)
    }

    @Test
    fun `filtering keeps the original block order`() {
        val visible = library.filterSearch("н", RaceFilters())

        val groups = groupRacesByBook(visible, library, other)

        // Совпадения есть в нескольких разделах — порядок между ними сохраняется.
        assertEquals(groups.map { it.title }.sortedBy { RACE_TITLES.indexOf(it) }, groups.map { it.title })
    }

    @Test
    fun `blocks without matches are hidden`() {
        val visible = library.filter { it.name == "Человек" }

        val groups = groupRacesByBook(visible, library, other)

        assertEquals(listOf("Основные расы"), groups.map { it.title })
    }

    @Test
    fun `unknown categories go last in alphabetical order`() {
        val extended = library + race("Своя раса", "Неизвестный раздел") + race("Ещё одна", "Аномалия")

        val groups = groupRacesByBook(extended, extended, other)

        assertEquals(listOf("Аномалия", "Неизвестный раздел"), groups.takeLast(2).map { it.title })
    }

    @Test
    fun `races without category fall into the fallback block`() {
        val custom = race("Ручная раса", "")
        val all = library + custom

        val groups = groupRacesByBook(all, all, other)

        assertEquals(other, groups.last().title)
        assertEquals(listOf("Ручная раса"), groups.last().races.map { it.name })
    }

    private companion object {
        val RACE_TITLES = listOf(
            "Основные расы",
            "Происхождения",
            "Mordenkainen Presents: Monsters of the Multiverse",
            "Unearthed Arcana",
            "Plane Shift: Amonkhet",
            "Plane Shift: Innistrad",
            "Midgard Heroes Handbook",
        )
    }
}
