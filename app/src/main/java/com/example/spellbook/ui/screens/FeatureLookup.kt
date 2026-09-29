package com.example.spellbook.ui.screens

import com.example.spellbook.util.HtmlUtils

/**
 * Место умения в описании класса: раздел и порядковый номер подзаголовка в нём.
 *
 * @param sectionIndex индекс раздела в списке [DescriptionSection].
 * @param subheadingIndex номер подзаголовка `### ` внутри раздела; null — переход
 * к началу раздела (например, «Умение пути» ведёт на блок подклассов целиком).
 */
data class FeatureTarget(val sectionIndex: Int, val subheadingIndex: Int?)

/**
 * Ищет описание умения по его названию из таблицы развития класса.
 *
 * Названия в таблице и заголовки в тексте почти никогда не совпадают дословно:
 * - в таблице есть уточнения: «Вдохновение барда (к6)», «Упорный (одно использование)»;
 * - в заголовке — хвосты: «Дополнительные заклинания барда» против «Дополнительные
 *   заклинания»; иногда перед названием стоит лишний дефис («-Формулы заговоров»);
 * - регистр разный: «ЯРОСТЬ» и «Ярость».
 *
 * Поэтому сравнение идёт по нормализованным названиям в три шага: точное совпадение,
 * затем заголовок, начинающийся с названия, затем название, начинающееся с заголовка.
 * Разделы «Классовые умения» и подклассы проверяются первыми: там описаны именно умения,
 * а одноимённые заголовки встречаются и в других местах — например, «Бонусное
 * владение» есть почти у каждого домена жреца.
 */
fun List<DescriptionSection>.findFeature(feature: String): FeatureTarget? {
    val target = normalizeFeatureName(feature)
    if (target.isEmpty()) return null

    // «Умение пути», «Умение коллегии бардов» — отсылка к подклассу: ведём на блок подклассов.
    if (target.startsWith(SUBCLASS_FEATURE_PREFIX)) {
        val firstSubclass = indexOfFirst { it.isCollapsible }
        return if (firstSubclass >= 0) FeatureTarget(firstSubclass, null) else null
    }

    val candidates = searchOrder().flatMap { sectionIndex ->
        this[sectionIndex].subheadings().mapIndexed { subIndex, title ->
            Triple(sectionIndex, subIndex, normalizeFeatureName(title))
        }
    }

    val matchers: List<(String) -> Boolean> = listOf(
        { title -> title == target },
        { title -> title.startsWith(target) },
        { title -> target.startsWith(title) && title.length >= MIN_PREFIX_LENGTH },
        // В ячейке умения разделены запятой, и название с запятой внутри
        // («Атака, наделённая ци») режется надвое — вторая половина тоже должна вести к нему.
        { title -> title.endsWith(", $target") && target.length >= MIN_PREFIX_LENGTH },
    )
    matchers.forEach { matches ->
        candidates.firstOrNull { (_, _, title) -> matches(title) }?.let { (section, sub, _) ->
            return FeatureTarget(section, sub)
        }
    }

    // Умение может быть и заголовком целого раздела — например, «Метамагия» у чародея.
    val byTitle = indexOfFirst { normalizeFeatureName(it.title) == target }
    if (byTitle >= 0) return FeatureTarget(byTitle, null)

    // То же название в другом падеже: «искусного исследователя» → «Искусный исследователь».
    findByStem(candidates, target)?.let { return it }

    // «Улучшение дикого облика», «Варианты боевых стилей» отдельного описания не имеют:
    // это усиление базового умения, и подробности написаны в его тексте.
    DERIVED_FEATURE_PREFIXES.firstNotNullOfOrNull { prefix ->
        target.removePrefix(prefix).takeIf { target.startsWith(prefix) }
    }?.let { base ->
        findByStem(candidates, base)?.let { return it }
        // «Улучшения ауры» усиливают все ауры — ведём к первой из них, «Ауре защиты».
        findByStem(candidates, base, prefixOnly = true)?.let { return it }
        // Базовое умение бывает заголовком целой части: «Варианты метамагии» → «Метамагия».
        val sectionTitles = indices.map { Triple(it, -1, normalizeFeatureName(this[it].title)) }
        findByStem(sectionTitles, base)?.let { return FeatureTarget(it.sectionIndex, null) }
        // Улучшение сразу нескольких умений — ведём к первому из них.
        base.substringBefore(AND_SEPARATOR).takeIf { it != base }?.let { first ->
            findByStem(candidates, first)?.let { return it }
        }
    }

    // Перечисление в одной ссылке: «искусного исследователя и исследователя природы».
    return target.substringBefore(AND_SEPARATOR).takeIf { it != target }?.let { findByStem(candidates, it) }
}

/**
 * Ищет заголовок с теми же словами в другом падеже.
 *
 * В таблице стоит «Улучшение дикого облика», а заголовок умения — «Дикий облик».
 * Слова считаются одинаковыми, если совпадают без последних [ENDING_LENGTH] букв более
 * короткого из них: этого хватает для русских падежей без полноценной морфологии
 * («дикого»/«дикий» → «дик», «избранного»/«избранный» → «избранн»).
 */
private fun findByStem(
    candidates: List<Triple<Int, Int, String>>,
    phrase: String,
    prefixOnly: Boolean = false,
): FeatureTarget? {
    val wanted = phrase.split(' ').filter { it.isNotEmpty() }
    if (wanted.isEmpty()) return null
    return candidates.firstOrNull { (_, _, title) ->
        val words = title.replace(",", "").split(' ').filter { it.isNotEmpty() }
        val sizeFits = if (prefixOnly) words.size > wanted.size else words.size == wanted.size
        sizeFits && words.zip(wanted).all { (a, b) -> sameWord(a, b) }
    }?.let { (section, sub, _) -> FeatureTarget(section, sub.takeIf { it >= 0 }) }
}

/** Совпадают ли слова с точностью до окончания. */
private fun sameWord(a: String, b: String): Boolean {
    val length = maxOf(MIN_STEM_LENGTH, minOf(a.length, b.length) - ENDING_LENGTH)
    return a.length >= length && b.length >= length && a.take(length) == b.take(length)
}

/** Разделитель умений, перечисленных в одной ссылке. */
private const val AND_SEPARATOR = " и "

/** Префиксы умений, которые усиливают другое умение и отдельного описания не имеют. */
private val DERIVED_FEATURE_PREFIXES = listOf("улучшения ", "улучшение ", "улучшенное ", "варианты ", "вариант ")

/** Минимальная длина общей основы: короче — слишком много случайных совпадений. */
private const val MIN_STEM_LENGTH = 3

/** Сколько букв короткого слова считаются окончанием. */
private const val ENDING_LENGTH = 2

/**
 * Порядок просмотра разделов: сначала «Классовые умения», затем остальные по порядку.
 * Раздел умений определяется по заголовку, а если его нет — просто идём сверху вниз.
 */
private fun List<DescriptionSection>.searchOrder(): List<Int> {
    val features = indexOfFirst { it.title.contains(CLASS_FEATURES_TITLE, ignoreCase = true) }
    val all = indices.toList()
    return if (features < 0) all else listOf(features) + all.filter { it != features }
}

/**
 * Подзаголовки раздела в порядке следования.
 *
 * Берутся именно те, что открывают куски [DescriptionSection.chunks]: так номер
 * подзаголовка напрямую соответствует элементу списка на экране.
 */
internal fun DescriptionSection.subheadings(): List<String> =
    chunks().drop(1).map { chunk ->
        HtmlUtils.subheadingText(chunk.lineSequence().first().trim())
    }

/**
 * Приводит название умения к виду, пригодному для сравнения: без уточнений в скобках,
 * без ведущих дефисов и кавычек, в нижнем регистре и с «е» вместо «ё».
 */
internal fun normalizeFeatureName(name: String): String =
    name.replace(PARENTHESES_REGEX, " ")
        .replace(MARKUP_REGEX, "")
        .replace('ё', 'е')
        .replace('Ё', 'Е')
        .trim()
        .trimStart('-', '–', '—', ' ')
        .replace(SPACES_REGEX, " ")
        .trim()
        .lowercase()

/** Название раздела с умениями основы класса. */
private const val CLASS_FEATURES_TITLE = "Классовые умения"

/** Отсылка к подклассу в таблице: «Умение пути», «Умение воинского архетипа». */
private const val SUBCLASS_FEATURE_PREFIX = "умение "

/**
 * Минимальная длина заголовка для сравнения по началу строки: иначе короткие заголовки
 * вроде «Хиты» совпадали бы с чем угодно.
 */
private const val MIN_PREFIX_LENGTH = 6

private val PARENTHESES_REGEX = Regex("\\([^)]*\\)")
private val MARKUP_REGEX = Regex("[*«»\"]")
private val SPACES_REGEX = Regex("\\s+")
