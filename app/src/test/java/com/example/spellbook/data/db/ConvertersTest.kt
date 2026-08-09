package com.example.spellbook.data.db

import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.DamagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты Room-конвертеров: сериализация коллекций в JSON и обратно. */
class ConvertersTest {

    private val converters = Converters()

    // region Списки строк

    @Test
    fun `string list survives round trip`() {
        val classes = listOf("wizard", "sorcerer")

        assertEquals(classes, converters.jsonToStringList(converters.stringListToJson(classes)))
    }

    @Test
    fun `empty string list is serialized into empty array`() {
        assertEquals("[]", converters.stringListToJson(emptyList()))
        assertTrue(converters.jsonToStringList("[]").isEmpty())
    }

    @Test
    fun `blank text is parsed into empty list`() {
        assertTrue(converters.jsonToStringList("").isEmpty())
        assertTrue(converters.jsonToStringList("   ").isEmpty())
    }

    // endregion

    // region Части урона

    @Test
    fun `damage parts survive round trip`() {
        val parts = listOf(DamagePart("8d6", "fire"), DamagePart("1d4", "healing"))

        assertEquals(parts, converters.jsonToDamageParts(converters.damagePartsToJson(parts)))
    }

    @Test
    fun `empty damage parts list is handled correctly`() {
        assertEquals("[]", converters.damagePartsToJson(emptyList()))
        assertTrue(converters.jsonToDamageParts("").isEmpty())
    }

    // endregion

    // region Ячейки заклинаний

    @Test
    fun `spell slots map survives round trip`() {
        val slots = mapOf(1 to 4, 2 to 3, 9 to 1)

        assertEquals(slots, converters.jsonToIntMap(converters.intMapToJson(slots)))
    }

    @Test
    fun `empty spell slots map is handled correctly`() {
        assertEquals("{}", converters.intMapToJson(emptyMap()))
        assertTrue(converters.jsonToIntMap("").isEmpty())
    }

    @Test
    fun `non numeric slot keys are ignored`() {
        assertEquals(mapOf(1 to 4), converters.jsonToIntMap("""{"1":4,"lvl":7}"""))
    }

    // endregion

    // region Ресурсы персонажа

    @Test
    fun `resources survive round trip`() {
        val resources = listOf(
            CharacterResource(id = "r1", name = "Очки чародейства", description = "Метамагия", current = 2, maximum = 5),
            CharacterResource(id = "r2", name = "Кости превосходства", current = 4, maximum = 4),
        )

        assertEquals(resources, converters.jsonToResources(converters.resourcesToJson(resources)))
    }

    @Test
    fun `resources without name or with non positive maximum are dropped`() {
        val json = """
            [
              {"id":"r1","name":"  ","current":1,"maximum":3},
              {"id":"r2","name":"Заряды","current":1,"maximum":0},
              {"id":"r3","name":"Очки","current":1,"maximum":3}
            ]
        """.trimIndent()

        val resources = converters.jsonToResources(json)

        assertEquals(listOf("Очки"), resources.map { it.name })
    }

    @Test
    fun `missing current value equals maximum`() {
        val resources = converters.jsonToResources("""[{"id":"r1","name":"Очки","maximum":3}]""")

        assertEquals(3, resources.single().current)
    }

    @Test
    fun `resource without id gets generated one`() {
        val resources = converters.jsonToResources("""[{"name":"Очки","current":1,"maximum":3}]""")

        assertTrue(resources.single().id.isNotBlank())
    }

    @Test
    fun `current resource value is normalized on parsing`() {
        val resources = converters.jsonToResources("""[{"name":"Очки","current":99,"maximum":3}]""")

        assertEquals(3, resources.single().current)
    }

    @Test
    fun `malformed resources json does not break parsing`() {
        assertTrue(converters.jsonToResources("не json").isEmpty())
        assertTrue(converters.jsonToResources("").isEmpty())
    }

    // endregion
}
