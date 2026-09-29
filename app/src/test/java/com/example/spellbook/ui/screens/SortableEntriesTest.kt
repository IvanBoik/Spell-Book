package com.example.spellbook.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты сортируемых разделов класса: инфузий изобретателя, воззваний колдуна.
 *
 * Варианты сортировки те же, что на dnd.su, — по названию и по уровню.
 */
class SortableEntriesTest {

    private val body = """
        Вступительный абзац раздела.
        Сортировка:
        - По уровню
        - По названию
        ### ГАММА
        *Требование: 6-й уровень изобретателя*
        Текст гаммы, упоминает заклинание 3 уровня.
        ### Альфа
        Текст альфы без требования.
        ### Бета
        *Требование: 14 уровень изобретателя*
        Текст беты.
        ### Ёжик
        *Требование: умение «Договор клинка»*
        Требование есть, но не по уровню.
    """.trimIndent()

    private val section = requireNotNull(parseSortableSection(body))

    @Test
    fun `section without the marker is not sortable`() {
        assertNull(parseSortableSection("Обычный текст без сортировки.\n### Запись\nТекст."))
    }

    @Test
    fun `intro keeps the text before the marker`() {
        assertEquals("Вступительный абзац раздела.", section.intro)
    }

    @Test
    fun `sort options from the site are not shown as entries`() {
        assertEquals(listOf("ГАММА", "Альфа", "Бета", "Ёжик"), section.entries.map { it.title })
    }

    @Test
    fun `level is taken only from the requirement line`() {
        // «заклинание 3 уровня» в тексте записи не должно считаться требованием.
        assertEquals(listOf(6, null, 14, null), section.entries.map { it.level })
    }

    @Test
    fun `sorting by name ignores case and treats yo as ye`() {
        // Капс у части заголовков сайта не должен ломать алфавит.
        assertEquals(
            listOf("Альфа", "Бета", "ГАММА", "Ёжик"),
            section.sorted(EntrySortOrder.NAME).map { it.title },
        )
    }

    @Test
    fun `sorting by level puts entries without a level requirement first`() {
        assertEquals(
            listOf("Альфа", "Ёжик", "ГАММА", "Бета"),
            section.sorted(EntrySortOrder.LEVEL).map { it.title },
        )
    }

    @Test
    fun `the two orders actually differ`() {
        // Прежняя версия показывала одинаковый порядок при любом выборе.
        assert(section.sorted(EntrySortOrder.NAME) != section.sorted(EntrySortOrder.LEVEL))
    }

    @Test
    fun `sorting never loses entries`() {
        EntrySortOrder.entries.forEach { order ->
            assertEquals(section.entries.size, section.sorted(order).size)
        }
    }

    @Test
    fun `marker without entries is not treated as sortable`() {
        assertNull(parseSortableSection("Текст.\nСортировка:\n- По уровню"))
    }
}
