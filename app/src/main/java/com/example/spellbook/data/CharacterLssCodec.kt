package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.ProficiencyLevel
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.abilityModifier
import org.json.JSONArray
import org.json.JSONObject

/**
 * Чтение и запись листа персонажа в формате LSS (Long Story Short).
 *
 * Особенность формата: содержимое листа лежит не самостоятельным объектом,
 * а строкой JSON в поле `data` внешней обёртки. Внутри значения хранятся
 * в виде `{"value": …}`, поэтому доступ к ним идёт через [valueOf].
 */
object CharacterLssCodec {

    private const val JSON_TYPE_CHARACTER = "character"
    private const val TEMPLATE = "default"
    private const val WRAPPER_VERSION = "2"
    private const val EDITION = "2014"
    private const val SHEET_EDITION = "2024"
    private const val MAX_SPELL_SLOT_LEVEL = 9
    private const val INDENT = 2

    /** Ключи характеристик в формате LSS в порядке [AbilityType]. */
    private val ABILITY_KEYS = listOf("str", "dex", "con", "int", "wis", "cha")

    /** Английские названия навыков LSS в порядке [SkillType]. */
    private val SKILL_KEYS = listOf(
        "acrobatics", "animal handling", "arcana", "athletics", "deception",
        "history", "insight", "intimidation", "investigation", "medicine",
        "nature", "perception", "performance", "persuasion", "religion",
        "sleight of hand", "stealth", "survival",
    )

    /** Флаги владений в блоке `prof`. */
    private val ARMOR_PROF_KEYS = mapOf(
        ArmorProficiency.LIGHT to "armor-light",
        ArmorProficiency.MEDIUM to "armor-medium",
        ArmorProficiency.HEAVY to "armor-heavy",
        ArmorProficiency.SHIELDS to "armor-shields",
    )
    private val WEAPON_PROF_KEYS = mapOf(
        WeaponProficiency.SIMPLE to "weapon-simple",
        WeaponProficiency.MARTIAL to "weapon-martial",
    )

    // region Экспорт

    /** Собирает лист персонажа в JSON формата LSS. */
    fun encode(character: Character): String {
        val data = JSONObject().apply {
            put("jsonType", JSON_TYPE_CHARACTER)
            put("template", TEMPLATE)
            put("name", valueObject(character.name))
            put("info", encodeInfo(character))
            put("subInfo", JSONObject())
            put("proficiency", character.proficiencyBonus)
            put("stats", encodeStats(character))
            put("saves", encodeSaves(character))
            put("skills", encodeSkills(character))
            put("vitality", encodeVitality(character))
            put("spells", encodeSpellSlots(character))
            put("coins", encodeCoins(character))
            put("prof", encodeProficiencies(character))
            put("text", encodeText(character))
            put("createdAt", java.time.Instant.ofEpochMilli(character.createdAt).toString())
        }
        return JSONObject().apply {
            put("jsonType", JSON_TYPE_CHARACTER)
            put("version", WRAPPER_VERSION)
            put("edition", EDITION)
            put("sheetEdition", SHEET_EDITION)
            put("data", data.toString())
        }.toString(INDENT)
    }

    private fun encodeInfo(character: Character): JSONObject = JSONObject().apply {
        put("level", namedValue("level", character.level))
        put("charClass", namedValue("charClass", ""))
        put("charSubclass", namedValue("charSubclass", ""))
        put("background", namedValue("background", ""))
        put("playerName", namedValue("playerName", ""))
        put("race", namedValue("race", ""))
        put("alignment", namedValue("alignment", ""))
        put("experience", namedValue("experience", ""))
    }

    private fun encodeStats(character: Character): JSONObject = JSONObject().apply {
        AbilityType.entries.forEach { ability ->
            val score = character.abilityScore(ability)
            put(
                ABILITY_KEYS[ability.ordinal],
                JSONObject().apply {
                    put("name", ABILITY_KEYS[ability.ordinal])
                    put("score", score)
                    put("modifier", abilityModifier(score))
                },
            )
        }
    }

    private fun encodeSaves(character: Character): JSONObject = JSONObject().apply {
        AbilityType.entries.forEach { ability ->
            val key = ABILITY_KEYS[ability.ordinal]
            put(
                key,
                JSONObject().apply {
                    put("name", key)
                    put("isProf", character.saveProficiency(ability) != ProficiencyLevel.NONE)
                },
            )
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
        put("hp-max", valueObject(character.maxHp))
        put("hp-current", valueObject(character.currentHp))
        put("hp-temp", valueObject(character.tempHp))
        put("ac", valueObject(character.armorClass))
        put("speed", valueObject(character.speed))
    }

    private fun encodeSpellSlots(character: Character): JSONObject = JSONObject().apply {
        for (level in 1..MAX_SPELL_SLOT_LEVEL) {
            val total = character.spellSlots[level] ?: 0
            if (total > 0) {
                put("slots-$level", valueObject(total))
                put("slots-$level-used", valueObject(character.spellSlotsUsed[level] ?: 0))
            }
        }
    }

    private fun encodeCoins(character: Character): JSONObject = JSONObject().apply {
        CoinType.entries.forEach { coin ->
            val amount = character.coins[coin.ordinal] ?: 0
            if (amount > 0) put(coin.code, valueObject(amount))
        }
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
     * Текстовые блоки листа. Владения инструментами, языками и прочим оружием
     * пишем в блок `prof` простым текстом — так их видно в редакторе LSS.
     */
    private fun encodeText(character: Character): JSONObject {
        val lines = buildList {
            if (character.languages.isNotBlank()) add("Языки: ${character.languages}")
            if (character.toolProficiencies.isNotBlank()) {
                add("Владение инструментами: ${character.toolProficiencies}")
            }
            if (character.otherWeaponProficiencies.isNotBlank()) {
                add("Другое оружие: ${character.otherWeaponProficiencies}")
            }
        }
        return JSONObject().apply {
            put("prof", JSONObject().put("value", richText(lines)))
        }
    }

    /** Формирует документ ProseMirror, в котором LSS хранит форматированный текст. */
    private fun richText(lines: List<String>): JSONObject {
        val paragraphs = JSONArray()
        lines.forEach { line ->
            paragraphs.put(
                JSONObject().apply {
                    put("type", "paragraph")
                    put("content", JSONArray().put(JSONObject().put("type", "text").put("text", line)))
                },
            )
        }
        if (paragraphs.length() == 0) paragraphs.put(JSONObject().put("type", "paragraph"))
        return JSONObject().put(
            "data",
            JSONObject().put("type", "doc").put("content", paragraphs),
        )
    }

    private fun valueObject(value: Any): JSONObject = JSONObject().put("value", value)

    private fun namedValue(name: String, value: Any): JSONObject =
        JSONObject().put("name", name).put("value", value)

    // endregion

    // region Импорт

    /**
     * Разбирает лист персонажа формата LSS.
     *
     * @param existing если задан, поля обновляются у существующего персонажа.
     * @throws IllegalArgumentException если это не лист персонажа.
     */
    fun decode(json: String, existing: Character? = null): Character {
        val root = JSONObject(json)
        // Данные могут лежать строкой в `data` либо самим объектом (укороченный вариант).
        val data = root.optString("data").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            ?: root.optJSONObject("data")
            ?: root
        require(isCharacterSheet(root, data)) { "Это не лист персонажа в формате LSS" }

        val base = existing ?: Character()
        val info = data.optJSONObject("info")
        val vitality = data.optJSONObject("vitality")
        val maxHp = vitality.valueInt("hp-max", base.maxHp)

        return base.copy(
            name = data.optJSONObject("name").valueString("value", base.name).ifBlank { base.name },
            level = info?.optJSONObject("level").valueInt("value", base.level).coerceIn(1, 20),
            maxHp = maxHp,
            currentHp = vitality.valueInt("hp-current", maxHp).coerceAtMost(maxHp),
            tempHp = vitality.valueInt("hp-temp", 0),
            armorClass = vitality.valueInt("ac", base.armorClass),
            speed = vitality.valueInt("speed", base.speed),
            abilityScores = decodeStats(data) ?: base.abilityScores,
            saveProficiencies = decodeSaves(data) ?: base.saveProficiencies,
            skillProficiencies = decodeSkills(data) ?: base.skillProficiencies,
            spellSlots = decodeSlots(data, used = false) ?: base.spellSlots,
            spellSlotsUsed = decodeSlots(data, used = true) ?: base.spellSlotsUsed,
            coins = decodeCoins(data) ?: base.coins,
            armorProficiencies = decodeArmor(data) ?: base.armorProficiencies,
            weaponProficiencies = decodeWeapons(data) ?: base.weaponProficiencies,
            languages = decodeTextField(data, LANGUAGES_LABELS) ?: base.languages,
            toolProficiencies = decodeTextField(data, TOOLS_LABELS) ?: base.toolProficiencies,
            otherWeaponProficiencies = decodeTextField(data, OTHER_WEAPON_LABELS)
                ?: base.otherWeaponProficiencies,
        )
    }

    /** Быстрая проверка, что JSON похож на лист персонажа LSS. */
    fun looksLikeCharacterSheet(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        val data = root.optString("data").takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            ?: root.optJSONObject("data")
            ?: root
        isCharacterSheet(root, data)
    }.getOrDefault(false)

    private fun isCharacterSheet(root: JSONObject, data: JSONObject): Boolean =
        root.optString("jsonType") == JSON_TYPE_CHARACTER ||
            data.optString("jsonType") == JSON_TYPE_CHARACTER ||
            (data.has("stats") && data.has("vitality"))

    private fun decodeStats(data: JSONObject): Map<Int, Int>? {
        val stats = data.optJSONObject("stats") ?: return null
        return AbilityType.entries.mapNotNull { ability ->
            val node = stats.optJSONObject(ABILITY_KEYS[ability.ordinal]) ?: return@mapNotNull null
            val score = if (node.has("score")) node.optInt("score") else node.optInt("value")
            if (score > 0) ability.ordinal to score else null
        }.toMap()
    }

    private fun decodeSaves(data: JSONObject): Map<Int, Int>? {
        val saves = data.optJSONObject("saves") ?: return null
        return AbilityType.entries.mapNotNull { ability ->
            val node = saves.optJSONObject(ABILITY_KEYS[ability.ordinal]) ?: return@mapNotNull null
            val multiplier = node.profMultiplier()
            if (multiplier > 0) ability.ordinal to ProficiencyLevel.PROFICIENT.multiplier else null
        }.toMap()
    }

    private fun decodeSkills(data: JSONObject): Map<Int, Int>? {
        val skills = data.optJSONObject("skills") ?: return null
        return SkillType.entries.mapNotNull { skill ->
            val node = skills.optJSONObject(SKILL_KEYS[skill.ordinal]) ?: return@mapNotNull null
            val multiplier = node.profMultiplier()
            if (multiplier > 0) skill.ordinal to multiplier.coerceAtMost(ProficiencyLevel.EXPERTISE.multiplier)
            else null
        }.toMap()
    }

    private fun decodeSlots(data: JSONObject, used: Boolean): Map<Int, Int>? {
        val spells = data.optJSONObject("spells") ?: return null
        val result = mutableMapOf<Int, Int>()
        for (level in 1..MAX_SPELL_SLOT_LEVEL) {
            val key = if (used) "slots-$level-used" else "slots-$level"
            val amount = spells.optJSONObject(key)?.optInt("value") ?: continue
            if (amount > 0) result[level] = amount
        }
        return result
    }

    private fun decodeCoins(data: JSONObject): Map<Int, Int>? {
        val coins = data.optJSONObject("coins") ?: return null
        return CoinType.entries.mapNotNull { coin ->
            val amount = coins.optJSONObject(coin.code)?.optInt("value") ?: return@mapNotNull null
            if (amount > 0) coin.ordinal to amount else null
        }.toMap()
    }

    private fun decodeArmor(data: JSONObject): List<String>? {
        val prof = data.optJSONObject("prof") ?: return null
        return ARMOR_PROF_KEYS.filter { (_, key) -> prof.flag(key) }.keys.map { it.name }
    }

    private fun decodeWeapons(data: JSONObject): List<String>? {
        val prof = data.optJSONObject("prof") ?: return null
        val weapons = WEAPON_PROF_KEYS.filter { (_, key) -> prof.flag(key) }.keys
            .map { it.name }
            .toMutableList()
        // Отдельный текст о прочих владениях означает выбор варианта «Другое».
        if (!decodeTextField(data, OTHER_WEAPON_LABELS).isNullOrBlank()) {
            weapons += WeaponProficiency.OTHER.name
        }
        return weapons
    }

    /**
     * Ищет в текстовых блоках строку вида «Языки: Общий, Драконий» и возвращает
     * часть после двоеточия. LSS хранит текст деревом ProseMirror, поэтому
     * собираем его в плоскую строку.
     */
    private fun decodeTextField(data: JSONObject, labels: List<String>): String? {
        val text = data.optJSONObject("text") ?: return null
        val plain = buildString {
            text.keys().forEach { key ->
                val node = text.optJSONObject(key)?.optJSONObject("value")?.optJSONObject("data")
                if (node != null) append(collectText(node)).append('\n')
            }
        }
        plain.lineSequence().forEach { line ->
            val label = labels.firstOrNull { line.trimStart().startsWith(it, ignoreCase = true) }
            if (label != null) {
                return line.substringAfter(':', "").trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** Рекурсивно собирает текст из дерева ProseMirror, сохраняя абзацы. */
    private fun collectText(node: JSONObject): String {
        val type = node.optString("type")
        val builder = StringBuilder()
        if (type == "text") builder.append(node.optString("text"))
        node.optJSONArray("content")?.let { content ->
            for (index in 0 until content.length()) {
                content.optJSONObject(index)?.let { builder.append(collectText(it)) }
            }
        }
        if (type == "paragraph") builder.append('\n')
        return builder.toString()
    }

    private val LANGUAGES_LABELS = listOf("Языки", "Languages")
    private val TOOLS_LABELS = listOf("Владение инструментами", "Инструменты", "Tools")
    private val OTHER_WEAPON_LABELS = listOf("Другое оружие", "Прочее оружие")

    // endregion
}

/** Значение `{"value": N}`; при отсутствии возвращает [fallback]. */
private fun JSONObject?.valueInt(key: String, fallback: Int): Int {
    val node = this?.optJSONObject(key) ?: return fallback
    return if (node.has("value")) node.optInt("value", fallback) else fallback
}

private fun JSONObject?.valueString(key: String, fallback: String): String {
    if (this == null) return fallback
    return optString(key).takeIf { it.isNotBlank() } ?: fallback
}

/** Флаг `{"value": true}` в блоке владений. */
private fun JSONObject.flag(key: String): Boolean =
    optJSONObject(key)?.optBoolean("value", false) == true

/**
 * Множитель владения: LSS пишет либо `true`, либо число (2 — экспертиза).
 */
private fun JSONObject.profMultiplier(): Int = when {
    !has("isProf") -> 0
    optBoolean("isProf", false) -> ProficiencyLevel.PROFICIENT.multiplier
    else -> optInt("isProf", 0)
}
