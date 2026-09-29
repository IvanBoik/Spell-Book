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
    fun `splitTableBlocks separates tables joined by their headers`() {
        // Как в «Персонализации» предысторий: таблицы идут подряд без текста между ними.
        val block = listOf(
            "| к8 | Черта характера |",
            "| --- | --- |",
            "| 1 | Первая |",
            "| к6 | Идеал |",
            "| --- | --- |",
            "| 1 | Второй |",
        )

        val parts = HtmlUtils.splitTableBlocks(block)

        assertEquals(2, parts.size)
        assertEquals("| к8 | Черта характера |", parts[0].first())
        assertEquals("| к6 | Идеал |", parts[1].first())
    }

    @Test
    fun `splitTableBlocks keeps a single table intact`() {
        val block = listOf("| к8 | Черта |", "| --- | --- |", "| 1 | Первая |")

        assertEquals(listOf(block), HtmlUtils.splitTableBlocks(block))
    }

    @Test
    fun `plainToHtml keeps stacked tables separate`() {
        val text = "| к8 | Черта |\n| --- | --- |\n| 1 | А |\n| к6 | Идеал |\n| --- | --- |\n| 1 | Б |"

        val html = HtmlUtils.plainToHtml(text)

        assertEquals(2, Regex("<table>").findAll(html).count())
    }

    @Test
    fun `callout without a title is recognised by both boundaries`() {
        // У врезки без заголовка обе границы выглядят одинаково.
        assertTrue(HtmlUtils.isCalloutBoundary(":::"))
        assertTrue(HtmlUtils.isCalloutBoundary("::: Заголовок"))
        assertEquals(false, HtmlUtils.isCalloutBoundary("Обычный текст"))
    }

    @Test
    fun `plainToHtml wraps an untitled callout into a block`() {
        val html = HtmlUtils.plainToHtml(":::\nТекст врезки.\n:::")

        assertTrue(html.contains("additionalInfo"))
        // Маркеры не должны протекать в видимый текст.
        assertEquals(false, html.contains("<p>:::</p>"))
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
        // Разделы — h3, подзаголовки внутри раздела — h4, как на dnd.su.
        assertEquals("<h3>Итог</h3>", HtmlUtils.plainToHtml("## Итог"))
        assertEquals("<h4>Подробности</h4>", HtmlUtils.plainToHtml("### Подробности"))
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
        // Точка стоит вне жирного, но внутри курсива — когда-то это давало нечитаемое `***X**.*`.
        // Знаки препинания выносятся за маркеры, поэтому оба порядка тегов дают жирный курсив.
        val result = HtmlUtils.htmlToPlain("<em><strong>1. Красный</strong>.</em>")

        assertEquals("***1. Красный***.", result)
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
        assertEquals("## Итог", HtmlUtils.htmlToPlain("<h3>Итог</h3>"))
    }

    @Test
    fun `htmlToPlain keeps deep headings as subheadings`() {
        // Иначе подписи к таблицам рвут единый раздел на десяток кусков.
        assertEquals("### Эльфийские безделушки", HtmlUtils.htmlToPlain("<h4>Эльфийские безделушки</h4>"))
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
    fun `subheading survives round trip through html`() {
        val text = "## Раздел\n### Подзаголовок\nТекст"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `blockquote becomes quote lines`() {
        // Художественная врезка в начале описания расы.
        assertEquals("> Цитата", HtmlUtils.htmlToPlain("<blockquote>Цитата</blockquote>"))
    }

    @Test
    fun `quote survives round trip through html`() {
        val text = "> Первая строка\n> Вторая строка"

        assertEquals(text, HtmlUtils.htmlToPlain(HtmlUtils.plainToHtml(text)))
    }

    @Test
    fun `ability name keeps bold italic when separated by nbsp`() {
        // На dnd.su часть способностей размечена с `&nbsp;` внутри жирного («Наследие Диса»).
        // Сущность не считалась пробелом, внешний маркер терялся и оставался один курсив.
        val result = HtmlUtils.htmlToPlain("<p><strong><em>Наследие Диса</em>.&nbsp;</strong>Начиная с 3-го.</p>")

        assertEquals("***Наследие Диса***. Начиная с 3-го.", result)
    }

    @Test
    fun `split emphasis does not leave stray asterisks`() {
        // Редактор сайта иногда рвёт одно название на два соседних тега — на стыке
        // получалось `***Р******евенант***`, и звёздочки были видны на экране.
        val html = "<p><strong><em>Р</em></strong><strong><em>евенант</em></strong>. Текст.</p>"

        assertEquals("***Ревенант***. Текст.", HtmlUtils.htmlToPlain(html))
    }

    @Test
    fun `nbsp does not survive as entity`() {
        assertEquals("А Б", HtmlUtils.htmlToPlain("<p>А&nbsp;Б</p>"))
    }

    @Test
    fun `ability name keeps bold italic with trailing dot outside`() {
        // Расовые способности на dnd.su: точка внутри жирного, но вне курсива.
        // Без выноса знаков препинания название оставалось одним курсивом.
        val result = HtmlUtils.htmlToPlain("<p><strong><em>Увеличение характеристик</em>.</strong> Текст.</p>")

        assertEquals("***Увеличение характеристик***. Текст.", result)
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
