package com.example.spellbook.data

import com.example.spellbook.data.model.Feat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация черт в JSON для встроенной библиотеки и обмена файлами.
 *
 * Формат намеренно простой — у черты есть только название, описание, источник
 * и книга, поэтому сложная схема (как LSS у заклинаний) здесь не нужна:
 * ```json
 * [{ "name": "…", "description": "…", "source": "https://dnd.su/feats/…", "book": "Player's Handbook" }]
 * ```
 * Разметка описания — та же, что и у заклинаний (см. `HtmlUtils`), включая токены
 * `[[ref …]]`, поэтому текст черты выглядит одинаково независимо от способа загрузки.
 *
 * Как и остальной слой данных, опирается только на встроенный [org.json].
 */
object FeatLibraryCodec {

    private const val NAME_KEY = "name"
    private const val DESCRIPTION_KEY = "description"
    private const val SOURCE_KEY = "source"
    private const val BOOK_KEY = "book"

    /** Преобразует черту в JSON-объект. Локальный `id` не сохраняется: он у каждого свой. */
    fun toJson(feat: Feat): JSONObject = JSONObject().apply {
        put(NAME_KEY, feat.name)
        put(DESCRIPTION_KEY, feat.description)
        put(SOURCE_KEY, feat.source)
        put(BOOK_KEY, feat.book)
    }

    /** Собирает массив черт в JSON-текст. */
    fun encodeList(feats: List<Feat>): String {
        val array = JSONArray()
        feats.forEach { array.put(toJson(it)) }
        return array.toString()
    }

    /** Читает черту из JSON-объекта. */
    fun fromJson(json: JSONObject): Feat = Feat(
        name = json.optString(NAME_KEY).trim(),
        description = json.optString(DESCRIPTION_KEY).trim(),
        source = json.optString(SOURCE_KEY).trim(),
        book = json.optString(BOOK_KEY).trim(),
    )

    /**
     * Читает список черт из JSON-текста.
     *
     * Записи без названия пропускаются: такая черта бесполезна и только засоряет
     * библиотеку. Ошибка разбора отдельной записи не отменяет импорт остальных.
     */
    fun decodeList(json: String): List<Feat> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            fromJson(item).takeIf { it.name.isNotBlank() }
        }
    }
}
