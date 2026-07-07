package com.example.spellbook.data

import com.example.spellbook.data.model.Components
import com.example.spellbook.data.model.DamagePart
import com.example.spellbook.data.model.Materials
import com.example.spellbook.data.model.Save
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.Target
import com.example.spellbook.util.HtmlUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Сериализация/десериализация заклинаний в формат LSS (FoundryVTT dnd5e).
 *
 * Опирается только на встроенный [org.json], чтобы не зависеть от внешних
 * библиотек сериализации. Поля, которые приложение не редактирует, заполняются
 * безопасными значениями по умолчанию, совместимыми с форматом сайта LSS.
 */
object SpellLssCodec {

    /** Внутренний ключ для хранения локального id; удаляется из чистого экспорта. */
    private const val LOCAL_ID_KEY = "_localId"
    private const val TYPE_SPELL = "spell"

    // region Сериализация

    /** Преобразует заклинание в LSS-объект. При [includeLocalId] добавляет служебный id. */
    fun toJson(spell: Spell, includeLocalId: Boolean = false): JSONObject {
        val descriptionHtml = HtmlUtils.plainToHtml(spell.description)

        val system = JSONObject().apply {
            put("description", JSONObject().apply {
                put("value", descriptionHtml)
                put("chat", "")
                put("unidentified", "")
            })
            put("source", spell.source)
            put("activation", JSONObject().apply {
                put("type", spell.activationType)
                put("cost", spell.activationCost ?: JSONObject.NULL)
                put("condition", spell.activationCondition)
            })
            put("duration", JSONObject().apply {
                put("value", spell.durationValue ?: JSONObject.NULL)
                put("units", spell.durationUnits)
            })
            put("cover", JSONObject.NULL)
            put("target", JSONObject().apply {
                put("value", spell.target.value ?: JSONObject.NULL)
                put("width", spell.target.width ?: JSONObject.NULL)
                put("units", spell.target.units)
                put("type", spell.target.type)
            })
            put("range", JSONObject().apply {
                put("value", spell.rangeValue ?: JSONObject.NULL)
                put("long", JSONObject.NULL)
                put("units", spell.rangeUnits)
            })
            put("uses", JSONObject().apply {
                put("value", 0)
                put("max", "0")
                put("per", "")
                put("recovery", "")
            })
            put("consume", JSONObject().apply {
                put("type", "")
                put("target", "")
                put("amount", JSONObject.NULL)
            })
            put("ability", spell.ability)
            put("actionType", spell.actionType)
            put("attackBonus", spell.attackBonus)
            put("chatFlavor", "")
            put("critical", JSONObject().apply {
                put("threshold", JSONObject.NULL)
                put("damage", JSONObject.NULL)
            })
            put("damage", JSONObject().apply {
                val parts = JSONArray()
                spell.damageParts.forEach { part ->
                    parts.put(JSONArray().apply {
                        put(part.formula)
                        put(part.type)
                    })
                }
                put("parts", parts)
                put("versatile", "")
            })
            put("formula", "")
            put("save", JSONObject().apply {
                put("ability", spell.save.ability)
                put("dc", spell.save.dc ?: JSONObject.NULL)
                put("scaling", spell.save.scaling)
            })
            put("level", spell.level)
            put("school", spell.school)
            put("components", JSONObject().apply {
                put("vocal", spell.components.vocal)
                put("somatic", spell.components.somatic)
                put("material", spell.components.material)
                put("ritual", spell.components.ritual)
                put("concentration", spell.components.concentration)
                put("value", spell.components.value)
            })
            put("materials", JSONObject().apply {
                put("value", spell.materials.value)
                put("consumed", spell.materials.consumed)
                put("cost", spell.materials.cost)
                put("supply", spell.materials.supply)
            })
            put("preparation", JSONObject().apply {
                put("mode", "prepared")
                put("prepared", false)
            })
            put("scaling", JSONObject().apply {
                put("mode", spell.scalingMode)
                put("formula", spell.scalingFormula)
            })
            put("details", JSONObject().apply {
                put("biography", JSONObject().apply {
                    put("value", descriptionHtml)
                    put("public", "")
                })
            })
        }

        return JSONObject().apply {
            put("name", spell.name)
            put("type", TYPE_SPELL)
            put("system", system)
            put("classes", JSONArray(spell.classes))
            if (includeLocalId) put(LOCAL_ID_KEY, spell.id)
        }
    }

    /** Сериализует одно заклинание в строку (для экспорта обмена). */
    fun encodeSpell(spell: Spell): String = toJson(spell, includeLocalId = false).toString(2)

    /** Сериализует список заклинаний в строку JSON-массива (для хранения). */
    fun encodeList(spells: List<Spell>): String {
        val array = JSONArray()
        spells.forEach { array.put(toJson(it, includeLocalId = true)) }
        return array.toString()
    }

    // endregion

    // region Десериализация

    /** Разбирает LSS-объект в доменную модель. Если id отсутствует — генерируется новый. */
    fun fromJson(json: JSONObject): Spell {
        val system = json.optJSONObject("system") ?: JSONObject()

        val activation = system.optJSONObject("activation") ?: JSONObject()
        val duration = system.optJSONObject("duration") ?: JSONObject()
        val range = system.optJSONObject("range") ?: JSONObject()
        val target = system.optJSONObject("target") ?: JSONObject()
        val components = system.optJSONObject("components") ?: JSONObject()
        val materials = system.optJSONObject("materials") ?: JSONObject()
        val save = system.optJSONObject("save") ?: JSONObject()
        val scaling = system.optJSONObject("scaling") ?: JSONObject()
        val damage = system.optJSONObject("damage") ?: JSONObject()

        val descriptionHtml = system.optJSONObject("description")?.optString("value").orEmpty()

        return Spell(
            id = json.optString(LOCAL_ID_KEY).ifEmpty { UUID.randomUUID().toString() },
            name = json.optString("name"),
            description = HtmlUtils.htmlToPlain(descriptionHtml),
            source = system.optString("source"),
            level = system.optInt("level", 0),
            school = system.optString("school"),
            activationType = activation.optString("type"),
            activationCost = activation.nullableInt("cost"),
            activationCondition = activation.optString("condition"),
            durationValue = duration.nullableInt("value"),
            durationUnits = duration.optString("units").ifEmpty { "inst" },
            rangeValue = range.nullableInt("value"),
            rangeUnits = range.optString("units").ifEmpty { "self" },
            target = Target(
                value = target.nullableInt("value"),
                width = target.nullableInt("width"),
                units = target.optString("units"),
                type = target.optString("type"),
            ),
            components = Components(
                vocal = components.optBoolean("vocal"),
                somatic = components.optBoolean("somatic"),
                material = components.optBoolean("material"),
                ritual = components.optBoolean("ritual"),
                concentration = components.optBoolean("concentration"),
                value = components.optString("value"),
            ),
            materials = Materials(
                value = materials.optString("value"),
                consumed = materials.optBoolean("consumed"),
                cost = materials.optInt("cost", 0),
                supply = materials.optInt("supply", 0),
            ),
            actionType = system.optString("actionType").ifEmpty { "util" },
            ability = system.optString("ability"),
            attackBonus = system.optInt("attackBonus", 0),
            save = Save(
                ability = save.optString("ability"),
                dc = save.nullableInt("dc"),
                scaling = save.optString("scaling").ifEmpty { "spell" },
            ),
            damageParts = parseDamageParts(damage.optJSONArray("parts")),
            scalingMode = scaling.optString("mode").ifEmpty { "none" },
            scalingFormula = scaling.optString("formula"),
            classes = parseStringArray(json.optJSONArray("classes")),
        )
    }

    /** Разбирает строку с одним заклинанием. */
    fun decodeSpell(text: String): Spell = fromJson(JSONObject(text))

    /** Разбирает строку с сохранённым списком заклинаний. */
    fun decodeList(text: String): List<Spell> {
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }

    private fun parseDamageParts(array: JSONArray?): List<DamagePart> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val pair = array.optJSONArray(i) ?: return@mapNotNull null
            DamagePart(
                formula = pair.optString(0),
                type = pair.optString(1),
            )
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }
    }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    // endregion
}
