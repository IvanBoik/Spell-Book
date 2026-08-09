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

    // endregion

    // region htmlToPlain

    @Test
    fun `htmlToPlain converts block tags into line breaks`() {
        assertEquals("A\nB\nC", HtmlUtils.htmlToPlain("<p>A</p><br><div>B</div><p>C</p>"))
    }

    @Test
    fun `htmlToPlain strips arbitrary tags`() {
        assertEquals("жирный текст", HtmlUtils.htmlToPlain("<p><strong>жирный</strong> <em>текст</em></p>"))
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
