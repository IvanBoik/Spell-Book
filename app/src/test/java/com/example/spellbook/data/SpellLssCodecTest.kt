package com.example.spellbook.data

import com.example.spellbook.data.model.Components
import com.example.spellbook.data.model.DamagePart
import com.example.spellbook.data.model.Materials
import com.example.spellbook.data.model.Save
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.Target
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты сериализации заклинаний в формат LSS и обратно. */
class SpellLssCodecTest {

    private val fireball = Spell(
        id = "local-id-1",
        createdAt = 12345L,
        name = "Огненный шар",
        description = "Первый абзац\nВторой абзац",
        source = "PHB",
        level = 3,
        school = "evo",
        activationType = "action",
        activationCost = 1,
        activationCondition = "при попадании",
        durationValue = null,
        durationUnits = "inst",
        rangeValue = 150,
        rangeUnits = "ft",
        target = Target(value = 20, width = 5, units = "ft", type = "sphere"),
        components = Components(
            vocal = true,
            somatic = true,
            material = true,
            ritual = false,
            concentration = true,
            value = "В, С, М",
        ),
        materials = Materials(value = "гуано", consumed = true, cost = 50, supply = 2),
        actionType = "save",
        ability = "int",
        attackBonus = 3,
        save = Save(ability = "dex", dc = 15, scaling = "spell"),
        damageParts = listOf(DamagePart("8d6", "fire"), DamagePart("1d4", "necrotic")),
        scalingMode = "level",
        scalingFormula = "1d6",
        classes = listOf("wizard", "sorcerer"),
    )

    // region Круговое преобразование

    @Test
    fun `spell survives round trip without losses`() {
        val restored = SpellLssCodec.fromJson(SpellLssCodec.toJson(fireball, includeLocalId = true))

        assertEquals(fireball, restored.copy(createdAt = fireball.createdAt))
    }

    @Test
    fun `encodeSpell and decodeSpell keep meaningful fields`() {
        val restored = SpellLssCodec.decodeSpell(SpellLssCodec.encodeSpell(fireball))

        assertEquals(fireball.name, restored.name)
        assertEquals(fireball.description, restored.description)
        assertEquals(fireball.level, restored.level)
        assertEquals(fireball.damageParts, restored.damageParts)
        assertEquals(fireball.classes, restored.classes)
    }

    @Test
    fun `encodeList and decodeList keep local ids and order`() {
        val second = fireball.copy(id = "local-id-2", name = "Щит", level = 1)

        val restored = SpellLssCodec.decodeList(SpellLssCodec.encodeList(listOf(fireball, second)))

        assertEquals(listOf("local-id-1", "local-id-2"), restored.map { it.id })
        assertEquals(listOf("Огненный шар", "Щит"), restored.map { it.name })
    }

    // endregion

    // region Сериализация

    @Test
    fun `single spell export contains no local id`() {
        val json = SpellLssCodec.toJson(fireball, includeLocalId = false)

        assertFalse(json.has("_localId"))
        assertEquals("spell", json.getString("type"))
        assertEquals("Огненный шар", json.getString("name"))
    }

    @Test
    fun `local id is added on demand`() {
        val json = SpellLssCodec.toJson(fireball, includeLocalId = true)

        assertEquals("local-id-1", json.getString("_localId"))
    }

    @Test
    fun `description is stored as html paragraphs`() {
        val system = SpellLssCodec.toJson(fireball).getJSONObject("system")

        assertEquals(
            "<p>Первый абзац</p><p>Второй абзац</p>",
            system.getJSONObject("description").getString("value"),
        )
    }

    @Test
    fun `missing numeric fields are written as null`() {
        val system = SpellLssCodec.toJson(fireball).getJSONObject("system")

        assertTrue(system.getJSONObject("duration").isNull("value"))
    }

    @Test
    fun `damage parts are written as formula type pairs`() {
        val parts = SpellLssCodec.toJson(fireball)
            .getJSONObject("system")
            .getJSONObject("damage")
            .getJSONArray("parts")

        assertEquals(2, parts.length())
        assertEquals("8d6", parts.getJSONArray(0).getString(0))
        assertEquals("fire", parts.getJSONArray(0).getString(1))
    }

    // endregion

    // region Десериализация

    @Test
    fun `empty object is parsed with safe defaults`() {
        val spell = SpellLssCodec.fromJson(JSONObject())

        assertEquals("", spell.name)
        assertEquals("", spell.description)
        assertEquals(0, spell.level)
        assertEquals("inst", spell.durationUnits)
        assertEquals("self", spell.rangeUnits)
        assertEquals("util", spell.actionType)
        assertEquals("none", spell.scalingMode)
        assertEquals("spell", spell.save.scaling)
        assertNull(spell.activationCost)
        assertTrue(spell.classes.isEmpty())
        assertTrue(spell.damageParts.isEmpty())
    }

    @Test
    fun `new id is generated when local id is missing`() {
        val first = SpellLssCodec.fromJson(JSONObject())
        val second = SpellLssCodec.fromJson(JSONObject())

        assertTrue(first.id.isNotBlank())
        assertTrue(first.id != second.id)
    }

    @Test
    fun `decodeList returns empty list for blank text`() {
        assertTrue(SpellLssCodec.decodeList("").isEmpty())
        assertTrue(SpellLssCodec.decodeList("   ").isEmpty())
    }

    @Test
    fun `decodeList returns empty list for empty array`() {
        assertTrue(SpellLssCodec.decodeList("[]").isEmpty())
    }

    @Test
    fun `links in description become ref tokens on parsing`() {
        val json = JSONObject(
            """
            {"name":"Тест","system":{"description":{"value":"<p>@Compendium[dnd5e.rules.x]{Рывок}</p>"}}}
            """.trimIndent(),
        )

        assertEquals("[[ref Рывок]]", SpellLssCodec.fromJson(json).description)
    }

    // endregion
}
