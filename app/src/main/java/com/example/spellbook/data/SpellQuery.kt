package com.example.spellbook.data

import com.example.spellbook.data.model.Spell

/**
 * Способ сортировки списка заклинаний.
 * [DATE_ADDED] — по дате добавления (используется по умолчанию, последние сверху).
 */
enum class SpellSort(val label: String) {
    DATE_ADDED("По дате добавления"),
    NAME("По названию"),
    LEVEL("По уровню");

    companion object {
        /** Пары (имя enum, отображаемое название) для выпадающего списка. */
        val options: List<Pair<String, String>> = entries.map { it.name to it.label }
    }
}

/** Компонент заклинания, по которому возможна фильтрация. */
enum class SpellComponent(val label: String) {
    VOCAL("Вербальный"),
    SOMATIC("Соматический"),
    MATERIAL("Материальный");

    /** Присутствует ли данный компонент у заклинания. */
    fun presentIn(spell: Spell): Boolean = when (this) {
        VOCAL -> spell.components.vocal
        SOMATIC -> spell.components.somatic
        MATERIAL -> spell.components.material
    }

    companion object {
        val options: List<Pair<SpellComponent, String>> = entries.map { it to it.label }
    }
}

/**
 * Набор активных фильтров. Внутри одной категории условия объединяются по ИЛИ
 * (например, вербальный ИЛИ соматический компонент), между категориями — по И.
 * Пустая категория означает «без ограничений».
 */
data class SpellFilters(
    val levels: Set<Int> = emptySet(),
    val classes: Set<String> = emptySet(),
    val schools: Set<String> = emptySet(),
    val components: Set<SpellComponent> = emptySet(),
    val activationTypes: Set<String> = emptySet(),
    val concentration: Boolean = false,
    val ritual: Boolean = false,
) {
    /** Количество активных условий — для отображения счётчика на кнопке фильтров. */
    val activeCount: Int
        get() = levels.size + classes.size + schools.size + components.size +
            activationTypes.size + (if (concentration) 1 else 0) + (if (ritual) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** Варианты времени накладывания для фильтра (коды совпадают с [Spell.activationType]). */
val castingTimeOptions: List<Pair<String, String>> = listOf(
    "action" to "Основное действие",
    "bonus" to "Бонусное действие",
    "reaction" to "Реакция",
    "minute" to "Минуты",
    "hour" to "Часы",
)

/**
 * Применяет поиск, фильтры и сортировку к списку заклинаний.
 * Исходный список уже отсортирован базой «по дате добавления» (новые — сверху),
 * поэтому для [SpellSort.DATE_ADDED] порядок сохраняется как есть.
 */
fun List<Spell>.filterSortSearch(
    query: String,
    filters: SpellFilters,
    sort: SpellSort,
): List<Spell> {
    val trimmedQuery = query.trim()
    val filtered = filter { spell ->
        (trimmedQuery.isEmpty() || spell.name.contains(trimmedQuery, ignoreCase = true)) &&
            (filters.levels.isEmpty() || spell.level in filters.levels) &&
            (filters.classes.isEmpty() || spell.classes.any { it in filters.classes }) &&
            (filters.schools.isEmpty() || spell.school in filters.schools) &&
            (filters.components.isEmpty() || filters.components.any { it.presentIn(spell) }) &&
            (filters.activationTypes.isEmpty() || spell.activationType in filters.activationTypes) &&
            (!filters.concentration || spell.components.concentration) &&
            (!filters.ritual || spell.components.ritual)
    }
    return when (sort) {
        SpellSort.DATE_ADDED -> filtered.sortedByDescending { it.createdAt }
        SpellSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        SpellSort.LEVEL -> filtered.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
    }
}
