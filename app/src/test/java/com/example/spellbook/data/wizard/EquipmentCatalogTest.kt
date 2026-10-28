package com.example.spellbook.data.wizard

import com.example.spellbook.data.ClassLibraryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Разбор стартового снаряжения на настоящих текстах классов dnd.su.
 *
 * Текст снаряжения свободный, поэтому проверяются конкретные строки каждого класса:
 * без этого поломка распознавания обнаружилась бы только на шаге выбора снаряжения.
 */
class EquipmentCatalogTest {

    private val classes by lazy {
        ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
            .associate { it.name to ClassRulesParser.parse(it) }
    }

    /** Разобранный вариант снаряжения класса, подпись которого содержит [fragment]. */
    private fun option(className: String, fragment: String): ParsedEquipment {
        val rules = classes.getValue(className)
        val label = rules.equipment.flatMap { it.options }.map { it.label }
            .firstOrNull { it.lowercase().contains(fragment.lowercase()) }
        assertNotNull("$className: нет варианта «$fragment» среди ${rules.equipment.flatMap { it.options }.map { it.label }}", label)
        return EquipmentCatalog.parseOption(label!!)
    }

    private fun ParsedEquipment.weapon(id: String) = entries.firstOrNull { it.weapon?.id == id }
    private fun ParsedEquipment.armor(id: String) = entries.firstOrNull { it.armor?.id == id }

    private val simple = setOf(WeaponCategory.SIMPLE_MELEE, WeaponCategory.SIMPLE_RANGED)
    private val martial = setOf(WeaponCategory.MARTIAL_MELEE, WeaponCategory.MARTIAL_RANGED)

    @Test
    fun `fighter equipment`() {
        assertNotNull(option("Воин", "кольчуга").armor("chain_mail"))
        val leather = option("Воин", "кожаный доспех")
        assertNotNull(leather.armor("leather"))
        assertNotNull(leather.weapon("longbow"))
        assertEquals(20, leather.entries.first { it.name == "Стрелы" }.quantity)

        val weaponAndShield = option("Воин", "воинское оружие и щит")
        assertEquals(listOf(WeaponSlot(1, martial, "Воинское оружие")), weaponAndShield.slots)
        assertNotNull(weaponAndShield.armor("shield"))

        val twoMartial = option("Воин", "два воинских оружия")
        assertEquals(2, twoMartial.slots.single().count)
        assertEquals(martial, twoMartial.slots.single().categories)

        val crossbow = option("Воин", "лёгкий арбалет")
        assertNotNull(crossbow.weapon("light_crossbow"))
        assertEquals(20, crossbow.entries.first { it.name == "Болты" }.quantity)
        assertEquals(2, option("Воин", "два ручных топора").weapon("handaxe")!!.quantity)
    }

    @Test
    fun `ranger equipment`() {
        assertNotNull(option("Следопыт", "чешуйчатый").armor("scale_mail"))
        assertEquals(2, option("Следопыт", "два коротких меча").weapon("shortsword")!!.quantity)
        val slot = option("Следопыт", "два простых рукопашных").slots.single()
        assertEquals(2, slot.count)
        assertEquals(setOf(WeaponCategory.SIMPLE_MELEE), slot.categories)
        val bow = option("Следопыт", "длинный лук")
        assertNotNull(bow.weapon("longbow"))
        // «Колчан с 20 стрелами» — обычный предмет.
        assertTrue(bow.entries.any { it.name.startsWith("Колчан") && it.weapon == null })
    }

    @Test
    fun `cleric equipment`() {
        assertNotNull(option("Жрец", "булава").weapon("mace"))
        val hammer = option("Жрец", "боевой молот").weapon("warhammer")
        assertEquals("Боевой молот", hammer!!.name)
        assertNotNull(option("Жрец", "кольчуга").armor("chain_mail"))
        val shield = option("Жрец", "щит и священный символ")
        assertNotNull(shield.armor("shield"))
        assertTrue(shield.entries.any { it.name == "Священный символ" })
    }

    @Test
    fun `paladin equipment`() {
        assertEquals(5, option("Паладин", "пять метательных").weapon("javelin")!!.quantity)
        val slot = option("Паладин", "любое простое рукопашное").slots.single()
        assertEquals(1, slot.count)
        assertEquals(setOf(WeaponCategory.SIMPLE_MELEE), slot.categories)
        assertNotNull(option("Паладин", "кольчуга и").armor("chain_mail"))
    }

    @Test
    fun `bard warlock artificer druid barbarian monk equipment`() {
        assertNotNull(option("Бард", "рапира").weapon("rapier"))
        assertEquals(simple, option("Бард", "любое простое оружие").slots.single().categories)
        val bardKit = option("Бард", "кожаный доспех и кинжал")
        assertNotNull(bardKit.armor("leather"))
        assertNotNull(bardKit.weapon("dagger"))

        val warlock = option("Колдун", "кожаный доспех, любое простое оружие")
        assertNotNull(warlock.armor("leather"))
        assertEquals(simple, warlock.slots.single().categories)
        assertEquals(2, warlock.weapon("dagger")!!.quantity)

        val artificerSlot = option("Изобретатель", "два простых оружия").slots.single()
        assertEquals(2, artificerSlot.count)
        assertEquals(simple, artificerSlot.categories)
        assertNotNull(option("Изобретатель", "проклёпанная кожа").armor("studded_leather"))

        assertNotNull(option("Друид", "деревянный щит").armor("shield"))
        assertEquals(1, option("Друид", "одно простое оружие").slots.single().count)
        assertNotNull(option("Друид", "скимитар").weapon("scimitar"))
        assertEquals(setOf(WeaponCategory.SIMPLE_MELEE), option("Друид", "простое рукопашное").slots.single().categories)

        assertNotNull(option("Варвар", "секира").weapon("greataxe"))
        assertEquals(4, option("Варвар", "четыре метательных").weapon("javelin")!!.quantity)
        assertEquals(setOf(WeaponCategory.MARTIAL_MELEE), option("Варвар", "воинское рукопашное").slots.single().categories)

        assertEquals(10, option("Монах", "дротик").weapon("dart")!!.quantity)
        assertNotNull(option("Плут", "кожаная броня").armor("leather"))
    }

    @Test
    fun `no unrecognized weapons or armor`() {
        val unrecognized = classes.flatMap { (name, rules) ->
            rules.equipment.flatMap { it.options }.flatMap { option ->
                EquipmentCatalog.parseOption(option.label).entries
                    .filter { it.armor == null && it.weapon == null }
                    .map { "$name: ${it.name}" }
            }
        }
        println("Обычные предметы снаряжения: $unrecognized")
        val suspicious = unrecognized.filter { entry ->
            val lower = entry.lowercase()
            lower.contains("оруж") || lower.contains("доспех") || lower.contains("брон")
        }
        assertTrue("Не распознаны: $suspicious", suspicious.isEmpty())
    }

    @Test
    fun `armor formulas and labels`() {
        val leather = EquipmentCatalog.armorById("leather")!!
        assertEquals("11 + [dex]", leather.acFormula())
        assertEquals("КД 11 + Лов", leather.acLabel())
        val scale = EquipmentCatalog.armorById("scale_mail")!!
        assertEquals("14 + min([dex], 2)", scale.acFormula())
        assertEquals("КД 14 + Лов (макс. 2)", scale.acLabel())
        assertEquals(listOf("Помеха Скрытности"), scale.notes())
        val chain = EquipmentCatalog.armorById("chain_mail")!!
        assertEquals("16", chain.acFormula())
        assertEquals(listOf("Сила 13", "Помеха Скрытности"), chain.notes())
        val shield = EquipmentCatalog.armorById("shield")!!
        assertEquals("2", shield.acFormula())
        assertEquals("+2 к КД", shield.acLabel())
    }

    @Test
    fun `weapon summary and lookup`() {
        assertEquals("1к8 колющий · фехтовальное", EquipmentCatalog.weaponById("rapier")!!.summary())
        assertEquals("метательное (5/15), особое", EquipmentCatalog.weaponById("net")!!.summary())
        assertEquals(10, EquipmentCatalog.weapons(setOf(WeaponCategory.SIMPLE_MELEE)).size)
        assertEquals(4, EquipmentCatalog.weapons(setOf(WeaponCategory.SIMPLE_RANGED)).size)
        // «Копьё» не путается с метательным и длинным.
        assertEquals("spear", EquipmentCatalog.parseOption("копьё").entries.single().weapon?.id)
        assertEquals("lance", EquipmentCatalog.parseOption("длинное копьё").entries.single().weapon?.id)
        // «Цепь» — не «цеп».
        assertNull(EquipmentCatalog.parseOption("цепь").entries.single().weapon)
        assertEquals(
            "Воинское рукопашное оружие. Урон: 1к8 колющий. Свойства: фехтовальное.",
            EquipmentCatalog.description(EquipmentEntry("Рапира", weapon = EquipmentCatalog.weaponById("rapier"))),
        )
    }
}
