package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.Background
import com.example.spellbook.data.model.SkillType

/**
 * Разбор предыстории: владения, языки, снаряжение и умение.
 *
 * Строки владений у dnd.su единообразны: `**Владение навыками**: …`,
 * `**Владение инструментами**: …`, `**Языки**: …`, `**Снаряжение**: …`.
 */
object BackgroundRulesParser {

    private val FIELD_LINE = Regex("^\\*\\*([^*]+)\\*\\*\\s*:?\\s*(.*)$")

    /** Слова, по которым часть строки владений считается выбором. */
    private val CHOICE_WORDS = listOf("выбор", "выберите", "один ", "одна ", "одно ", "два ", "две ", "любой", "любая", " или ")

    fun parse(background: Background): BackgroundRules {
        val nodes = MarkupText.outline(background.description)
        val fields = mutableMapOf<String, String>()
        background.description.lines().forEach { raw ->
            val match = FIELD_LINE.matchEntire(raw.trim()) ?: return@forEach
            val key = match.groupValues[1].trim().trimEnd(':').lowercase()
            if (key !in fields) fields[key] = match.groupValues[2].trim()
        }

        val choices = mutableListOf<ChoiceSpec>()

        // Навыки.
        val skillsText = fields.entries.firstOrNull { it.key.contains("навык") }?.value.orEmpty()
        val skills = mutableSetOf<SkillType>()
        if (skillsText.isNotBlank()) {
            val lower = MarkupText.plain(skillsText).lowercase()
            val listed = MarkupText.skillRefs(skillsText).ifEmpty { MarkupText.skillMentions(skillsText) }
            val choiceMatch = Regex("выберите\\s+(\\S+)").find(lower)
            if (choiceMatch != null) {
                choices += ChoiceSpec(
                    id = "background.skills",
                    source = ChoiceSource.BACKGROUND,
                    title = "Навыки",
                    kind = ChoiceKind.SKILL,
                    count = MarkupText.number(choiceMatch.groupValues[1]) ?: 1,
                    sourceText = skillsText,
                    options = listed.map { ChoiceOption(it.name, it.name) },
                )
            } else {
                skills += listed
            }
        }

        // Инструменты: фиксированные части сразу, части с выбором — отдельными выборами.
        val toolsText = fields.entries.firstOrNull { it.key.contains("инструмент") }?.value.orEmpty()
        val fixedTools = mutableListOf<String>()
        if (toolsText.isNotBlank() && !MarkupText.plain(toolsText).lowercase().startsWith("нет")) {
            // «Выберите два из следующих наборов — …, …, …» — один выбор из перечисления, а не несколько.
            val parts = if (MarkupText.plain(toolsText).lowercase().startsWith("выберите")) {
                listOf(toolsText)
            } else {
                splitParts(toolsText)
            }
            parts.forEachIndexed { index, part ->
                val plain = MarkupText.plain(part).trimEnd('.')
                if (isChoice(plain)) {
                    choices += ChoiceSpec(
                        id = "background.tool.$index",
                        source = ChoiceSource.BACKGROUND,
                        title = "Инструменты: ${plain.replaceFirstChar { it.lowercase() }}",
                        kind = ChoiceKind.TOOL,
                        count = MarkupText.firstNumber(plain.substringBefore("("))?.takeIf { it in 1..4 } ?: 1,
                        // Варианты подбирает справочник инструментов по тексту (см. WizardEngine.withToolOptions).
                        sourceText = part,
                    )
                } else if (plain.isNotBlank()) {
                    fixedTools += plain
                }
            }
        }

        // Языки.
        val languagesText = fields.entries.firstOrNull { it.key.contains("язык") }?.value.orEmpty()
        val languages = mutableListOf<String>()
        if (languagesText.isNotBlank()) {
            val plain = MarkupText.plain(languagesText)
            val lower = plain.lowercase()
            if (lower.contains("если уже знаете")) {
                // «Дварфийский, или еще один на ваш выбор, если уже знаете Дварфийский» — даётся Дварфийский.
                languages += MarkupText.languages(plain.substringBefore(","))
            } else if (isChoice(lower)) {
                // «Великаний и один язык по вашему выбору»: часть до «и» — фиксированная.
                val fixedPart = lower.substringBefore(" и ", "").takeIf { !isChoice(it) }.orEmpty()
                languages += MarkupText.languages(fixedPart)
                // «Выберите один из Великаньего, Эльфийского или языка Краулов» — только названные.
                val listed = MarkupText.languageAlternatives(plain)
                choices += ChoiceSpec(
                    id = "background.languages",
                    source = ChoiceSource.BACKGROUND,
                    title = "Языки",
                    kind = ChoiceKind.LANGUAGE,
                    count = MarkupText.firstNumber(lower.substringAfter(" и ", lower))?.takeIf { it in 1..4 } ?: 1,
                    sourceText = languagesText,
                    options = listed.map { ChoiceOption(it, it) },
                )
            } else {
                languages += MarkupText.languages(plain)
            }
        }

        // Снаряжение и золото.
        val equipmentText = fields.entries.firstOrNull { it.key.contains("снаряжение") }?.value.orEmpty()
        var gold = 0
        val equipment = mutableListOf<String>()
        splitItems(MarkupText.plain(equipmentText)).forEach { item ->
            val goldMatch = Regex("(\\d+)\\s*зм").find(item)
            if (goldMatch != null && (item.lowercase().contains("кош") || item.lowercase().contains("мешоч") ||
                    item.trim().matches(Regex("^\\d+\\s*зм.*")))
            ) {
                gold += goldMatch.groupValues[1].toInt()
            } else if (item.isNotBlank()) {
                equipment += item.replaceFirstChar { it.uppercase() }
            }
        }

        // Умение предыстории: заголовок «УМЕНИЕ: …».
        val featureNode = nodes.firstOrNull { node ->
            node.level in 1..2 && node.title.lowercase().let { it.startsWith("умение") && !it.startsWith("умения") }
        }
        val feature = featureNode?.let { node ->
            val title = node.title.substringAfter(':', node.title).trim()
            RuleFeature(title = MarkupText.titleCase(title), text = node.body)
        }

        return BackgroundRules(
            background = background,
            skills = skills,
            tools = fixedTools.joinToString(", "),
            languages = languages.joinToString(", "),
            equipment = equipment,
            gold = gold,
            feature = feature,
            choices = choices,
            featOptions = featOptions(background.description),
        )
    }

    /** «Вы получаете черту «Крепкий»», «…черту Бдительный, Одарённый или Крепкий (на ваш выбор)». */
    private val FEAT_GRANT = Regex("[Вв]ы получаете черту\\s+([^.;]+)")

    /**
     * Имена черт, которые даёт предыстория.
     *
     * Учитывается только прямая фраза «Вы получаете черту …»: сборники вроде «Адаптации
     * предысторий» описывают правила кампании для всех персонажей, а не предысторию.
     */
    fun featOptions(description: String): List<String> {
        val text = MarkupText.plain(description)
        val match = FEAT_GRANT.find(text) ?: return emptyList()
        val tail = match.groupValues[1].substringBefore(" из ").substringBefore("(").substringBefore(" и обязаны").trim()
        return tail.split(Regex(",\\s*|\\s+или\\s+"))
            .map { it.trim().trim('«', '»', '"', ' ', '*') }
            .filter { it.isNotBlank() && it.first().isUpperCase() }
    }

    private fun isChoice(text: String): Boolean {
        val lower = " ${text.lowercase()} "
        return CHOICE_WORDS.any { lower.contains(it) }
    }

    /**
     * Части строки владений через запятую. Не делим внутри скобок («(например, карты или …)»)
     * и перед «или»: «Один игровой набор, или музыкальный инструмент» — один выбор.
     */
    private fun splitParts(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        text.forEachIndexed { index, char ->
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(char) }
                depth == 0 && (char == ',' || char == ';') && !text.startsWith(", или", index) -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result += current.toString()
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Предметы снаряжения: запятые и последнее «и». Скобки не разрезаются. */
    fun splitItems(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        fun flush() {
            val item = current.toString().trim().trimEnd('.', ';').trim()
            if (item.isNotEmpty()) result += item
            current.clear()
        }
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(char) }
                depth == 0 && (char == ',' || char == ';') -> flush()
                depth == 0 && text.startsWith(" и ", index) -> { flush(); index += 2 }
                else -> current.append(char)
            }
            index++
        }
        flush()
        return result
    }
}
