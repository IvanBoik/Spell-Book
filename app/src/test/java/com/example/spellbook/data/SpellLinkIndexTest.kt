package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Поиск ссылок на заклинания в текстах библиотек на настоящей библиотеке заклинаний. */
class SpellLinkIndexTest {

    private val index by lazy {
        SpellLinkIndex(SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText()))
    }

    /** Найденные ссылки как пары «текст в описании → название заклинания». */
    private fun links(text: String): List<Pair<String, String>> =
        index.find(text).map { text.substring(it.range) to it.spell.name }

    @Test
    fun `tiefling legacy links both spells`() {
        val text = "***Дьявольское наследие***. Начиная с 3-го уровня, вы можете один раз наложить заклинание " +
            "адское возмездие [hellish rebuke] как заклинание 2-го уровня с помощью этой особенности. " +
            "Начиная с 5-го уровня, вы также можете накладывать заклинание тьма [darkness] с помощью этой особенности."
        assertEquals(
            listOf("адское возмездие" to "Адское возмездие", "тьма" to "Тьма"),
            links(text),
        )
    }

    @Test
    fun `invocation with emphasis links bane`() {
        val text = "Вы можете один раз наложить заклинание *порча [bane]*, используя ячейку заклинаний колдуна."
        assertEquals(listOf("порча" to "Порча"), links(text))
    }

    @Test
    fun `declined name is matched by stem`() {
        val text = "Начиная с 3-го уровня, вы можете накладывать палящую кару [searing smite] как заклинание 2-го уровня."
        assertEquals(listOf("палящую кару" to "Палящая кара"), links(text))
    }

    @Test
    fun `names with slash and apostrophe`() {
        val result = links(
            "накладывать на себя заклинание увеличение/уменьшение [enlarge/reduce] без компонентов, " +
                "заклинание Тензеров парящий диск [Tenser’s floating disk] с помощью этой особенности",
        ).map { it.second }
        assertTrue(result.toString(), result.any { it.lowercase().startsWith("увеличение") })
        assertTrue(result.toString(), result.any { it.lowercase().contains("парящий диск") })
    }

    @Test
    fun `range covers only russian name`() {
        val text = "заклинание тьма [darkness]"
        val link = index.find(text).single()
        assertEquals("тьма", text.substring(link.range))
    }

    @Test
    fun `no brackets no links`() {
        assertTrue(links("Вы можете накладывать заклинание тьма с помощью этой особенности.").isEmpty())
        assertTrue(links("Бросьте к20 [и прибавьте модификатор].").isEmpty())
    }
}
