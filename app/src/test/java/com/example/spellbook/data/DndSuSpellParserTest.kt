package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты разбора страницы заклинания dnd.su на HTML-фикстурах. */
class DndSuSpellParserTest {

    private companion object {
        const val TITLE = "Огненный шар [Fireball]"
        const val TYPE_LINE = "3 уровень, воплощение"
        const val CASTING_TIME = "1 действие"
        const val RANGE = "150 футов"
        const val COMPONENTS = "В, С, М (крошечный шарик из гуано летучей мыши и серы)"
        const val DURATION = "Мгновенная"
        const val CLASSES = "Волшебник, Чародей"
        const val DESCRIPTION_HTML =
            """<p>Яркая вспышка. Существо получает <span tooltip-for="fire">урон огнём</span> 8d6.</p>"""
    }

    /** Собирает страницу dnd.su с указанными параметрами. */
    private fun page(
        title: String = TITLE,
        typeLine: String = TYPE_LINE,
        castingTime: String = CASTING_TIME,
        range: String = RANGE,
        components: String = COMPONENTS,
        duration: String = DURATION,
        classes: String = CLASSES,
        subclasses: String? = null,
        descriptionHtml: String = DESCRIPTION_HTML,
    ): String = """
        <html><body>
          <h2 class="card-title"><span data-copy="x">$title</span></h2>
          <ul class="params">
            <li class="size-type-alignment">$typeLine</li>
            <li><strong>Время накладывания:</strong> $castingTime</li>
            <li><strong>Дистанция:</strong> $range</li>
            <li><strong>Компоненты:</strong> $components</li>
            <li><strong>Длительность:</strong> $duration</li>
            <li><strong>Классы:</strong> $classes</li>
            ${subclasses?.let { "<li><strong>Подклассы:</strong> $it</li>" } ?: ""}
            <li class="subsection desc">
              <div itemprop="description">$descriptionHtml</div>
            </li>
          </ul>
        </body></html>
    """.trimIndent()

    // region Полный разбор

    @Test
    fun `parses all main page fields`() {
        val spell = DndSuSpellParser.parse(page())

        assertEquals("Огненный шар", spell.name)
        assertEquals("dnd.su", spell.source)
        assertEquals(3, spell.level)
        assertEquals("evo", spell.school)
        assertEquals("action", spell.activationType)
        assertEquals(1, spell.activationCost)
        assertEquals(150, spell.rangeValue)
        assertEquals("ft", spell.rangeUnits)
        assertEquals("inst", spell.durationUnits)
        assertNull(spell.durationValue)
        assertEquals(listOf("wizard", "sorcerer"), spell.classes)
    }

    @Test
    fun `parses school note in parentheses`() {
        // Формат Wildemount: «преобразование (дюнамантия: гравитургия)».
        val spell = DndSuSpellParser.parse(
            page(typeLine = "2 уровень, преобразование (дюнамантия: гравитургия)"),
        )

        assertEquals("trs", spell.school)
        assertEquals("дюнамантия: гравитургия", spell.schoolNote)
    }

    @Test
    fun `parses school note without subtype`() {
        val spell = DndSuSpellParser.parse(page(typeLine = "7 уровень, некромантия (дюнамантия)"))

        assertEquals("nec", spell.school)
        assertEquals("дюнамантия", spell.schoolNote)
    }

    @Test
    fun `ritual mark is not treated as school note`() {
        // «(ритуал)» — пометка ритуала, а не уточнение школы.
        val spell = DndSuSpellParser.parse(page(typeLine = "1 уровень, прорицание (ритуал)"))

        assertEquals("div", spell.school)
        assertEquals("", spell.schoolNote)
        assertTrue(spell.components.ritual)
    }

    @Test
    fun `school note is empty when there are no parentheses`() {
        assertEquals("", DndSuSpellParser.parse(page()).schoolNote)
    }

    @Test
    fun `parses subclasses when the page has them`() {
        // Формат dnd.su: «подкласс (класс)» через запятую.
        val spell = DndSuSpellParser.parse(
            page(subclasses = "домен магии (жрец), круг земли (друид)"),
        )

        assertEquals(listOf("домен магии (жрец)", "круг земли (друид)"), spell.subclasses)
    }

    @Test
    fun `subclasses are empty when the page has no such field`() {
        assertEquals(emptyList<String>(), DndSuSpellParser.parse(page()).subclasses)
    }

    @Test
    fun `name is cleaned from bracketed english variant`() {
        assertEquals("Щит", DndSuSpellParser.parse(page(title = "Щит [Shield]")).name)
    }

    @Test
    fun `name without english variant is taken as is`() {
        assertEquals("Щит", DndSuSpellParser.parse(page(title = "Щит")).name)
    }

    @Test
    fun `description is converted to plain text with ref tokens`() {
        val spell = DndSuSpellParser.parse(page())

        assertEquals("Яркая вспышка. Существо получает [[ref урон огнём]] 8d6.", spell.description)
    }

    // endregion

    // region Уровень, школа, ритуал

    @Test
    fun `cantrip is detected by level word`() {
        val spell = DndSuSpellParser.parse(page(typeLine = "Заговор, воплощение"))

        assertEquals(0, spell.level)
        assertEquals("evo", spell.school)
    }

    @Test
    fun `ritual mark does not break school detection`() {
        val spell = DndSuSpellParser.parse(page(typeLine = "1 уровень, прорицание (ритуал)"))

        assertEquals(1, spell.level)
        assertEquals("div", spell.school)
        assertTrue(spell.components.ritual)
    }

    @Test
    fun `ritual flag stays false without ritual mark`() {
        assertFalse(DndSuSpellParser.parse(page()).components.ritual)
    }

    @Test
    fun `unknown school falls back to evocation`() {
        assertEquals("evo", DndSuSpellParser.parse(page(typeLine = "2 уровень, хронургия")).school)
    }

    // endregion

    // region Активация, дистанция, длительность

    @Test
    fun `bonus action is detected before plain action`() {
        val spell = DndSuSpellParser.parse(page(castingTime = "1 бонусное действие"))

        assertEquals("bonus", spell.activationType)
        assertEquals(1, spell.activationCost)
    }

    @Test
    fun `reaction without number gets default cost`() {
        val spell = DndSuSpellParser.parse(page(castingTime = "Реакция, совершаемая при попадании"))

        assertEquals("reaction", spell.activationType)
        assertEquals(1, spell.activationCost)
    }

    @Test
    fun `casting time in minutes is parsed with amount`() {
        val spell = DndSuSpellParser.parse(page(castingTime = "10 минут"))

        assertEquals("minute", spell.activationType)
        assertEquals(10, spell.activationCost)
    }

    @Test
    fun `touch and self ranges have no numeric value`() {
        val touch = DndSuSpellParser.parse(page(range = "Касание"))
        val self = DndSuSpellParser.parse(page(range = "На себя"))

        assertEquals("touch", touch.rangeUnits)
        assertNull(touch.rangeValue)
        assertEquals("self", self.rangeUnits)
        assertNull(self.rangeValue)
    }

    @Test
    fun `range in miles is parsed correctly`() {
        val spell = DndSuSpellParser.parse(page(range = "1 миля"))

        assertEquals("mi", spell.rangeUnits)
        assertEquals(1, spell.rangeValue)
    }

    @Test
    fun `concentration is detected in duration`() {
        val spell = DndSuSpellParser.parse(page(duration = "Концентрация, вплоть до 1 минуты"))

        assertEquals("minute", spell.durationUnits)
        assertEquals(1, spell.durationValue)
        assertTrue(spell.components.concentration)
    }

    @Test
    fun `duration in hours is parsed correctly`() {
        val spell = DndSuSpellParser.parse(page(duration = "8 часов"))

        assertEquals("hour", spell.durationUnits)
        assertEquals(8, spell.durationValue)
        assertFalse(spell.components.concentration)
    }

    // endregion

    // region Компоненты и классы

    @Test
    fun `component letters are converted into flags`() {
        val components = DndSuSpellParser.parse(page()).components

        assertTrue(components.vocal)
        assertTrue(components.somatic)
        assertTrue(components.material)
        assertEquals(COMPONENTS, components.value)
    }

    @Test
    fun `material components are taken from parentheses`() {
        val spell = DndSuSpellParser.parse(page())

        assertEquals("крошечный шарик из гуано летучей мыши и серы", spell.materials.value)
    }

    @Test
    fun `material components are empty without parentheses`() {
        val spell = DndSuSpellParser.parse(page(components = "В, С"))

        assertEquals("", spell.materials.value)
        assertFalse(spell.components.material)
    }

    @Test
    fun `source marks near class names are dropped`() {
        val spell = DndSuSpellParser.parse(page(classes = "Волшебник (TCE), Изобретатель"))

        assertEquals(listOf("wizard", "artificer"), spell.classes)
    }

    @Test
    fun `unknown classes are ignored and duplicates removed`() {
        val spell = DndSuSpellParser.parse(page(classes = "Волшебник, Монах, Волшебник"))

        assertEquals(listOf("wizard"), spell.classes)
    }

    // endregion

    @Test(expected = IllegalArgumentException::class)
    fun `page without params block throws error`() {
        DndSuSpellParser.parse("<html><body><h2 class=\"card-title\">Щит</h2></body></html>")
    }
}
