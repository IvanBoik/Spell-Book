package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboWithSteps
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.NoteSpan
import com.example.spellbook.data.model.ProficiencyLevel
import com.example.spellbook.data.model.ResourceRecovery
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.WeaponProficiency
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Обмен листом персонажа с LSS: разбор настоящего экспорта LSS и полный цикл
 * «выгрузка из приложения → загрузка обратно».
 */
class CharacterLssCodecTest {

    // region Настоящий лист из LSS

    /** Экспорт паладина 4 уровня из LSS с заметками, ресурсом, чертой и снаряжением. */
    private val sample by lazy {
        CharacterLssCodec.decode(File("src/test/resources/lss/sample-character.json").readText())
    }

    @Test
    fun `sample header and class are read`() {
        val character = sample.character
        assertEquals("Мортас", character.name)
        assertEquals(4, character.level)
        assertEquals(listOf(CharacterClassLevel(CharacterClass.PALADIN, 4)), character.classLevels)
        assertEquals("Клятва преданности", character.subclass)
        assertEquals("Тифлинг (Зариэль)", character.race)
        assertEquals("Чужеземец", character.background)
        assertTrue(character.canPrepareSpells)
        assertTrue(character.imageUri!!.startsWith("https://hotbox.longstoryshort.app/"))
    }

    @Test
    fun `sample vitality is read from numbers`() {
        val character = sample.character
        assertEquals(44, character.maxHp)
        assertEquals(44, character.currentHp)
        assertEquals(0, character.tempHp)
        // В настоящем экспорте КД и скорость — числа, хотя схема описывает строки.
        assertEquals(16, character.armorClass)
        assertTrue(character.armorClassParts.isEmpty())
        assertEquals(30, character.speed)
        assertEquals("", character.initiativeFormula)
        assertEquals(mapOf(1 to 3), character.spellSlots)
        assertEquals(emptyMap<Int, Int>(), character.spellSlotsUsed)
    }

    @Test
    fun `sample abilities and proficiencies are read`() {
        val character = sample.character
        assertEquals(16, character.abilityScore(AbilityType.STRENGTH))
        assertEquals(8, character.abilityScore(AbilityType.INTELLIGENCE))
        assertEquals(ProficiencyLevel.PROFICIENT, character.saveProficiency(AbilityType.WISDOM))
        assertEquals(ProficiencyLevel.PROFICIENT, character.saveProficiency(AbilityType.CHARISMA))
        assertEquals(ProficiencyLevel.NONE, character.saveProficiency(AbilityType.STRENGTH))
        listOf(SkillType.ATHLETICS, SkillType.SURVIVAL, SkillType.RELIGION, SkillType.PERSUASION).forEach {
            assertEquals(it.name, ProficiencyLevel.PROFICIENT, character.skillProficiency(it))
        }
        assertEquals(ProficiencyLevel.NONE, character.skillProficiency(SkillType.STEALTH))
        ArmorProficiency.entries.forEach { assertTrue(it.name, character.hasArmorProficiency(it)) }
        assertTrue(character.hasWeaponProficiency(WeaponProficiency.SIMPLE))
        assertTrue(character.hasWeaponProficiency(WeaponProficiency.MARTIAL))
        assertFalse(character.hasWeaponProficiency(WeaponProficiency.OTHER))
        assertEquals("Общий, Инфернальный, Эльфийский", character.languages)
        assertEquals("Рожок", character.toolProficiencies)
        assertEquals(mapOf(CoinType.GOLD.ordinal to 100), character.coins)
    }

    @Test
    fun `sample resource keeps its rest flag`() {
        val resource = sample.character.resources.single()
        assertEquals("Палящая кара", resource.name)
        assertEquals(1, resource.maximum)
        assertEquals(1, resource.current)
        assertEquals("", resource.maximumFormula)
        assertEquals(ResourceRecovery(longRest = true), resource.recovery)
    }

    @Test
    fun `sample text blocks become notes with sheet labels`() {
        val titles = sample.notes.map { it.title }
        assertEquals(
            listOf(
                "Умения и способности",
                "Дополнительные способности и умения",
                "способности расы",
                "Предыстория",
                "предыстория",
            ),
            titles,
        )
        val race = sample.notes.first { it.title == "способности расы" }
        assertTrue(race.paragraphs.first().text.startsWith("Темное зрение: 120 футов"))
        // Жирное начало абзаца переносится оформлением.
        assertTrue(race.paragraphs.first().spans.any { it.bold && it.start == 0 })
        assertTrue(sample.notes.all { it.characterId == sample.character.id })
    }

    @Test
    fun `sample feat is split from its description`() {
        val feat = sample.feats.single()
        assertEquals("Мастер древкового оружия", feat.name)
        assertTrue(feat.description.startsWith("Вы получаете следующие преимущества:"))
        assertTrue(feat.description.contains("\n• Если вы совершаете действие Атака"))
    }

    @Test
    fun `sample equipment lines become items`() {
        assertEquals(10, sample.items.size)
        assertEquals("Посох", sample.items.first().name)
        assertEquals("Священный символ", sample.items.last().name)
        assertTrue(sample.items.all { it.quantity == 1 && !it.isMagic })
    }

    @Test
    fun `sample spells are lss ids and all prepared`() {
        assertEquals(10, sample.spells.size)
        assertTrue(sample.spells.all { LssSpellRefs.isLssId(it.ref) && it.prepared })
    }

    @Test
    fun `sample weapons become combos`() {
        assertEquals(5, sample.combos.size)
        val halberd = sample.combos.first()
        assertEquals("Алебарда", halberd.combo.name)
        val step = halberd.steps.single()
        assertEquals("1d10 + [str]", step.expression)
        assertEquals("slashing", step.damageType)
        assertEquals("Двуручное, досягаемость 10 футов", step.effect)
        assertEquals("radiant", sample.combos.first { it.combo.name == "Священное пламя" }.steps.single().damageType)
    }

    // endregion

    // region Выгрузка и загрузка обратно

    private val wizardWarlock = Character(
        name = "Элара",
        level = 7,
        classes = listOf(
            CharacterClassLevel(CharacterClass.WIZARD, 5).serialize(),
            CharacterClassLevel(CharacterClass.WARLOCK, 2).serialize(),
        ),
        canPrepareSpells = true,
        subclass = "Школа воплощения",
        race = "Эльф",
        subrace = "Высший эльф",
        background = "Мудрец",
        maxHp = 40,
        currentHp = 31,
        tempHp = 5,
        abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 14, AbilityType.INTELLIGENCE.ordinal to 18),
        saveProficiencies = mapOf(AbilityType.INTELLIGENCE.ordinal to 1),
        skillProficiencies = mapOf(SkillType.ARCANA.ordinal to 2, SkillType.HISTORY.ordinal to 1),
        // Ячейки волшебника 5 и колдуна 2 (две ячейки 1 круга) хранятся вместе.
        spellSlots = mapOf(1 to 6, 2 to 3, 3 to 2),
        spellSlotsUsed = mapOf(1 to 3),
        armorClassParts = listOf(
            StatPart(name = "Доспехи мага", formula = "13 + [dex]", kind = StatPartKind.BASE),
            StatPart(name = "Щит", formula = "2", kind = StatPartKind.BONUS, enabled = false),
            StatPart(name = "Кольцо защиты", formula = "1", kind = StatPartKind.BONUS),
        ),
        initiativeFormula = "[dex] + [pb]",
        languages = "Общий, Эльфийский",
        armorProficiencies = listOf(ArmorProficiency.SHIELDS.name),
        coins = mapOf(CoinType.GOLD.ordinal to 25, CoinType.COPPER.ordinal to 7),
        resources = listOf(
            CharacterResource(
                name = "Магическое восстановление",
                description = "Раз в день после короткого отдыха.",
                current = 1,
                maximum = 1,
                recovery = ResourceRecovery(longRest = true),
            ),
            CharacterResource(
                name = "Ограниченное желание",
                current = 1,
                maximum = 1,
                recovery = ResourceRecovery(other = true, otherText = "после 1к4 долгих отдыхов"),
            ),
            CharacterResource(
                name = "Кости чародейства",
                current = 2,
                maximum = 3,
                maximumFormula = "[pb]",
                recovery = ResourceRecovery(shortRest = true, longRest = true),
            ),
        ),
    )

    private fun note(title: String, vararg paragraphs: NoteParagraph, order: Long) = NoteBlock(
        characterId = wizardWarlock.id,
        title = title,
        paragraphs = paragraphs.toList(),
        sortOrder = order,
    )

    private fun feature(title: String, body: String) = NoteParagraph(
        text = "$title\n$body",
        spans = listOf(NoteSpan(start = 0, end = title.length, bold = true)),
    )

    private val notes = listOf(
        note("Раса: Эльф, Высший эльф", feature("Тёмное зрение", "60 футов."), order = 0),
        note("Класс: Волшебник", feature("Магическое восстановление", "Раз в день."), order = 1),
        note("Класс: Колдун", feature("Таинственные воззвания", "• Мучительный взрыв\n• Маска многих лиц"), order = 2),
        note("Архетип: Школа воплощения", feature("Лепка заклинаний", "Защищает союзников."), order = 3),
        note("Предыстория: Мудрец", feature("Исследователь", "Знает, где искать."), order = 4),
    ) + (1..6).map { note("Заметка $it", NoteParagraph(text = "Текст $it"), order = 4L + it) }

    private val fireball = Spell(id = "fireball", name = "Огненный шар", nameEn = "Fireball", level = 3)
    private val disk = Spell(id = "disk", name = "Тензеров парящий диск", nameEn = "Tenser’s floating disk", level = 1)
    private val light = Spell(id = "light", name = "Свет", nameEn = "Light", level = 0)
    private val homebrew = Spell(id = "hb", name = "Звериный рёв", level = 0)

    private val export = LssSheetExport(
        character = wizardWarlock,
        notes = notes,
        feats = listOf(
            CharacterFeat(
                id = "f1", name = "Боевой заклинатель", description = "Преимущество на концентрацию.\nСоматика с оружием.",
                source = "", book = "", createdAt = 0, collapsed = false, sortOrder = 0,
            ),
            CharacterFeat(
                id = "f2", name = "Внимательный", description = "+5 к пассивной Внимательности.",
                source = "", book = "", createdAt = 0, collapsed = false, sortOrder = 1,
            ),
        ),
        items = listOf(
            InventoryItem(characterId = wizardWarlock.id, name = "Кинжал", quantity = 2, description = "Лёгкое, метательное.", sortOrder = 0),
            InventoryItem(characterId = wizardWarlock.id, name = "Книга заклинаний", sortOrder = 1),
            InventoryItem(
                characterId = wizardWarlock.id, name = "Плащ защиты", isMagic = true,
                requiresAttunement = true, attuned = true, sortOrder = 2,
            ),
        ),
        spells = listOf(
            LssSpellEntry(fireball, prepared = true),
            LssSpellEntry(disk, prepared = false),
            LssSpellEntry(light, prepared = false),
            LssSpellEntry(homebrew, prepared = false),
        ),
        combos = listOf(
            ComboWithSteps(
                Combo(id = "c1", characterId = wizardWarlock.id, name = "Огненный шар"),
                listOf(ComboStep(characterId = wizardWarlock.id, name = "Урон", expression = "8d6", damageType = "fire", effect = "Спасбросок Ловкости")),
            ),
        ),
    )

    private val json by lazy { CharacterLssCodec.encode(export) }
    private val root by lazy { JSONObject(json) }
    private val data by lazy { JSONObject(root.getString("data")) }
    private val restored by lazy { CharacterLssCodec.decode(json) }

    @Test
    fun `wrapper follows the lss format`() {
        assertEquals("character", root.getString("jsonType"))
        assertEquals("2", root.getString("version"))
        assertEquals("2014", root.getString("edition"))
        // `data` — строка с JSON, а не вложенный объект.
        assertTrue(root.get("data") is String)
        assertEquals("character", data.getString("jsonType"))
        // Все шесть блоков заметок заняты — скрытых нет.
        assertEquals(0, root.getJSONObject("disabledBlocks").getJSONArray("notes-left").length())
    }

    @Test
    fun `computed fields are not written`() {
        val strength = data.getJSONObject("stats").getJSONObject("str")
        assertFalse(strength.has("modifier"))
        assertFalse(data.has("createdAt"))
    }

    @Test
    fun `slots are split into regular and pact with filled counter`() {
        val slots = data.getJSONObject("spells")
        val pact = data.getJSONObject("spellsPact")
        assertEquals(4, slots.getJSONObject("slots-1").getInt("value"))
        assertEquals(2, pact.getJSONObject("slots-1").getInt("value"))
        // Потраченные считаются сначала с ячеек договора.
        assertEquals(2, pact.getJSONObject("slots-1").getInt("filled"))
        assertEquals(1, slots.getJSONObject("slots-1").getInt("filled"))
        assertFalse(slots.has("slots-1-used"))
        // При загрузке ячейки снова складываются.
        assertEquals(wizardWarlock.spellSlots, restored.character.spellSlots)
        assertEquals(wizardWarlock.spellSlotsUsed, restored.character.spellSlotsUsed)
    }

    @Test
    fun `info lists classes subclass and origin`() {
        val info = data.getJSONObject("info")
        assertEquals("Волшебник 5 / Колдун 2", info.getJSONObject("charClass").getString("value"))
        assertEquals(7, info.getJSONObject("level").getInt("value"))
        assertEquals("Высший эльф", info.getJSONObject("subrace").getString("value"))
        val character = restored.character
        assertEquals(wizardWarlock.classLevels, character.classLevels)
        assertEquals(7, character.level)
        assertEquals("Школа воплощения", character.subclass)
        assertEquals("Эльф", character.race)
        assertEquals("Высший эльф", character.subrace)
        assertEquals("Мудрец", character.background)
    }

    @Test
    fun `armor class uses lss formula and separate shield`() {
        val vitality = data.getJSONObject("vitality")
        assertEquals("13 + [DEX] + 1", vitality.getJSONObject("ac").getString("value"))
        assertFalse(vitality.getJSONObject("shield").getBoolean("value"))
        assertEquals("2", vitality.getJSONObject("shield").getString("mod"))
        assertEquals("[DEX] + [PROF]", vitality.getJSONObject("initiative").getString("value"))
        assertEquals("d6", vitality.getJSONObject("hit-die").getString("value"))

        val character = restored.character
        assertEquals(wizardWarlock.effectiveArmorClass, character.effectiveArmorClass)
        assertEquals(wizardWarlock.initiativeBonus, character.initiativeBonus)
        assertEquals("[dex] + [pb]", character.initiativeFormula)
    }

    @Test
    fun `abilities proficiencies hp and coins survive`() {
        val character = restored.character
        // Лист хранит все шесть характеристик, поэтому значения по умолчанию (10) записаны явно.
        AbilityType.entries.forEach {
            assertEquals(it.name, wizardWarlock.abilityScore(it), character.abilityScore(it))
        }
        assertEquals(wizardWarlock.saveProficiencies, character.saveProficiencies)
        assertEquals(wizardWarlock.skillProficiencies, character.skillProficiencies)
        assertEquals(40, character.maxHp)
        assertEquals(31, character.currentHp)
        assertEquals(5, character.tempHp)
        assertEquals(wizardWarlock.coins, character.coins)
        assertEquals(listOf(ArmorProficiency.SHIELDS.name), character.armorProficiencies)
        assertEquals("Общий, Эльфийский", character.languages)
        assertTrue(data.getJSONObject("prof").has("armor-label"))
    }

    @Test
    fun `wizard race and class notes go to traits, subclass to features`() {
        val text = data.getJSONObject("text")
        val traits = LssRichText.lines(text.getJSONObject("traits").getJSONObject("value").getJSONObject("data"))
        assertEquals(
            listOf("Раса: Эльф, Высший эльф", "Класс: Волшебник", "Класс: Колдун"),
            traits.filter { it.isSectionHeading }.map { it.text },
        )
        val features = LssRichText.lines(text.getJSONObject("features").getJSONObject("value").getJSONObject("data"))
        assertEquals(listOf("Архетип: Школа воплощения"), features.filter { it.isSectionHeading }.map { it.text })
        // Предыстория и 6 заметок: пять — по блоку, остальные — вместе в notes-6.
        assertEquals("Предыстория: Мудрец", text.getJSONObject("notes-1").getString("customLabel"))
        assertEquals("Прочие заметки", text.getJSONObject("notes-6").getString("customLabel"))
    }

    @Test
    fun `notes are split back by section headings`() {
        val titles = restored.notes.map { it.title }
        assertEquals(
            listOf(
                "Раса: Эльф, Высший эльф",
                "Класс: Волшебник",
                "Класс: Колдун",
                "Архетип: Школа воплощения",
                "Предыстория: Мудрец",
                "Заметка 1", "Заметка 2", "Заметка 3", "Заметка 4", "Заметка 5", "Заметка 6",
            ),
            titles,
        )
        val warlock = restored.notes.first { it.title == "Класс: Колдун" }.paragraphs.single()
        assertEquals("Таинственные воззвания\n• Мучительный взрыв\n• Маска многих лиц", warlock.text)
        assertEquals(listOf(NoteSpan(start = 0, end = "Таинственные воззвания".length, bold = true)), warlock.spans)
    }

    @Test
    fun `feats survive the round trip`() {
        assertEquals(
            listOf(
                LssFeat("Боевой заклинатель", "Преимущество на концентрацию.\nСоматика с оружием."),
                LssFeat("Внимательный", "+5 к пассивной Внимательности."),
            ),
            restored.feats,
        )
    }

    @Test
    fun `items keep quantity description and attunement`() {
        val items = restored.items
        assertEquals(listOf("Кинжал", "Книга заклинаний", "Плащ защиты"), items.map { it.name })
        assertEquals(2, items[0].quantity)
        assertEquals("Лёгкое, метательное.", items[0].description)
        assertTrue(items[2].isMagic && items[2].requiresAttunement && items[2].attuned)
        val attunements = data.getJSONArray("attunementsList")
        assertEquals(3, attunements.length())
        assertEquals("Плащ защиты", attunements.getJSONObject(0).getString("value"))
    }

    @Test
    fun `resources keep recovery flags and other is long rest in lss`() {
        val resources = data.getJSONObject("resources")
        val other = resources.keys().asSequence().map { resources.getJSONObject(it) }
            .first { it.getString("name") == "Ограниченное желание" }
        assertTrue(other.getBoolean("isLongRest"))
        assertFalse(other.getBoolean("isShortRest"))
        assertEquals("traits", other.getString("location"))
        val sorcery = resources.keys().asSequence().map { resources.getJSONObject(it) }
            .first { it.getString("name") == "Кости чародейства" }
        assertEquals("[PROF]", sorcery.getString("maxExpr"))

        val byName = restored.character.resources.associateBy { it.name }
        assertEquals(ResourceRecovery(longRest = true), byName.getValue("Магическое восстановление").recovery)
        assertEquals("Раз в день после короткого отдыха.", byName.getValue("Магическое восстановление").description)
        assertEquals(
            ResourceRecovery(other = true, otherText = "после 1к4 долгих отдыхов"),
            byName.getValue("Ограниченное желание").recovery,
        )
        assertEquals("", byName.getValue("Ограниченное желание").description)
        assertEquals(ResourceRecovery(shortRest = true, longRest = true), byName.getValue("Кости чародейства").recovery)
        assertEquals("[pb]", byName.getValue("Кости чародейства").maximumFormula)
        assertEquals(3, byName.getValue("Кости чародейства").maximum)
    }

    @Test
    fun `every resource has a widget in its text block`() {
        val traits = data.getJSONObject("text").getJSONObject("traits").getJSONObject("value").getJSONObject("data")
        val content = traits.getJSONArray("content")
        val widgets = (0 until content.length()).map { content.getJSONObject(it) }
            .filter { it.getString("type") == "resource" }
            .map { it.getJSONObject("attrs").getString("id") }
        assertEquals(data.getJSONObject("resources").keys().asSequence().toSet(), widgets.toSet())
    }

    @Test
    fun `wizard spellbook uses slugs and srd aliases`() {
        val spells = root.getJSONObject("spells")
        val book = spells.getJSONArray("book")
        val prepared = spells.getJSONArray("prepared")
        assertEquals(listOf("fireball", "floating-disk"), (0 until book.length()).map { book.getString(it) })
        // Заговоры и подготовленные; заклинание без английского названия — русским названием.
        assertEquals(listOf("light", "Звериный рёв", "fireball"), (0 until prepared.length()).map { prepared.getString(it) })

        val refs = restored.spells.associate { it.ref to it.prepared }
        assertEquals(mapOf("fireball" to true, "floating-disk" to false, "light" to true, "Звериный рёв" to true), refs)
    }

    @Test
    fun `combos become weapons and back`() {
        val weapon = data.getJSONArray("weaponsList").getJSONObject(0)
        assertEquals("8d6", weapon.getJSONObject("dmg").getString("value"))
        assertEquals("Огонь", weapon.getJSONObject("dmgType").getString("value"))
        assertEquals("none", weapon.getString("ability"))
        val combo = restored.combos.single()
        assertEquals("Огненный шар", combo.combo.name)
        assertEquals("8d6", combo.steps.single().expression)
        assertEquals("fire", combo.steps.single().damageType)
        assertEquals("Спасбросок Ловкости", combo.steps.single().effect)
    }

    @Test
    fun `default initiative is written as null`() {
        val plain = JSONObject(JSONObject(CharacterLssCodec.encode(LssSheetExport(Character(name = "Б")))).getString("data"))
        assertTrue(plain.getJSONObject("vitality").getJSONObject("initiative").isNull("value"))
        assertEquals("10", plain.getJSONObject("vitality").getJSONObject("ac").getString("value"))
    }

    // endregion

    // region Формулы и разбор текста

    @Test
    fun `formulas are converted between notations`() {
        assertEquals("[PROF] * 2 + [LVL] + [DEX]", CharacterLssCodec.toLssFormula("[pb] * 2 + [level] + [dex]"))
        assertEquals("[pb] * 2 + [level] + [dex]", CharacterLssCodec.fromLssFormula("[PROF] * 2 + [LVL] + [DEX]"))
        assertEquals("1к8 + [str]", CharacterLssCodec.fromLssFormula("1к8 + [Str]"))
    }

    @Test
    fun `lss bonuses become stat parts`() {
        val data = JSONObject().apply {
            put("jsonType", "character")
            put("name", JSONObject().put("value", "Б"))
            put("stats", JSONObject())
            put(
                "vitality",
                JSONObject()
                    .put("ac", JSONObject().put("value", "10+[DEX]"))
                    .put("shield", JSONObject().put("value", true).put("mod", "2")),
            )
            put(
                "bonuses",
                JSONArray().put(
                    JSONObject().put("id", "b").put("label", "Кольцо защиты").put("target", "ac")
                        .put("mode", "add").put("value", 1).put("source", JSONObject().put("kind", "item")),
                ),
            )
        }
        val sheet = JSONObject().put("jsonType", "character").put("data", data.toString()).toString()
        val character = CharacterLssCodec.decode(sheet).character
        assertEquals(listOf("10+[dex]", "2", "1"), character.armorClassParts.map { it.formula })
        assertEquals("Кольцо защиты", character.armorClassParts.last().name)
        // 10 + 0 (Ловкость 10) + щит 2 + кольцо 1.
        assertEquals(13, character.effectiveArmorClass)
    }

    @Test
    fun `resource with formula keeps spent uses`() {
        val data = JSONObject().apply {
            put("jsonType", "character")
            put("info", JSONObject().put("level", JSONObject().put("name", "level").put("value", 9)))
            put("stats", JSONObject())
            put("vitality", JSONObject())
            put(
                "resources",
                JSONObject().put(
                    "resource-1",
                    JSONObject().put("id", "resource-1").put("name", "Кости").put("current", 2)
                        .put("maxExpr", "[PROF]").put("isShortRest", true),
                ),
            )
        }
        val sheet = JSONObject().put("jsonType", "character").put("data", data.toString()).toString()
        val resource = CharacterLssCodec.decode(sheet).character.resources.single()
        // Бонус мастерства 9 уровня — 4; потрачено два из четырёх.
        assertEquals(4, resource.maximum)
        assertEquals(2, resource.current)
        assertEquals("[pb]", resource.maximumFormula)
    }

    @Test
    fun `feats without bold headings are split by paragraphs`() {
        val plainHeading = listOf(
            RichLine("Внимательный"),
            RichLine("Вы получаете +5 к пассивной Внимательности."),
        )
        assertEquals(
            listOf(LssFeat("Внимательный", "Вы получаете +5 к пассивной Внимательности.")),
            CharacterLssCodec.parseFeats(plainHeading),
        )
        val inlineHeading = listOf(
            RichLine(
                "Крепкий. Ваш максимум хитов растёт.",
                spans = listOf(NoteSpan(start = 0, end = "Крепкий.".length, italic = true)),
            ),
            RichLine("", isBreak = true),
            RichLine(
                "Удачливый. Три очка удачи.",
                spans = listOf(NoteSpan(start = 0, end = "Удачливый.".length, bold = true)),
            ),
        )
        assertEquals(
            listOf(LssFeat("Крепкий", "Ваш максимум хитов растёт."), LssFeat("Удачливый", "Три очка удачи.")),
            CharacterLssCodec.parseFeats(inlineHeading),
        )
    }

    @Test
    fun `capitalized feat names are normalized`() {
        val feats = CharacterLssCodec.parseFeats(
            listOf(
                RichLine("МАСТЕР ДРЕВКОВОГО ОРУЖИЯ", spans = listOf(NoteSpan(0, 24, bold = true))),
                RichLine("Бонусная атака древком."),
            ),
        )
        assertEquals(listOf(LssFeat("Мастер древкового оружия", "Бонусная атака древком.")), feats)
    }

    // endregion

    @Test
    fun `non sheet json is rejected`() {
        assertFalse(CharacterLssCodec.looksLikeCharacterSheet("""{"name":"Огненный шар","type":"spell"}"""))
        assertTrue(CharacterLssCodec.looksLikeCharacterSheet(File("src/test/resources/lss/sample-character.json").readText()))
        assertNull(runCatching { CharacterLssCodec.decode("""{"type":"spell"}""") }.getOrNull())
    }
}
