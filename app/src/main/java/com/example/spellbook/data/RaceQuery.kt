package com.example.spellbook.data

import com.example.spellbook.data.model.Race

/**
 * Фильтры библиотеки рас.
 *
 * Фильтр один — по книге-источнику. Условия внутри объединяются по ИЛИ: выбрав
 * Player's Handbook и Volo's Guide, пользователь увидит расы из любой из этих книг.
 *
 * Книга хранится строкой, поэтому список доступных значений строится по самой
 * библиотеке (см. [availableBooks]), а не задаётся перечислением: набор источников
 * меняется вместе со встроенным файлом.
 */
data class RaceFilters(
    val books: Set<String> = emptySet(),
) {
    /** Количество активных условий — для счётчика на кнопке фильтров. */
    val activeCount: Int get() = books.size

    val isActive: Boolean get() = activeCount > 0
}

/** Книги-источники, встречающиеся в библиотеке, по алфавиту и без пустых значений. */
fun List<Race>.availableBooks(): List<String> =
    mapNotNull { race -> race.book.trim().takeIf { it.isNotEmpty() } }
        .distinct()
        .sorted()

/**
 * Отбирает расы по названию и фильтрам.
 *
 * Поиск и фильтр объединяются по И: сначала совпадение по названию, затем проверка
 * источника. Пустой запрос и пустой фильтр ничего не отсекают.
 */
fun List<Race>.filterSearch(query: String, filters: RaceFilters): List<Race> {
    val trimmed = query.trim()
    return filter { race ->
        val matchesQuery = trimmed.isEmpty() || race.name.contains(trimmed, ignoreCase = true)
        val matchesBook = filters.books.isEmpty() || race.book.trim() in filters.books
        matchesQuery && matchesBook
    }
}
