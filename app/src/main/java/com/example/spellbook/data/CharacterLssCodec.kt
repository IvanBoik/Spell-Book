package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.CreatureSize
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboWithSteps
import com.example.spellbook.data.model.DEFAULT_SPEED_FORMULA
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.MAX_CLASS_LEVEL
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.ProficiencyLevel
import com.example.spellbook.data.model.ResourceRecovery
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.UNARMORED_AC_FORMULA
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.pactSlotsFor
import com.example.spellbook.data.model.preparesSpells
import com.example.spellbook.data.wizard.FeatureSpells
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Заклинание персонажа для выгрузки: запись библиотеки и отметка подготовки. */
data class LssSpellEntry(val spell: Spell, val prepared: Boolean)

/** Всё, что выгружается в лист LSS вместе с персонажем. */
data class LssSheetExport(
    val character: Character,
    val notes: List<NoteBlock> = emptyList(),
    val feats: List<CharacterFeat> = emptyList(),
    val items: List<InventoryItem> = emptyList(),
    val spells: List<LssSpellEntry> = emptyList(),
    val combos: List<ComboWithSteps> = emptyList(),
)

/** Черта из блока «Черты» листа LSS: с библиотекой её сопоставляет репозиторий. */
data class LssFeat(val name: String, val description: String)

/**
 * Заклинание из книги заклинаний листа LSS: слаг, английское название или id базы LSS
 * (см. [LssSpellRefs]). С библиотекой его сопоставляет репозиторий.
 */
data class LssSpellRef(val ref: String, val prepared: Boolean)

/**
 * Разобранный лист LSS: новый персонаж и всё, что к нему относится. Связи с персонажем
 * ([NoteBlock.characterId] и т. п.) уже проставлены, заклинания и черты ещё не сопоставлены с библиотекой.
 */
data class LssSheetImport(
    val character: Character,
    val notes: List<NoteBlock>,
    val feats: List<LssFeat>,
    val items: List<InventoryItem>,
    val spells: List<LssSpellRef>,
    val combos: List<ComboWithSteps>,
)

/**
 * Чтение и запись листа персонажа в формате LSS (Long Story Short),
 * см. https://longstoryshort.app/doc/developers/characters/.
 *
 * Особенность формата: содержимое листа лежит строкой JSON в поле `data` внешней обёртки,
 * а редактируемые значения обёрнуты в `{"value": …}`.
 *
 * Как данные приложения раскладываются по листу:
 * - заметки мастера «Раса: …» и «Класс: …» — в блок «Умения и способности» (`traits`),
 *   «Архетип: …» — в «Дополнительные способности» (`features`), остальные заметки — в `notes-1…6`;
 * - черты — в блок «Черты» (`feats`), снаряжение — в `equipment`, настройка — в `attunementsList`;
 * - ресурсы — в `resources` с виджетами в блоке `traits`;
 * - комбинации — в список оружия (`weaponsList`);
 * - заклинания — слагами в книгу заклинаний (`spells` внешней обёртки).
 *
 * Формулы переводятся между нотациями: `[pb]` ↔ `[PROF]`, `[level]` ↔ `[LVL]`, `[dex]` ↔ `[DEX]`.
 */
object CharacterLssCodec {

    private const val JSON_TYPE_CHARACTER = "character"
    private const val TEMPLATE = "default"
    private const val WRAPPER_VERSION = "2"
    private const val EDITION = "2014"
    private const val SHEET_EDITION = "2024"
    private const val MAX_SPELL_SLOT_LEVEL = 9
    private const val INDENT = 2
    private const val NOTE_ORDER_BASE = 1_000_000L

    /** Текстовые блоки листа. */
    private const val ATTACKS = "attacks"
    private const val TRAITS = "traits"
    private const val FEATURES = "features"
    private const val FEATS = "feats"
    private const val EQUIPMENT = "equipment"
    private const val PROF = "prof"
    private val NOTE_SLOTS = (1..6).map { "notes-$it" }

    /** Заголовки заметок, которые создаёт мастер, — по ним заметки раскладываются по блокам листа. */
    private const val RACE_PREFIX = "Раса:"
    private const val CLASS_PREFIX = "Класс:"
    private const val SUBCLASS_PREFIX = "Архетип:"

    /** Подпись блока `notes-6`, если заметок больше, чем блоков. */
    private const val OVERFLOW_NOTES_LABEL = "Прочие заметки"

    /** Подписи строк блока владений. */
    private const val LANGUAGES_LABEL = "Языки"
    private const val TOOLS_LABEL = "Инструменты"
    private const val OTHER_WEAPONS_LABEL = "Другое оружие"

    /** Ключи характеристик в формате LSS в порядке [AbilityType]. */
    private val ABILITY_KEYS = listOf("str", "dex", "con", "int", "wis", "cha")

    /** Английские названия навыков LSS в порядке [SkillType]. */
    private val SKILL_KEYS = listOf(
        "acrobatics", "animal handling", "arcana", "athletics", "deception",
        "history", "insight", "intimidation", "investigation", "medicine",
        "nature", "perception", "performance", "persuasion", "religion",
        "sleight of hand", "stealth", "survival",
    )

    /** Флаги владений в блоке `prof`; `armor-label` — это щиты (историческое имя ключа). */
    private val ARMOR_PROF_KEYS = mapOf(
        ArmorProficiency.LIGHT to "armor-light",
        ArmorProficiency.MEDIUM to "armor-medium",
        ArmorProficiency.HEAVY to "armor-heavy",
        ArmorProficiency.SHIELDS to "armor-label",
    )
    private val WEAPON_PROF_KEYS = mapOf(
        WeaponProficiency.SIMPLE to "weapon-simple",
        WeaponProficiency.MARTIAL to "weapon-martial",
        WeaponProficiency.OTHER to "weapon-other",
    )

    /** Названия классов: русское (так их пишет лист LSS) и английское. */
    private val CLASS_NAMES = mapOf(
        CharacterClass.ARTIFICER to ("Изобретатель" to "Artificer"),
        CharacterClass.BARBARIAN to ("Варвар" to "Barbarian"),
        CharacterClass.BARD to ("Бард" to "Bard"),
        CharacterClass.CLERIC to ("Жрец" to "Cleric"),
        CharacterClass.DRUID to ("Друид" to "Druid"),
        CharacterClass.FIGHTER to ("Воин" to "Fighter"),
        CharacterClass.MONK to ("Монах" to "Monk"),
        CharacterClass.PALADIN to ("Паладин" to "Paladin"),
        CharacterClass.RANGER to ("Следопыт" to "Ranger"),
        CharacterClass.ROGUE to ("Плут" to "Rogue"),
        CharacterClass.SORCERER to ("Чародей" to "Sorcerer"),
        CharacterClass.WARLOCK to ("Колдун" to "Warlock"),
        CharacterClass.WIZARD to ("Волшебник" to "Wizard"),
    )

    /** Заклинательная характеристика класса — для блока каста листа. */
    private val CASTING_ABILITY = mapOf(
        CharacterClass.ARTIFICER to "int",
        CharacterClass.WIZARD to "int",
        CharacterClass.CLERIC to "wis",
        CharacterClass.DRUID to "wis",
        CharacterClass.RANGER to "wis",
        CharacterClass.BARD to "cha",
        CharacterClass.PALADIN to "cha",
        CharacterClass.SORCERER to "cha",
        CharacterClass.WARLOCK to "cha",
    )

    /** Подписи текстовых блоков LSS по умолчанию — заголовки заметок при загрузке. */
    private val BLOCK_LABELS = mapOf(
        ATTACKS to "Атаки и заклинания",
        "weapons" to "Оружие",
        TRAITS to "Умения и способности",
        FEATURES to "Дополнительные способности и умения",
        "attunement" to "Настройка",
        "personality" to "Черты характера",
        "ideals" to "Идеалы",
        "bonds" to "Привязанности",
        "flaws" to "Слабости",
        "background" to "Предыстория",
        "allies" to "Союзники и организации",
        "quests" to "Задания",
        "appearance" to "Внешность",
        "items" to "Предметы",
    ) + NOTE_SLOTS.mapIndexed { index, key -> key to "Заметки ${index + 1}" }

    /**
     * Порядок, в котором текстовые блоки становятся заметками; остальные — следом по алфавиту.
     * «Атаки и заклинания» идут после способностей: выгруженные туда умения с заклинаниями
     * дописываются в конец заметок своей расы, класса и архетипа.
     */
    private val BLOCK_ORDER = listOf(
        TRAITS, FEATURES, ATTACKS, "weapons", "attunement", "background", "personality",
        "ideals", "bonds", "flaws", "allies", "quests", "appearance", "items",
    ) + NOTE_SLOTS

    /** Блоки, у которых при загрузке своё назначение, а не заметки. */
    private val SPECIAL_BLOCKS = setOf(FEATS, EQUIPMENT, PROF)

    private val VARIABLE = Regex("\\[([^\\]]*)]")
    private val APP_TO_LSS_VARIABLES = mapOf("pb" to "PROF", "level" to "LVL")
    private val LSS_TO_APP_VARIABLES = mapOf("PROF" to "pb", "LVL" to "level")

    // region Экспорт

    /** Собирает лист персонажа в JSON формата LSS. */
    fun encode(sheet: LssSheetExport): String {
        val character = sheet.character
        val text = encodeText(sheet)
        val data = JSONObject().apply {
            put("jsonType", JSON_TYPE_CHARACTER)
            put("template", TEMPLATE)
            put("name", valueObject(character.name))
            put("info", encodeInfo(character))
            put("subInfo", encodeSubInfo())
            put("spellsInfo", encodeSpellsInfo(character))
            val (slots, pactSlots) = encodeSpellSlots(character)
            put("spells", slots)
            put("spellsPact", pactSlots)
            // Числовые бонусы уже учтены в формулах КД, скорости и инициативы.
            put("bonuses", JSONArray())
            put("proficiency", character.proficiencyBonus)
            put("stats", encodeStats(character))
            put("saves", encodeSaves(character))
            put("skills", encodeSkills(character))
            put("vitality", encodeVitality(character))
            put("attunementsList", encodeAttunements(character, sheet.items))
            put("weaponsList", encodeWeapons(sheet.combos) + encodeCantrips(character, sheet))
            put("text", text.blocks)
            put("coins", encodeCoins(character))
            put("resources", encodeResources(character))
            put("conditions", JSONArray())
            put("exhaustion", 0)
            put("inspiration", false)
            put("avatar", encodeAvatar(character))
            put("prof", encodeProficiencies(character))
        }
        return JSONObject().apply {
            put("jsonType", JSON_TYPE_CHARACTER)
            put("version", WRAPPER_VERSION)
            put("edition", EDITION)
            put("sheetEdition", SHEET_EDITION)
            put("spells", encodeSpellbook(character, sheet.spells))
            put("disabledBlocks", encodeDisabledBlocks(text.usedNotes))
            put("data", data.toString())
        }.toString(INDENT)
    }

    private fun encodeInfo(character: Character): JSONObject = JSONObject().apply {
        put("charClass", namedValue("charClass", classSummary(character.classLevels)))
        put("charSubclass", namedValue("charSubclass", character.subclass))
        put("level", namedValue("level", character.level))
        put("background", namedValue("background", character.background))
        put("playerName", namedValue("playerName", ""))
        put("race", namedValue("race", character.race))
        // У разновидности своя обёртка — без `name`, только `value`.
        if (character.subrace.isNotBlank()) put("subrace", valueObject(character.subrace))
        put("alignment", namedValue("alignment", ""))
        put("experience", namedValue("experience", ""))
        // Размер влияет на грузоподъёмность; без него лист считает её нулевой.
        CreatureSize.fromCode(character.size)?.let { put("size", valueObject(it.code)) }
    }

    /** «Паладин» для одного класса (уровень — отдельным полем) и «Волшебник 5 / Колдун 2» для мультикласса. */
    private fun classSummary(classes: List<CharacterClassLevel>): String = when (classes.size) {
        0 -> ""
        1 -> className(classes.single())
        else -> classes.joinToString(" / ") { "${className(it)} ${it.level}" }
    }

    private fun className(entry: CharacterClassLevel): String =
        entry.displayName ?: CLASS_NAMES[entry.characterClass]?.first.orEmpty()

    private fun encodeSubInfo(): JSONObject = JSONObject().apply {
        listOf("age", "height", "weight", "eyes", "skin", "hair").forEach { put(it, namedValue(it, "")) }
    }

    /** Блок каста: заклинательная характеристика основного класса и списки заклинаний классов. */
    private fun encodeSpellsInfo(character: Character): JSONObject {
        val casters = character.classLevels.filter { it.characterClass in CASTING_ABILITY }
        val main = casters.maxByOrNull { it.level }?.characterClass
        return JSONObject().apply {
            put(
                "base",
                namedValue("base", "").apply { main?.let { put("code", CASTING_ABILITY.getValue(it)) } },
            )
            put("save", namedValue("save", ""))
            put("mod", namedValue("mod", ""))
            val lists = casters.mapNotNull { it.characterClass.spellListCode }.distinct()
            if (lists.isNotEmpty()) put("available", JSONObject().put("classes", JSONArray(lists)))
        }
    }

    /**
     * Ячейки заклинаний: обычные — в `spells`, колдуна — в `spellsPact`. В приложении они
     * хранятся вместе, поэтому ячейки договора отделяются по уровню колдуна.
     */
    private fun encodeSpellSlots(character: Character): Pair<JSONObject, JSONObject> {
        val slots = JSONObject()
        val pactSlots = JSONObject()
        val pact = pactSlotsFor(character.classLevels)
        for (level in 1..MAX_SPELL_SLOT_LEVEL) {
            val total = character.spellSlots[level] ?: 0
            if (total <= 0) continue
            val used = (character.spellSlotsUsed[level] ?: 0).coerceIn(0, total)
            val pactCount = if (pact != null && pact.second == level) minOf(pact.first, total) else 0
            val regular = total - pactCount
            // Ячейки договора восстанавливаются чаще, поэтому потраченные считаются сначала с них.
            val pactUsed = minOf(used, pactCount)
            val regularUsed = (used - pactUsed).coerceAtMost(regular)
            if (regular > 0) slots.put("slots-$level", slotObject(regular, regularUsed))
            if (pactCount > 0) pactSlots.put("slots-$level", slotObject(pactCount, pactUsed))
        }
        return slots to pactSlots
    }

    private fun slotObject(total: Int, used: Int) = JSONObject().put("value", total).put("filled", used)

    private fun encodeStats(character: Character): JSONObject = JSONObject().apply {
        AbilityType.entries.forEach { ability ->
            val key = ABILITY_KEYS[ability.ordinal]
            // Модификатор лист считает сам: схема запрещает вычисляемые поля.
            put(key, JSONObject().put("name", key).put("score", character.abilityScore(ability)))
        }
    }

    private fun encodeSaves(character: Character): JSONObject = JSONObject().apply {
        AbilityType.entries.forEach { ability ->
            val key = ABILITY_KEYS[ability.ordinal]
            val isProf: Any = when (character.saveProficiency(ability)) {
                ProficiencyLevel.NONE -> false
                ProficiencyLevel.PROFICIENT -> true
                ProficiencyLevel.EXPERTISE -> ProficiencyLevel.EXPERTISE.multiplier
            }
            put(key, JSONObject().put("name", key).put("isProf", isProf))
        }
    }

    private fun encodeSkills(character: Character): JSONObject = JSONObject().apply {
        SkillType.entries.forEach { skill ->
            val key = SKILL_KEYS[skill.ordinal]
            put(
                key,
                JSONObject().apply {
                    put("name", key)
                    put("baseStat", ABILITY_KEYS[skill.ability.ordinal])
                    // LSS хранит множитель: 1 — владение, 2 — экспертиза.
                    val multiplier = character.skillProficiency(skill).multiplier
                    if (multiplier > 0) put("isProf", multiplier)
                },
            )
        }
    }

    private fun encodeVitality(character: Character): JSONObject = JSONObject().apply {
        put("hp-current", valueObject(character.currentHp))
        put("hp-temp", valueObject(character.tempHp))
        put("hp-max", valueObject(character.maxHp))
        put("hp-max-bonus", valueObject(0))
        val dice = character.classLevels
            .groupBy { hitDie(it.characterClass) }
            .mapValues { (_, entries) -> entries.sumOf { it.level } }
        dice.maxByOrNull { it.value }?.let { (die, _) -> put("hit-die", valueObject("d$die")) }
        // Потраченные кости хитов приложение не ведёт: все кости считаются доступными.
        put("hp-dice-current", valueObject(character.level))
        put(
            "hp-dice-multi",
            JSONObject().apply {
                if (dice.size > 1) {
                    dice.forEach { (die, count) -> put("d$die", JSONObject().put("max", count).put("current", count)) }
                }
            },
        )
        put("darkvision", valueObject(0))
        put("speed", valueObject(speedFormula(character)))
        val (ac, shield) = armorClass(character)
        put("ac", valueObject(ac))
        put("shield", shield)
        put("initiative", JSONObject().put("value", initiativeFormula(character) ?: JSONObject.NULL))
        put("isDying", false)
        put("deathFails", 0)
        put("deathSuccesses", 0)
    }

    private fun hitDie(characterClass: CharacterClass): Int = when (characterClass) {
        CharacterClass.BARBARIAN -> 12
        CharacterClass.FIGHTER, CharacterClass.PALADIN, CharacterClass.RANGER -> 10
        CharacterClass.SORCERER, CharacterClass.WIZARD -> 6
        else -> 8
    }

    /**
     * КД формулой листа и щит отдельным переключателем (в LSS он прибавляется поверх `ac`).
     * Формула проверяется: если она не даёт тот же КД, что в приложении, пишется число.
     */
    private fun armorClass(character: Character): Pair<String, JSONObject> {
        val parts = character.armorClassParts
        if (parts.isEmpty()) return character.armorClass.toString() to JSONObject().put("value", false)
        val shieldPart = parts.firstOrNull { it.kind == StatPartKind.BONUS && isShield(it.name) }
        val base = character.activeStatBase(parts)?.formula ?: UNARMORED_AC_FORMULA
        val bonuses = parts.filter { it.kind == StatPartKind.BONUS && it.enabled && it !== shieldPart }
        val shieldValue = shieldPart?.takeIf { it.enabled }?.let { character.statPartValue(it) } ?: 0
        val ac = verifiedFormula(composeFormula(base, bonuses.map { it.formula }), character, character.effectiveArmorClass - shieldValue)
        val shield = JSONObject().put("value", shieldPart?.enabled == true)
        shieldPart?.let { shield.put("mod", toLssFormula(it.formula)) }
        return ac to shield
    }

    private fun isShield(name: String): Boolean {
        val normalized = name.trim().lowercase()
        return normalized.startsWith("щит") || normalized.startsWith("shield")
    }

    private fun speedFormula(character: Character): String {
        val parts = character.speedParts
        if (parts.isEmpty()) return character.speed.toString()
        val base = character.activeStatBase(parts)?.formula ?: DEFAULT_SPEED_FORMULA
        val bonuses = parts.filter { it.kind == StatPartKind.BONUS && it.enabled }.map { it.formula }
        return verifiedFormula(composeFormula(base, bonuses), character, character.effectiveSpeed)
    }

    /** Формула инициативы листа; null — по умолчанию (модификатор Ловкости). */
    private fun initiativeFormula(character: Character): String? {
        val formula = if (character.initiativeParts.isNotEmpty()) {
            val parts = character.initiativeParts
            val base = character.activeStatBase(parts)?.formula ?: DEFAULT_INITIATIVE_FORMULA
            composeFormula(base, parts.filter { it.kind == StatPartKind.BONUS && it.enabled }.map { it.formula })
        } else {
            character.initiativeFormula.trim()
        }
        if (formula.isBlank()) return null
        val lss = verifiedFormula(formula, character, character.initiativeBonus)
        return lss.takeUnless { it.replace(" ", "").equals("[DEX]", ignoreCase = true) }
    }

    /** Формула в нотации LSS, если она даёт [expected]; иначе — само число. */
    private fun verifiedFormula(formula: String, character: Character, expected: Int): String =
        if (StatFormula.evaluateValue(formula, character) == expected) toLssFormula(formula) else expected.toString()

    /** «Основа + прибавки»: отрицательные числа пишутся вычитанием, выражения — в скобках. */
    private fun composeFormula(base: String, bonuses: List<String>): String = buildString {
        append(base.trim())
        bonuses.map { it.trim() }.filter { it.isNotEmpty() }.forEach { bonus ->
            val number = bonus.removePrefix("+").trim().toIntOrNull()
            when {
                number != null && number < 0 -> append(" - ").append(-number)
                number != null -> append(" + ").append(number)
                else -> append(" + (").append(bonus).append(')')
            }
        }
    }

    /** Формула приложения в нотации LSS: `[pb]` → `[PROF]`, `[level]` → `[LVL]`, `[dex]` → `[DEX]`. */
    internal fun toLssFormula(formula: String): String = VARIABLE.replace(formula) { match ->
        val code = match.groupValues[1].trim().lowercase()
        "[" + (APP_TO_LSS_VARIABLES[code] ?: code.uppercase()) + "]"
    }

    /** Формула LSS в нотации приложения: обратное к [toLssFormula]. */
    internal fun fromLssFormula(formula: String): String = VARIABLE.replace(formula) { match ->
        val code = match.groupValues[1].trim().uppercase()
        "[" + (LSS_TO_APP_VARIABLES[code] ?: code.lowercase()) + "]"
    }

    /**
     * Слоты настройки: магические предметы, требующие настройки; отметка — настроен ли предмет.
     * Пустые слоты добивают список до лимита настройки, как в самом LSS.
     */
    private fun encodeAttunements(character: Character, items: List<InventoryItem>): JSONArray {
        val array = JSONArray()
        val attunable = items.filter { it.isMagic && it.requiresAttunement }
        attunable.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", "attunement-${item.id}")
                    .put("checked", item.attuned)
                    .put("value", item.name),
            )
        }
        repeat((character.maxAttunedItems - attunable.size).coerceAtLeast(0)) { index ->
            array.put(JSONObject().put("id", "attunement-empty-${index + 1}").put("checked", false).put("value", ""))
        }
        return array
    }

    /**
     * Комбинации — оружием листа: шаги складываются в формулу урона, типы урона и эффекты —
     * в подписи. Характеристика атаки — первая из формулы, без неё атака без модификатора.
     */
    private fun encodeWeapons(combos: List<ComboWithSteps>): JSONArray {
        val array = JSONArray()
        combos.forEach { (combo, steps) ->
            val damage = steps.map { it.formula.trim().removePrefix("+").trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" + ")
            val ability = Regex("\\[(str|dex|con|int|wis|cha)]", RegexOption.IGNORE_CASE)
                .find(damage)?.groupValues?.get(1)?.lowercase() ?: "none"
            val damageTypes = steps.mapNotNull { step ->
                step.damageType.takeIf { it.isNotBlank() }?.let { code ->
                    SpellOptions.damageTypes.firstOrNull { it.code == code }?.canonical ?: code
                }
            }.distinct()
            val effects = steps.map { it.effect.trim() }.filter { it.isNotEmpty() }
            array.put(
                JSONObject().apply {
                    put("id", "weapon-${combo.id}")
                    put("name", valueObject(combo.name))
                    put("dmg", valueObject(toLssFormula(damage)))
                    put("ability", ability)
                    put("isProf", true)
                    put("dmgType", valueObject(damageTypes.joinToString(", ")))
                    if (effects.isNotEmpty()) {
                        put("notes", valueObject(effects.joinToString("; ")))
                        put("notesVisibility", true)
                    }
                },
            )
        }
        return array
    }

    /**
     * Заговоры, наносящие урон, — в «Оружие и боевые заговоры», как их заводят в самом LSS:
     * урон с учётом уровня, атака от заклинательной характеристики, спасбросок — в заметке.
     * Заговор пропускается, если у персонажа уже есть комбинация с тем же названием.
     */
    private fun encodeCantrips(character: Character, sheet: LssSheetExport): JSONArray {
        val comboNames = sheet.combos.map { normalize(it.combo.name) }.toSet()
        val ability = character.classLevels
            .filter { it.characterClass in CASTING_ABILITY }
            .maxByOrNull { it.level }
            ?.let { CASTING_ABILITY.getValue(it.characterClass) }
            ?: "none"
        val array = JSONArray()
        sheet.spells.map { it.spell }.distinctBy { it.id }
            .filter { it.level == 0 && normalize(it.name) !in comboNames }
            .forEach { spell ->
                val damage = CantripDamages.of(spell, character.level) ?: return@forEach
                val typeLabel = SpellOptions.damageTypes.firstOrNull { it.code == damage.damageType }?.canonical.orEmpty()
                array.put(
                    JSONObject().apply {
                        put("id", "weapon-spell-${spell.id}")
                        put("name", valueObject(spell.name))
                        put("dmg", valueObject(damage.dice))
                        put("ability", ability)
                        put("isProf", true)
                        put("dmgType", valueObject(typeLabel))
                        if (damage.notes.isNotBlank()) {
                            put("notes", valueObject(damage.notes))
                            put("notesVisibility", true)
                        }
                    },
                )
            }
        return array
    }

    private operator fun JSONArray.plus(other: JSONArray): JSONArray {
        val result = JSONArray()
        for (index in 0 until length()) result.put(get(index))
        for (index in 0 until other.length()) result.put(other.get(index))
        return result
    }

    /** Текстовые блоки листа и занятые блоки заметок (остальные скрываются). */
    private class TextBlocks(val blocks: JSONObject, val usedNotes: Set<String>)

    private fun encodeText(sheet: LssSheetExport): TextBlocks {
        val blocks = JSONObject()
        val notes = sheet.notes.sortedWith(compareBy<NoteBlock> { it.sortOrder }.thenByDescending { it.createdAt })
        val race = notes.filter { it.title.startsWith(RACE_PREFIX) }
        val classes = notes.filter { it.title.startsWith(CLASS_PREFIX) }
        val subclasses = notes.filter { it.title.startsWith(SUBCLASS_PREFIX) }
        val others = notes - (race + classes + subclasses).toSet()

        // Умения расы, класса и архетипа, дающие заклинания (таблица заклинаний архетипа,
        // «вы знаете заговор…»), — в «Атаки и заклинания», остальные — по своим блокам.
        fun spellsOnly(block: NoteBlock) = block.copy(paragraphs = block.paragraphs.filter(::grantsSpells))
        fun withoutSpells(block: NoteBlock) = block.copy(paragraphs = block.paragraphs.filterNot(::grantsSpells))
        sections((race + classes + subclasses).map(::spellsOnly))
            .takeIf { it.isNotEmpty() }
            ?.let { blocks.put(ATTACKS, LssRichText.textBlock(it)) }

        // Способности расы и класса — в «Умения и способности», там же виджеты ресурсов.
        val traits = sections((race + classes).map(::withoutSpells)).toMutableList()
        val resources = sheet.character.resources
        if (resources.isNotEmpty()) {
            if (traits.isNotEmpty()) traits += LssRichText.emptyParagraph()
            resources.forEach { traits += LssRichText.resourceNode(lssResourceId(it), TRAITS) }
        }
        if (traits.isNotEmpty()) blocks.put(TRAITS, LssRichText.textBlock(traits))

        sections(subclasses.map(::withoutSpells)).takeIf { it.isNotEmpty() }?.let { blocks.put(FEATURES, LssRichText.textBlock(it)) }
        encodeFeats(sheet.feats).takeIf { it.isNotEmpty() }?.let { blocks.put(FEATS, LssRichText.textBlock(it)) }
        encodeEquipment(sheet.items).takeIf { it.isNotEmpty() }?.let { blocks.put(EQUIPMENT, LssRichText.textBlock(it)) }
        encodeProfText(sheet.character).takeIf { it.isNotEmpty() }?.let { blocks.put(PROF, LssRichText.textBlock(it)) }

        // Остальные заметки — по одной на блок, а не поместившиеся — вместе в последний.
        val usedNotes = mutableSetOf<String>()
        val single = if (others.size <= NOTE_SLOTS.size) others else others.take(NOTE_SLOTS.size - 1)
        single.forEachIndexed { index, block ->
            val key = NOTE_SLOTS[index]
            blocks.put(key, LssRichText.textBlock(LssRichText.noteParagraphs(block.paragraphs), block.title))
            usedNotes += key
        }
        val overflow = others.drop(single.size)
        if (overflow.isNotEmpty()) {
            val key = NOTE_SLOTS.last()
            blocks.put(key, LssRichText.textBlock(sections(overflow), OVERFLOW_NOTES_LABEL))
            usedNotes += key
        }
        return TextBlocks(blocks, usedNotes)
    }

    /** Даёт ли умение (абзац заметки мастера) заклинания, которые всегда при персонаже. */
    private fun grantsSpells(paragraph: NoteParagraph): Boolean = FeatureSpells.grantsSpells(paragraph.text)

    /**
     * Несколько заметок в одном блоке: у каждой заголовок раздела, по которому её можно отделить снова.
     * Пустые заметки пропускаются.
     */
    private fun sections(notes: List<NoteBlock>): List<JSONObject> {
        val nodes = mutableListOf<JSONObject>()
        notes.forEach { block ->
            val content = LssRichText.noteParagraphs(block.paragraphs)
            if (content.isEmpty()) return@forEach
            if (nodes.isNotEmpty()) nodes += LssRichText.emptyParagraph()
            if (block.title.isNotBlank()) nodes += LssRichText.sectionHeading(block.title)
            nodes += content
        }
        return nodes
    }

    /** Черты: жирный заголовок и описание, черты разделены пустой строкой. */
    private fun encodeFeats(feats: List<CharacterFeat>): List<JSONObject> {
        val nodes = mutableListOf<JSONObject>()
        feats.sortedBy { it.sortOrder }.forEach { feat ->
            if (nodes.isNotEmpty()) nodes += LssRichText.emptyParagraph()
            nodes += LssRichText.heading(feat.name)
            nodes += LssRichText.plainText(feat.description)
        }
        return nodes
    }

    /** Снаряжение — списком: «Название ×2», описание — абзацами того же пункта. */
    private fun encodeEquipment(items: List<InventoryItem>): List<JSONObject> {
        if (items.isEmpty()) return emptyList()
        val list = JSONArray()
        items.sortedWith(compareBy<InventoryItem> { it.sortOrder }.thenByDescending { it.createdAt }).forEach { item ->
            val title = if (item.quantity > 1) "${item.name} ×${item.quantity}" else item.name
            val content = JSONArray().put(plainParagraph(title))
            item.description.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { content.put(plainParagraph(it)) }
            list.put(JSONObject().put("type", "listItem").put("content", content))
        }
        return listOf(JSONObject().put("type", "bulletList").put("content", list))
    }

    private fun plainParagraph(text: String): JSONObject = JSONObject().put("type", "paragraph")
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

    /** Языки, инструменты и прочее оружие — строками «Подпись: значения», как в листе LSS. */
    private fun encodeProfText(character: Character): List<JSONObject> {
        val lines = listOf(
            LANGUAGES_LABEL to character.languages,
            TOOLS_LABEL to character.toolProficiencies,
            OTHER_WEAPONS_LABEL to character.otherWeaponProficiencies,
        ).filter { it.second.isNotBlank() }
        val nodes = mutableListOf<JSONObject>()
        lines.forEach { (label, value) ->
            if (nodes.isNotEmpty()) nodes += LssRichText.emptyParagraph()
            nodes += LssRichText.labeledParagraph(label, value.trim())
        }
        return nodes
    }

    private fun encodeCoins(character: Character): JSONObject = JSONObject().apply {
        CoinType.entries.forEach { coin ->
            val amount = character.coins[coin.ordinal] ?: 0
            if (amount > 0) put(coin.code, valueObject(amount))
        }
    }

    /**
     * Ресурсы — счётчиками LSS. Особое восстановление («Другое») LSS не умеет: такой ресурс
     * восстанавливается долгим отдыхом, а условие дописывается в его заметку.
     */
    private fun encodeResources(character: Character): JSONObject = JSONObject().apply {
        character.resources.forEach { resource ->
            val id = lssResourceId(resource)
            put(
                id,
                JSONObject().apply {
                    put("id", id)
                    put("name", resource.name)
                    put("current", resource.current)
                    if (resource.maximumFormula.isNotBlank()) {
                        put("maxExpr", toLssFormula(resource.maximumFormula))
                    } else {
                        put("max", resource.maximum)
                    }
                    put("location", TRAITS)
                    put("isShortRest", resource.recovery.shortRest)
                    put("isLongRest", resource.recovery.longRest || resource.recovery.other)
                    val notes = listOfNotNull(
                        resource.description.takeIf { it.isNotBlank() },
                        resource.recovery.otherText.takeIf { resource.recovery.other && it.isNotBlank() }
                            ?.let { "$RECOVERY_NOTE_PREFIX$it" },
                    ).joinToString("\n")
                    if (notes.isNotBlank()) {
                        put("notes", notes)
                        put("notesVisibility", false)
                    }
                },
            )
        }
    }

    /** Строка заметки ресурса LSS с особым восстановлением. */
    private const val RECOVERY_NOTE_PREFIX = "Восстановление: "

    private fun lssResourceId(resource: CharacterResource): String =
        if (resource.id.startsWith("resource-")) resource.id else "resource-${resource.id}"

    /** Портрет переносится, только если это картинка с серверов LSS: локальный файл листу недоступен. */
    private fun encodeAvatar(character: Character): Any {
        val uri = character.imageUri?.takeIf { it.startsWith("https://") && it.contains("longstoryshort.app") }
            ?: return JSONObject.NULL
        return JSONObject().put("jpeg", uri)
    }

    private fun encodeProficiencies(character: Character): JSONObject = JSONObject().apply {
        ARMOR_PROF_KEYS.forEach { (armor, key) ->
            if (character.hasArmorProficiency(armor)) put(key, valueObject(true))
        }
        WEAPON_PROF_KEYS.forEach { (weapon, key) ->
            if (character.hasWeaponProficiency(weapon)) put(key, valueObject(true))
        }
    }

    /**
     * Книга заклинаний. У волшебника все заклинания книги идут в `book`, а в `prepared` —
     * подготовленные и заговоры. У других готовящих классов — подготовленные и заговоры,
     * у остальных — все известные.
     */
    private fun encodeSpellbook(character: Character, spells: List<LssSpellEntry>): JSONObject {
        val known = spells.distinctBy { it.spell.id }
        val cantrips = known.filter { it.spell.level == 0 }
        val preparedSpells = known.filter { it.spell.level > 0 && it.prepared }
        val isWizard = character.classLevels.any { it.characterClass == CharacterClass.WIZARD }
        val (prepared, book) = when {
            isWizard -> (cantrips + preparedSpells) to known.filter { it.spell.level > 0 }
            character.canPrepareSpells -> (cantrips + preparedSpells) to emptyList()
            else -> known to emptyList()
        }
        return JSONObject().apply {
            put("mode", "cards")
            put("prepared", JSONArray(prepared.map { LssSpellRefs.refOf(it.spell) }.distinct()))
            put("book", JSONArray(book.map { LssSpellRefs.refOf(it.spell) }.distinct()))
            put("granted", JSONArray())
            put("slotless", JSONArray())
        }
    }

    /** Скрытые блоки листа: пустые блоки заметок (так выглядит и новый лист LSS). */
    private fun encodeDisabledBlocks(usedNotes: Set<String>): JSONObject = JSONObject().apply {
        listOf("info-left", "info-right", "subinfo-left", "subinfo-right").forEach { put(it, JSONArray()) }
        put("notes-left", JSONArray(NOTE_SLOTS.take(3).filterNot { it in usedNotes }))
        put("notes-right", JSONArray(NOTE_SLOTS.drop(3).filterNot { it in usedNotes }))
    }

    private fun valueObject(value: Any): JSONObject = JSONObject().put("value", value)

    private fun namedValue(name: String, value: Any): JSONObject =
        JSONObject().put("name", name).put("value", value)

    // endregion

    // region Импорт

    /**
     * Разбирает лист персонажа формата LSS в нового персонажа.
     *
     * @throws IllegalArgumentException если это не лист персонажа.
     */
    fun decode(json: String): LssSheetImport {
        val root = JSONObject(json)
        val data = dataOf(root)
        require(isCharacterSheet(root, data)) { "Это не лист персонажа в формате LSS" }

        val characterId = UUID.randomUUID().toString()
        val info = data.optJSONObject("info")
        val vitality = data.optJSONObject("vitality")
        val sheetLevel = info?.optJSONObject("level").intValue(1).coerceIn(1, MAX_CLASS_LEVEL)
        val classes = decodeClasses(info?.optJSONObject("charClass"), sheetLevel)
        val level = if (classes.size > 1) {
            classes.sumOf { it.level }.coerceIn(1, MAX_CLASS_LEVEL)
        } else {
            sheetLevel
        }
        val text = data.optJSONObject("text")
        val profText = decodeProfText(text)
        val bonuses = decodeBonuses(data)

        val maxHp = vitality?.optJSONObject("hp-max").intValue(0) +
            vitality?.optJSONObject("hp-max-bonus").intValue(0) +
            bonuses.filter { it.target == "hp.max" && it.enabled }.sumOf { it.formula.toIntOrNull() ?: 0 }

        val base = Character(
            id = characterId,
            name = data.optJSONObject("name").stringValue().ifBlank { DEFAULT_NAME },
            imageUri = decodeAvatar(data),
            level = level,
            classes = classes.map { it.serialize() },
            canPrepareSpells = preparesSpells(classes),
            subclass = info?.optJSONObject("charSubclass").stringValue(),
            race = info?.optJSONObject("race").stringValue(),
            subrace = info?.optJSONObject("subrace").stringValue(),
            background = info?.optJSONObject("background").stringValue(),
            size = CreatureSize.fromCode(info?.optJSONObject("size").stringValue())?.code.orEmpty(),
            maxHp = maxHp,
            currentHp = vitality?.optJSONObject("hp-current").intValue(maxHp).coerceIn(0, maxHp.coerceAtLeast(0)),
            tempHp = vitality?.optJSONObject("hp-temp").intValue(0).coerceAtLeast(0),
            abilityScores = decodeStats(data),
            saveProficiencies = decodeSaves(data),
            skillProficiencies = decodeSkills(data),
            spellSlots = decodeSlots(data, used = false),
            spellSlotsUsed = decodeSlots(data, used = true),
            coins = decodeCoins(data),
            armorProficiencies = decodeArmor(data),
            weaponProficiencies = decodeWeapons(data, profText.otherWeapons),
            languages = profText.languages,
            toolProficiencies = profText.tools,
            otherWeaponProficiencies = profText.otherWeapons,
            maxAttunedItems = data.optJSONArray("attunementsList")?.length()?.coerceAtLeast(DEFAULT_ATTUNED_ITEMS)
                ?: DEFAULT_ATTUNED_ITEMS,
            resources = decodeResources(data),
        )
        // Максимум ресурса по формуле считается по уже собранному персонажу, а потраченное — из листа.
        val withResources = base.copy(
            resources = base.resources.map { resource ->
                val maximum = resource.maximumFormula.takeIf { it.isNotBlank() }
                    ?.let { StatFormula.evaluateValue(it, base) }
                    ?.coerceAtLeast(1)
                    ?: resource.maximum
                resource.copy(maximum = maximum).normalized()
            },
        )
        val character = withVitalityParts(withResources, vitality, bonuses).withRecalculatedResources()

        val notes = decodeNotes(characterId, text, profText.leftover)
        val items = decodeItems(characterId, text, data.optJSONArray("attunementsList"))
        return LssSheetImport(
            character = character,
            notes = notes,
            feats = parseFeats(LssRichText.lines(blockDoc(text, FEATS))),
            items = items,
            spells = decodeSpellRefs(root, character.canPrepareSpells),
            combos = decodeCombos(characterId, data.optJSONArray("weaponsList")),
        )
    }

    private const val DEFAULT_NAME = "Персонаж"
    private const val DEFAULT_ATTUNED_ITEMS = 3

    /** Быстрая проверка, что JSON похож на лист персонажа LSS. */
    fun looksLikeCharacterSheet(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        isCharacterSheet(root, dataOf(root))
    }.getOrDefault(false)

    /** Данные листа лежат строкой в `data`, реже — самим объектом или прямо в корне. */
    private fun dataOf(root: JSONObject): JSONObject =
        root.optString("data").takeIf { it.isNotBlank() && it.trimStart().startsWith("{") }?.let { JSONObject(it) }
            ?: root.optJSONObject("data")
            ?: root

    private fun isCharacterSheet(root: JSONObject, data: JSONObject): Boolean =
        root.optString("jsonType") == JSON_TYPE_CHARACTER ||
            data.optString("jsonType") == JSON_TYPE_CHARACTER ||
            (data.has("stats") && data.has("vitality"))

    /** Классы из строки «Паладин» или «Волшебник 5 / Колдун 2»; неизвестный класс — «свой класс». */
    private fun decodeClasses(node: JSONObject?, level: Int): List<CharacterClassLevel> {
        val raw = node.stringValue()
        val datasetId = node?.optString("id").orEmpty()
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return listOfNotNull(classById(datasetId)?.let { CharacterClassLevel(it, level) })
        }
        val parsed = parts.map { part ->
            val match = CLASS_LEVEL.matchEntire(part)
            val name = match?.groupValues?.get(1)?.trim().orEmpty().ifBlank { part }
            val classLevel = match?.groupValues?.get(2)?.toIntOrNull()
            val known = classByName(name) ?: classById(datasetId).takeIf { parts.size == 1 }
            CharacterClassLevel(
                characterClass = known ?: CharacterClass.OTHER,
                level = (classLevel ?: if (parts.size == 1) level else 1).coerceIn(1, MAX_CLASS_LEVEL),
                customName = if (known == null) name else "",
            )
        }
        return parsed
    }

    private val CLASS_LEVEL = Regex("^(.*?)\\s*(\\d+)?$")

    private fun classByName(name: String): CharacterClass? {
        val normalized = normalize(name)
        return CLASS_NAMES.entries.firstOrNull { (_, names) ->
            normalize(names.first) == normalized || normalize(names.second) == normalized
        }?.key
    }

    /** Класс по id датасета LSS (`paladin`). */
    private fun classById(id: String): CharacterClass? =
        id.takeIf { it.isNotBlank() }?.let { classByName(it) }

    private fun normalize(text: String): String = text.trim().lowercase().replace('ё', 'е')

    private fun decodeAvatar(data: JSONObject): String? {
        val avatar = data.optJSONObject("avatar") ?: return null
        return listOf("jpeg", "webp").firstNotNullOfOrNull { key ->
            avatar.optString(key).takeIf { it.startsWith("https://") }
        }
    }

    private fun decodeStats(data: JSONObject): Map<Int, Int> {
        val stats = data.optJSONObject("stats") ?: return emptyMap()
        return AbilityType.entries.mapNotNull { ability ->
            val node = stats.optJSONObject(ABILITY_KEYS[ability.ordinal]) ?: return@mapNotNull null
            val score = if (node.has("score")) node.optInt("score") else node.optInt("value")
            if (score > 0) ability.ordinal to score else null
        }.toMap()
    }

    private fun decodeSaves(data: JSONObject): Map<Int, Int> {
        val saves = data.optJSONObject("saves") ?: return emptyMap()
        return AbilityType.entries.mapNotNull { ability ->
            val multiplier = saves.optJSONObject(ABILITY_KEYS[ability.ordinal])?.profMultiplier() ?: 0
            if (multiplier > 0) ability.ordinal to multiplier.coerceAtMost(ProficiencyLevel.EXPERTISE.multiplier) else null
        }.toMap()
    }

    private fun decodeSkills(data: JSONObject): Map<Int, Int> {
        val skills = data.optJSONObject("skills") ?: return emptyMap()
        return SkillType.entries.mapNotNull { skill ->
            val multiplier = skills.optJSONObject(SKILL_KEYS[skill.ordinal])?.profMultiplier() ?: 0
            if (multiplier > 0) skill.ordinal to multiplier.coerceAtMost(ProficiencyLevel.EXPERTISE.multiplier) else null
        }.toMap()
    }

    /** Ячейки: обычные и колдуна складываются — в приложении они хранятся вместе. */
    private fun decodeSlots(data: JSONObject, used: Boolean): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        listOf("spells", "spellsPact").forEach { key ->
            val slots = data.optJSONObject(key) ?: return@forEach
            for (level in 1..MAX_SPELL_SLOT_LEVEL) {
                val node = slots.optJSONObject("slots-$level") ?: continue
                val total = node.optInt("value", 0).coerceAtLeast(0)
                val amount = if (used) node.optInt("filled", 0).coerceIn(0, total) else total
                if (amount > 0) result[level] = (result[level] ?: 0) + amount
            }
        }
        return result
    }

    private fun decodeCoins(data: JSONObject): Map<Int, Int> {
        val coins = data.optJSONObject("coins") ?: return emptyMap()
        return CoinType.entries.mapNotNull { coin ->
            val amount = coins.optJSONObject(coin.code).intValue(0)
            if (amount > 0) coin.ordinal to amount else null
        }.toMap()
    }

    private fun decodeArmor(data: JSONObject): List<String> {
        val prof = data.optJSONObject("prof") ?: return emptyList()
        return ARMOR_PROF_KEYS.filter { (_, key) -> prof.flag(key) }.keys.map { it.name }
    }

    private fun decodeWeapons(data: JSONObject, otherWeapons: String): List<String> {
        val prof = data.optJSONObject("prof")
        val weapons = WEAPON_PROF_KEYS.filter { (_, key) -> prof?.flag(key) == true }.keys.map { it.name }.toMutableList()
        // Описание прочих владений имеет смысл только вместе с вариантом «Другое».
        if (otherWeapons.isNotBlank() && WeaponProficiency.OTHER.name !in weapons) weapons += WeaponProficiency.OTHER.name
        return weapons
    }

    /** Блок владений: языки, инструменты, прочее оружие; нераспознанные строки уходят в заметку. */
    private class ProfText(
        val languages: String,
        val tools: String,
        val otherWeapons: String,
        val leftover: List<String>,
    )

    private fun decodeProfText(text: JSONObject?): ProfText {
        val languages = mutableListOf<String>()
        val tools = mutableListOf<String>()
        val weapons = mutableListOf<String>()
        val leftover = mutableListOf<String>()
        LssRichText.lines(blockDoc(text, PROF)).filterNot { it.isBreak || it.text.isBlank() }.forEach { line ->
            val content = line.text.trim()
            val label = content.substringBefore(':', "").trim().lowercase()
            val value = content.substringAfter(':', "").trim()
            when {
                value.isEmpty() -> leftover += content
                label.contains("язык") || label.contains("language") -> languages += value
                label.contains("инструмент") || label.contains("tool") -> tools += value
                label.contains("оруж") || label.contains("weapon") -> weapons += value
                else -> leftover += content
            }
        }
        return ProfText(
            languages = languages.joinToString(", "),
            tools = tools.joinToString(", "),
            otherWeapons = weapons.joinToString(", "),
            leftover = leftover,
        )
    }

    /** Числовой бонус листа, который приложение умеет перенести в составляющие. */
    private class SheetBonus(val target: String, val label: String, val formula: String, val enabled: Boolean)

    /**
     * Бонусы `data.bonuses`. Переносятся только прибавки (`mode: add`); состояния лист
     * строит сам, поэтому их бонусы пропускаются. Бонус выключенного эффекта переносится выключенным.
     */
    private fun decodeBonuses(data: JSONObject): List<SheetBonus> {
        val array = data.optJSONArray("bonuses") ?: return emptyList()
        val effects = data.optJSONArray("effects")?.let { effects ->
            (0 until effects.length()).mapNotNull { effects.optJSONObject(it) }
                .associate { it.optString("id") to !it.optBoolean("disabled", false) }
        }.orEmpty()
        return (0 until array.length()).mapNotNull { index ->
            val bonus = array.optJSONObject(index) ?: return@mapNotNull null
            val mode = bonus.optString("mode").ifBlank {
                if (bonus.optString("kind") == "override") "set" else "add"
            }
            if (mode != "add") return@mapNotNull null
            val source = bonus.optJSONObject("source")
            val kind = source?.optString("kind").orEmpty()
            if (kind == "condition") return@mapNotNull null
            val formula = bonus.nullableString("expr")?.let(::fromLssFormula)
                ?: bonus.opt("value")?.let { it as? Number }?.let(::formatNumber)
                ?: return@mapNotNull null
            val effectOn = kind != "effect" || source?.optString("refId")?.let { effects[it] } == true
            SheetBonus(
                target = bonus.optString("target"),
                label = bonus.optString("label").ifBlank { source?.optString("label").orEmpty() }.ifBlank { DEFAULT_BONUS_LABEL },
                formula = formula,
                enabled = effectOn && !bonus.optBoolean("disabled", false),
            )
        }
    }

    private const val DEFAULT_BONUS_LABEL = "Бонус"
    private const val SHEET_BASE_LABEL = "Из листа LSS"
    private const val SHIELD_LABEL = "Щит"
    private const val DEFAULT_SHIELD_FORMULA = "2"

    private fun formatNumber(number: Number): String {
        val value = number.toDouble()
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    /**
     * КД, скорость и инициатива. Простое число без бонусов остаётся числом, формула, щит
     * и бонусы листа становятся составляющими — тогда они пересчитываются в приложении.
     */
    private fun withVitalityParts(base: Character, vitality: JSONObject?, bonuses: List<SheetBonus>): Character {
        fun bonusParts(target: String) = bonuses.filter { it.target == target }.map {
            StatPart(name = it.label, formula = it.formula, kind = StatPartKind.BONUS, enabled = it.enabled)
        }

        // Класс доспеха.
        val acRaw = vitality?.optJSONObject("ac")?.opt("value")
        val acFormula = sheetFormula(acRaw, base)
        val shield = vitality?.optJSONObject("shield")
        val shieldOn = shield?.optBoolean("value", false) == true
        val acBonuses = bonusParts("ac")
        var character = base
        val acNumber = acFormula?.toIntOrNull()
        character = if (acNumber != null && !shieldOn && acBonuses.isEmpty()) {
            character.copy(armorClass = acNumber)
        } else if (acFormula != null || shieldOn || acBonuses.isNotEmpty()) {
            val parts = buildList {
                add(StatPart(name = SHEET_BASE_LABEL, formula = acFormula ?: UNARMORED_AC_FORMULA, kind = StatPartKind.BASE))
                if (shield != null && (shieldOn || shield.has("mod"))) {
                    val mod = shield.nullableString("mod")?.let(::fromLssFormula)
                        ?.takeIf { StatFormula.evaluateValue(it, base) != null } ?: DEFAULT_SHIELD_FORMULA
                    add(StatPart(name = SHIELD_LABEL, formula = mod, kind = StatPartKind.BONUS, enabled = shieldOn))
                }
                addAll(acBonuses)
            }
            character.copy(armorClassParts = parts)
        } else {
            character
        }

        // Скорость.
        val speedFormula = sheetFormula(vitality?.optJSONObject("speed")?.opt("value"), base)
        val speedBonuses = bonusParts("speed.walk")
        val speedNumber = speedFormula?.toIntOrNull()
        character = when {
            speedNumber != null && speedBonuses.isEmpty() -> character.copy(speed = speedNumber)
            speedFormula != null || speedBonuses.isNotEmpty() -> character.copy(
                speedParts = listOf(
                    StatPart(name = SHEET_BASE_LABEL, formula = speedFormula ?: DEFAULT_SPEED_FORMULA, kind = StatPartKind.BASE),
                ) + speedBonuses,
            )
            else -> character
        }

        // Инициатива: `null` в листе — модификатор Ловкости.
        val initiative = sheetFormula(vitality?.optJSONObject("initiative")?.opt("value"), base).orEmpty()
        val initiativeBonuses = bonusParts("initiative")
        character = if (initiativeBonuses.isEmpty()) {
            character.copy(initiativeFormula = initiative)
        } else {
            character.copy(
                initiativeParts = listOf(
                    StatPart(name = "", formula = initiative.ifBlank { DEFAULT_INITIATIVE_FORMULA }, kind = StatPartKind.BASE),
                ) + initiativeBonuses,
            )
        }
        return character
    }

    /** Значение формульного поля листа (число или строка) в нотации приложения; null — пусто или не разобрать. */
    private fun sheetFormula(raw: Any?, character: Character): String? {
        val text = when (raw) {
            null, JSONObject.NULL -> return null
            is Number -> return formatNumber(raw)
            else -> raw.toString().trim()
        }
        if (text.isEmpty()) return null
        val formula = fromLssFormula(text)
        return formula.takeIf { StatFormula.evaluateValue(it, character) != null }
    }

    /**
     * Ресурсы листа. Удалённые из текста (`isDeleted`, `orphanedAt`) пропускаются: в LSS
     * они уже не видны. Заметка ресурса становится его описанием. Максимум по формуле
     * досчитывается в [decode], когда персонаж собран, поэтому здесь ресурс не нормализуется.
     */
    private fun decodeResources(data: JSONObject): List<CharacterResource> {
        val resources = data.optJSONObject("resources") ?: return emptyList()
        return resources.keys().asSequence().mapNotNull { key ->
            val node = resources.optJSONObject(key) ?: return@mapNotNull null
            if (node.optBoolean("isDeleted", false) || node.nullableString("orphanedAt") != null) return@mapNotNull null
            val name = node.optString("name").trim().ifBlank { return@mapNotNull null }
            val expression = node.nullableString("maxExpr")?.trim().orEmpty()
            val fixedMax = expression.toIntOrNull()
            val maximum = fixedMax
                ?: node.optInt("max", 0).takeIf { it > 0 }
                ?: node.optInt("resolvedMax", 0).takeIf { it > 0 }
                ?: node.optInt("current", 1).coerceAtLeast(1)
            val formula = if (expression.isNotEmpty() && fixedMax == null) fromLssFormula(expression) else ""
            // Строку «Восстановление: …» пишет выгрузка ресурса с особым восстановлением:
            // по ней флаг «Другое» возвращается, а долгий отдых был лишь его заменой для LSS.
            val noteLines = node.nullableString("notes").orEmpty().lines()
            val otherLine = noteLines.lastOrNull { it.startsWith(RECOVERY_NOTE_PREFIX) }
            CharacterResource(
                id = node.optString("id").ifBlank { key },
                name = name,
                description = noteLines.filterNot { it === otherLine }.joinToString("\n").trim(),
                current = node.optInt("current", maximum),
                maximum = maximum,
                maximumFormula = formula,
                recovery = ResourceRecovery(
                    shortRest = node.optBoolean("isShortRest", false),
                    longRest = otherLine == null && node.optBoolean("isLongRest", false),
                    other = otherLine != null,
                    otherText = otherLine?.removePrefix(RECOVERY_NOTE_PREFIX)?.trim().orEmpty(),
                ),
            )
        }.toList()
    }

    private fun blockDoc(text: JSONObject?, key: String): JSONObject? =
        text?.optJSONObject(key)?.optJSONObject("value")?.optJSONObject("data")

    /**
     * Текстовые блоки — заметками. Блок, разделённый заголовками разделов (так приложение
     * пишет «Раса: …» и «Класс: …» в общий блок), снова распадается на отдельные заметки.
     */
    private fun decodeNotes(characterId: String, text: JSONObject?, profLeftover: List<String>): List<NoteBlock> {
        val result = mutableListOf<Pair<String, List<RichLine>>>()
        if (text != null) {
            val keys = text.keys().asSequence().toList()
            val ordered = BLOCK_ORDER.filter { it in keys } + keys.filter { it !in BLOCK_ORDER }.sorted()
            ordered.filterNot { it in SPECIAL_BLOCKS }.forEach { key ->
                val block = text.optJSONObject(key) ?: return@forEach
                val title = block.nullableString("customLabel")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: BLOCK_LABELS[key]
                    ?: key
                splitSections(LssRichText.lines(blockDoc(text, key)), title).forEach { result += it }
            }
        }
        if (profLeftover.isNotEmpty()) {
            result += PROF_NOTE_TITLE to profLeftover.map { RichLine(it) }
        }
        // Разделы с одним заголовком сливаются: умения класса с заклинаниями выгружаются
        // в «Атаки и заклинания» под тем же заголовком «Класс: …», что и остальные его умения.
        val merged = linkedMapOf<String, MutableList<RichLine>>()
        result.forEach { (title, lines) ->
            val target = merged.getOrPut(title) { mutableListOf() }
            if (target.isNotEmpty()) target += RichLine("", isBreak = true)
            target += lines
        }
        val now = System.currentTimeMillis()
        return merged.toList().mapIndexedNotNull { index, (title, lines) ->
            val paragraphs = LssRichText.toNoteParagraphs(lines)
            if (paragraphs.isEmpty()) return@mapIndexedNotNull null
            NoteBlock(
                characterId = characterId,
                title = title,
                collapsed = true,
                paragraphs = paragraphs,
                createdAt = now,
                sortOrder = index.toLong() - NOTE_ORDER_BASE,
            )
        }
    }

    private const val PROF_NOTE_TITLE = "Прочие владения"

    /** Делит строки по заголовкам разделов; текст до первого заголовка получает [defaultTitle]. */
    private fun splitSections(lines: List<RichLine>, defaultTitle: String): List<Pair<String, List<RichLine>>> {
        val sections = mutableListOf<Pair<String, MutableList<RichLine>>>()
        var current: MutableList<RichLine>? = null
        lines.forEach { line ->
            if (line.isSectionHeading) {
                current = mutableListOf<RichLine>().also { sections += line.text.trim() to it }
            } else {
                val target = current ?: mutableListOf<RichLine>().also {
                    sections += defaultTitle to it
                    current = it
                }
                target += line
            }
        }
        return sections.filter { (_, content) -> content.any { !it.isBreak && it.text.isNotBlank() } }
    }

    /**
     * Черты из блока «Черты». Черта — заголовок и текст до следующего заголовка. Заголовок —
     * строка, отделённая от текста: выделенная целиком или короткая отдельная строка без точки
     * в конце. Если заголовков нет, чертой считается каждый абзац с выделенным началом
     * («**Внимательный.** Вы…») или каждый абзац целиком.
     */
    internal fun parseFeats(lines: List<RichLine>): List<LssFeat> {
        if (lines.none { !it.isBreak && it.text.isNotBlank() }) return emptyList()
        val headings = lines.indices.filter { isFeatHeading(lines, it) }
        if (headings.isNotEmpty()) {
            val feats = mutableListOf<LssFeat>()
            // Текст до первого заголовка — отдельными абзацами по запасному правилу.
            feats += paragraphFeats(lines.subList(0, headings.first()))
            headings.forEachIndexed { order, start ->
                val end = headings.getOrNull(order + 1) ?: lines.size
                val name = cleanFeatName(lines[start].text)
                val description = LssRichText.toPlainText(lines.subList(start + 1, end))
                if (name.isNotBlank()) feats += LssFeat(name, description)
            }
            return feats
        }
        return paragraphFeats(lines)
    }

    private const val MAX_HEADING_LENGTH = 80
    private const val MAX_PLAIN_HEADING_LENGTH = 60
    private const val HEADING_END_MARKS = ".,;:!?…"

    private fun isFeatHeading(lines: List<RichLine>, index: Int): Boolean {
        val line = lines[index]
        val text = line.text.trim()
        if (line.isBreak || line.listItem || line.continuation || text.isEmpty()) return false
        val next = lines.drop(index + 1).firstOrNull { !it.isBreak && it.text.isNotBlank() }
        if (line.isFullyMarked && text.length <= MAX_HEADING_LENGTH) return true
        // Короткая отдельная строка в начале абзаца, после которой идёт текст.
        val startsParagraph = index == 0 || lines[index - 1].isBreak
        return startsParagraph && next != null && text.length <= MAX_PLAIN_HEADING_LENGTH &&
            text.last() !in HEADING_END_MARKS && next.text.trim().length > text.length
    }

    /** Абзацы как отдельные черты: название — выделенное начало или первая фраза. */
    private fun paragraphFeats(lines: List<RichLine>): List<LssFeat> {
        val groups = mutableListOf<MutableList<RichLine>>()
        lines.forEach { line ->
            if (line.isBreak) {
                if (groups.lastOrNull()?.isNotEmpty() != false) groups += mutableListOf<RichLine>()
            } else {
                if (groups.isEmpty()) groups += mutableListOf<RichLine>()
                groups.last() += line
            }
        }
        return groups.filter { group -> group.any { it.text.isNotBlank() } }.mapNotNull { group ->
            val first = group.first()
            val prefix = first.markedPrefix
            val (name, rest) = if (prefix.isNotBlank() && prefix.length <= MAX_HEADING_LENGTH && prefix.length < first.text.trim().length) {
                prefix to first.text.trim().removePrefix(prefix).trim()
            } else {
                val sentence = first.text.trim().split(Regex("[.:]"), limit = 2).first().trim()
                val title = if (sentence.length <= MAX_PLAIN_HEADING_LENGTH) sentence else sentence.take(MAX_PLAIN_HEADING_LENGTH).trim()
                title to first.text.trim()
            }
            val description = (listOf(rest) + group.drop(1).map { it.text }).filter { it.isNotBlank() }.joinToString("\n")
            cleanFeatName(name).takeIf { it.isNotBlank() }?.let { LssFeat(it, description) }
        }
    }

    /** Убирает точку или двоеточие в конце, а «НАПИСАННОЕ КАПСОМ» приводит к обычному виду. */
    private fun cleanFeatName(raw: String): String {
        val name = raw.trim().trimEnd('.', ':', ' ').trim()
        val letters = name.filter { it.isLetter() }
        return if (letters.length > 1 && letters.all { it.isUpperCase() }) {
            name.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            name
        }
    }

    /**
     * Снаряжение: каждый абзац, пункт списка или строка — отдельный предмет. «Название ×2 — описание»
     * разбирается на количество и описание; следующие абзацы пункта списка дописываются в описание.
     * Предметы из слотов настройки становятся магическими, требующими настройки.
     */
    private fun decodeItems(characterId: String, text: JSONObject?, attunements: JSONArray?): List<InventoryItem> {
        val drafts = mutableListOf<ItemDraft>()
        LssRichText.lines(blockDoc(text, EQUIPMENT)).forEach { line ->
            if (line.isBreak || line.text.isBlank()) return@forEach
            val previous = drafts.lastOrNull()
            if (line.continuation && previous != null) {
                previous.description += line.text.trim()
            } else {
                drafts += parseItemLine(line.text)
            }
        }
        (0 until (attunements?.length() ?: 0)).mapNotNull { attunements?.optJSONObject(it) }.forEach { slot ->
            val name = slot.optString("value").trim()
            if (name.isEmpty()) return@forEach
            val draft = drafts.firstOrNull { normalize(it.name) == normalize(name) }
                ?: ItemDraft(name).also { drafts += it }
            draft.attunement = slot.optBoolean("checked", false)
        }
        val now = System.currentTimeMillis()
        return drafts.mapIndexed { index, draft ->
            val attunement = draft.attunement
            InventoryItem(
                characterId = characterId,
                name = draft.name,
                quantity = draft.quantity,
                description = draft.description.joinToString("\n"),
                isMagic = attunement != null,
                requiresAttunement = attunement != null,
                attuned = attunement == true,
                createdAt = now,
                sortOrder = index.toLong() - NOTE_ORDER_BASE,
            ).normalized()
        }
    }

    private class ItemDraft(
        val name: String,
        val quantity: Int = 1,
        val description: MutableList<String> = mutableListOf(),
        /** null — предмет не из слотов настройки; иначе — настроен ли он. */
        var attunement: Boolean? = null,
    )

    private val LIST_PREFIX = Regex("^(•|\\d+\\.)\\s+")
    /** «Кинжал ×2», «Кинжал x2»; буква x без пробела перед ней — часть названия. */
    private val QUANTITY_SUFFIX = Regex("^(.+?)\\s*(?:×\\s*|\\s+[xхX]\\s*)(\\d+)$")
    private val QUANTITY_PARENTHESES = Regex("^(.+?)\\s*\\((\\d+)\\s*(?:шт\\.?)?\\)$")
    private val DESCRIPTION_SEPARATOR = Regex("\\s+[—–]\\s+")

    private fun parseItemLine(raw: String): ItemDraft {
        val line = raw.trim().replace(LIST_PREFIX, "")
        val parts = line.split(DESCRIPTION_SEPARATOR, limit = 2)
        val title = parts.first().trim()
        val match = QUANTITY_SUFFIX.matchEntire(title) ?: QUANTITY_PARENTHESES.matchEntire(title)
        val name = match?.groupValues?.get(1)?.trim() ?: title
        val quantity = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return ItemDraft(
            name = name.ifBlank { title },
            quantity = quantity,
            description = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { mutableListOf(it) } ?: mutableListOf(),
        )
    }

    /**
     * Ссылки на заклинания из книги заклинаний листа. У волшебника известны все заклинания книги,
     * подготовлены — из `prepared`; у остальных известно всё из `prepared`, а подготовленными
     * они считаются, если персонаж готовит заклинания. Выданные датасетом (`granted`) всегда подготовлены.
     */
    private fun decodeSpellRefs(root: JSONObject, canPrepare: Boolean): List<LssSpellRef> {
        val spellbook = root.optJSONObject("spells") ?: return emptyList()
        val prepared = spellbook.optJSONArray("prepared").strings()
        val book = spellbook.optJSONArray("book").strings()
        val granted = spellbook.optJSONArray("granted")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.optString("id") ?: array.optString(index)
            }
        }.orEmpty().filter { it.isNotBlank() }
        val refs = linkedMapOf<String, Boolean>()
        if (book.isNotEmpty()) {
            book.forEach { refs[it] = it in prepared }
            prepared.forEach { refs[it] = true }
        } else {
            prepared.forEach { refs[it] = canPrepare }
        }
        granted.forEach { refs[it] = true }
        return refs.map { (ref, isPrepared) -> LssSpellRef(ref, isPrepared) }
    }

    /** Оружие листа — комбинациями из одного шага: формула урона, тип урона и заметка. */
    private fun decodeCombos(characterId: String, weapons: JSONArray?): List<ComboWithSteps> {
        if (weapons == null) return emptyList()
        val now = System.currentTimeMillis()
        return (0 until weapons.length()).mapNotNull { weapons.optJSONObject(it) }.mapIndexedNotNull { index, weapon ->
            val name = weapon.optJSONObject("name").stringValue().trim()
            val damage = weapon.optJSONObject("dmg").stringValue().trim()
            if (name.isEmpty() && damage.isEmpty()) return@mapIndexedNotNull null
            val title = name.ifBlank { DEFAULT_WEAPON_NAME }
            val typeLabel = weapon.optJSONObject("dmgType").stringValue().trim()
            val typeCode = damageTypeCode(typeLabel)
            val notes = weapon.optJSONObject("notes").stringValue().trim()
            // Тип урона, которого нет в справочнике, сохраняется текстом в эффекте шага.
            val effect = listOfNotNull(
                typeLabel.takeIf { it.isNotEmpty() && typeCode.isEmpty() },
                notes.takeIf { it.isNotEmpty() },
            ).joinToString("; ")
            val combo = Combo(
                characterId = characterId,
                name = title,
                createdAt = now,
                sortOrder = index.toLong() - NOTE_ORDER_BASE,
            )
            val steps = if (damage.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ComboStep(
                        characterId = characterId,
                        name = title,
                        expression = fromLssFormula(damage),
                        damageType = typeCode,
                        effect = effect,
                        createdAt = now,
                    ),
                )
            }
            ComboWithSteps(combo, steps)
        }
    }

    private const val DEFAULT_WEAPON_NAME = "Оружие"

    /** Код типа урона по подписи листа («Рубящий», «slashing»); пусто — не распознан. */
    private fun damageTypeCode(label: String): String {
        val normalized = normalize(label)
        if (normalized.isEmpty()) return ""
        return SpellOptions.damageTypes.firstOrNull { option ->
            normalize(option.code) == normalized || normalize(option.canonical) == normalized
        }?.code.orEmpty()
    }

    // endregion
}

/** Число из обёртки `{"value": N}` (в том числе строкой `"30"`); при отсутствии — [fallback]. */
private fun JSONObject?.intValue(fallback: Int): Int {
    if (this == null || !has("value") || isNull("value")) return fallback
    return optInt("value", fallback)
}

/** Строка из обёртки `{"value": "…"}`; пусто, если значения нет. */
private fun JSONObject?.stringValue(): String {
    if (this == null || !has("value") || isNull("value")) return ""
    return optString("value")
}

/** Строковое поле, у которого `null` в JSON означает «нет значения». */
private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

/** Флаг `{"value": true}` в блоке владений. */
private fun JSONObject.flag(key: String): Boolean =
    optJSONObject(key)?.optBoolean("value", false) == true

/** Множитель владения: LSS пишет либо `true`/`false`, либо число 0/1/2 (2 — экспертиза). */
private fun JSONObject.profMultiplier(): Int {
    if (!has("isProf") || isNull("isProf")) return 0
    return when (val value = opt("isProf")) {
        is Boolean -> if (value) ProficiencyLevel.PROFICIENT.multiplier else 0
        is Number -> value.toInt()
        else -> optInt("isProf", 0)
    }
}
