package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.SkillType

/**
 * Источник владения, который даёт его без выбора: раса, класс, предыстория, черта.
 *
 * @param name название источника («Преступник»); пусто — источник называется по [source].
 */
data class GrantSource(val source: ChoiceSource, val name: String)

/**
 * Повторное владение: навык или инструмент, выбранный в выборе [choice], который персонаж
 * и так получает от [grantedBy] (или из другого выбора). По правилам вместо него можно
 * взять другой — уведомление предлагает поменять или оставить как есть.
 *
 * @param value значение в выборе (`SkillType.name` или `ToolInfo.id`).
 * @param otherChoice другой выбор, в котором взято то же владение; null — даётся без выбора.
 */
data class ProficiencyOverlap(
    val choice: ChoiceSpec,
    val value: String,
    val label: String,
    val grantedBy: GrantSource?,
    val otherChoice: ChoiceSpec?,
) {
    /** Ключ для «оставить как есть»: не зависит от того, какой из двух выборов изменят. */
    val key: String get() = "${choice.id}|$value"
}

/** Поиск повторных владений навыками и инструментами. */
object ProficiencyOverlaps {

    fun find(draft: CharacterDraft, context: WizardContext): List<ProficiencyOverlap> {
        val result = mutableListOf<ProficiencyOverlap>()
        val skillSources = WizardEngine.fixedSkillSources(context)
        val toolSources = fixedToolSources(context)
        // Выборы в порядке шагов: повтор отмечается у более позднего выбора.
        val ordered = WizardEngine.steps(draft, context).flatMap { step -> WizardEngine.choicesOn(step, context) }
        val seenSkills = mutableMapOf<SkillType, ChoiceSpec>()
        val seenTools = mutableMapOf<String, ChoiceSpec>()
        ordered.forEach { choice ->
            WizardEngine.chosenSkills(draft, choice).forEach { skill ->
                val granted = skillSources.firstOrNull { skill in it.second }?.first
                val other = seenSkills[skill]
                if (granted != null || other != null) {
                    result += ProficiencyOverlap(choice, skillValue(choice, skill), skill.name, granted, other)
                }
                seenSkills.putIfAbsent(skill, choice)
            }
            WizardEngine.chosenTools(draft, choice).forEach { toolId ->
                val tool = ToolCatalog.byId(toolId)
                val name = tool?.name ?: toolId
                val granted = toolSources.firstOrNull { (_, names) -> names.any { it.equals(name, ignoreCase = true) } }?.first
                val other = seenTools[toolId]
                if (granted != null || other != null) {
                    result += ProficiencyOverlap(choice, toolValue(choice, toolId), name, granted, other)
                }
                seenTools.putIfAbsent(toolId, choice)
            }
        }
        return result.filter { it.key !in draft.keptOverlaps }
    }

    private fun skillValue(choice: ChoiceSpec, skill: SkillType) =
        if (choice.kind == ChoiceKind.PROFICIENCY) WizardEngine.SKILL_PREFIX + skill.name else skill.name

    private fun toolValue(choice: ChoiceSpec, toolId: String) =
        if (choice.kind == ChoiceKind.PROFICIENCY) WizardEngine.TOOL_PREFIX + toolId else toolId

    /** Инструменты без выбора по источникам (названия из справочника). */
    private fun fixedToolSources(context: WizardContext): List<Pair<GrantSource, List<String>>> {
        fun names(text: String) = ToolCatalog.findAll(text).map { it.name }
        return listOfNotNull(
            context.race?.let { GrantSource(ChoiceSource.RACE, it.race.name) to names(it.grants.tools) },
            context.subrace?.let { GrantSource(ChoiceSource.SUBRACE, it.title) to names(it.grants.tools) },
            context.characterClass?.let {
                GrantSource(ChoiceSource.CLASS, it.charClass.name) to
                    names(WizardEngine.fixedClassTools(it)) + context.classGrants.tools
            },
            context.background?.let { GrantSource(ChoiceSource.BACKGROUND, it.background.name) to names(it.tools) },
            GrantSource(ChoiceSource.FEAT, "") to context.featGrants.tools,
        ).filter { it.second.isNotEmpty() }
    }
}
