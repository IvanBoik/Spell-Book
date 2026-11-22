package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Общий поиск ссылок на заклинания и магические предметы на настоящих библиотеках. */
class LibraryLinkIndexTest {

    private val spells by lazy { SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText()) }
    private val items by lazy { MagicItemLibraryCodec.decodeAll(File("src/main/assets/magic-items-library.json").readText()) }
    private val index by lazy { LibraryLinkIndex(spells, items) }

    /** Найденные ссылки как «текст в описании → вид:название». */
    private fun links(text: String): List<Pair<String, String>> = index.find(text).map { link ->
        text.substring(link.range) to when (link) {
            is LibraryLink.ToSpell -> "spell:${link.spell.name}"
            is LibraryLink.ToItem -> "item:${link.item.name}"
        }
    }

    @Test
    fun `bag of holding links to item`() {
        assertEquals(
            listOf("Сумка хранения" to "item:Сумка хранения"),
            links("Предмет: Сумка хранения [bag of holding]"),
        )
    }

    @Test
    fun `item found by english name when russian differs`() {
        val result = links("вы можете создать волшебный мешок [bag of holding]")
        assertEquals(listOf("item:Сумка хранения"), result.map { it.second })
    }

    @Test
    fun `spells still link next to items`() {
        val result = links(
            "Наложите заклинание тьма [darkness], затем достаньте из сумки хранения [bag of holding] верёвку " +
                "лазания [rope of climbing].",
        )
        assertEquals(
            listOf("тьма" to "spell:Тьма", "сумки хранения" to "item:Сумка хранения", "верёвку лазания" to "item:Верёвка лазания"),
            result,
        )
    }

    @Test
    fun `replicate infusion table items link to library`() {
        val artificer = ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
            .single { it.name == "Изобретатель" }
        val table = artificer.description.lines().filter { it.startsWith("|") && it.contains("[") && it.contains("Нет |") }
        assertTrue(table.size > 5)
        val linked = table.count { line -> index.find(line).any { it is LibraryLink.ToItem } }
        assertEquals(table.toString(), table.size, linked)
    }

    /**
     * Скобка, подходящая и заклинанию, и предмету, по умолчанию ведёт к заклинанию; к предмету — только
     * при явном указании (английское название предмета или буквальное русское при падежном у заклинания).
     */
    @Test
    fun `collisions resolve by rule`() {
        val collisions = mutableListOf<String>()
        val texts = items.map { it.description } + spells.map { it.description }
        texts.forEach { text ->
            if (!text.contains('[')) return@forEach
            if (index.isCollision(text)) collisions += text.take(60)
        }
        println("Коллизий заклинание/предмет в текстах библиотек: ${collisions.size}")
        // Заклинание, названное своим английским именем, не становится предметом.
        assertEquals(listOf("spell:Тьма"), links("заклинание тьма [darkness]").map { it.second })
    }
}
