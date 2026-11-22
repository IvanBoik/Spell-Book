package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.util.HtmlUtils

/**
 * Общие помощники разбора размеченного текста dnd.su для мастера создания персонажа.
 *
 * Разметка та же, что в библиотеках: `[[ref X]]`, `[[abbr a::b]]`, `***жирный курсив***`,
 * `**жирный**`, `*курсив*`, заголовки `#`/`##`/`###`, врезки `:::`, таблицы `| … |`.
 */
object MarkupText {

    private val REF = Regex("\\[\\[ref\\s+(.+?)]]")
    private val ABBR = Regex("\\[\\[abbr\\s+(.+?)::(.*?)]]")
    private val ROLL = Regex("\\[\\[/r\\s+(.+?)]]")
    private val EMPHASIS = Regex("\\*{1,3}")

    /** Строка особенности вида `***Название***. Текст` или `**Название**. Текст`. */
    private val TRAIT_LINE = Regex("^\\*{2,3}([^*]+?)\\*{2,3}\\s*[.:]?\\s*(.*)$")

    /** Убирает токены и выделение: остаётся читаемый текст. */
    fun plain(text: String): String = text
        .replace(REF) { it.groupValues[1] }
        .replace(ABBR) { it.groupValues[2].ifBlank { it.groupValues[1] } }
        .replace(ROLL) { it.groupValues[1] }
        .replace(EMPHASIS, "")
        .replace('\u00A0', ' ')
        .trim()

    /** Значения ссылок `[[ref X]]` по порядку (без завершающей пунктуации). */
    fun refs(text: String): List<String> =
        REF.findAll(text).map { it.groupValues[1].trim().trimEnd('.', ',', ';') }.toList()

    /** Разбирает строку особенности; null — строка не является особенностью. */
    fun traitLine(line: String): Pair<String, String>? {
        val match = TRAIT_LINE.matchEntire(line.trim()) ?: return null
        val title = match.groupValues[1].trim().trimEnd('.', ':')
        if (title.isEmpty()) return null
        return title to match.groupValues[2].trim()
    }

    /** Заголовок таблицы без токенов сокращений: `[[abbr из::Известные заговоры]]` → `Известные заговоры`. */
    fun headerText(cell: String): String = plain(cell)

    // region Числа

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "один" to 1, "одну" to 1, "одного" to 1, "одним" to 1, "одной" to 1, "одна" to 1, "одно" to 1,
        "одном" to 1,
        "два" to 2, "две" to 2, "двумя" to 2, "двух" to 2,
        "три" to 3, "трёх" to 3, "трех" to 3, "тремя" to 3,
        "четыре" to 4, "четырёх" to 4, "четырех" to 4, "четырьмя" to 4,
        "пять" to 5, "пяти" to 5, "шесть" to 6, "шести" to 6,
    )

    /** Число словом или цифрой: «два», «3»; null — не число. */
    fun number(word: String): Int? {
        val clean = word.lowercase().trim().trim('.', ',', '(', ')')
        return clean.toIntOrNull() ?: NUMBER_WORDS[clean]
    }

    /** Первое число (словом или цифрой) в тексте; null — чисел нет. */
    fun firstNumber(text: String): Int? =
        text.split(Regex("[\\s,;:]+")).firstNotNullOfOrNull { number(it) }

    // endregion

    // region Характеристики и навыки

    /** Основы названий характеристик во всех падежах. */
    private val ABILITY_STEMS: List<Pair<String, AbilityType>> = listOf(
        "сил" to AbilityType.STRENGTH,
        "ловкост" to AbilityType.DEXTERITY,
        "телосложени" to AbilityType.CONSTITUTION,
        "интеллект" to AbilityType.INTELLIGENCE,
        "мудрост" to AbilityType.WISDOM,
        "харизм" to AbilityType.CHARISMA,
    )

    /** Характеристика по слову в любом падеже («Ловкости», «Силу»); null — не характеристика. */
    fun ability(word: String): AbilityType? {
        val clean = word.lowercase().trim().trim('.', ',', '(', ')', '*')
        return ABILITY_STEMS.firstOrNull { (stem, _) ->
            // «сил» не должно совпадать с «силовой» и т. п. — допускаем только короткие окончания.
            clean.startsWith(stem) && clean.length - stem.length <= 3
        }?.second
    }

    /** Все характеристики, упомянутые в тексте, по порядку появления. */
    fun abilities(text: String): List<AbilityType> =
        text.split(Regex("[^а-яёА-ЯЁ]+")).mapNotNull { ability(it) }

    /** Русские названия навыков (как в ссылках dnd.su). */
    private val SKILL_NAMES: Map<String, SkillType> = mapOf(
        "акробатика" to SkillType.ACROBATICS,
        "уход за животными" to SkillType.ANIMAL_HANDLING,
        "магия" to SkillType.ARCANA,
        "атлетика" to SkillType.ATHLETICS,
        "обман" to SkillType.DECEPTION,
        "история" to SkillType.HISTORY,
        "проницательность" to SkillType.INSIGHT,
        "запугивание" to SkillType.INTIMIDATION,
        "расследование" to SkillType.INVESTIGATION,
        "медицина" to SkillType.MEDICINE,
        "природа" to SkillType.NATURE,
        "восприятие" to SkillType.PERCEPTION,
        "выступление" to SkillType.PERFORMANCE,
        "убеждение" to SkillType.PERSUASION,
        "религия" to SkillType.RELIGION,
        "ловкость рук" to SkillType.SLEIGHT_OF_HAND,
        "скрытность" to SkillType.STEALTH,
        "выживание" to SkillType.SURVIVAL,
    )

    /** Русское название навыка с заглавной буквы («Ловкость рук») — для подписей вариантов. */
    fun skillTitle(skill: SkillType): String =
        SKILL_NAMES.entries.firstOrNull { it.value == skill }?.key?.replaceFirstChar { it.uppercase() } ?: skill.name

    /** Навык по названию (без учёта регистра и пунктуации); null — не навык. */
    fun skill(name: String): SkillType? {
        val key = normalizeSkill(name)
        return SKILL_NAMES.entries.firstOrNull { normalizeSkill(it.key) == key }?.value
    }

    private fun normalizeSkill(name: String): String =
        plain(name).lowercase().replace('ё', 'е').trim().trim('.', ',', ';', ':')

    /** Навыки, упомянутые ссылками `[[ref X]]`. */
    fun skillRefs(text: String): List<SkillType> = refs(text).mapNotNull(::skill).distinct()

    /**
     * Навыки, упомянутые в тексте как обычные слова (без ссылок): «Восприятие», «Скрытность».
     * Используется как запасной вариант, если ссылок нет.
     */
    fun skillMentions(text: String): List<SkillType> {
        val lower = plain(text).lowercase().replace('ё', 'е')
        return SKILL_NAMES.entries
            .map { it.key.replace('ё', 'е') to it.value }
            .filter { (name, _) -> Regex("(^|[^а-я])${Regex.escape(name)}([^а-я]|$)").containsMatchIn(lower) }
            .map { it.second }
            .distinct()
    }

    // endregion

    // region Языки

    /** Основы названий языков → каноническое название. */
    private val LANGUAGE_STEMS: List<Pair<String, String>> = listOf(
        "общ" to "Общий",
        "эльфийск" to "Эльфийский",
        "дварфск" to "Дварфский",
        "великань" to "Великаний",
        "гномь" to "Гномий",
        "гоблинск" to "Гоблинский",
        "полурослич" to "Язык полуросликов",
        "полуросликов" to "Язык полуросликов",
        "орочь" to "Орочий",
        "бездн" to "Язык Бездны",
        "глубинн" to "Глубинная речь",
        "драконь" to "Драконий",
        "инферналь" to "Инфернальный",
        "небесн" to "Небесный",
        "первичн" to "Первичный",
        "сильван" to "Сильван",
        "подземн" to "Подземный",
        "акван" to "Акван",
        "ауран" to "Ауран",
        "игнан" to "Игнан",
        "терран" to "Терран",
        "друидическ" to "Друидический",
    )

    /** Языки, названные в тексте, в каноническом виде. */
    fun languages(text: String): List<String> {
        val words = plain(text).lowercase().split(Regex("[^а-яё]+"))
        return words.mapNotNull { word ->
            LANGUAGE_STEMS.firstOrNull { (stem, _) -> word.startsWith(stem) }?.second
        }.distinct()
    }

    /** Слова, которые не являются названием языка в перечислении. */
    private val NOT_LANGUAGE = Regex("одн|два|двух|друг|ещё|еще|выбор|люб|дополнительн|котор|должен|следующ")

    /**
     * Языки, из которых правило предлагает выбрать; пусто — выбор из любых.
     *
     * Распознаёт «Выберите один из Великаньего, Эльфийского или языка Краулов»,
     * «Один на выбор (Эльфийский, Гномий или Сильван)» и «на Эльфийском или Ведалкенском
     * по вашему выбору». Рекомендации в скобках («рекомендуется…», «обычно это…») и правила
     * «один из которых должен быть…» выбор не ограничивают.
     */
    fun languageAlternatives(text: String): List<String> {
        val lower = plain(text).lowercase()
        if (lower.contains("один из которых")) return emptyList()
        val withoutHints = lower.replace(Regex("\\((?:[^)]*(?:рекоменд|обычно|см\\.)[^)]*)\\)"), " ")
        val segment = Regex("\\(([^)]+)\\)").find(withoutHints)?.groupValues?.get(1)
            ?: Regex("\\sиз\\s+(.+)").find(withoutHints)?.groupValues?.get(1)
            ?: Regex("([а-яё]+(?:\\s+язык\\S*)?\\s+или\\s+(?:на\\s+)?[а-яё]+)").find(withoutHints)?.groupValues?.get(1)
            ?: return emptyList()
        val names = segment.split(Regex(",\\s*|\\s+или\\s+|\\s+и\\s+"))
            .map { part ->
                part.trim().trimEnd('.', ';')
                    .removePrefix("на ").removePrefix("языка ").removePrefix("язык ").removePrefix("языке ")
                    .replace(Regex("\\s+(?:язык\\S*|по вашему выбору|на ваш выбор)\\s*$"), "")
                    .trim()
            }
            .filter { it.isNotBlank() && it.split(' ').size <= 2 && !NOT_LANGUAGE.containsMatchIn(it) }
            // Языки сеттингов (Ведалкенский, язык Краулов) остаются как в тексте.
            .map { part -> languages(part).firstOrNull() ?: part.replaceFirstChar { it.uppercase() } }
            .distinct()
        return names.takeIf { it.size >= 2 }.orEmpty()
    }

    /**
     * Сколько языков «на выбор» даёт текст: «ещё одном языке на ваш выбор» → 1,
     * «двух дополнительных языках» → 2. 0 — выбора нет.
     */
    fun languageChoiceCount(text: String): Int {
        val lower = plain(text).lowercase()
        if (!lower.contains("язык")) return 0
        val hasChoice = lower.contains("на ваш выбор") || lower.contains("по вашему выбору") ||
            lower.contains("выберите") || lower.contains("дополнительн") || lower.contains("на выбор")
        if (!hasChoice) return 0
        val match = Regex("(\\S+)\\s+(?:дополнительн\\S*\\s+)?(?:язык|языка|языках|языке|языками)").find(lower)
        return match?.let { number(it.groupValues[1]) }
            ?: Regex("(?:ещё|еще)\\s+(\\S+)").find(lower)?.let { number(it.groupValues[1]) }
            ?: 1
    }

    // endregion

    // region Заклинания

    /** Классы по родительному падежу в фразе «из списка заклинаний волшебника». */
    private val CLASS_GENITIVE: List<Pair<String, String>> = listOf(
        "волшебник" to "wizard",
        "друид" to "druid",
        "жрец" to "cleric",
        "бард" to "bard",
        "чароде" to "sorcerer",
        "колдун" to "warlock",
        "паладин" to "paladin",
        "следопыт" to "ranger",
        "изобретател" to "artificer",
    )

    /** Коды классов, списки которых упомянуты в тексте («заклинаний волшебника» → `wizard`). */
    fun spellListCodes(text: String): Set<String> {
        val lower = plain(text).lowercase()
        // «из списка заклинаний барда, волшебника, друида или чародея» — все классы перечисления, а не первый.
        val lists = Regex("спис\\S*\\s+заклинаний\\s+([а-яё]+(?:(?:\\s*,\\s*|\\s+или\\s+|\\s+и\\s+)[а-яё]+)*)")
            .findAll(lower)
            .map { it.groupValues[1] }
        return lists.flatMap { list ->
            list.split(Regex("\\s*,\\s*|\\s+или\\s+|\\s+и\\s+"))
                .map { word -> CLASS_GENITIVE.firstOrNull { word.startsWith(it.first) }?.second }
                // Перечисление заканчивается на первом слове, которое не является классом.
                .takeWhile { it != null }
                .filterNotNull()
        }.toSet()
    }

    /**
     * Все классы-заклинатели, названные в тексте в любом падеже: «Выберите класс: бард,
     * волшебник, друид…», «из списка заклинаний барда, волшебника…».
     */
    fun classMentions(lower: String): Set<String> =
        lower.split(Regex("[^а-яё]+"))
            .mapNotNull { word ->
                CLASS_GENITIVE.firstOrNull { word.startsWith(it.first) && word.length - it.first.length <= 3 }?.second
            }
            .toSet()

    /** Класс сразу после «заговор»/«заклинание»: «один заговор друида на ваш выбор» → `druid`. */
    fun classAfterSpellWord(lower: String): Set<String> =
        Regex("(?:заговор|заклинани)\\S*\\s+([а-яё]+)").findAll(lower)
            .mapNotNull { match -> CLASS_GENITIVE.firstOrNull { match.groupValues[1].startsWith(it.first) }?.second }
            .toSet()

    /**
     * Фрагменты перед английскими названиями заклинаний: `огонь фей [faerie fire]` → `огонь фей`.
     *
     * Русское название нельзя надёжно отделить от предшествующих слов, поэтому возвращается
     * хвост из нескольких слов; сопоставление с библиотекой — по совпадению окончания
     * (см. [matchesSpellName]).
     */
    fun spellNameCandidates(text: String): List<String> {
        val clean = text.replace(REF) { it.groupValues[1] }.replace(EMPHASIS, "")
        return Regex("([А-Яа-яЁё’'\\-\\s]{2,80}?)\\s*\\[([A-Za-z’'\\-\\s]+)]").findAll(clean)
            .map { match ->
                val words = match.groupValues[1].trim().split(Regex("\\s+")).takeLast(MAX_SPELL_NAME_WORDS)
                // Всё до последнего служебного слова («изучаете заклинание», запятая) — не название.
                // Союзы внутри не режутся: «защита от зла и добра» — одно название, снимается только ведущий «и».
                val cut = words.indexOfLast { it.lowercase().trimEnd(',', '.', ':') in NAME_STOP_WORDS || it.endsWith(',') }
                words.drop(cut + 1)
                    .dropWhile { it.lowercase() in LEADING_CONJUNCTIONS }
                    .joinToString(" ")
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    /** Слова, после которых начинается название заклинания: «заклинание тишина», «и тьму». */
    private val NAME_STOP_WORDS = setOf(
        "заклинание", "заклинания", "заклинаний", "заклинанию", "заклинанием",
        "заговор", "заговоры", "заговора", "заговоров",
        "наложить", "накладывать", "сотворить", "также",
    )

    private val LEADING_CONJUNCTIONS = setOf("и", "или", "а", "либо")

    /** Совпадает ли название заклинания из библиотеки с фрагментом из [spellNameCandidates]. */
    fun matchesSpellName(candidate: String, spellName: String): Boolean {
        val a = candidate.lowercase().replace('ё', 'е').trim()
        val b = spellName.lowercase().replace('ё', 'е').trim()
        if (b.isEmpty()) return false
        if (a == b || a.endsWith(" $b")) return true
        // Название в тексте бывает в другом падеже: «наложить тишину» — «Тишина», «тьму» — «Тьма».
        // Сравниваем последние слова по основе: совпадает всё, кроме окончания из 1–2 букв.
        val words = b.split(WHITESPACE)
        val tail = a.split(WHITESPACE).takeLast(words.size)
        if (tail.size != words.size) return false
        return words.zip(tail).all { (expected, actual) -> sameStem(expected, actual) }
    }

    /** Слова с одной основой: «тишина»/«тишину», «бесследное»/«бесследного». */
    private fun sameStem(a: String, b: String): Boolean {
        if (a == b) return true
        val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
        val longest = maxOf(a.length, b.length)
        return common >= MIN_STEM && longest - common <= MAX_ENDING
    }

    /** Скомпилирован один раз: сравнение названий вызывается сотни раз на каждый текст. */
    private val WHITESPACE = Regex("\\s+")

    private const val MIN_STEM = 3
    private const val MAX_ENDING = 2

    private const val MAX_SPELL_NAME_WORDS = 6

    // endregion

    /**
     * Узел оглавления описания.
     *
     * @param level 0 — вступление до первого заголовка, 1 — `# `, 2 — `## `, 3 — `### `.
     */
    data class Node(val level: Int, val title: String, val body: String)

    /** Плоское оглавление: заголовки всех уровней по порядку с текстом до следующего заголовка. */
    fun outline(description: String): List<Node> {
        val nodes = mutableListOf<Node>()
        var level = 0
        var title = ""
        val body = mutableListOf<String>()
        fun flush() {
            val text = body.joinToString("\n").trim()
            if (level > 0 || text.isNotEmpty()) nodes += Node(level, title, text)
            body.clear()
        }
        description.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                HtmlUtils.isSubheading(trimmed) -> {
                    flush(); level = 3; title = HtmlUtils.subheadingText(trimmed)
                }
                HtmlUtils.isHeading(trimmed) -> {
                    flush(); level = 2; title = HtmlUtils.headingText(trimmed)
                }
                HtmlUtils.isTopHeading(trimmed) -> {
                    flush(); level = 1; title = HtmlUtils.topHeadingText(trimmed)
                }
                else -> body += line
            }
        }
        flush()
        return nodes
    }

    /** Строки таблицы (без строки-разделителя) для первой таблицы в тексте; первая строка — шапка. */
    fun firstTable(text: String): List<List<String>> = tables(text).firstOrNull().orEmpty()

    /** Все таблицы текста: список строк (шапка первой), разделители пропущены. */
    fun tables(text: String): List<List<List<String>>> {
        val result = mutableListOf<List<List<String>>>()
        var current = mutableListOf<List<String>>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (HtmlUtils.isTableRow(line)) {
                if (!HtmlUtils.isTableSeparator(line)) current += HtmlUtils.parseTableRow(line)
            } else if (current.isNotEmpty()) {
                result += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    /** Разбивает текст по подзаголовкам `### ` на (заголовок, тело); текст до первого — с пустым заголовком. */
    fun subsections(body: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var title = ""
        val lines = mutableListOf<String>()
        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (HtmlUtils.isSubheading(trimmed)) {
                result += title to lines.joinToString("\n").trim()
                lines.clear()
                title = HtmlUtils.subheadingText(trimmed)
            } else {
                lines += line
            }
        }
        result += title to lines.joinToString("\n").trim()
        return result.filter { it.first.isNotEmpty() || it.second.isNotEmpty() }
    }

    /**
     * Уровень из строки вида `*3-й уровень, умение мастера боевых искусств*`; null — строки нет.
     * Смотрит только первую непустую строку тела.
     */
    fun featureLevel(body: String): Int? {
        val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        // «Требование: 15-й уровень колдуна» — условие варианта, а не уровень умения.
        if (!first.startsWith("*") || first.lowercase().contains("требование")) return null
        return Regex("(\\d+)\\s*-?\\s*[а-я]*\\s+уровень").find(first)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Помечено ли умение как опциональное (Tasha) в строке уровня. */
    fun isOptionalFeature(body: String): Boolean {
        val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return false
        return first.startsWith("*") && first.lowercase().contains("опциональн")
    }

    /** Капитализация заголовка, набранного капсом: «БОЕВОЕ ПРЕВОСХОДСТВО» → «Боевое превосходство». */
    fun titleCase(title: String): String {
        val letters = title.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return title.trim()
        val lower = title.lowercase().trim()
        return lower.replaceFirstChar { it.uppercase() }
    }
}
