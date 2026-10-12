package com.example.spellbook.data

import com.example.spellbook.data.model.MagicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты сериализации библиотеки магических предметов. */
class MagicItemLibraryCodecTest {

    private val amulet = MagicItem(
        name = "Амулет здоровья",
        description = "Ваше Телосложение равно 19.",
        source = "https://dnd.su/items/4-amulet_of_health/",
        book = "Dungeon Master's Guide",
        category = "Чудесный предмет, редкий (требуется настройка)",
        kind = "Чудесный предмет",
        rarity = "редкий",
        requiresAttunement = true,
        price = "501-5 000 зм",
    )

    @Test
    fun `all fields survive the round trip`() {
        val restored = MagicItemLibraryCodec.decodeAll(
            MagicItemLibraryCodec.encodeAll(listOf(amulet)),
        ).single()

        // Локальный id не переносится: у каждого пользователя он свой.
        assertEquals(amulet.copy(id = restored.id, createdAt = restored.createdAt), restored)
    }

    @Test
    fun `broken json yields an empty list`() {
        // Встроенный набор читается при старте: ошибка в нём не должна мешать запуску.
        assertTrue(MagicItemLibraryCodec.decodeAll("{not json").isEmpty())
    }

    @Test
    fun `entries without a name are skipped`() {
        val json = """[{"name":"","description":"x"},{"name":"Боб","description":"y"}]"""

        val items = MagicItemLibraryCodec.decodeAll(json)

        assertEquals(listOf("Боб"), items.map { it.name })
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val item = MagicItemLibraryCodec.decodeAll("""[{"name":"Боб"}]""").single()

        assertEquals("", item.kind)
        assertEquals("", item.rarity)
        assertEquals("", item.price)
        assertEquals(false, item.requiresAttunement)
    }
}
