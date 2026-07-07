package com.example.spellbook.data.db

import androidx.room.TypeConverter
import com.example.spellbook.data.model.DamagePart
import org.json.JSONArray

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
}
