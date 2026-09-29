package com.example.spellbook.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты перехода из таблицы развития класса к описанию умения.
 *
 * Названия в таблице и заголовки в тексте на dnd.su почти никогда не совпадают
 * дословно — каждый случай ниже взят с реальной страницы класса.
 */
class FeatureLookupTest {

    private val description = """
        # Классовые умения
        ### ЯРОСТЬ
        Текст ярости.
        ### Вдохновение барда
        Текст вдохновения.
        ### Дополнительные заклинания барда
        Список заклинаний.
        ### Дикий облик
        Текст облика.
        ### Атака, наделённая ци
        Текст атаки.
        ### Искусный исследователь
        Текст.
        ### АУРА ЗАЩИТЫ
        Текст ауры.
        # Метамагия
        Текст метамагии.
        # Пути дикости
        ## Путь берсерка
        ### Бешенство
        Текст.
        ## Путь зверя
        ### ЯРОСТЬ
        Одноимённый подзаголовок у подкласса.
    """.trimIndent()

    private val sections = splitDescriptionSections(description)

    private fun find(feature: String) = sections.findFeature(feature)

    private fun titleOf(target: FeatureTarget?): String? = target?.let {
        val section = sections[it.sectionIndex]
        it.subheadingIndex?.let { sub -> section.subheadings()[sub] } ?: section.title
    }

    @Test
    fun `case does not matter`() {
        assertEquals("ЯРОСТЬ", titleOf(find("Ярость")))
    }

    @Test
    fun `class features win over a subclass subheading with the same name`() {
        assertEquals(0, find("Ярость")?.sectionIndex)
    }

    @Test
    fun `details in parentheses are ignored`() {
        assertEquals("Вдохновение барда", titleOf(find("Вдохновение барда (к6)")))
    }

    @Test
    fun `heading may be longer than the table name`() {
        assertEquals("Дополнительные заклинания барда", titleOf(find("Дополнительные заклинания")))
    }

    @Test
    fun `leading dash from the site is ignored`() {
        assertEquals("Дикий облик", titleOf(find("-Дикий облик")))
    }

    @Test
    fun `second half of a name with a comma still links`() {
        // В ячейке «Атака, наделённая ци» рвётся по запятой на два умения.
        assertEquals("Атака, наделённая ци", titleOf(find("наделённая ци")))
    }

    @Test
    fun `improvement of a feature leads to the base feature`() {
        assertEquals("Дикий облик", titleOf(find("Улучшение дикого облика")))
    }

    @Test
    fun `improvement of several auras leads to the first aura`() {
        assertEquals("АУРА ЗАЩИТЫ", titleOf(find("Улучшения ауры")))
    }

    @Test
    fun `list of features in one link leads to the first of them`() {
        assertEquals(
            "Искусный исследователь",
            titleOf(find("искусного исследователя и исследователя природы")),
        )
    }

    @Test
    fun `feature may be a whole part of the article`() {
        assertEquals("Метамагия", titleOf(find("Варианты метамагии")))
        assertNull(find("Метамагия")?.subheadingIndex)
    }

    @Test
    fun `subclass feature leads to the first subclass`() {
        val target = find("Умение пути")

        assertEquals("Путь берсерка", target?.let { sections[it.sectionIndex].title })
        assertNull(target?.subheadingIndex)
    }

    @Test
    fun `unknown feature gives nothing`() {
        assertNull(find("Несуществующее умение"))
    }
}
