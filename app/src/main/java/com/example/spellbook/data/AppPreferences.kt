package com.example.spellbook.data

import android.content.Context

/**
 * Небольшие пользовательские настройки в SharedPreferences: последний открытый персонаж,
 * флаг импорта встроенной библиотеки, язык/тема интерфейса и настройка панели разделов
 * (общая и персональная для каждого персонажа).
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastCharacterId: String?
        get() = prefs.getString(KEY_LAST_CHARACTER, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_LAST_CHARACTER) else putString(KEY_LAST_CHARACTER, value)
        }.apply()

    /**
     * Был ли уже импортирован встроенный набор заклинаний.
     * Импорт выполняется один раз, чтобы не возвращать удалённые пользователем заклинания
     * при каждом запуске.
     */
    var bundledLibraryImported: Boolean
        get() = prefs.getBoolean(KEY_BUNDLED_LIBRARY, false)
        set(value) = prefs.edit().putBoolean(KEY_BUNDLED_LIBRARY, value).apply()

    /** Язык интерфейса; по умолчанию — системный. */
    var language: AppLanguage
        get() = AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    /** Оформление приложения; по умолчанию — как в системе. */
    var theme: AppTheme
        get() = AppTheme.fromName(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Настройка панели разделов, применяемая ко всем персонажам без личной настройки. */
    var globalSectionLayout: SectionLayout
        get() = readLayout(KEY_GLOBAL_SECTIONS)
        set(value) = writeLayout(KEY_GLOBAL_SECTIONS, value)

    /**
     * Личная настройка панели разделов персонажа.
     * null означает «наследовать общую настройку» — так пользователь может вернуть
     * персонажа к общим правилам, не перечисляя разделы заново.
     */
    fun sectionLayoutFor(characterId: String): SectionLayout? {
        val key = characterKey(characterId)
        if (!prefs.contains(orderKey(key))) return null
        return readLayout(key)
    }

    fun setSectionLayoutFor(characterId: String, layout: SectionLayout?) {
        val key = characterKey(characterId)
        if (layout == null) {
            prefs.edit().remove(orderKey(key)).remove(hiddenKey(key)).apply()
        } else {
            writeLayout(key, layout)
        }
    }

    /** Все персональные настройки разделов: id персонажа -> его раскладка. */
    fun allCharacterSectionLayouts(): Map<String, SectionLayout> =
        prefs.all.keys
            .filter { it.startsWith(CHARACTER_SECTIONS_PREFIX) && it.endsWith(ORDER_SUFFIX) }
            .mapNotNull { orderKeyName ->
                val characterId = orderKeyName
                    .removePrefix(CHARACTER_SECTIONS_PREFIX)
                    .removeSuffix(ORDER_SUFFIX)
                sectionLayoutFor(characterId)?.let { characterId to it }
            }
            .toMap()

    private fun readLayout(key: String): SectionLayout = SectionLayout(
        order = prefs.getString(orderKey(key), null).toNameList(),
        hidden = prefs.getString(hiddenKey(key), null).toNameList().toSet(),
    )

    private fun writeLayout(key: String, layout: SectionLayout) {
        prefs.edit()
            .putString(orderKey(key), layout.order.joinToString(SEPARATOR))
            .putString(hiddenKey(key), layout.hidden.joinToString(SEPARATOR))
            .apply()
    }

    private fun String?.toNameList(): List<String> =
        this?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun characterKey(characterId: String) = CHARACTER_SECTIONS_PREFIX + characterId

    private fun orderKey(key: String) = key + ORDER_SUFFIX

    private fun hiddenKey(key: String) = key + HIDDEN_SUFFIX

    private companion object {
        const val PREFS_NAME = "spellbook_prefs"
        const val KEY_LAST_CHARACTER = "last_character_id"
        const val KEY_BUNDLED_LIBRARY = "bundled_library_imported"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_THEME = "app_theme"
        const val KEY_GLOBAL_SECTIONS = "sections_global"
        const val CHARACTER_SECTIONS_PREFIX = "sections_character_"
        const val ORDER_SUFFIX = "_order"
        const val HIDDEN_SUFFIX = "_hidden"
        const val SEPARATOR = ","
    }
}
