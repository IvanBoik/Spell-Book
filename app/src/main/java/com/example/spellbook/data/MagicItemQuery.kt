package com.example.spellbook.data

import com.example.spellbook.data.model.MagicItem

/**
 * Редкость магического предмета.
 *
 * На сайте редкость записана прилагательным и согласована с типом предмета:
 * «редкий» у посоха, «редкое» у оружия, «редкая» у палочки. Для фильтра эти формы —
 * одно и то же значение, поэтому они сводятся к перечислению через [fromText].
 */
enum class ItemRarity(
    /**
     * Начало строки редкости без родового окончания.
     *
     * Сравнение идёт по префиксу: так одно правило покрывает все три формы
     * и не ломается, если на сайте появится ещё одна.
     */
    val prefix: String,
) {
    COMMON("обычн"),
    UNCOMMON("необычн"),
    RARE("редк"),
    VERY_RARE("очень редк"),
    LEGENDARY("легендарн"),
    ARTIFACT("артефакт"),
    VARIES("редкость варьируется"),
    ;

    companion object {
        /**
         * Распознаёт редкость; null — если строка пустая или незнакомая.
         *
         * Порядок проверки важен: «очень редкий» и «необычный» содержат в себе
         * префиксы RARE и COMMON, поэтому длинные варианты проверяются первыми.
         */
        fun fromText(text: String): ItemRarity? {
            val normalized = text.trim().lowercase()
            if (normalized.isEmpty()) return null
            return entries
                .sortedByDescending { it.prefix.length }
                .firstOrNull { normalized.startsWith(it.prefix) }
        }
    }
}

/**
 * Фильтры библиотеки магических предметов.
 *
 * Условия внутри одного фильтра объединяются по ИЛИ, а сами фильтры — по И:
 * выбрав «редкий» и «очень редкий» вместе с настройкой, пользователь увидит
 * предметы любой из этих редкостей, требующие настройки.
 *
 * @param attunement null — фильтр не задан; true/false — только с настройкой или только без.
 */
data class MagicItemFilters(
    val rarities: Set<ItemRarity> = emptySet(),
    val attunement: Boolean? = null,
    val books: Set<String> = emptySet(),
) {
    /** Количество активных условий — для счётчика на кнопке фильтров. */
    val activeCount: Int
        get() = rarities.size + books.size + if (attunement != null) 1 else 0

    val isActive: Boolean get() = activeCount > 0
}

/** Книги-источники, встречающиеся в библиотеке, по алфавиту и без пустых значений. */
fun List<MagicItem>.availableItemBooks(): List<String> =
    mapNotNull { item -> item.book.trim().takeIf { it.isNotEmpty() } }
        .distinct()
        .sorted()

/**
 * Редкости, встречающиеся в библиотеке, в порядке возрастания.
 *
 * Порядок берётся из перечисления, а не из данных: редкости — шкала, и «обычный»
 * должен стоять перед «легендарным» независимо от того, сколько таких предметов.
 */
fun List<MagicItem>.availableRarities(): List<ItemRarity> {
    val present = mapNotNull { ItemRarity.fromText(it.rarity) }.toSet()
    return ItemRarity.entries.filter { it in present }
}

/**
 * Отбирает предметы по названию и фильтрам.
 *
 * Поиск и фильтры объединяются по И. Пустой запрос и незаданные фильтры
 * ничего не отсекают.
 */
fun List<MagicItem>.filterSearch(query: String, filters: MagicItemFilters): List<MagicItem> {
    val trimmed = query.trim()
    return filter { item ->
        val matchesQuery = trimmed.isEmpty() || item.name.contains(trimmed, ignoreCase = true)
        val matchesRarity = filters.rarities.isEmpty() ||
            ItemRarity.fromText(item.rarity) in filters.rarities
        val matchesAttunement = filters.attunement == null ||
            item.requiresAttunement == filters.attunement
        val matchesBook = filters.books.isEmpty() || item.book.trim() in filters.books
        matchesQuery && matchesRarity && matchesAttunement && matchesBook
    }
}
