package com.example.spellbook.data

import com.example.spellbook.data.model.Feat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты формата встроенной библиотеки черт. */
class FeatLibraryCodecTest {

    @Test
    fun `feat survives encode and decode`() {
        val feat = Feat(
            name = "Бдительный",
            description = "Вы всегда готовы к опасностям:\n- Бонус +5 к инициативе.",
            source = "https://dnd.su/feats/103-alert/",
            book = "Player's Handbook",
        )

        val decoded = FeatLibraryCodec.decodeList(FeatLibraryCodec.encodeList(listOf(feat)))

        assertEquals(1, decoded.size)
        assertEquals(feat.name, decoded[0].name)
        assertEquals(feat.description, decoded[0].description)
        assertEquals(feat.source, decoded[0].source)
        assertEquals(feat.book, decoded[0].book)
    }

    @Test
    fun `feat without book falls back to empty value`() {
        // Созданные вручную черты книги не имеют — в библиотеке они попадут в «Прочее».
        val decoded = FeatLibraryCodec.decodeList("""[{"name":"Своя черта"}]""")

        assertEquals("", decoded[0].book)
    }

    @Test
    fun `local id is not exported`() {
        val json = FeatLibraryCodec.toJson(Feat(name = "Удачливый"))

        // id у каждого пользователя свой, поэтому в общий файл он не попадает.
        assertTrue(json.keys().asSequence().toList().none { it.contains("id", ignoreCase = true) })
    }

    @Test
    fun `decoded feat gets its own id`() {
        val feat = Feat(name = "Меткий стрелок")

        val decoded = FeatLibraryCodec.decodeList(FeatLibraryCodec.encodeList(listOf(feat)))

        assertTrue(decoded[0].id.isNotBlank())
    }

    @Test
    fun `entries without name are skipped`() {
        val json = """[{"name":"","description":"текст"},{"name":"Знаток","description":"текст"}]"""

        val decoded = FeatLibraryCodec.decodeList(json)

        assertEquals(1, decoded.size)
        assertEquals("Знаток", decoded[0].name)
    }

    @Test
    fun `malformed json yields empty list instead of crash`() {
        assertTrue(FeatLibraryCodec.decodeList("не json").isEmpty())
        assertTrue(FeatLibraryCodec.decodeList("").isEmpty())
    }

    @Test
    fun `missing fields fall back to empty values`() {
        val feat = FeatLibraryCodec.fromJson(JSONObject("""{"name":"Атлет"}"""))

        assertEquals("Атлет", feat.name)
        assertEquals("", feat.description)
        assertEquals("", feat.source)
    }
}
