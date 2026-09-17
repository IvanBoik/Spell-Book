package com.example.spellbook.data

import java.util.Locale

/**
 * Язык интерфейса. Переводится только оболочка приложения: персонажи, заклинания,
 * черты и прочие пользовательские материалы остаются на языке оригинала.
 */
enum class AppLanguage(val code: String) {
    /** Язык берётся из системных настроек устройства. */
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en");

    /** Локаль для подмены ресурсов; null — использовать системную. */
    val locale: Locale?
        get() = if (code.isEmpty()) null else Locale.forLanguageTag(code)

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** Оформление приложения. */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    /** Нужна ли тёмная палитра при текущей системной настройке [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Настройка панели разделов персонажа: порядок и скрытые разделы.
 *
 * Разделы хранятся именами (см. `CharacterSection`), чтобы слой данных не зависел от UI.
 * Неизвестные и новые разделы, которых нет в [order], показываются в конце — так
 * обновление приложения не «теряет» добавленные разделы.
 */
data class SectionLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    /** Пустая настройка означает «всё по умолчанию». */
    val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty()

    companion object {
        val DEFAULT = SectionLayout()
    }
}
