package com.example.spellbook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты преобразования простого текста в HTML формата LSS и обратно. */
class HtmlUtilsTest {

    // region plainToHtml

    @Test
    fun `plainToHtml wraps every paragraph`() {
        assertEquals("<p>Первый</p><p>Второй</p>", HtmlUtils.plainToHtml("Первый\nВторой"))
    }

    @Test
    fun `plainToHtml skips blank lines and trims spaces`() {
        assertEquals("<p>Первый</p><p>Второй</p>", HtmlUtils.plainToHtml("  Первый  \n\n\r\n  Второй "))
    }

    @Test
    fun `plainToHtml escapes special characters`() {
        assertEquals("<p>a &lt; b &amp; c &gt; d</p>", HtmlUtils.plainToHtml("a < b & c > d"))
    }

    @Test
    fun `plainToHtml returns empty string for blank text`() {
        assertEquals("", HtmlUtils.plainToHtml(""))
        assertEquals("", HtmlUtils.plainToHtml("   \n  \n "))
    }

    @Test
    fun `plainToHtml converts table rows into html table`() {
        val text = "| Знание | Модификатор |\n| --- | --- |\n| Знакомые | -5 |"

        assertEquals(
            "<table><tbody>" +
                "<tr class=\"table_header\"><td>Знание</td><td>Модификатор</td></tr>" +
                "<tr><td>Знакомые</td><td>-5</td></tr>" +
                "</tbody></table>",
            HtmlUtils.plainToHtml(text),
        )
    }

    @Test
    fun `plainToHtml keeps paragraphs around a table`() {
        val html = HtmlUtils.plainToHtml("До\n| A | B |\nПосле")

        assertTrue(html.startsWith("<p>До</p><table>"))
        assertTrue(html.endsWith("</table><p>После</p>"))
    }

    @Test
    fun `table helpers detect rows and separators`() {
        assertTrue(HtmlUtils.isTableRow("| A | B |"))
        assertTrue(HtmlUtils.isTableSeparator("| --- | :---: |"))
        // Обычная строка с данными не должна считаться разделителем.
        assertEquals(false, HtmlUtils.isTableSeparator("| A | B |"))
        assertEquals(false, HtmlUtils.isTableRow("Обычный текст"))
    }

    @Test
    fun `parseTableRow splits cells and trims spaces`() {
        assertEquals(listOf("A", "B", "C"), HtmlUtils.parseTableRow("|  A |B  | C |"))
    }

    @Test
    fun `buildTableRow joins cells back`() {
        assertEquals("| A | B |", HtmlUtils.buildTableRow(listOf("A", "B")))
    }

    @Test
    fun `plainToHtml converts inline emphasis`() {
        assertEquals(
            "<p><strong>Жирный</strong> и <em>курсив</em></p>",
            HtmlUtils.plainToHtml("**Жирный** и *курсив*"),
        )
    }

    @Test
    fun `plainToHtml converts headings and lists`() {
        assertEquals("<h4>Итог</h4>", HtmlUtils.plainToHtml("## Итог"))
        assertEquals("<ul><li>Первый</li><li>Второй</li></ul>", HtmlUtils.plainToHtml("- Первый\n- Второй"))
        assertEquals("<ol><li>Первый</li><li>Второй</li></ol>", HtmlUtils.plainToHtml("1. Первый\n2. Второй"))
    }

    @Test
    fun `plainToHtml wraps callout block`() {
        val html = HtmlUtils.plainToHtml("::: Из Таши\nТекст врезки\n:::")

        assertEquals(
            "<div class=\"additionalInfo\">" +
                "<h3 class=\"smallSectionTitle\"><strong>Из Таши</strong></h3>" +
                "<p>Текст врезки</p></div>",
            html,
        )
    }

    @Test
    fun `list and callout helpers detect their markers`() {
        assertTrue(HtmlUtils.isBulletItem("- Пункт"))
        assertTrue(HtmlUtils.isNumberedItem("2. Пункт"))
        assertEquals("Пункт", HtmlUtils.listItemText("2. Пункт"))
        assertTrue(HtmlUtils.isCalloutStart("::: Заголовок"))
        assertTrue(HtmlUtils.isCalloutEnd(":::"))
        assertEquals("Заголовок", HtmlUtils.calloutTitle("::: Заголовок"))
    }

    // endregion

    // region htmlToPlain

    @Test
    fun `htmlToPlain converts block tags into line breaks`() {
        assertEquals("A\nB\nC", HtmlUtils.htmlToPlain("<p>A</p><br><div>B</div><p>C</p>"))
    }

    @Test
    fun `htmlToPlain keeps table structure as pipe rows`() {
        // Разметка совпадает с тем, что отдаёт dnd.su для «Наблюдения».
        val html = "<table><tbody>" +
            "<tr class=\"table_header\"><td>Знание</td><td>Модификатор<br>спасброска</td></tr>" +
            "<tr><td>Знакомые</td><td>-5</td></tr>" +
            "</tbody></table>"

        assertEquals(
            "| Знание | Модификатор спасброска |\n| --- | --- |\n| Знакомые | -5 |",
            HtmlUtils.htmlToPlain(html),
        )
    }

    @Test
    fun `htmlToPlain keeps text around a table`() {
        val html = "<p>До</p><table><tbody><tr><td>A</td><td>B</td></tr></tbody></table><p>После</p>"

        assertEquals("До\n| A | B |\nПосле", HtmlUtils.htmlToPlain(html))
    }

    @Test
    fun `htmlToPlain supports th cells as header`() {
        val html = "<table><tr><th>Кубик</th><th>Эффект</th></tr><tr><td>1</td><td>Огонь</td></tr></table>"

        assertEquals("| Кубик | Эффект |\n| --- | --- |\n| 1 | Огонь |", HtmlUtils.htmlToPlain(html))
    }

    @Test
    fun `htmlToPlain replaces pipe inside cell to keep row structure`() {
        val html = "<table><tr><td>A|B</td><td>C</td></tr></table>"

        assertEquals("| A/B | C |", HtmlUtils.htmlToPlain(html))
    }

    @Test
    fun `table survives round trip through html`() {
        val text = "| Знание | Модификатор |\n| --- | --- |\n| Знакомые | -5 |"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `htmlToPlain merges nested emphasis into bold italic`() {
        // Разметка встречается в «Радужных брызгах»: теги идут в обоих порядках.
        assertEquals("***Зелёный***", HtmlUtils.htmlToPlain("<strong><em>Зелёный</em></strong>"))
        assertEquals("***Голубой***", HtmlUtils.htmlToPlain("<em><strong>Голубой</strong></em>"))
    }

    @Test
    fun `htmlToPlain leaves no stray asterisks on partial nesting`() {
        // Точка стоит вне жирного, но внутри курсива — раньше это давало нечитаемое `***X**.*`.
        val result = HtmlUtils.htmlToPlain("<em><strong>1. Красный</strong>.</em>")

        assertEquals("**1. Красный**.", result)
    }

    @Test
    fun `bold italic survives round trip through html`() {
        val text = "***Жёлтый***"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `removeMascotNotes drops callout signed by site mascot`() {
        val text = "Основной текст\n:::\nПояснение сайта\n**— Господин Финик**\n:::"

        assertEquals("Основной текст", HtmlUtils.removeMascotNotes(text))
    }

    @Test
    fun `removeMascotNotes keeps useful callouts`() {
        // Блок из Таши в «Телепортации» — часть правил, его убирать нельзя.
        val text = "Основной текст\n::: Путешествие в другие миры\nСправочный текст\n:::"

        assertEquals(text, HtmlUtils.removeMascotNotes(text))
    }

    @Test
    fun `removeMascotNotes keeps text without callouts unchanged`() {
        val text = "Простое описание\nБез врезок"

        assertEquals(text, HtmlUtils.removeMascotNotes(text))
    }

    @Test
    fun `removeMascotNotes drops only the signed block`() {
        val text = "Начало\n::: Важно\nПравило\n:::\n:::\nКомментарий\n**— Господин Финик**\n:::\nКонец"

        assertEquals("Начало\n::: Важно\nПравило\n:::\nКонец", HtmlUtils.removeMascotNotes(text))
    }

    @Test
    fun `htmlToPlain keeps bold and italic as markers`() {
        assertEquals(
            "**Жирный** и *курсив*",
            HtmlUtils.htmlToPlain("<p><strong>Жирный</strong> и <em>курсив</em></p>"),
        )
    }

    @Test
    fun `htmlToPlain converts lists into markers`() {
        assertEquals("- Первый\n- Второй", HtmlUtils.htmlToPlain("<ul><li>Первый</li><li>Второй</li></ul>"))
        assertEquals("1. Первый\n2. Второй", HtmlUtils.htmlToPlain("<ol><li>Первый</li><li>Второй</li></ol>"))
    }

    @Test
    fun `htmlToPlain converts headings`() {
        assertEquals("## Итог", HtmlUtils.htmlToPlain("<h4>Итог</h4>"))
    }

    @Test
    fun `htmlToPlain converts dnd su callout block`() {
        // Разметка совпадает с блоком из Таши в заклинании «Телепортация».
        val html = "<p>Основной текст</p>" +
            "<div class=\"additionalInfo\">" +
            "<h3 class=\"smallSectionTitle\"><strong>Путешествие в другие миры</strong></h3>" +
            "<p>Справочный текст</p></div>"

        assertEquals(
            "Основной текст\n::: Путешествие в другие миры\nСправочный текст\n:::",
            HtmlUtils.htmlToPlain(html),
        )
    }

    @Test
    fun `callout survives round trip through html`() {
        val text = "::: Из Таши\nТекст врезки\n:::"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `emphasis survives round trip through html`() {
        val text = "**Жирный** и *курсив*\n- Пункт\n## Заголовок"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `htmlToPlain strips arbitrary tags`() {
        // Выделения сохраняются маркерами, остальные теги убираются.
        assertEquals("жирный текст", HtmlUtils.htmlToPlain("<p><span class=\"x\">жирный</span> текст</p>"))
    }

    @Test
    fun `htmlToPlain returns empty string for blank html`() {
        assertEquals("", HtmlUtils.htmlToPlain(""))
        assertEquals("", HtmlUtils.htmlToPlain("   "))
    }

    @Test
    fun `htmlToPlain converts labeled link into ref token`() {
        assertEquals(
            "[[ref Рывок]]",
            HtmlUtils.htmlToPlain("<p>@Compendium[dnd5e.rules.abc123]{Рывок}</p>"),
        )
    }

    @Test
    fun `htmlToPlain uses last id segment for link without label`() {
        assertEquals(
            "[[ref dash]]",
            HtmlUtils.htmlToPlain("<p>@UUID[Compendium.dnd5e.rules.dash]</p>"),
        )
    }

    @Test
    fun `htmlToPlain unescapes html entities`() {
        assertEquals(
            """<b> & "x" 'y' z""",
            HtmlUtils.htmlToPlain("<p>&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;&nbsp;z</p>"),
        )
    }

    // endregion

    @Test
    fun `plainToHtml and htmlToPlain make a round trip`() {
        val text = "Первый абзац\nВторой абзац"
        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `ref token regex extracts word from token`() {
        val match = HtmlUtils.REF_TOKEN_REGEX.find("Совершите [[ref Рывок]] к цели")

        assertTrue(match != null)
        assertEquals("Рывок", match?.groupValues?.get(1))
    }
}
