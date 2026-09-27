package com.example.spellbook.data

import com.example.spellbook.data.model.Race
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация рас в JSON для встроенной библиотеки и обмена файлами.
 *
 * Формат совпадает по духу с библиотекой черт — у расы нет числовых характеристик,
 * только текст и книга-источник:
 * ```json
 * [{ "name": "Голиаф", "description": "…", "book": "…", "category": "…", "source": "…" }]
 * ```
 * Локальный `id` не экспортируется: у каждого пользователя он свой.
 */
object RaceLibraryCodec {

    private const val KEY_NAME = "name"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_BOOK = "book"
    private const val KEY_CATEGORY = "category"
    private const val KEY_SOURCE = "source"

    /** Одна раса в виде JSON-объекта. */
    fun encodeRace(race: Race): JSONObject = JSONObject().apply {
        put(KEY_NAME, race.name)
        put(KEY_DESCRIPTION, race.description)
        put(KEY_BOOK, race.book)
        put(KEY_CATEGORY, race.category)
        put(KEY_SOURCE, race.source)
    }

    /** Список рас в виде форматированного JSON-массива. */
    fun encodeAll(races: List<Race>): String {
        val array = JSONArray()
        races.forEach { array.put(encodeRace(it)) }
        return array.toString(2)
    }

    /** Одна раса в виде форматированного JSON — для выгрузки и отправки файлом. */
    fun encode(race: Race): String = encodeRace(race).toString(2)

    /**
     * Разбирает JSON-массив рас.
     *
     * Записи без названия пропускаются, а битый JSON даёт пустой список: встроенная
     * библиотека не должна ронять приложение при запуске.
     */
    fun decodeAll(json: String): List<Race> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decodeRace)
        }
    }.getOrDefault(emptyList())

    /** Разбирает одну расу; null — если нет названия. */
    fun decode(json: String): Race? = runCatching { decodeRace(JSONObject(json)) }.getOrNull()

    private fun decodeRace(obj: JSONObject): Race? {
        val name = obj.optString(KEY_NAME).trim()
        if (name.isEmpty()) return null
        return Race(
            name = name,
            description = obj.optString(KEY_DESCRIPTION).trim(),
            book = obj.optString(KEY_BOOK).trim(),
            category = obj.optString(KEY_CATEGORY).trim(),
            source = obj.optString(KEY_SOURCE).trim(),
        )
    }
}
