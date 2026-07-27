package com.example.spellbook.data.db

import androidx.room.TypeConverter
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.DamagePart
import org.json.JSONArray
import org.json.JSONObject

/**
 * Конвертеры Room для полей-списков модели заклинания. Сериализация выполняется
 * встроенным [org.json], чтобы не тянуть внешние библиотеки.
 */
class Converters {

    @TypeConverter
    fun stringListToJson(list: List<String>): String =
        JSONArray(list).toString()

    @TypeConverter
    fun jsonToStringList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { array.optString(it) }
    }

    @TypeConverter
    fun damagePartsToJson(parts: List<DamagePart>): String {
        val array = JSONArray()
        parts.forEach { part ->
            array.put(JSONArray().apply { put(part.formula); put(part.type) })
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToDamageParts(json: String): List<DamagePart> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val pair = array.optJSONArray(i) ?: return@mapNotNull null
            DamagePart(formula = pair.optString(0), type = pair.optString(1))
        }
    }

    /** Ячейки заклинаний по уровням: Map<уровень, количество> ↔ JSON-объект. */
    @TypeConverter
    fun intMapToJson(map: Map<Int, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        return obj.toString()
    }

    @TypeConverter
    fun jsonToIntMap(json: String): Map<Int, Int> {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, Int>()
        obj.keys().forEach { key ->
            val level = key.toIntOrNull() ?: return@forEach
            result[level] = obj.optInt(key)
        }
        return result
    }

    /** Пользовательские ресурсы персонажа ↔ JSON-массив. */
    @TypeConverter
    fun resourcesToJson(resources: List<CharacterResource>): String {
        val array = JSONArray()
        resources.forEach { resource ->
            array.put(JSONObject().apply {
                put("id", resource.id)
                put("name", resource.name)
                put("description", resource.description)
                put("current", resource.current)
                put("maximum", resource.maximum)
            })
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToResources(json: String): List<CharacterResource> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            val maximum = obj.optInt("maximum", 0)
            if (name.isBlank() || maximum <= 0) return@mapNotNull null
            CharacterResource(
                id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                name = name,
                description = obj.optString("description"),
                current = obj.optInt("current", maximum),
                maximum = maximum,
            ).normalized()
        }
    }
}
