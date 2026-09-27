package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера страницы расы.
 *
 * Разметка воспроизводит структуру dnd.su: название с английским дубликатом,
 * источник строкой в списке параметров и меню-навигацию в начале описания.
 */
class DndSuRaceParserTest {

    private fun page(
        title: String = "Тестраса [Testrace]",
        params: String = "<ul class=\"params\"><li><strong>Источник:</strong> «<span>Тестовая книга</span>»</li></ul>",
        body: String = "<p>Описание расы.</p>",
    ) = """
        <html><body>
          <h2 class="card-title">$title</h2>
          $params
          <div class="desc" itemprop="articleBody">$body</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `english name in brackets is stripped`() {
        val parsed = DndSuRaceParser.parse(page())

        assertEquals("Тестраса", parsed.name)
    }

    @Test
    fun `book is taken from the source line`() {
        val parsed = DndSuRaceParser.parse(page())

        assertEquals("Тестовая книга", parsed.book)
    }

    @Test
    fun `missing source gives empty book`() {
        val parsed = DndSuRaceParser.parse(page(params = ""))

        assertEquals("", parsed.book)
    }

    @Test
    fun `site navigation menu is removed from description`() {
        // Меню сайта дублирует навигацию приложения — в тексте оно только мешает.
        val body = """
            <p><ul class="new-article-menu">
              <li class="new-article-menu__li"><a href="#anchor">Ссылка меню</a></li>
            </ul></p>
            <h3 class="underlined">Раздел</h3><p>Текст раздела.</p>
        """.trimIndent()

        val parsed = DndSuRaceParser.parse(page(body = body))

        assertFalse(parsed.description.contains("Ссылка меню"))
        assertTrue(parsed.description.contains("Текст раздела."))
    }

    @Test
    fun `headings become markup headings for navigation`() {
        val body = "<h3 class=\"underlined\">Особенности</h3><p>Текст.</p>"

        val parsed = DndSuRaceParser.parse(page(body = body))

        assertTrue(parsed.description.contains("## Особенности"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `page without description is rejected`() {
        DndSuRaceParser.parse("<html><body><h2 class=\"card-title\">Имя</h2></body></html>")
    }
}
