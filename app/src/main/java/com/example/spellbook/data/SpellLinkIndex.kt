package com.example.spellbook.data

import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.wizard.MarkupText

/**
 * Найденная в тексте ссылка на заклинание.
 *
 * @param range диапазон русского названия в тексте (без английского `[…]`) — его и делают ссылкой.
 */
data class SpellLink(val range: IntRange, val spell: Spell)

/**
 * Поиск упоминаний заклинаний в текстах библиотек dnd.su: «адское возмездие [hellish rebuke]»,
 * «*порча [bane]*», «палящую кару [searing smite]», «Тензеров парящий диск [Tenser’s floating disk]».
 *
 * Русское название стоит перед английским в квадратных скобках, но его начало в тексте
 * не отмечено («вы можете накладывать заклинание тьма [darkness]»). Поэтому перебираются
 * хвосты из 1–6 слов перед скобкой, от самого длинного, и берётся первый, совпадающий
 * с названием из библиотеки с тем же числом слов. Падеж учитывается сравнением по основам
 * ([MarkupText.matchesSpellName]). Без совпадения ссылки нет.
 *
 * Класс чистый (без Android), индекс строится один раз на список заклинаний.
 */
class SpellLinkIndex(spells: List<Spell>) {

    /** Заклинания по числу слов в названии — сравниваются только хвосты той же длины. */
    private val byWordCount: Map<Int, List<Pair<String, Spell>>> = spells
        .filter { it.name.isNotBlank() }
        .map { normalize(it.name) to it }
        .groupBy { (name, _) -> name.split(' ').size }

    /** Пустой индекс: искать нечего. */
    val isEmpty: Boolean get() = byWordCount.isEmpty()

    /** Ссылки на заклинания в тексте по порядку. */
    fun find(text: String): List<SpellLink> {
        if (isEmpty || !text.contains('[')) return emptyList()
        val result = mutableListOf<SpellLink>()
        ENGLISH.findAll(text).forEach { bracket ->
            val words = wordsBefore(text, bracket.range.first)
            for (count in minOf(words.size, MAX_WORDS) downTo 1) {
                val tail = words.takeLast(count)
                val candidate = tail.joinToString(" ") { normalize(it.text) }
                val spell = byWordCount[count]?.firstOrNull { (name, _) ->
                    MarkupText.matchesSpellName(candidate, name)
                }?.second ?: continue
                result += SpellLink(tail.first().range.first..tail.last().range.last, spell)
                break
            }
        }
        return result
    }

    /** Слово с позицией в тексте. */
    private data class Word(val text: String, val range: IntRange)

    /**
     * Слова непосредственно перед английским названием: только кириллица и `’'-/`,
     * разделители — пробелы и звёздочки разметки. На любом другом знаке (запятая, точка,
     * скобка) цепочка обрывается: название заклинания не пересекает пунктуацию.
     */
    private fun wordsBefore(text: String, bracketStart: Int): List<Word> {
        val words = mutableListOf<Word>()
        var index = bracketStart - 1
        while (index >= 0 && words.size < MAX_WORDS) {
            // Пропускаем пробелы и звёздочки между словами.
            while (index >= 0 && (text[index].isWhitespace() || text[index] == '*')) index--
            if (index < 0) break
            val end = index
            while (index >= 0 && isNameChar(text[index])) index--
            if (index == end) break
            val word = text.substring(index + 1, end + 1).trim('-', '/', '\'', '’')
            if (word.isEmpty() || word.none { it.isCyrillic() }) break
            val start = text.indexOf(word, index + 1)
            words.add(0, Word(word, start until start + word.length))
            // Слово обрывается не пробелом — это граница фразы (например, «(»).
            if (index >= 0 && !(text[index].isWhitespace() || text[index] == '*')) break
        }
        return words
    }

    private fun isNameChar(char: Char): Boolean =
        char.isCyrillic() || char == '-' || char == '/' || char == '\'' || char == '’'

    private fun Char.isCyrillic(): Boolean = this in 'а'..'я' || this in 'А'..'Я' || this == 'ё' || this == 'Ё'

    private companion object {
        /** Английское название в квадратных скобках: `[hellish rebuke]`, `[Tenser’s floating disk]`. */
        val ENGLISH = Regex("\\[[A-Za-z][A-Za-z’'\\-/ ]*]")

        /** Самое длинное название заклинания в словах («защита от зла и добра» — 5). */
        const val MAX_WORDS = 6

        fun normalize(text: String): String =
            text.lowercase().replace('ё', 'е').replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
    }
}
