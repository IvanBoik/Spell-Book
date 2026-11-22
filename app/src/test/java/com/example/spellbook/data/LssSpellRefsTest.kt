package com.example.spellbook.data

import com.example.spellbook.data.model.Spell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ссылки на заклинания в листе LSS: слаги, английские названия и id базы LSS. */
class LssSpellRefsTest {

    @Test
    fun `names are split and joined like on dnd su`() {
        assertEquals("Огненный снаряд" to "Fire bolt", SpellNames.split("Огненный снаряд [Fire bolt]"))
        assertEquals("Щит" to "", SpellNames.split("Щит"))
        assertEquals("[Fire bolt]" to "", SpellNames.split("[Fire bolt]"))
        assertEquals("Щит [Shield]", SpellNames.join("Щит", "Shield"))
        assertEquals("Щит", SpellNames.join("Щит", ""))
    }

    @Test
    fun `slugs follow lss and srd naming`() {
        assertEquals("cure-wounds", LssSpellRefs.slugify("Cure wounds"))
        assertEquals("hunters-mark", LssSpellRefs.slugify("Hunter's mark"))
        assertEquals("heroes-feast", LssSpellRefs.slugify("Heroes' feast"))
        assertEquals("antipathy-or-sympathy", LssSpellRefs.slugify("Antipathy/sympathy"))
        // В SRD заклинания волшебников названы без имён.
        assertEquals("floating-disk", LssSpellRefs.slugify("Tenser’s floating disk"))
        assertEquals("arcanists-magic-aura", LssSpellRefs.slugify("Nystul's magic aura"))
        assertEquals("arcane-hand", LssSpellRefs.slugify("Bigby's Hand"))
    }

    @Test
    fun `lss database ids are recognized`() {
        assertTrue(LssSpellRefs.isLssId("65d3c16df3d820fa1add45d1"))
        assertFalse(LssSpellRefs.isLssId("cure-wounds"))
    }

    @Test
    fun `resolver finds spells by slug name and english name`() {
        val cure = Spell(id = "cure", name = "Лечение ран", nameEn = "Cure wounds")
        val disk = Spell(id = "disk", name = "Тензеров парящий диск", nameEn = "Tenser’s floating disk")
        val homebrew = Spell(id = "hb", name = "Звериный рёв")
        val resolver = LssSpellRefs.Resolver(listOf(cure, disk, homebrew))
        assertEquals(cure, resolver.resolve("cure-wounds"))
        assertEquals(cure, resolver.resolve("Cure Wounds"))
        assertEquals(cure, resolver.resolve("Исцеление ран [Cure Wounds]"))
        assertEquals(cure, resolver.resolve("лечение ран"))
        assertEquals(disk, resolver.resolve("floating-disk"))
        assertEquals(disk, resolver.resolve("tensers-floating-disk"))
        assertEquals(homebrew, resolver.resolve("Звериный рёв"))
        assertNull(resolver.resolve("65d3c16df3d820fa1add45d1"))
        assertNull(resolver.resolve("wish"))
    }

    @Test
    fun `bundled library spells have english names`() {
        val spells = SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
        val withoutEnglish = spells.filter { it.nameEn.isBlank() }.map { it.name }
        // У двух заклинаний на dnd.su нет английского названия в каталоге.
        assertEquals(listOf("Звериный рёв", "Мистическая бодрость"), withoutEnglish.sorted())
        assertTrue(spells.none { it.name.contains('[') })
        assertEquals("Fear", spells.first { it.name == "Ужас" }.nameEn)
    }

    @Test
    fun `spell export keeps english name in lss form`() {
        val spell = Spell(name = "Лечение ран", nameEn = "Cure wounds")
        val json = SpellLssCodec.toJson(spell)
        assertEquals("Лечение ран [Cure wounds]", json.getString("name"))
        val restored = SpellLssCodec.fromJson(json)
        assertEquals("Лечение ран", restored.name)
        assertEquals("Cure wounds", restored.nameEn)
    }
    @Test
    fun `srd ids from lss sheet are resolved through the bundled table`() {
        val ids = LssSpellRefs.parseIdTable(File("src/main/assets/lss-spell-ids.json").readText())
        val library = SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
        val resolver = LssSpellRefs.Resolver(library, ids)
        val sheet = CharacterLssCodec.decode(File("src/test/resources/lss/sample-character.json").readText())
        val names = sheet.spells.map { resolver.resolve(it.ref)?.name }
        assertEquals(
            listOf(
                "Благословение", "Защита от зла и добра", "Лечение ран", "Приказ", "Щит веры",
                "Чудотворство", "Священное пламя", "Указание", "Убежище",
                // Заклинание из гримуара владельца листа: в открытых справочниках LSS его нет.
                null,
            ),
            names,
        )
        // id SRD 5.2 (пример из документации LSS) и слаг 5.2 без «or».
        assertEquals("Лечение ран", resolver.resolve("698197c2a5dc6923c7028c8c")?.name)
        assertEquals("Антипатия/симпатия", resolver.resolve("antipathy-sympathy")?.name)
    }
}
