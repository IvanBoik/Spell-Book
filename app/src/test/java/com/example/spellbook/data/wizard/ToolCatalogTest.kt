package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Справочник инструментов: поиск по тексту правил и подбор вариантов выбора. */
class ToolCatalogTest {

    private fun ids(text: String) = ToolCatalog.candidates(text).map { it.id }

    private fun category(category: ToolCategory) = ToolCatalog.all.filter { it.category == category }.map { it.id }

    @Test
    fun `catalog has unique ids and full content`() {
        assertEquals(ToolCatalog.all.size, ToolCatalog.all.map { it.id }.toSet().size)
        assertEquals(17, category(ToolCategory.ARTISAN).size)
        assertEquals(10, category(ToolCategory.MUSICAL).size)
        assertEquals(4, category(ToolCategory.GAMING).size)
        ToolCatalog.all.forEach {
            assertTrue(it.name, it.description.isNotBlank() && it.uses.isNotBlank())
            assertEquals(it, ToolCatalog.byId(it.id))
        }
    }

    @Test
    fun `find in any case and markup`() {
        assertEquals("smith", ToolCatalog.find("кузнеца")?.id)
        assertEquals("brewer", ToolCatalog.find("[[ref пивовара]]")?.id)
        assertEquals("poisoner", ToolCatalog.find("Набор отравителя")?.id)
        assertEquals("navigator", ToolCatalog.find("инструменты навигатора")?.id)
        assertEquals("thieves", ToolCatalog.find("воровские инструменты")?.id)
        assertEquals("cook", ToolCatalog.find("Инструменты повара")?.id)
        assertEquals("brewer", ToolCatalog.find("Инструменты пивовара")?.id)
        assertEquals("three_dragon_ante", ToolCatalog.find("ставка трёх драконов")?.id)
        assertEquals(null, ToolCatalog.find("лирандар"))
    }

    @Test
    fun `category choices`() {
        assertEquals(category(ToolCategory.MUSICAL), ids("Три музыкальных инструмента на ваш выбор"))
        assertEquals(
            category(ToolCategory.GAMING),
            ids("один игровой набор на ваш выбор (например, игральные карты или ставка трёх драконов)."),
        )
        assertEquals(category(ToolCategory.GAMING), ids("Один [[ref игровой набор]]."))
        assertEquals(category(ToolCategory.ARTISAN), ids("Один вид ремесленных инструментов."))
        assertEquals(category(ToolCategory.ARTISAN), ids("инструменты ремесленника"))
        assertEquals(
            (category(ToolCategory.ARTISAN) + category(ToolCategory.MUSICAL)).toSet(),
            ids("Выберите один вид инструмента ремесленников, либо музыкального инструмента.").toSet(),
        )
    }

    @Test
    fun `specific tools`() {
        assertEquals(listOf("smith", "brewer", "mason"), ids("инструменты [[ref кузнеца]], [[ref пивовара]] или [[ref каменщика]]"))
        assertEquals(
            listOf("smith", "brewer", "mason"),
            ids("Вы владеете ремесленными инструментами на ваш выбор: инструменты [[ref кузнеца]], [[ref пивовара]] или [[ref каменщика]]."),
        )
        assertEquals(listOf("cartographer", "navigator"), ids("[[ref Инструменты картографа]] или [[ref инструменты навигатора]]."))
        assertEquals(setOf("land_vehicles", "water_vehicles"), ids("[[ref Транспорт (водный, наземный)]]").toSet())
        assertEquals(ToolCatalog.all.size, ids("что-то непонятное").size)
    }

    @Test
    fun `coverage on bundled backgrounds and classes`() {
        val backgrounds = BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
        val classes = ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
        val lines = backgrounds.flatMap { bg -> toolLines(bg.description, "владение инструментами").map { bg.name to it } } +
            classes.flatMap { cls -> toolLines(cls.description, "инструменты").map { cls.name to it } }
        assertTrue("Нет строк с инструментами", lines.size > 40)
        val uncovered = lines.filter { (_, line) ->
            val plain = MarkupText.plain(line).lowercase()
            !plain.startsWith("нет") && ToolCatalog.candidates(line).size == ToolCatalog.all.size
        }
        println("Строк с инструментами: ${lines.size}, не распознано: ${uncovered.size}")
        uncovered.forEach { (owner, line) -> println("  $owner :: $line") }
    }

    /** Значения строк `**Название**: …` с указанным названием. */
    private fun toolLines(description: String, key: String): List<String> =
        description.lines().mapNotNull { raw ->
            val match = Regex("^\\*\\*([^*]+?)\\*\\*\\s*:\\s*(.*)$").matchEntire(raw.trim()) ?: return@mapNotNull null
            match.groupValues[2].takeIf { match.groupValues[1].trim().lowercase() == key }
        }
}
