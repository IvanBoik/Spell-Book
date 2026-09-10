package com.example.spellbook.data

import android.content.Context

/**
 * Небольшие пользовательские настройки в SharedPreferences.
 * Сейчас хранит id последнего открытого персонажа, чтобы при следующем запуске
 * сразу открыть его список заклинаний.
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

    private companion object {
        const val PREFS_NAME = "spellbook_prefs"
        const val KEY_LAST_CHARACTER = "last_character_id"
        const val KEY_BUNDLED_LIBRARY = "bundled_library_imported"
    }
}
