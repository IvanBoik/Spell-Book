package com.example.spellbook.data

import com.example.spellbook.data.model.CharClass
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация классов в JSON для встроенной библиотеки и обмена файлами.
 *
 * Формат совпадает с библиотекой рас: у класса нет числовых характеристик,
 * только текст и книга-источник.
 * ```json
 * [{ "name": "Варвар", "description": "…", "book": "…", "source": "https://dnd.su/class/87-barbarian/" }]
 * ```
 * Локальный `id` не экспортируется: у каждого пользователя он свой.
 */
object ClassLibraryCodec {

    private const val KEY_NAME = "name"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_BOOK = "book"
    private const val KEY_SOURCE = "source"

    /** Один класс в виде JSON-объекта. */
    fun encodeClass(charClass: CharClass): JSONObject = JSONObject().apply {
        put(KEY_NAME, charClass.name)
        put(KEY_DESCRIPTION, charClass.description)
        put(KEY_BOOK, charClass.book)
        put(KEY_SOURCE, charClass.source)
    }

    /** JSON одного класса для выгрузки в файл или отправки. */
    fun encode(charClass: CharClass): String = encodeClass(charClass).toString(2)

    /** JSON всей библиотеки. */
    fun encodeAll(classes: List<CharClass>): String =
        JSONArray().apply { classes.forEach { put(encodeClass(it)) } }.toString(2)

    /**
     * Разбирает библиотеку классов.
     *
     * Битый файл даёт пустой список, а не падение: встроенный набор читается
     * при старте приложения, и ошибка в нём не должна мешать запуску.
     */
    fun decodeAll(json: String): List<CharClass> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decodeClass)
        }
    }.getOrDefault(emptyList())

    /** Класс из JSON-объекта; без названия запись бесполезна. */
    private fun decodeClass(json: JSONObject): CharClass? {
        val name = json.optString(KEY_NAME).trim()
        if (name.isEmpty()) return null
        return CharClass(
            name = name,
            description = json.optString(KEY_DESCRIPTION).trim(),
            book = json.optString(KEY_BOOK).trim(),
            source = json.optString(KEY_SOURCE).trim(),
        )
    }
}
