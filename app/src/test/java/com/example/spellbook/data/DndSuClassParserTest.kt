package com.example.spellbook.data

import com.example.spellbook.util.HtmlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера страницы класса.
 *
 * Главное требование: подклассы из Unearthed Arcana сохраняются, а homebrew
 * вырезается. Отличить их можно только по префиксу якоря (`ua.` против `hb.`),
 * поэтому именно эта логика и проверяется.
 */
class DndSuClassParserTest {

    private fun page(
        title: String = "Тесткласс [Testclass]",
        params: String = "<ul class=\"params\"><li><strong>Источник:</strong> «<span>Тестовая книга</span>»</li></ul>",
        body: String = "<p>Описание класса.</p>",
    ) = """
        <html><body>
          <h2 class="card-title">$title</h2>
          $params
          <div class="desc" itemprop="articleBody">$body</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `english name in brackets is stripped`() {
        assertEquals("Тесткласс", DndSuClassParser.parse(page()).name)
    }

    @Test
    fun `book is taken from the source line`() {
        assertEquals("Тестовая книга", DndSuClassParser.parse(page()).book)
    }

    @Test
    fun `source plaque wins over the params line`() {
        val html = page(
            title = "Тесткласс<span class='source-plaque' title=\"Player's Handbook\">PH</span>",
        )

        assertEquals("Player's Handbook", DndSuClassParser.parse(html).book)
    }

    @Test
    fun `unearthed arcana subclasses are kept`() {
        val html = page(
            body = """
                <h2><span id='unofficial'>Unearthed Arcana</span></h2>
                <h3><span id='ua.primals'>Пути дикости из «Unearthed Arcana»</span></h3>
                <p>Текст подкласса из UA.</p>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertTrue(description.contains("Unearthed Arcana"))
        assertTrue(description.contains("Текст подкласса из UA."))
    }

    @Test
    fun `homebrew subclasses are removed with their text`() {
        val html = page(
            body = """
                <p>Основной текст класса.</p>
                <h3><span id='hb.primals'>Пути дикости из «Homebrew»</span></h3>
                <p>Текст homebrew-подкласса.</p>
                <h3><span id='hb.primal.depth'>Путь глубин</span></h3>
                <p>Ещё текст homebrew.</p>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertTrue(description.contains("Основной текст класса."))
        assertFalse(description.contains("Homebrew"))
        assertFalse(description.contains("Текст homebrew-подкласса."))
        assertFalse(description.contains("Путь глубин"))
    }

    @Test
    fun `homebrew group is removed even when its subclasses have other anchors`() {
        // Так устроен волшебник: в группе «Homebrew» лежат подклассы Midgard с якорями `mhh.*`,
        // а у жреца заголовок группы homebrew имеет тот же якорь, что и группа UA.
        val html = page(
            body = """
                <p>Основной текст класса.</p>
                <h2 class="bigSectionTitle"><span id='ua.domains'>Домены из «Unearthed Arcana»</span></h2>
                <h2 class="bigSectionTitle hide-next"><span id='ua.domain.x'>Домен UA</span></h2>
                <div class="hide-wrapper"><p>Текст UA.</p></div>
                <h2 class="bigSectionTitle"><span id='ua.domains'>Домены из «Homebrew»</span></h2>
                <div class="addition-wrapper">
                  <h2 class="bigSectionTitle hide-next"><span id='mhh.domain.y'>Домен Midgard</span></h2>
                  <div class="hide-wrapper"><p>Текст Midgard.</p></div>
                </div>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertTrue(description.contains("Текст UA."))
        assertFalse(description.contains("Homebrew"))
        assertFalse(description.contains("Домен Midgard"))
        assertFalse(description.contains("Текст Midgard."))
    }

    @Test
    fun `italic spanning several blocks does not glue the text into one line`() {
        // Незакрытый `<em>` сайта у волшебника склеивал половину страницы в одну строку.
        val html = page(
            body = "<em><p>Первый абзац.</p><h3>Способность</h3><p>Второй абзац.</p></em>",
        )

        val lines = DndSuClassParser.parse(html).description.lines()

        assertTrue(lines.size >= 3)
        assertTrue(lines.any { it == "### Способность" })
    }

    @Test
    fun `only hide-next headings become collapsible sections`() {
        val html = page(
            body = """
                <p>Вступление.</p>
                <h3 class="underlined spoiler_head">ОПИСАНИЕ КЛАССА</h3>
                <p>Нарратив.</p>
                <h2 class="bigSectionTitle"><span id='class-features'>Классовые умения</span></h2>
                <h3 class="underlined">ЯРОСТЬ</h3>
                <p>Текст ярости.</p>
                <h2 class="bigSectionTitle hide-next hide-next-h2">Путь берсерка</h2>
                <p>Текст подкласса.</p>
            """.trimIndent(),
        )

        val lines = DndSuClassParser.parse(html).description.lines()

        assertEquals(listOf("## Путь берсерка"), lines.filter { it.startsWith("## ") })
        assertTrue(lines.contains("# Классовые умения"))
        assertTrue(lines.contains("### ЯРОСТЬ"))
        // Нарративные заголовки с spoiler_head — обычные подзаголовки, а не сворачиваемые блоки.
        assertTrue(lines.contains("### ОПИСАНИЕ КЛАССА"))
    }

    @Test
    fun `collapsed preview line is not duplicated`() {
        // На сайте это первая строка врезки, видная до раскрытия блока.
        val html = page(
            body = """
                <div class="spoiler_head_body">
                  <blockquote>Художественная врезка.</blockquote>
                  <p>Описание класса.</p>
                </div>
                <blockquote class="spoiler_neck">Художественная</blockquote>
            """.trimIndent(),
        )

        val lines = DndSuClassParser.parse(html).description.lines()

        assertEquals(listOf("> Художественная врезка.", "Описание класса."), lines)
    }

    @Test
    fun `column titles keep the abbreviation and its full form`() {
        // Сайт держит оба варианта сразу; без разбора они склеивались в «Заговорыиз».
        val html = page(
            body = """
                <table>
                  <tr class="table_header">
                    <td><span class="long">Известные<br>заговоры</span><span class="short tooltip" title="Известные заговоры">из</span></td>
                  </tr>
                  <tr><td>2</td></tr>
                </table>
            """.trimIndent(),
        )

        val header = DndSuClassParser.parse(html).description.lines().first()
        val token = HtmlUtils.ABBR_TOKEN_REGEX.find(header)

        assertEquals("из", token?.groupValues?.get(1))
        assertEquals("Известные заговоры", token?.groupValues?.get(2))
    }

    @Test
    fun `abbreviation token survives the table cell escaping`() {
        // Внутри ячейки `|` заменяется на `/`, поэтому разделителем токена он быть не может.
        val html = page(
            body = """
                <table>
                  <tr class="table_header">
                    <td><span class="long">Бонус мастерства</span><span class="short" title="Бонус мастерства">бм</span></td>
                    <td>Умения</td>
                  </tr>
                  <tr><td>+2</td><td>Ярость</td></tr>
                </table>
            """.trimIndent(),
        )

        val header = DndSuClassParser.parse(html).description.lines().first()
        val cells = HtmlUtils.parseTableRow(header)

        assertEquals(2, cells.size)
        assertTrue(HtmlUtils.ABBR_TOKEN_REGEX.matches(cells[0]))
        assertEquals("Умения", cells[1])
    }

    @Test
    fun `official content after a homebrew block survives`() {
        // Блок homebrew заканчивается на следующем заголовке того же уровня.
        val html = page(
            body = """
                <h3><span id='hb.primal.depth'>Путь глубин</span></h3>
                <p>Текст homebrew.</p>
                <h3><span id='primal.berserker'>Путь берсерка</span></h3>
                <p>Официальный текст.</p>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertFalse(description.contains("Путь глубин"))
        assertTrue(description.contains("Путь берсерка"))
        assertTrue(description.contains("Официальный текст."))
    }

    @Test
    fun `menu links to removed homebrew sections are dropped`() {
        val html = page(
            body = """
                <p>Основной текст класса.</p>
                <p><a href="#hb.primals">Пути дикости из «Homebrew»</a></p>
                <p><a href="#ua.primals">Пути дикости из «Unearthed Arcana»</a></p>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertFalse(description.contains("Homebrew"))
        assertTrue(description.contains("Unearthed Arcana"))
    }

    @Test
    fun `embedded statblocks are removed`() {
        // На страницах классов встречаются карточки существ — это не часть класса.
        val html = page(
            body = """
                <p>Текст класса.</p>
                <div class="card embed"><h2 class="card-title">Первобытный страж</h2></div>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertTrue(description.contains("Текст класса."))
        assertFalse(description.contains("Первобытный страж"))
    }

    @Test
    fun `site navigation menu is not part of the description`() {
        val html = page(
            body = """
                <ul class="new-article-menu"><li><a href="#barbarian">Варвар</a></li></ul>
                <p>Текст класса.</p>
            """.trimIndent(),
        )

        val description = DndSuClassParser.parse(html).description

        assertEquals("Текст класса.", description)
    }

    @Test
    fun `tooltips become ref tokens`() {
        val html = page(body = "<p>Существо <span tooltip-for='size.large'>Большого</span> размера.</p>")

        assertTrue(DndSuClassParser.parse(html).description.contains("[[ref Большого]]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `page without description is rejected`() {
        DndSuClassParser.parse("<html><body><h2 class=\"card-title\">Класс</h2></body></html>")
    }
}
