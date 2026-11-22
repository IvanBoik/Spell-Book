package com.example.spellbook.data

import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.NoteSpan
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Перевод форматированного текста LSS в абзацы заметок и обратно. */
class LssRichTextTest {

    private fun roundTrip(paragraphs: List<NoteParagraph>): List<NoteParagraph> {
        val doc = LssRichText.doc(LssRichText.noteParagraphs(paragraphs))
        // Как в настоящем листе: документ проходит через строку JSON.
        return LssRichText.toNoteParagraphs(LssRichText.lines(JSONObject(doc.toString())))
    }

    @Test
    fun `paragraphs with styles and lists survive`() {
        val paragraphs = listOf(
            NoteParagraph(
                text = "Боевой стиль\nВыберите один:\n• Защита\n• Дуэлянт",
                spans = listOf(
                    NoteSpan(start = 0, end = 12, bold = true),
                    NoteSpan(start = 13, end = 21, italic = true, underline = true),
                ),
            ),
            NoteParagraph(text = "1. Первый\n2. Второй"),
        )
        val restored = roundTrip(paragraphs)
        assertEquals(paragraphs.map { it.text }, restored.map { it.text })
        assertEquals(paragraphs.map { it.spans }, restored.map { it.spans })
    }

    @Test
    fun `only node types known to lss are written`() {
        val doc = LssRichText.doc(
            LssRichText.noteParagraphs(listOf(NoteParagraph(text = "А\n• б\n1. в"))) +
                LssRichText.resourceNode("resource-1", "traits"),
        )
        val types = mutableSetOf<String>()
        fun collect(node: JSONObject) {
            types += node.getString("type")
            node.optJSONArray("content")?.let { content ->
                (0 until content.length()).forEach { collect(content.getJSONObject(it)) }
            }
        }
        collect(doc)
        val allowed = setOf("doc", "paragraph", "text", "bulletList", "orderedList", "listItem", "resource")
        assertTrue(types.toString(), allowed.containsAll(types))
    }

    @Test
    fun `hard breaks and inline widgets become text`() {
        val doc = JSONObject(
            """
            {"type":"doc","content":[
              {"type":"paragraph","content":[
                {"type":"text","text":"Урон "},
                {"type":"roller","attrs":{"formula":"2к8"}},
                {"type":"hardBreak"},
                {"type":"text","marks":[{"type":"bold"}],"text":"Конец"}
              ]},
              {"type":"divider"},
              {"type":"spoiler","content":[
                {"type":"spoilerSummary","content":[{"type":"text","text":"Скрыто"}]},
                {"type":"spoilerContent","content":[{"type":"paragraph","content":[{"type":"text","text":"Текст"}]}]}
              ]}
            ]}
            """.trimIndent(),
        )
        val paragraphs = LssRichText.toNoteParagraphs(LssRichText.lines(doc))
        assertEquals(listOf("Урон 2к8\nКонец", "Скрыто\nТекст"), paragraphs.map { it.text })
        assertEquals(listOf(NoteSpan(start = 9, end = 14, bold = true)), paragraphs[0].spans)
        assertEquals(listOf(NoteSpan(start = 0, end = 6, bold = true)), paragraphs[1].spans)
    }
}
