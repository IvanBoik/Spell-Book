package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat

/**
 * Фильтры библиотеки черт.
 *
 * Пока фильтр один — по характеристикам, которые черта повышает. Внутри категории
 * условия объединяются по ИЛИ: выбрав Силу и Ловкость, пользователь увидит черты,
 * повышающие любую из них.
 */
data class FeatFilters(
    val abilities: Set<AbilityType> = emptySet(),
) {
    /** Количество активных условий — для счётчика на кнопке фильтров. */
    val activeCount: Int get() = abilities.size

    val isActive: Boolean get() = activeCount > 0
}

/**
 * Определяет по тексту черты, какие характеристики она повышает.
 *
 * Готового поля у черты нет: dnd.su отдаёт только сплошное описание, поэтому
 * разбираем формулировку вида «Увеличьте значение Силы на 1 при максимуме 20».
 * Разбор ограничен предложением с «Увеличьте», иначе в выборку попадали бы черты,
 * где характеристика упомянута лишь в условии применения.
 */
object FeatAbilities {

    /** Предложение о повышении характеристики. */
    private val INCREASE_SENTENCE_REGEX = Regex("Увеличьте[^.]*", RegexOption.IGNORE_CASE)

    /**
     * Характеристики в родительном падеже — именно в такой форме они стоят
     * в тексте: «значение Силы», «значение вашей Ловкости».
     */
    private val ABILITY_FORMS: Map<AbilityType, String> = mapOf(
        AbilityType.STRENGTH to "Сил",
        AbilityType.DEXTERITY to "Ловкост",
        AbilityType.CONSTITUTION to "Телосложени",
        AbilityType.INTELLIGENCE to "Интеллект",
        AbilityType.WISDOM to "Мудрост",
        AbilityType.CHARISMA to "Харизм",
    )

    /**
     * Формулировки «характеристика по вашему выбору» — такая черта повышает любую,
     * поэтому подходит под любой выбранный фильтр.
     */
    private val ANY_ABILITY_MARKERS = listOf("по вашему выбору", "выбранной характеристики")

    /**
     * Характеристики, которые повышает черта. Пустое множество — черта не повышает ничего.
     */
    fun increasedBy(feat: Feat): Set<AbilityType> {
        val sentences = INCREASE_SENTENCE_REGEX.findAll(feat.description).map { it.value }.toList()
        if (sentences.isEmpty()) return emptySet()

        val result = mutableSetOf<AbilityType>()
        sentences.forEach { sentence ->
            if (ANY_ABILITY_MARKERS.any { sentence.contains(it, ignoreCase = true) }) {
                return AbilityType.entries.toSet()
            }
            ABILITY_FORMS.forEach { (ability, form) ->
                if (sentence.contains(form, ignoreCase = true)) result += ability
            }
        }
        return result
    }
}

/** Отбирает черты по строке поиска и фильтрам. Поиск — по названию. */
fun List<Feat>.filterSearch(query: String, filters: FeatFilters): List<Feat> {
    val trimmed = query.trim()
    return filter { feat ->
        val matchesQuery = trimmed.isEmpty() || feat.name.contains(trimmed, ignoreCase = true)
        val matchesAbilities = filters.abilities.isEmpty() ||
            FeatAbilities.increasedBy(feat).any { it in filters.abilities }
        matchesQuery && matchesAbilities
    }
}
