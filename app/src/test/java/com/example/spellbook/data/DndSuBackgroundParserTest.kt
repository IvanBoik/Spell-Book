package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера страницы предыстории.
 *
 * Разметка воспроизводит структуру dnd.su: название с английским дубликатом,
 * плашка источника, абзацы владений сразу за художественным описанием и
 * заголовки разделов классом `smallSectionTitle`.
 */
class DndSuBackgroundParserTest {

    private fun page(
        title: String = "Тестпредыстория [Testbackground]",
        plaque: String = "<span class='source-plaque' title=\"Тестовая книга\">TB</span>",
        body: String = DESCRIPTION + TRAITS,
    ) = """
        <html><body>
          <h2 class="card-title"><span data-copy="$title">$title</span>$plaque</h2>
          <div itemprop="description">$body</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `english name in brackets is stripped`() {
        val parsed = DndSuBackgroundParser.parse(page())

        assertEquals("Тестпредыстория", parsed.name)
    }

    @Test
    fun `book is taken from the source plaque`() {
        val parsed = DndSuBackgroundParser.parse(page())

        assertEquals("Тестовая книга", parsed.book)
    }

    @Test
    fun `trait paragraphs are wrapped into a callout`() {
        val lines = DndSuBackgroundParser.parse(page()).description.lines()

        // Художественное описание остаётся снаружи, владения — внутри врезки.
        val start = lines.indexOfFirst { HtmlUtils.isCalloutEnd(it) }
        val end = lines.indexOfLast { HtmlUtils.isCalloutEnd(it) }
        assertTrue("Врезка не найдена", start in 1..<end)
        assertTrue(
            "Описание не должно попадать во врезку",
            lines.subList(0, start).any { it.contains("Художественное описание") },
        )
        assertTrue(
            "Владения должны быть внутри врезки",
            lines.subList(start, end).any { it.contains("Владение навыками") },
        )
    }

    @Test
    fun `text after traits stays outside the callout`() {
        val body = DESCRIPTION + TRAITS + "<p>Продолжение текста.</p>"

        val lines = DndSuBackgroundParser.parse(page(body = body)).description.lines()

        val end = lines.indexOfLast { HtmlUtils.isCalloutEnd(it) }
        assertTrue(
            "Текст после владений должен остаться снаружи",
            lines.drop(end + 1).any { it.contains("Продолжение текста") },
        )
    }

    @Test
    fun `page without traits has no callout`() {
        val lines = DndSuBackgroundParser.parse(page(body = DESCRIPTION)).description.lines()

        assertTrue("Врезки быть не должно", lines.none { HtmlUtils.isCalloutEnd(it) })
    }

    @Test
    fun `section titles become top level headings`() {
        val body = DESCRIPTION + "<h3 class=\"smallSectionTitle\">Умение: Проверка</h3><p>Текст.</p>"

        val lines = DndSuBackgroundParser.parse(page(body = body)).description.lines()

        // Разделы предыстории на сайте не сворачиваются — значит, это `# `, а не `## `.
        assertTrue(lines.any { HtmlUtils.isTopHeading(it) && it.contains("Умение: Проверка") })
    }

    @Test
    fun `table titles become bold captions`() {
        val body = DESCRIPTION + "<h4 class=\"tableTitle\">Черты характера</h4>"

        val lines = DndSuBackgroundParser.parse(page(body = body)).description.lines()

        // Подпись таблицы не должна становиться разделом: иначе таблица отрывается от текста.
        assertTrue(lines.any { it.trim() == "**Черты характера**" })
        assertTrue(lines.none { HtmlUtils.isTopHeading(it) })
    }

    private companion object {
        const val DESCRIPTION = "<p>Художественное описание предыстории.</p>"

        /** Игровые параметры идут обычными абзацами с жирной подписью — как на сайте. */
        const val TRAITS = "<p><strong>Владение навыками:</strong> Акробатика.</p>" +
            "<p><strong>Снаряжение:</strong> Костюм, кошель с 15 зм</p>"
    }
}
