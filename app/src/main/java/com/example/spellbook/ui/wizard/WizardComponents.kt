package com.example.spellbook.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.wizard.AbilityBonusRule
import com.example.spellbook.data.wizard.ChoiceKind
import com.example.spellbook.data.wizard.ChoiceOption
import com.example.spellbook.data.wizard.ChoiceSpec
import com.example.spellbook.data.wizard.IssueKind
import com.example.spellbook.data.wizard.MarkupText
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardIssue
import com.example.spellbook.ui.screens.DescriptionText
import com.example.spellbook.util.HtmlUtils

/** Заголовок шага: крупное название и пояснение. */
@Composable
fun StepHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Подзаголовок раздела внутри шага со счётчиком выбора справа. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Плашка «выбрано N из M»: красная, пока выбор не завершён, и с галочкой, когда готово. */
@Composable
fun CountBadge(selected: Int, total: Int) {
    val done = selected >= total
    val container by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "countBadge",
    )
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                stringResource(R.string.wizard_count, selected, total),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Небольшая плашка-метка: бонус характеристики, кость хитов, книга. */
@Composable
fun InfoPill(text: String, highlighted: Boolean = false, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Карточка выбираемого варианта: раса, класс, архетип, предыстория, вариант выбора.
 *
 * Выбранная карточка обведена бордовой рамкой и подсвечена — так выбор заметен
 * при беглом просмотре длинного списка.
 */
@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiSelect: Boolean = false,
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "cardBorder",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (multiSelect) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled)
            } else {
                RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            }
            Box(Modifier.weight(1f).padding(top = 10.dp, bottom = 8.dp)) { content() }
        }
    }
}

/**
 * Раскрываемый текст из библиотеки: заголовок и разметка dnd.su.
 *
 * Заголовок виден всегда. Короткий текст (до [SHORT_DESCRIPTION_CHARS] символов без таблиц)
 * сразу показан целиком, длинный — свёрнут, чтобы не заслонять выбор. Свернуть и развернуть
 * можно любой текст.
 *
 * @param initiallyExpanded раскрыть даже длинный текст; null — решает длина текста.
 */
@Composable
fun ExpandableDescription(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean? = null,
    badge: String? = null,
) {
    val startExpanded = initiallyExpanded ?: isShortDescription(text)
    var expanded by rememberSaveable(title, text) { mutableStateOf(startExpanded) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                badge?.let { InfoPill(it) }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                )
            }
            AnimatedVisibility(visible = expanded && text.isNotBlank()) {
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    DescriptionText(description = text, onDiceClick = {})
                }
            }
        }
    }
}

/** Описание достаточно короткое, чтобы показать его целиком без сворачивания. */
fun isShortDescription(text: String): Boolean =
    text.lines().none { HtmlUtils.isTableRow(it) } && MarkupText.plain(text).length <= SHORT_DESCRIPTION_CHARS

/** Примерно три-четыре строки на телефоне: длиннее — уже заслоняет выбор. */
const val SHORT_DESCRIPTION_CHARS = 280

/** Подсказка на шаге: что ещё не выбрано. */
@Composable
fun IssuesBanner(issues: List<WizardIssue>, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                issues.forEach { issue ->
                    Text(issueText(issue), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Блок «Вы получаете»: владения, которые источник даёт без выбора. Навыки — плашками
 * с замком (такими же, как уже полученные навыки в выборе), остальное — строками «вид: список».
 * Ничего не даётся — блок не показывается.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrantedProficiencies(skills: Set<SkillType>, rows: List<Pair<String, List<String>>>) {
    val filled = rows.filter { it.second.isNotEmpty() }
    if (skills.isEmpty() && filled.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.wizard_granted_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (skills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.wizard_bg_skills),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        skills.sortedBy { it.ordinal }.forEach {
                            InfoPill(stringResource(it.labelRes), highlighted = true, icon = Icons.Default.Lock)
                        }
                    }
                }
            }
            filled.forEach { (label, values) ->
                Column {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(values.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Уведомления о повторных владениях: «Скрытность уже даёт предыстория Преступник».
 *
 * «Поменять» снимает повторное значение и переводит на шаг, где сделан выбор, — там можно
 * взять другое. «Оставить» запоминает решение, и уведомление больше не показывается.
 */
@Composable
fun OverlapBanner(ui: WizardUi, overlaps: List<com.example.spellbook.data.wizard.ProficiencyOverlap>) {
    if (overlaps.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    stringResource(R.string.wizard_overlap_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            overlaps.forEach { overlap ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(overlapText(overlap), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = {
                            ui.select(overlap.choice.id, ui.selection(overlap.choice.id) - overlap.value)
                            val target = WizardEngine.choiceStepKey(overlap.choice, ui.context)
                            val step = WizardEngine.steps(ui.draft, ui.context).firstOrNull { step -> step.key == target }
                            step?.let(ui.goTo)
                        }) { Text(stringResource(R.string.wizard_overlap_change)) }
                        TextButton(onClick = {
                            ui.update { it.copy(keptOverlaps = it.keptOverlaps + overlap.key) }
                        }) { Text(stringResource(R.string.wizard_overlap_keep)) }
                    }
                }
            }
        }
    }
}

/** «Скрытность (Навыки класса) уже даёт предыстория «Преступник»». */
@Composable
private fun overlapText(overlap: com.example.spellbook.data.wizard.ProficiencyOverlap): String {
    val skill = runCatching { SkillType.valueOf(overlap.label) }.getOrNull()
    val name = skill?.let { stringResource(it.labelRes) } ?: overlap.label
    val granted = overlap.grantedBy
    val source = when {
        granted != null -> {
            val kind = stringResource(
                when (granted.source) {
                    com.example.spellbook.data.wizard.ChoiceSource.RACE -> R.string.wizard_source_race
                    com.example.spellbook.data.wizard.ChoiceSource.SUBRACE -> R.string.wizard_source_subrace
                    com.example.spellbook.data.wizard.ChoiceSource.CLASS,
                    com.example.spellbook.data.wizard.ChoiceSource.SUBCLASS -> R.string.wizard_source_class
                    com.example.spellbook.data.wizard.ChoiceSource.BACKGROUND -> R.string.wizard_source_background
                    com.example.spellbook.data.wizard.ChoiceSource.FEAT -> R.string.wizard_source_feat
                },
            )
            if (granted.name.isBlank()) kind else "$kind «${granted.name}»"
        }
        else -> stringResource(R.string.wizard_source_choice, overlap.otherChoice?.title.orEmpty())
    }
    return stringResource(R.string.wizard_overlap_item, name, overlap.choice.title, source)
}

/** Пояснение (например, почему вариант недоступен) — небольшим блоком с иконкой. */
@Composable
fun HintRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Текст незавершённости на языке интерфейса. */
@Composable
fun issueText(issue: WizardIssue): String = when (issue.kind) {
    IssueKind.NAME_MISSING -> stringResource(R.string.wizard_issue_name)
    IssueKind.RACE_MISSING -> stringResource(R.string.wizard_issue_race)
    IssueKind.SUBRACE_MISSING -> stringResource(R.string.wizard_issue_subrace)
    IssueKind.CLASS_MISSING -> stringResource(R.string.wizard_issue_class)
    IssueKind.SUBCLASS_MISSING -> stringResource(R.string.wizard_issue_subclass, issue.label)
    IssueKind.BACKGROUND_MISSING -> stringResource(R.string.wizard_issue_background)
    IssueKind.CHOICE_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_choice, issue.missing, issue.label, issue.missing)
    IssueKind.CANTRIPS_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_cantrips, issue.missing, issue.missing)
    IssueKind.SPELLS_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_spells, issue.missing, issue.missing)
    IssueKind.PREPARED_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_prepared, issue.missing, issue.missing)
    IssueKind.SCORES_UNASSIGNED -> pluralStringResource(R.plurals.wizard_issue_scores, issue.missing, issue.missing)
    IssueKind.POINT_BUY_OVER_BUDGET -> stringResource(R.string.wizard_issue_point_buy)
    IssueKind.BONUS_UNASSIGNED -> pluralStringResource(R.plurals.wizard_issue_bonus, issue.missing, issue.missing)
    IssueKind.ASI_INCOMPLETE -> stringResource(R.string.wizard_issue_asi, issue.label)
    IssueKind.FEAT_MISSING -> stringResource(R.string.wizard_issue_feat)
    IssueKind.WEAPON_MISSING -> pluralStringResource(R.plurals.wizard_issue_weapons, issue.missing, issue.missing)
}

/** Краткая запись бонусов расы: «ЛОВ +2 · ИНТ +1» или «+2/+1 на выбор». */
@Composable
fun bonusSummary(rule: AbilityBonusRule?): String {
    if (rule == null) return ""
    val fixed = rule.fixed.map { "${stringResource(it.ability.shortLabelRes)} ${signed(it.amount)}" }
    val free = rule.patterns.map { pattern -> pattern.joinToString("/") { signed(it) } }
    val parts = buildList {
        if (fixed.size == AbilityType.entries.size && fixed.all { it.endsWith("+1") }) {
            add(stringResource(R.string.wizard_bonus_all))
        } else {
            addAll(fixed)
        }
        if (free.isNotEmpty()) add(stringResource(R.string.wizard_bonus_free, free.joinToString(stringResource(R.string.wizard_or))))
    }
    return parts.joinToString(" · ")
}

fun signed(value: Int): String = if (value >= 0) "+$value" else "−${-value}"

// region Выборы

/**
 * Встроенный редактор выбора: навыки, языки, инструменты, свободный ввод
 * и небольшие списки вариантов. Заклинания выбираются через [SpellChoiceField].
 *
 * @param ownedSkills навыки, которые уже есть у персонажа из других источников:
 * их нельзя выбрать повторно, но для компетентности они и есть кандидаты.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineChoice(
    choice: ChoiceSpec,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    ownedSkills: Set<SkillType> = emptySet(),
    showSourceText: Boolean = true,
    /** Языки, которые персонаж уже знает (в нижнем регистре): их не предлагаем выбрать снова. */
    knownLanguages: Set<String> = emptySet(),
    /** Полноэкранный браузер длинных списков (инструменты) с поиском и фильтрами. */
    browse: (@Composable (onClose: () -> Unit) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(choice.title) { CountBadge(selected.count { it.isNotBlank() }, choice.count) }
        if (choice.hint.isNotBlank()) HintRow(choice.hint)
        if (showSourceText && choice.sourceText.isNotBlank()) {
            // Полный текст умения — тут же, рядом с выбором: свёрнут, чтобы не заслонять варианты,
            // а таблицы в нём рисуются таблицами.
            ExpandableDescription(stringResource(R.string.wizard_rules_text), choice.sourceText)
        }
        when (choice.kind) {
            ChoiceKind.SKILL, ChoiceKind.EXPERTISE -> {
                val candidates = when {
                    choice.kind == ChoiceKind.EXPERTISE -> ownedSkills.toList().sortedBy { it.ordinal }
                    choice.options.isNotEmpty() -> choice.options.mapNotNull { runCatching { SkillType.valueOf(it.id) }.getOrNull() }
                    else -> SkillType.entries
                }
                if (choice.kind == ChoiceKind.EXPERTISE && candidates.isEmpty()) {
                    HintRow(stringResource(R.string.wizard_expertise_empty))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { skill ->
                        val isSelected = skill.name in selected
                        val owned = choice.kind == ChoiceKind.SKILL && skill in ownedSkills && !isSelected
                        val full = selected.size >= choice.count && !isSelected
                        FilterChip(
                            selected = isSelected || owned,
                            enabled = !owned && !full,
                            onClick = { onChange(toggle(selected, skill.name, choice.count)) },
                            label = { Text(stringResource(skill.labelRes)) },
                            leadingIcon = when {
                                owned -> ({ Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) })
                                isSelected -> ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                                else -> null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }

            // Инструменты — карточками из справочника: по «Подробнее» видно, что даёт владение.
            ChoiceKind.TOOL -> ToolOptions(choice, selected, onChange, browse)

            // Языки — только чипами; уже известные (например, Общий) не предлагаются.
            ChoiceKind.LANGUAGE -> OptionChips(
                choice.options.filter { it.title.lowercase() !in knownLanguages || it.id in selected },
                selected,
                choice.count,
                onChange,
            )

            // Короткие списки без описаний (избранный враг, местность) — чипами, остальное — карточками.
            ChoiceKind.OPTION -> if (choice.options.none { it.text.isNotBlank() }) {
                OptionChips(choice.options, selected, choice.count, onChange)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choice.options.forEach { option ->
                        OptionCard(
                            option = option,
                            selected = option.id in selected,
                            multiSelect = choice.count > 1,
                            enabled = option.id in selected || selected.size < choice.count || choice.count == 1,
                            onClick = { onChange(toggle(selected, option.id, choice.count)) },
                        )
                    }
                }
            }

            // Характеристика для +1 от черты — чипами с полными названиями.
            ChoiceKind.ABILITY -> OptionChips(
                choice.options.map { option ->
                    val ability = runCatching { com.example.spellbook.data.model.AbilityType.valueOf(option.id) }.getOrNull()
                    option.copy(title = ability?.let { stringResource(it.labelRes) } ?: option.title)
                },
                selected,
                choice.count,
                onChange,
            )

            // «Навыки или инструменты» («Одарённый»): навыки — чипами, инструменты — из справочника.
            // Оба списка пишут в один выбор и делят общий лимит.
            ChoiceKind.PROFICIENCY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val skills = choice.options.filter { it.id.startsWith(WizardEngine.SKILL_PREFIX) }
                val owned = ownedSkills.map { WizardEngine.SKILL_PREFIX + it.name }.toSet()
                OptionChips(skills.filter { it.id !in owned || it.id in selected }, selected, choice.count, onChange)
                ToolOptions(
                    choice.copy(options = choice.options.filter { it.id.startsWith(WizardEngine.TOOL_PREFIX) }),
                    selected,
                    onChange,
                    browse,
                )
            }

            // Оружие на выбор («Мастер оружия») — карточками с уроном, длинный список — в браузере.
            ChoiceKind.WEAPON -> ToolOptions(choice, selected, onChange, browse)

            // Заклинания выбираются отдельным полем со списком из библиотеки.
            ChoiceKind.CANTRIP, ChoiceKind.SPELL -> Unit
        }
    }
}

/**
 * Переключение значения с учётом лимита. При выборе одного варианта новое
 * значение заменяет старое — это привычнее, чем снимать отметку вручную.
 */
fun toggle(selected: List<String>, value: String, limit: Int): List<String> = when {
    value in selected -> selected - value
    limit == 1 -> listOf(value)
    selected.size < limit -> selected + value
    else -> selected
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(options: List<ChoiceOption>, selected: List<String>, limit: Int, onChange: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val isSelected = option.id in selected
            FilterChip(
                selected = isSelected,
                enabled = isSelected || selected.size < limit || limit == 1,
                onClick = { onChange(toggle(selected, option.id, limit)) },
                label = { Text(option.title) },
                leadingIcon = if (isSelected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
            )
        }
    }
}

/**
 * Выбор инструментов. Короткий список (до нескольких вариантов) показывается карточками сразу,
 * длинный (все ремесленные) — выбранными карточками и кнопкой, открывающей весь список.
 */
@Composable
private fun ToolOptions(
    choice: ChoiceSpec,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    browse: (@Composable (onClose: () -> Unit) -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    val cards: @Composable (List<ChoiceOption>) -> Unit = { options ->
        options.forEach { option ->
            OptionCard(
                option = option,
                selected = option.id in selected,
                multiSelect = choice.count > 1,
                enabled = option.id in selected || selected.size < choice.count || choice.count == 1,
                onClick = { onChange(toggle(selected, option.id, choice.count)) },
            )
        }
    }
    if (choice.options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (choice.options.size <= INLINE_TOOL_LIMIT) {
            cards(choice.options)
        } else {
            cards(choice.options.filter { it.id in selected })
            androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (choice.kind == ChoiceKind.WEAPON) R.string.wizard_choose_weapon else R.string.wizard_choose_tool,
                    ),
                )
            }
        }
    }
    if (open && browse != null) {
        FullScreenPicker(title = choice.title, onClose = { open = false }) { browse { open = false } }
    }
}

/** Сколько инструментов показывать прямо на шаге; длинные списки — в браузере с поиском. */
private const val INLINE_TOOL_LIMIT = 4

/** Карточка варианта со сворачиваемым описанием и требованиями. */
@Composable
fun OptionCard(
    option: ChoiceOption,
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    unavailableReason: String? = null,
) {
    // Короткое описание видно целиком сразу и без кнопки «Подробнее», длинное — свёрнуто.
    val short = remember(option.text) { isShortDescription(option.text) }
    var expanded by rememberSaveable(option.id, option.title) { mutableStateOf(short) }
    SelectableCard(selected = selected, onClick = onClick, enabled = enabled, multiSelect = multiSelect) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                if (option.text.isNotBlank() && !short) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.wizard_less else R.string.wizard_more))
                    }
                }
            }
            if (option.requirement.isNotBlank() || option.extended) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (option.requirement.isNotBlank()) InfoPill(option.requirement)
                    if (option.extended) InfoPill(stringResource(R.string.wizard_optional_badge), highlighted = true)
                }
            }
            unavailableReason?.let { HintRow(it) }
            if (!expanded && option.text.isNotBlank()) {
                Text(
                    MarkupText.plain(option.text.lineSequence().firstOrNull { line ->
                        line.isNotBlank() && !line.trim().startsWith("*")
                    } ?: option.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(visible = expanded) {
                DescriptionText(description = option.text, onDiceClick = {})
            }
        }
    }
}


// endregion

/** Индикатор завершённости в списках (например, слотов черт). */
@Composable
fun DoneMark(done: Boolean) {
    Icon(
        if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp),
    )
}

/** Разделитель с отступом между крупными блоками шага. */
@Composable
fun BlockSpacer() = Spacer(Modifier.width(0.dp).padding(vertical = 4.dp))

/** Круглая цифра-значок (номер шага, значение характеристики). */
@Composable
fun NumberCircle(text: String, filled: Boolean, size: Int = 28) {
    Surface(
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (filled) 1f else 0.5f)),
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
