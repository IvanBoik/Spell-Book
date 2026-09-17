package com.example.spellbook.data

import androidx.annotation.StringRes
import com.example.spellbook.R
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.SpellOrigin

/**
 * Способ сортировки списка заклинаний.
 * [LEVEL] — по уровню (используется по умолчанию).
 */
enum class SpellSort(@param:StringRes val labelRes: Int) {
    DATE_ADDED(R.string.sort_date_added),
    NAME(R.string.sort_name),
    LEVEL(R.string.sort_level),
}

/** Компонент заклинания, по которому возможна фильтрация. */
enum class SpellComponent(@param:StringRes val labelRes: Int) {
    VOCAL(R.string.component_vocal),
    SOMATIC(R.string.component_somatic),
    MATERIAL(R.string.component_material);

    /** Присутствует ли данный компонент у заклинания. */
    fun presentIn(spell: Spell): Boolean = when (this) {
        VOCAL -> spell.components.vocal
        SOMATIC -> spell.components.somatic
        MATERIAL -> spell.components.material
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
    /** Книги-источники: PHB, XGE, TCE и т. п. */
    val sources: Set<String> = emptySet(),
    /** Откуда запись появилась в библиотеке. */
    val origins: Set<SpellOrigin> = emptySet(),
    val concentration: Boolean = false,
    val ritual: Boolean = false,
) {
    /** Количество активных условий — для отображения счётчика на кнопке фильтров. */
    val activeCount: Int
        get() = levels.size + classes.size + schools.size + components.size +
            activationTypes.size + sources.size + origins.size +
            (if (concentration) 1 else 0) + (if (ritual) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** Подписи для фильтра по происхождению заклинания. */
val spellOriginOptions: List<Pair<SpellOrigin, Int>> = listOf(
    SpellOrigin.OFFICIAL to R.string.origin_official,
    SpellOrigin.IMPORTED to R.string.origin_imported,
    SpellOrigin.USER to R.string.origin_user,
)

/**
 * Собирает список книг-источников, встречающихся в библиотеке.
 * Список динамический: зависит от того, что у пользователя загружено.
 */
fun List<Spell>.availableSources(): List<Pair<String, String>> = asSequence()
    .map { it.source.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sorted()
    .map { it to it }
    .toList()

/** Варианты времени накладывания для фильтра (коды совпадают с [Spell.activationType]). */
val castingTimeOptions: List<Pair<String, Int>> = listOf(
    "action" to R.string.activation_action,
    "bonus" to R.string.activation_bonus,
    "reaction" to R.string.activation_reaction,
    "minute" to R.string.unit_minutes,
    "hour" to R.string.unit_hours,
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
            (filters.sources.isEmpty() || spell.source.trim() in filters.sources) &&
            (filters.origins.isEmpty() || spell.spellOrigin in filters.origins) &&
            (!filters.concentration || spell.components.concentration) &&
            (!filters.ritual || spell.components.ritual)
    }
    return when (sort) {
        SpellSort.DATE_ADDED -> filtered.sortedByDescending { it.createdAt }
        SpellSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        SpellSort.LEVEL -> filtered.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
    }
}
