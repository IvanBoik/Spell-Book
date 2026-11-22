package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboWithSteps
import com.example.spellbook.data.model.CreatureSize
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.NoteSpan
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.UNARMORED_AC_FORMULA
import com.example.spellbook.data.wizard.RaceSizes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Дополнения выгрузки в LSS: умения с заклинаниями в «Атаки и заклинания», боевые заговоры
 * в списке оружия, размер персонажа; основа КД без доспеха.
 */
class LssSheetExtrasTest {

    private val library by lazy {
        SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
    }

    private fun spell(name: String) = library.first { it.name == name }

    private fun feature(title: String, body: String) = NoteParagraph(
        text = "$title\n$body",
        spans = listOf(NoteSpan(start = 0, end = title.length, bold = true)),
    )

    private val artificer = Character(
        name = "Тинк",
        level = 5,
        classes = listOf(CharacterClassLevel(CharacterClass.ARTIFICER, 5).serialize()),
        abilityScores = mapOf(AbilityType.INTELLIGENCE.ordinal to 16),
        size = CreatureSize.SMALL.code,
    )

    private val notes = listOf(
        NoteBlock(
            characterId = artificer.id,
            title = "Раса: Тифлинг",
            sortOrder = 0,
            paragraphs = listOf(
                feature("Тёмное зрение", "60 футов."),
                feature("Адское наследие", "Вы знаете заговор чудотворство [thaumaturgy]."),
            ),
        ),
        NoteBlock(
            characterId = artificer.id,
            title = "Класс: Изобретатель",
            sortOrder = 1,
            paragraphs = listOf(feature("Магическая мастеровитость", "Вы наделяете предмет магией.")),
        ),
        NoteBlock(
            characterId = artificer.id,
            title = "Архетип: Алхимик",
            sortOrder = 2,
            paragraphs = listOf(
                feature(
                    "Заклинания алхимика",
                    "Вы всегда подготавливаете эти заклинания.\n" +
                        "| Уровень изобретателя | Заклинания |\n" +
                        "| --- | --- |\n" +
                        "| 3 | лечащее слово [healing word], луч болезни [ray of sickness] |\n" +
                        "| 5 | пылающий шар [flaming sphere], стрела Мельфа [Melf's acid arrow] |",
                ),
                feature("Экспериментальный эликсир", "Вы создаёте эликсир."),
            ),
        ),
    )

    private val sheet = LssSheetExport(
        character = artificer,
        notes = notes,
        spells = listOf(
            LssSpellEntry(spell("Огненный снаряд"), prepared = false),
            LssSpellEntry(spell("Священное пламя"), prepared = false),
            LssSpellEntry(spell("Свет"), prepared = false),
            LssSpellEntry(spell("Ядовитые брызги"), prepared = false),
        ),
        // Комбинация с тем же названием, что и заговор: заговор второй раз не выгружается.
        combos = listOf(ComboWithSteps(Combo(characterId = artificer.id, name = "Ядовитые брызги"), emptyList())),
    )

    private val data by lazy { JSONObject(JSONObject(CharacterLssCodec.encode(sheet)).getString("data")) }

    private fun blockLines(key: String) =
        LssRichText.lines(data.getJSONObject("text").getJSONObject(key).getJSONObject("value").getJSONObject("data"))

    @Test
    fun `features granting spells go to attacks block`() {
        val attacks = blockLines("attacks").map { it.text }
        assertTrue(attacks.contains("Адское наследие"))
        assertTrue(attacks.contains("Заклинания алхимика"))
        // Таблица — строками «уровень — заклинания» с жирной шапкой.
        assertTrue(attacks.contains("Уровень изобретателя — Заклинания"))
        assertTrue(attacks.contains("3 — лечащее слово [healing word], луч болезни [ray of sickness]"))
        assertTrue(blockLines("attacks").first { it.text == "Уровень изобретателя — Заклинания" }.isFullyMarked)

        val traits = blockLines("traits").map { it.text }
        assertTrue(traits.contains("Тёмное зрение"))
        assertFalse(traits.contains("Адское наследие"))
        val features = blockLines("features").map { it.text }
        assertTrue(features.contains("Экспериментальный эликсир"))
        assertFalse(features.contains("Заклинания алхимика"))
    }

    @Test
    fun `notes from attacks merge back with the same title`() {
        val restored = CharacterLssCodec.decode(CharacterLssCodec.encode(sheet))
        assertEquals(
            listOf("Раса: Тифлинг", "Класс: Изобретатель", "Архетип: Алхимик"),
            restored.notes.map { it.title },
        )
        val race = restored.notes.first().paragraphs.map { it.text.lines().first() }
        assertEquals(listOf("Тёмное зрение", "Адское наследие"), race)
    }

    @Test
    fun `damage cantrips are weapons unless a combo has the name`() {
        val weapons = data.getJSONArray("weaponsList")
        val byName = (0 until weapons.length()).map { weapons.getJSONObject(it) }
            .associateBy { it.getJSONObject("name").getString("value") }
        assertEquals(setOf("Ядовитые брызги", "Огненный снаряд", "Священное пламя"), byName.keys)
        // Комбинация «Ядовитые брызги» осталась единственной с этим названием.
        assertEquals("weapon-${sheet.combos.single().combo.id}", byName.getValue("Ядовитые брызги").getString("id"))

        val fireBolt = byName.getValue("Огненный снаряд")
        // 5 уровень: вторая кость урона; атака — от Интеллекта изобретателя.
        assertEquals("2к10", fireBolt.getJSONObject("dmg").getString("value"))
        assertEquals("Огонь", fireBolt.getJSONObject("dmgType").getString("value"))
        assertEquals("int", fireBolt.getString("ability"))
        val flame = byName.getValue("Священное пламя")
        assertEquals("2к8", flame.getJSONObject("dmg").getString("value"))
        assertEquals("Излучение", flame.getJSONObject("dmgType").getString("value"))
        assertEquals("Спасбросок Ловкости", flame.getJSONObject("notes").getString("value"))
    }

    @Test
    fun `cantrip damage is read from descriptions`() {
        assertEquals(CantripDamage("1к10", "force", "Лучей: 1"), CantripDamages.of(spell("Мистический заряд"), 1))
        assertEquals("Лучей: 3", CantripDamages.of(spell("Мистический заряд"), 11)?.notes)
        assertEquals(CantripDamage("4к4", "psychic", "Спасбросок Мудрости"), CantripDamages.of(spell("Злая насмешка"), 17))
        assertEquals("piercing", CantripDamages.of(spell("Терновый кнут"), 1)?.damageType)
        assertEquals("necrotic", CantripDamages.of(spell("Погребальный звон"), 1)?.damageType)
        assertNull(CantripDamages.of(spell("Свет"), 5))
        assertNull(CantripDamages.of(spell("Огненный шар"), 5))
    }

    @Test
    fun `size is exported and imported`() {
        assertEquals("small", data.getJSONObject("info").getJSONObject("size").getString("value"))
        assertEquals("small", CharacterLssCodec.decode(CharacterLssCodec.encode(sheet)).character.size)
        val sample = CharacterLssCodec.decode(File("src/test/resources/lss/sample-character.json").readText())
        assertEquals("medium", sample.character.size)
    }

    @Test
    fun `sizes are parsed from race texts`() {
        assertEquals(listOf(CreatureSize.SMALL, CreatureSize.MEDIUM), CreatureSize.parse("Маленький или средний"))
        assertEquals(listOf(CreatureSize.MEDIUM), CreatureSize.parse("Средний"))
        // «й» двумя символами, как в части текстов dnd.su.
        assertEquals(listOf(CreatureSize.SMALL), CreatureSize.parse("Маленькй"))

        val races = RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
            .mapIndexed { index, race -> race.copy(id = "r$index") }
        fun sizes(race: String, book: String? = null): List<CreatureSize> {
            val match = races.filter { it.name == race && (book == null || it.book == book) }
            return RaceSizes.forCharacter(Character(race = race), match)
        }
        // «Ваш размер — [[ref Маленький]]»: ссылка-подсказка снимается.
        assertEquals(listOf(CreatureSize.SMALL), sizes("Фэйри"))
        assertEquals(listOf(CreatureSize.SMALL, CreatureSize.MEDIUM), sizes("Табакси", "Mordenkainen Presents: Monsters of the Multiverse"))
        // «Ваш размер Средний» без тире.
        assertEquals(listOf(CreatureSize.MEDIUM), sizes("Авен"))
    }

    @Test
    fun `character without armor gets 10 plus dexterity`() {
        val character = Character(abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 16))
        assertEquals(10, character.effectiveArmorClass)
        val fixed = character.withUnarmoredBase()
        assertEquals(listOf(UNARMORED_AC_FORMULA), fixed.armorClassParts.map { it.formula })
        assertEquals(13, fixed.effectiveArmorClass)
        assertEquals(13, fixed.armorClass)
        // Число КД, заданное вручную или из листа, не трогается.
        val manual = character.copy(armorClass = 15)
        assertEquals(manual, manual.withUnarmoredBase())
        assertEquals(listOf("15"), manual.armorClassPartsOrBase.map { it.formula })
        assertEquals(StatPartKind.BASE, character.armorClassPartsOrBase.single().kind)
        val withParts = fixed.copy(armorClassParts = listOf(StatPart(name = "Кольчуга", formula = "16", kind = StatPartKind.BASE)))
        assertEquals(withParts, withParts.withUnarmoredBase())
    }
}
