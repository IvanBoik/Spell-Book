package com.example.spellbook.data

import com.example.spellbook.data.model.Background
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация предысторий в JSON для встроенной библиотеки и обмена файлами.
 *
 * Формат совпадает с библиотекой классов: у предыстории нет числовых характеристик,
 * только текст и книга-источник.
 * ```json
 * [{ "name": "Артист", "description": "…", "book": "…", "category": "…", "source": "https://dnd.su/backgrounds/757-entertainer/" }]
 * ```
 * Локальный `id` не экспортируется: у каждого пользователя он свой.
 */
object BackgroundLibraryCodec {

    private const val KEY_NAME = "name"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_BOOK = "book"
    private const val KEY_CATEGORY = "category"
    private const val KEY_SOURCE = "source"

    /** Одна предыстория в виде JSON-объекта. */
    fun encodeBackground(background: Background): JSONObject = JSONObject().apply {
        put(KEY_NAME, background.name)
        put(KEY_DESCRIPTION, background.description)
        put(KEY_BOOK, background.book)
        put(KEY_CATEGORY, background.category)
        put(KEY_SOURCE, background.source)
    }

    /** JSON одной предыстории для выгрузки в файл или отправки. */
    fun encode(background: Background): String = encodeBackground(background).toString(2)

    /** JSON всей библиотеки. */
    fun encodeAll(backgrounds: List<Background>): String =
        JSONArray().apply { backgrounds.forEach { put(encodeBackground(it)) } }.toString(2)

    /**
     * Разбирает библиотеку предысторий.
     *
     * Битый файл даёт пустой список, а не падение: встроенный набор читается
     * при старте приложения, и ошибка в нём не должна мешать запуску.
     */
    fun decodeAll(json: String): List<Background> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decodeBackground)
        }
    }.getOrDefault(emptyList())

    /** Предыстория из JSON-объекта; без названия запись бесполезна. */
    private fun decodeBackground(json: JSONObject): Background? {
        val name = json.optString(KEY_NAME).trim()
        if (name.isEmpty()) return null
        return Background(
            name = name,
            description = json.optString(KEY_DESCRIPTION).trim(),
            book = json.optString(KEY_BOOK).trim(),
            category = json.optString(KEY_CATEGORY).trim(),
            source = json.optString(KEY_SOURCE).trim(),
        )
    }
}
