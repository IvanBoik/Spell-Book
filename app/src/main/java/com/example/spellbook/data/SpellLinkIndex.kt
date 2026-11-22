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
 * Сам поиск — [BracketNameMatcher]; общий индекс заклинаний и предметов — [LibraryLinkIndex].
 * Класс чистый (без Android), индекс строится один раз на список заклинаний.
 */
class SpellLinkIndex(spells: List<Spell>) {

    private val matcher = BracketNameMatcher(spells.map { it.name to it })

    /** Пустой индекс: искать нечего. */
    val isEmpty: Boolean get() = matcher.isEmpty

    /** Ссылки на заклинания в тексте по порядку. */
    fun find(text: String): List<SpellLink> {
        if (isEmpty || !text.contains('[')) return emptyList()
        return BracketNameMatcher.brackets(text).mapNotNull { bracket ->
            matcher.match(text, bracket)?.let { SpellLink(it.range, it.value) }
        }.toList()
    }
}

/**
 * Сопоставление «русское название [english]» с записями библиотеки.
 *
 * Русское название стоит перед английским в квадратных скобках, но его начало в тексте
 * не отмечено («вы можете накладывать заклинание тьма [darkness]»). Поэтому перебираются
 * хвосты из нескольких слов перед скобкой, от самого длинного, и берётся первый, совпадающий
 * с названием из библиотеки с тем же числом слов. Падеж учитывается сравнением по основам
 * ([MarkupText.matchesSpellName]).
 *
 * @param entries название → запись.
 * @param englishKeys дополнительный поиск по английскому названию в скобках (ключ — [englishKey]):
 * у магических предметов это часть ссылки dnd.su. Срабатывает, если русский хвост не совпал
 * (в тексте предмет назван иначе, чем в библиотеке).
 */
internal class BracketNameMatcher<T>(
    entries: List<Pair<String, T>>,
    private val englishKeys: Map<String, Pair<String, T>> = emptyMap(),
) {

    /**
     * Совпадение у скобки.
     *
     * @param exact русский хвост равен названию буквально (без учёта падежа).
     * @param english английское название в скобках указывает именно на эту запись ([englishKeys]).
     */
    data class Match<T>(val range: IntRange, val value: T, val exact: Boolean, val english: Boolean = false)

    /**
     * Записи по числу слов в названии и началу последнего слова — сравниваются только хвосты
     * той же длины с тем же началом последнего слова. Это точно: [MarkupText.matchesSpellName]
     * требует либо совпадения слов, либо общей основы из 3+ букв. Без индекса каждое английское
     * название в тексте (таблицы предметов инфузий — десятки строк) сравнивалось со всеми записями,
     * и раскрытие длинных описаний подвисало.
     */
    private val byWordCount: Map<Int, Map<String, List<Pair<String, T>>>> = entries
        .filter { it.first.isNotBlank() }
        .map { normalize(it.first) to it.second }
        .groupBy { (name, _) -> name.split(' ').size }
        .mapValues { (_, list) -> list.groupBy { (name, _) -> stemKey(name.substringAfterLast(' ')) } }

    /** Самое длинное название в словах, но не больше [MAX_WORDS_LIMIT]. */
    private val maxWords: Int = (byWordCount.keys.maxOrNull() ?: 1).coerceIn(1, MAX_WORDS_LIMIT)

    val isEmpty: Boolean get() = byWordCount.isEmpty() && englishKeys.isEmpty()

    /** Запись, на которую указывает скобка [bracket] в тексте; null — совпадения нет. */
    fun match(text: String, bracket: MatchResult): Match<T>? {
        val words = wordsBefore(text, bracket.range.first)
        val byEnglish = if (englishKeys.isEmpty()) null else englishKeys[englishKey(bracket.value.trim('[', ']'))]
        for (count in minOf(words.size, maxWords) downTo 1) {
            val tail = words.takeLast(count)
            val candidate = tail.joinToString(" ") { normalize(it.text) }
            val (name, value) = byWordCount[count]?.get(stemKey(candidate.substringAfterLast(' ')))
                ?.firstOrNull { (name, _) -> MarkupText.matchesSpellName(candidate, name) }
                ?: continue
            return Match(
                range = tail.first().range.first..tail.last().range.last,
                value = value,
                exact = candidate == name,
                english = byEnglish?.second == value,
            )
        }
        val (name, value) = byEnglish ?: return null
        // Русский хвост не совпал: ссылкой становятся последние слова по числу слов в названии,
        // а если слов перед скобкой нет — само английское название.
        val count = minOf(words.size, name.split(' ').size)
        val range = if (count > 0) {
            val tail = words.takeLast(count)
            tail.first().range.first..tail.last().range.last
        } else {
            (bracket.range.first + 1) until bracket.range.last
        }
        return Match(range, value, exact = false, english = true)
    }

    /** Слово с позицией в тексте. */
    private data class Word(val text: String, val range: IntRange)

    /**
     * Слова непосредственно перед английским названием: только кириллица и `’'-/`,
     * разделители — пробелы и звёздочки разметки. На любом другом знаке (запятая, точка,
     * скобка) цепочка обрывается: название не пересекает пунктуацию.
     */
    private fun wordsBefore(text: String, bracketStart: Int): List<Word> {
        val words = mutableListOf<Word>()
        var index = bracketStart - 1
        while (index >= 0 && words.size < maxWords) {
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

    companion object {
        /** Английское название в квадратных скобках: `[hellish rebuke]`, `[Tenser’s floating disk]`. */
        private val ENGLISH = Regex("\\[[A-Za-z][A-Za-z’'\\-/ ]*]")

        /** Не английские буквы и цифры — для ключа английского названия. */
        private val NON_KEY_CHARS = Regex("[^a-z0-9]+")

        private val WHITESPACE = Regex("\\s+")

        /**
         * Предел длины названия в словах: у заклинаний самое длинное — 6 слов
         * («защита от зла и добра»), у предметов встречаются и длиннее.
         */
        const val MAX_WORDS_LIMIT = 8

        /** Ключ слова для индекса: первые 3 буквы (минимальная общая основа) или всё короткое слово. */
        private const val STEM_KEY = 3

        private fun stemKey(word: String): String = word.take(STEM_KEY)

        /** Английские названия в скобках по порядку. */
        fun brackets(text: String): Sequence<MatchResult> = ENGLISH.findAll(text)

        fun normalize(text: String): String =
            text.lowercase().replace('ё', 'е').replace('\u00A0', ' ').trim().replace(WHITESPACE, " ")

        /** Ключ английского названия: `Tenser’s floating disk` → `tensers_floating_disk`. */
        fun englishKey(english: String): String =
            english.lowercase().replace("’", "").replace("'", "").replace(NON_KEY_CHARS, "_").trim('_')
    }
}
