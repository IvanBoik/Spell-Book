package com.example.spellbook.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты разбиения описания расы на разделы.
 *
 * По этим разделам строятся меню навигации и сворачивание, поэтому важно, чтобы
 * текст не терялся и заголовки определялись корректно.
 */
class RaceSectionsTest {

    @Test
    fun `text before first heading becomes intro`() {
        val sections = splitDescriptionSections("Вступительный абзац.\n## Раздел\nТекст раздела.")

        assertEquals(2, sections.size)
        assertTrue(sections.first().isIntro)
        assertEquals("Вступительный абзац.", sections.first().body)
    }

    @Test
    fun `headings split the description`() {
        val description = """
            ## Первый
            Текст первого.
            ## Второй
            Текст второго.
        """.trimIndent()

        val sections = splitDescriptionSections(description)

        assertEquals(listOf("Первый", "Второй"), sections.map { it.title })
        assertEquals("Текст первого.", sections[0].body)
        assertEquals("Текст второго.", sections[1].body)
    }

    @Test
    fun `multiline body is kept as is`() {
        val description = "## Раздел\nПервая строка.\nВторая строка."

        val sections = splitDescriptionSections(description)

        assertEquals("Первая строка.\nВторая строка.", sections.single().body)
    }

    @Test
    fun `heading without text is dropped`() {
        // Пустой раздел только засоряет меню навигации.
        val sections = splitDescriptionSections("## Пустой\n## Полный\nЕсть текст.")

        assertEquals(listOf("Полный"), sections.map { it.title })
    }

    @Test
    fun `heading without text becomes a label for the next section`() {
        // Так на dnd.su оформлен разделитель перед неофициальными подрасами.
        val sections = splitDescriptionSections("## Подрасы из UA\n## Тифлинг Бездны\nОписание.")

        assertEquals("Подрасы из UA", sections.single().label)
        assertEquals("Тифлинг Бездны", sections.single().title)
    }

    @Test
    fun `label applies only to the nearest section`() {
        val sections = splitDescriptionSections(
            "## Разделитель\n## Первый\nТекст.\n## Второй\nТекст.",
        )

        assertEquals("Разделитель", sections[0].label)
        assertEquals("", sections[1].label)
    }

    @Test
    fun `subheadings stay inside the section body`() {
        // Иначе «Таблицы эльфов» рассыпаются на десяток отдельных блоков.
        val sections = splitDescriptionSections(
            "## Таблицы\n### Первая\n| A |\n### Вторая\n| B |",
        )

        assertEquals(1, sections.size)
        assertEquals("Таблицы", sections.single().title)
        assertEquals("### Первая\n| A |\n### Вторая\n| B |", sections.single().body)
    }

    @Test
    fun `description without headings is a single intro`() {
        val sections = splitDescriptionSections("Просто текст без заголовков.")

        assertEquals(1, sections.size)
        assertTrue(sections.single().isIntro)
    }

    @Test
    fun `blank description gives no sections`() {
        assertTrue(splitDescriptionSections("   ").isEmpty())
    }

    @Test
    fun `quote lines stay in the intro`() {
        val sections = splitDescriptionSections("> Цитата\nОбычный текст.")

        assertTrue(sections.single().isIntro)
        assertEquals("> Цитата\nОбычный текст.", sections.single().body)
    }

    @Test
    fun `nothing is lost from the original text`() {
        val description = "Вступление.\n## Раздел\nТекст.\n## Другой\nЕщё текст."

        val sections = splitDescriptionSections(description)

        val restored = sections.joinToString("\n") { section ->
            if (section.isIntro) section.body else "## ${section.title}\n${section.body}"
        }
        assertEquals(description, restored)
    }

    @Test
    fun `top heading starts a part that does not collapse`() {
        val sections = splitDescriptionSections("# Классовые умения\n### Ярость\nТекст ярости.")

        assertEquals("Классовые умения", sections.single().title)
        assertTrue(!sections.single().isCollapsible)
    }

    @Test
    fun `collapsible block ends at the next top heading`() {
        // Врезка с предысторией не должна поглотить идущие за ней «Классовые умения».
        val sections = splitDescriptionSections(
            "## Углублённая предыстория\nТекст.\n# Классовые умения\nУмения.",
        )

        assertEquals(listOf(true, false), sections.map { it.isCollapsible })
        assertEquals("Текст.", sections.first().body)
    }

    @Test
    fun `chunks split the part by subheadings`() {
        val section = splitDescriptionSections("# Часть\nВвод.\n### Первое\nА.\n### Второе\nБ.").single()

        assertEquals(listOf("Ввод.", "### Первое\nА.", "### Второе\nБ."), section.chunks())
    }

    @Test
    fun `chunks do not cut a callout in half`() {
        val section = splitDescriptionSections("# Часть\n::: Врезка\n### Внутри\nТекст.\n:::").single()

        assertEquals(1, section.chunks().size)
    }
}
