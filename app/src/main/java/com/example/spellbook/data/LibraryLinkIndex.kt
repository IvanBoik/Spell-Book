package com.example.spellbook.data

import com.example.spellbook.data.model.MagicItem
import com.example.spellbook.data.model.Spell

/** Ссылка в тексте на запись библиотеки: заклинание или магический предмет. */
sealed interface LibraryLink {
    /** Диапазон русского названия в тексте (без английского `[…]`) — его и делают ссылкой. */
    val range: IntRange

    data class ToSpell(override val range: IntRange, val spell: Spell) : LibraryLink
    data class ToItem(override val range: IntRange, val item: MagicItem) : LibraryLink
}

/**
 * Общий поиск ссылок на заклинания и магические предметы: «адское возмездие [hellish rebuke]»,
 * «Сумка хранения [bag of holding]».
 *
 * Предмет находится по русскому названию перед скобкой (с учётом падежа, как у заклинаний) или,
 * если в тексте он назван иначе, по английскому названию — оно совпадает с концом ссылки dnd.su
 * (`…/items/227-bag_of_holding/`).
 *
 * Если одна скобка подходит и заклинанию, и предмету, побеждает предмет, только когда на него
 * указывает английское название или его русское название совпало буквально, а у заклинания — нет;
 * в остальных случаях (неоднозначность) — заклинание.
 *
 * Класс чистый (без Android), индекс строится один раз на библиотеки.
 */
class LibraryLinkIndex(spells: List<Spell>, items: List<MagicItem>) {

    private val spellMatcher = BracketNameMatcher(spells.map { it.name to it })

    private val itemMatcher = BracketNameMatcher(
        entries = items.map { it.name to it },
        englishKeys = items.mapNotNull { item -> slugOf(item)?.let { it to (BracketNameMatcher.normalize(item.name) to item) } }
            .distinctBy { it.first }
            .toMap(),
    )

    val isEmpty: Boolean get() = spellMatcher.isEmpty && itemMatcher.isEmpty

    /** Ссылки в тексте по порядку. */
    fun find(text: String): List<LibraryLink> {
        if (isEmpty || !text.contains('[')) return emptyList()
        return BracketNameMatcher.brackets(text).mapNotNull { bracket ->
            val spell = spellMatcher.match(text, bracket)
            val item = itemMatcher.match(text, bracket)
            when {
                spell == null && item == null -> null
                item == null -> LibraryLink.ToSpell(spell!!.range, spell.value)
                spell == null -> LibraryLink.ToItem(item.range, item.value)
                prefersItem(spell, item) -> LibraryLink.ToItem(item.range, item.value)
                else -> LibraryLink.ToSpell(spell.range, spell.value)
            }
        }.toList()
    }

    /** Найдено и заклинание, и предмет: предмет — только при явном указании на него. */
    internal fun isCollision(text: String): Boolean = BracketNameMatcher.brackets(text).any { bracket ->
        spellMatcher.match(text, bracket) != null && itemMatcher.match(text, bracket) != null
    }

    private fun prefersItem(
        spell: BracketNameMatcher.Match<Spell>,
        item: BracketNameMatcher.Match<MagicItem>,
    ): Boolean = (item.english && item.exact) || (item.exact && !spell.exact) || (item.english && !spell.exact)

    companion object {
        /** Ключ предмета по ссылке dnd.su: `…/items/227-bag_of_holding/` → `bag_of_holding`. */
        fun slugOf(item: MagicItem): String? {
            val last = item.source.trimEnd('/').substringAfterLast('/', "")
            if (!last.contains('-')) return null
            return BracketNameMatcher.englishKey(last.substringAfter('-')).takeIf { it.isNotBlank() }
        }
    }
}
