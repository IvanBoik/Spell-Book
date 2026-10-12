package com.example.spellbook.data

import com.example.spellbook.data.model.MagicItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация магических предметов в JSON для встроенной библиотеки и обмена файлами.
 *
 * Формат повторяет остальные библиотеки, добавляя поля карточки предмета:
 * ```json
 * [{
 *   "name": "Адамантиновый доспех",
 *   "description": "…",
 *   "book": "Dungeon Master's Guide",
 *   "category": "Доспех (средний или тяжёлый, кроме шкурного), необычный",
 *   "kind": "Доспех",
 *   "rarity": "необычный",
 *   "attunement": false,
 *   "price": "101-500 зм",
 *   "source": "https://dnd.su/items/1-adamantine_armor/"
 * }]
 * ```
 * Локальный `id` не экспортируется: у каждого пользователя он свой.
 */
object MagicItemLibraryCodec {

    private const val KEY_NAME = "name"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_BOOK = "book"
    private const val KEY_CATEGORY = "category"
    private const val KEY_KIND = "kind"
    private const val KEY_RARITY = "rarity"
    private const val KEY_ATTUNEMENT = "attunement"
    private const val KEY_PRICE = "price"
    private const val KEY_SOURCE = "source"

    /** Один предмет в виде JSON-объекта. */
    fun encodeItem(item: MagicItem): JSONObject = JSONObject().apply {
        put(KEY_NAME, item.name)
        put(KEY_DESCRIPTION, item.description)
        put(KEY_BOOK, item.book)
        put(KEY_CATEGORY, item.category)
        put(KEY_KIND, item.kind)
        put(KEY_RARITY, item.rarity)
        put(KEY_ATTUNEMENT, item.requiresAttunement)
        put(KEY_PRICE, item.price)
        put(KEY_SOURCE, item.source)
    }

    /** JSON одного предмета для выгрузки в файл или отправки. */
    fun encode(item: MagicItem): String = encodeItem(item).toString(2)

    /** JSON всей библиотеки. */
    fun encodeAll(items: List<MagicItem>): String =
        JSONArray().apply { items.forEach { put(encodeItem(it)) } }.toString(2)

    /**
     * Разбирает библиотеку предметов.
     *
     * Битый файл даёт пустой список, а не падение: встроенный набор читается
     * при старте приложения, и ошибка в нём не должна мешать запуску.
     */
    fun decodeAll(json: String): List<MagicItem> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::decodeItem)
        }
    }.getOrDefault(emptyList())

    /** Предмет из JSON-объекта; без названия запись бесполезна. */
    private fun decodeItem(json: JSONObject): MagicItem? {
        val name = json.optString(KEY_NAME).trim()
        if (name.isEmpty()) return null
        return MagicItem(
            name = name,
            description = json.optString(KEY_DESCRIPTION).trim(),
            book = json.optString(KEY_BOOK).trim(),
            category = json.optString(KEY_CATEGORY).trim(),
            kind = json.optString(KEY_KIND).trim(),
            rarity = json.optString(KEY_RARITY).trim(),
            requiresAttunement = json.optBoolean(KEY_ATTUNEMENT),
            price = json.optString(KEY_PRICE).trim(),
            source = json.optString(KEY_SOURCE).trim(),
        )
    }
}
