package com.example.spellbook.ui.screens

import com.example.spellbook.data.FeatFilters
import com.example.spellbook.data.filterSearch
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты порядка блоков книг в списке черт.
 *
 * Главное правило: порядок книг задаётся всей библиотекой и не меняется при поиске
 * и фильтрации — иначе привычный Player's Handbook уезжал бы вниз при каждом фильтре.
 */
class FeatBookOrderTest {

    private val other = "Прочее"

    private fun feat(name: String, book: String, description: String = "") =
        Feat(name = name, book = book, description = description)

    /** Библиотека: PHB — 3 черты, XGE — 2, TCE — 1. */
    private val library = listOf(
        feat("Атлет", "Player's Handbook", "Увеличьте значение Силы на 1 при максимуме 20."),
        feat("Бдительный", "Player's Handbook"),
        feat("Меткий стрелок", "Player's Handbook"),
        feat("Знаток", "Xanathar's Guide", "Увеличьте значение Интеллекта на 1 при максимуме 20."),
        feat("Ловкач", "Xanathar's Guide"),
        feat("Телохранитель", "Tasha's Cauldron", "Увеличьте значение Силы на 1 при максимуме 20."),
    )

    @Test
    fun `books are ordered by size across the whole library`() {
        val books = groupByBook(library, library, other)

        assertEquals(
            listOf("Player's Handbook", "Xanathar's Guide", "Tasha's Cauldron"),
            books.map { it.title },
        )
    }

    @Test
    fun `filtering keeps the original book order`() {
        // По Силе остаются PHB (1 черта) и Tasha's (1 черта). Если бы порядок считался
        // по видимым чертам, блоки встали бы по алфавиту и PHB оказался бы вторым.
        val filters = FeatFilters(abilities = setOf(AbilityType.STRENGTH))
        val visible = library.filterSearch("", filters)

        val books = groupByBook(visible, library, other)

        assertEquals(listOf("Player's Handbook", "Tasha's Cauldron"), books.map { it.title })
    }

    @Test
    fun `search keeps the original book order`() {
        val visible = library.filterSearch("к", FeatFilters())

        val books = groupByBook(visible, library, other)

        // «Меткий стрелок» (PHB), «Знаток» и «Ловкач» (XGE) — PHB остаётся первым.
        assertEquals(listOf("Player's Handbook", "Xanathar's Guide"), books.map { it.title })
    }

    @Test
    fun `books without matches are not shown`() {
        val visible = library.filterSearch("Бдительный", FeatFilters())

        val books = groupByBook(visible, library, other)

        assertEquals(listOf("Player's Handbook"), books.map { it.title })
    }

    @Test
    fun `feats without a book fall into the fallback section`() {
        val custom = feat("Своя черта", "")
        val all = library + custom

        val books = groupByBook(all, all, other)

        assertEquals(other, books.last().title)
        assertEquals(listOf("Своя черта"), books.last().feats.map { it.name })
    }

    @Test
    fun `feats inside a book are sorted by name`() {
        val books = groupByBook(library, library, other)

        assertEquals(
            listOf("Атлет", "Бдительный", "Меткий стрелок"),
            books.first().feats.map { it.name },
        )
    }

    @Test
    fun `books of equal size are ordered alphabetically`() {
        val equal = listOf(feat("A", "Book B"), feat("B", "Book A"))

        val books = groupByBook(equal, equal, other)

        assertEquals(listOf("Book A", "Book B"), books.map { it.title })
    }
}
