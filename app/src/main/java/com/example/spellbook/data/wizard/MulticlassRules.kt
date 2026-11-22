package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.util.HtmlUtils

/**
 * Правила мультиклассирования класса из врезки `::: Мультиклассирование и <класс>`.
 *
 * @param requirements группы требований: в каждой группе достаточно одной характеристики не ниже
 * [minScore] («Силы или Ловкости» у воина — одна группа из двух), а выполнены должны быть все группы
 * («Силы и Харизмы» у паладина — две группы). Пусто — требований нет (врезка не найдена).
 * @param grants владения без выбора, которые даёт первый уровень класса при мультиклассе.
 * @param choices выборы владений («один навык из списка классовых навыков плута», «один музыкальный инструмент»).
 * @param found врезка найдена в тексте класса.
 */
data class MulticlassRules(
    val requirements: List<Set<AbilityType>> = emptyList(),
    val minScore: Int = DEFAULT_MIN_SCORE,
    val grants: FeatureGrants = FeatureGrants(),
    val choices: List<ChoiceSpec> = emptyList(),
    val requirementText: String = "",
    val grantsText: String = "",
    val found: Boolean = false,
) {
    /** Выполнены ли требования при этих значениях характеристик. */
    fun isMet(scores: Map<AbilityType, Int>): Boolean = unmet(scores).isEmpty()

    /** Группы требований, которые не выполнены. */
    fun unmet(scores: Map<AbilityType, Int>): List<Set<AbilityType>> =
        requirements.filter { group -> group.none { (scores[it] ?: 0) >= minScore } }

    companion object {
        const val DEFAULT_MIN_SCORE = 13

        /** id выбора навыка/инструмента мультикласса. */
        const val SKILLS_CHOICE_ID = "multiclass.skills"
        const val TOOLS_CHOICE_ID = "multiclass.tools"

        private val SCORE = Regex("значени\\S*\\s+(.+?)\\s+не\\s+ниже\\s+(\\d+)", RegexOption.IGNORE_CASE)

        /** Разбирает врезку мультиклассирования; врезки нет — правила без требований и владений. */
        fun parse(rules: ClassRules): MulticlassRules {
            val lines = calloutLines(rules.charClass.description) ?: return MulticlassRules()
            var requirementText = ""
            var grantsText = ""
            lines.forEach { line ->
                val trait = MarkupText.traitLine(line) ?: return@forEach
                val title = trait.first.lowercase()
                when {
                    title.contains("характеристик") || title.contains("требовани") -> requirementText = MarkupText.plain(trait.second)
                    title.contains("владени") -> grantsText = MarkupText.plain(trait.second)
                }
            }
            val (requirements, minScore) = parseRequirements(requirementText)
            val (grants, choices) = parseGrants(grantsText, rules)
            return MulticlassRules(
                requirements = requirements,
                minScore = minScore,
                grants = grants,
                choices = choices,
                requirementText = requirementText,
                grantsText = grantsText,
                found = true,
            )
        }

        /** Строки врезки мультиклассирования без границ; null — врезки нет. */
        private fun calloutLines(description: String): List<String>? {
            val lines = description.lines().map { it.trim() }
            val start = lines.indexOfFirst { line ->
                HtmlUtils.isCalloutStart(line) && HtmlUtils.calloutTitle(line).lowercase().contains("мультикласс")
            }
            if (start < 0) return null
            val end = (start + 1 until lines.size).firstOrNull { HtmlUtils.isCalloutBoundary(lines[it]) } ?: lines.size
            return lines.subList(start + 1, end)
        }

        /** «Силы или Ловкости не ниже 13» → [{STR, DEX}]; «Силы и Харизмы» → [{STR}, {CHA}]. */
        internal fun parseRequirements(text: String): Pair<List<Set<AbilityType>>, Int> {
            val match = SCORE.find(text) ?: return emptyList<Set<AbilityType>>() to DEFAULT_MIN_SCORE
            val minScore = match.groupValues[2].toIntOrNull() ?: DEFAULT_MIN_SCORE
            val groups = match.groupValues[1].split(Regex("\\s+и\\s+|,\\s*"))
                .map { part -> part.split(Regex("\\s+или\\s+")).flatMap { MarkupText.abilities(it) }.toSet() }
                .filter { it.isNotEmpty() }
            return groups to minScore
        }

        /** Владения из строки «…когда берёте свой первый уровень барда: лёгкие доспехи, один навык…». */
        private fun parseGrants(text: String, rules: ClassRules): Pair<FeatureGrants, List<ChoiceSpec>> {
            if (text.isBlank()) return FeatureGrants() to emptyList()
            val lower = text.lowercase()
            if (lower.contains("не получаете никаких владений")) return FeatureGrants() to emptyList()
            val list = text.substringAfter(':', text).replace(Regex("\\([^)]*\\)"), " ")
            val armor = mutableSetOf<ArmorProficiency>()
            val weapons = mutableSetOf<WeaponProficiency>()
            val otherWeapons = mutableListOf<String>()
            val tools = mutableListOf<String>()
            val choices = mutableListOf<ChoiceSpec>()
            list.split(',', ';').map { it.trim().trimEnd('.').trim() }.filter { it.isNotEmpty() }.forEach { item ->
                val l = item.lowercase()
                when {
                    l.contains("доспех") -> {
                        if (l.contains("лёгк") || l.contains("легк")) armor += ArmorProficiency.LIGHT
                        if (l.contains("средн")) armor += ArmorProficiency.MEDIUM
                        if (l.contains("тяжёл") || l.contains("тяжел")) armor += ArmorProficiency.HEAVY
                    }
                    l.startsWith("щит") -> armor += ArmorProficiency.SHIELDS
                    l.startsWith("простое оружие") -> weapons += WeaponProficiency.SIMPLE
                    l.startsWith("воинское оружие") -> weapons += WeaponProficiency.MARTIAL
                    l.contains("навык") -> {
                        val count = MarkupText.firstNumber(l)?.takeIf { it in 1..MAX_COUNT } ?: 1
                        val options = if (l.contains("классов")) rules.skillChoice?.options.orEmpty() else emptyList()
                        choices += ChoiceSpec(
                            id = SKILLS_CHOICE_ID,
                            source = ChoiceSource.CLASS,
                            title = "Мультиклассирование: навыки",
                            kind = ChoiceKind.SKILL,
                            count = count,
                            sourceText = item,
                            options = options,
                        )
                    }
                    (l.contains("инструмент") || l.contains("набор")) &&
                        (l.startsWith("один") || l.startsWith("одн") || l.contains("выбор")) -> {
                        val count = MarkupText.firstNumber(l)?.takeIf { it in 1..MAX_COUNT } ?: 1
                        choices += ChoiceSpec(
                            id = TOOLS_CHOICE_ID,
                            source = ChoiceSource.CLASS,
                            title = "Мультиклассирование: инструменты",
                            kind = ChoiceKind.TOOL,
                            count = count,
                            sourceText = item,
                            options = ToolCatalog.candidates(item).map(WizardEngine::toolOption),
                        )
                    }
                    l.contains("инструмент") || l.contains("набор") ->
                        tools += item.replaceFirstChar { it.uppercase() }
                    else -> otherWeapons += item
                }
            }
            if (otherWeapons.isNotEmpty()) weapons += WeaponProficiency.OTHER
            return FeatureGrants(
                armor = armor,
                weapons = weapons,
                otherWeapons = otherWeapons,
                tools = tools,
            ) to choices
        }

        private const val MAX_COUNT = 4
    }
}
