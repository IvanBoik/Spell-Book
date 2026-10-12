package com.example.spellbook.data

import com.example.spellbook.data.model.MagicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты фильтров библиотеки магических предметов. */
class MagicItemQueryTest {

    private fun item(
        name: String,
        rarity: String = "редкий",
        attunement: Boolean = false,
        book: String = "Dungeon Master's Guide",
    ) = MagicItem(
        name = name,
        rarity = rarity,
        requiresAttunement = attunement,
        book = book,
    )

    private val library = listOf(
        item("Амулет здоровья", rarity = "редкий", attunement = true),
        item("Зелье лечения", rarity = "обычное"),
        item("Посох мощи", rarity = "легендарный", attunement = true, book = "Tasha's Cauldron"),
        item("Щит +1", rarity = "необычный"),
        item("Колода многих вещей", rarity = "очень редкое"),
    )

    @Test
    fun `rarity is recognised in all gender forms`() {
        // На сайте редкость согласована с типом: «редкий», «редкое», «редкая».
        assertEquals(ItemRarity.RARE, ItemRarity.fromText("редкий"))
        assertEquals(ItemRarity.RARE, ItemRarity.fromText("редкое"))
        assertEquals(ItemRarity.RARE, ItemRarity.fromText("редкая"))
    }

    @Test
    fun `longer rarity prefixes win over shorter ones`() {
        // «очень редкий» содержит «редк», а «необычный» — «обычн»: порядок проверки важен.
        assertEquals(ItemRarity.VERY_RARE, ItemRarity.fromText("очень редкий"))
        assertEquals(ItemRarity.UNCOMMON, ItemRarity.fromText("необычное"))
    }

    @Test
    fun `unknown and empty rarity yield null`() {
        assertNull(ItemRarity.fromText(""))
        assertNull(ItemRarity.fromText("загадочный"))
    }

    @Test
    fun `empty filters keep everything`() {
        assertEquals(library, library.filterSearch("", MagicItemFilters()))
    }

    @Test
    fun `rarity filter joins values with or`() {
        val filters = MagicItemFilters(rarities = setOf(ItemRarity.RARE, ItemRarity.LEGENDARY))

        assertEquals(
            listOf("Амулет здоровья", "Посох мощи"),
            library.filterSearch("", filters).map { it.name },
        )
    }

    @Test
    fun `attunement filter works both ways`() {
        val required = library.filterSearch("", MagicItemFilters(attunement = true))
        val notRequired = library.filterSearch("", MagicItemFilters(attunement = false))

        assertEquals(listOf("Амулет здоровья", "Посох мощи"), required.map { it.name })
        assertEquals(3, notRequired.size)
    }

    @Test
    fun `book filter narrows the list`() {
        val filters = MagicItemFilters(books = setOf("Tasha's Cauldron"))

        assertEquals(listOf("Посох мощи"), library.filterSearch("", filters).map { it.name })
    }

    @Test
    fun `different filters are joined with and`() {
        // Легендарный И требующий настройки — под оба условия подходит только посох.
        val filters = MagicItemFilters(
            rarities = setOf(ItemRarity.LEGENDARY),
            attunement = true,
        )

        assertEquals(listOf("Посох мощи"), library.filterSearch("", filters).map { it.name })
    }

    @Test
    fun `query and filters are joined with and`() {
        val filters = MagicItemFilters(attunement = true)

        assertEquals(listOf("Амулет здоровья"), library.filterSearch("амулет", filters).map { it.name })
    }

    @Test
    fun `active count sums all conditions`() {
        val filters = MagicItemFilters(
            rarities = setOf(ItemRarity.RARE, ItemRarity.COMMON),
            attunement = true,
            books = setOf("Dungeon Master's Guide"),
        )

        assertEquals(4, filters.activeCount)
        assertTrue(filters.isActive)
    }

    @Test
    fun `available rarities follow the scale order`() {
        // Порядок берётся из шкалы, а не из данных: «обычный» всегда перед «легендарным».
        assertEquals(
            listOf(
                ItemRarity.COMMON,
                ItemRarity.UNCOMMON,
                ItemRarity.RARE,
                ItemRarity.VERY_RARE,
                ItemRarity.LEGENDARY,
            ),
            library.availableRarities(),
        )
    }

    @Test
    fun `available books are sorted and deduplicated`() {
        assertEquals(
            listOf("Dungeon Master's Guide", "Tasha's Cauldron"),
            library.availableItemBooks(),
        )
    }

    @Test
    fun `items without rarity are dropped by the rarity filter`() {
        val adventureItem = item("Боб", rarity = "")
        val all = library + adventureItem

        val filtered = all.filterSearch("", MagicItemFilters(rarities = setOf(ItemRarity.RARE)))

        assertTrue(filtered.none { it.name == "Боб" })
    }
}
