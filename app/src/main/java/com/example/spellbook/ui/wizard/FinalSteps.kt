package com.example.spellbook.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.wizard.AbilityMethod
import com.example.spellbook.data.wizard.AsiChoice
import com.example.spellbook.data.wizard.AsiKind
import com.example.spellbook.data.wizard.BackgroundRules
import com.example.spellbook.data.wizard.BonusMode
import com.example.spellbook.data.wizard.ChoiceSource
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardStep
import com.example.spellbook.ui.screens.groupBackgroundsByBook
import androidx.compose.ui.res.pluralStringResource

// region Предыстория

@Composable
fun BackgroundStep(ui: WizardUi) {
    val background = ui.context.background
    var browsing by rememberSaveable { mutableStateOf(background == null) }
    if (browsing || background == null) {
        BackgroundList(ui) { id ->
            ui.update { WizardEngine.selectBackground(it, id) }
            browsing = false
        }
    } else {
        BackgroundDetails(ui, background, onChange = { browsing = true })
    }
}

/**
 * Список предысторий в том же виде, что и в библиотеке: блоки каталога в порядке
 * dnd.su (`groupBackgroundsByBook`), каждый сворачивается.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundList(ui: WizardUi, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val collapsed = rememberCollapsedGroups()
    val otherTitle = stringResource(R.string.library_backgrounds_other_book)
    val rulesById = remember(ui.library.backgrounds) {
        ui.library.backgrounds.associate { it.id to ui.controller.cache.background(it) }
    }
    // Сборники «Адаптация предысторий» не являются самостоятельной предысторией.
    val selectable = ui.library.backgrounds.filter { background ->
        val rules = rulesById[background.id] ?: return@filter false
        rules.skills.isNotEmpty() || rules.choices.isNotEmpty() || rules.equipment.isNotEmpty()
    }
    val groups = remember(selectable, query, otherTitle) {
        groupBackgroundsByBook(
            selectable.filter { it.name.contains(query.trim(), ignoreCase = true) },
            ui.library.backgrounds,
            otherTitle,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StepHeader(stringResource(R.string.wizard_background_title), stringResource(R.string.wizard_background_subtitle)) }
        item { SearchField(query, { query = it }, stringResource(R.string.wizard_search_backgrounds)) }
        if (ui.library.backgrounds.isEmpty()) item { HintRow(stringResource(R.string.wizard_library_empty)) }
        groups.forEach { group ->
            val isCollapsed = group.title in collapsed.value && query.isBlank()
            item(key = "h:${group.title}") {
                GroupHeader(
                    title = group.title,
                    count = pluralStringResource(R.plurals.background_count, group.backgrounds.size, group.backgrounds.size),
                    collapsed = isCollapsed,
                    onToggle = { collapsed.value = collapsed.value.toggle(group.title) },
                )
            }
            if (!isCollapsed) {
                items(group.backgrounds, key = { it.id }) { background ->
                    val item = rulesById[background.id] ?: return@items
                    SelectableCard(selected = background.id == ui.draft.backgroundId, onClick = { onPick(background.id) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(background.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            // Книга — только если отличается от блока, как в библиотеке.
                            if (background.book.isNotBlank() && !background.book.equals(group.title, ignoreCase = true)) {
                                Text(background.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                item.skills.forEach { InfoPill(stringResource(it.labelRes), highlighted = true) }
                                if (item.featOptions.isNotEmpty()) InfoPill(stringResource(R.string.wizard_grants_feat))
                                item.feature?.let { InfoPill(it.title) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundDetails(ui: WizardUi, background: BackgroundRules, onChange: () -> Unit) {
    StepColumn {
        ChosenHeader(
            label = stringResource(R.string.wizard_step_background),
            title = background.background.name,
            subtitle = background.background.book,
            onChange = onChange,
        )
        StepIssues(ui, WizardStep.Background)
        GrantedProficiencies(
            skills = background.skills,
            rows = listOf(
                stringResource(R.string.cform_tools) to listOf(background.tools).filter { it.isNotBlank() },
                stringResource(R.string.cform_languages) to listOf(background.languages).filter { it.isNotBlank() },
            ),
        )
        if (background.featOptions.isNotEmpty()) {
            SectionTitle(stringResource(R.string.wizard_bg_feat))
            HintRow(
                stringResource(
                    if (background.featOptions.size > 1) R.string.wizard_bg_feat_choice else R.string.wizard_bg_feat_fixed,
                    background.featOptions.joinToString(", "),
                ),
            )
        }


        WizardEngine.choicesOn(WizardStep.Background, ui.context).forEach { choice -> ChoiceBlock(ui, choice) }

        if (background.equipment.isNotEmpty() || background.gold > 0) {
            SectionTitle(stringResource(R.string.wizard_equipment))
            background.equipment.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DoneMark(true)
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (background.gold > 0) InfoPill(stringResource(R.string.wizard_gold_value, background.gold), highlighted = true)
        }
        background.feature?.let {
            SectionTitle(stringResource(R.string.wizard_bg_feature))
            ExpandableDescription(it.title, it.text, initiallyExpanded = true)
        }
        ExpandableDescription(stringResource(R.string.wizard_full_description), background.background.description)
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// endregion

// region Характеристики

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbilitiesStep(ui: WizardUi) {
    val draft = ui.draft
    val context = ui.context
    StepColumn {
        StepHeader(stringResource(R.string.wizard_abilities_title), stringResource(R.string.wizard_abilities_subtitle))
        StepIssues(ui, WizardStep.Abilities)

        // Способ заполнения.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AbilityMethod.entries.forEach { method ->
                ValueChip(
                    text = stringResource(
                        when (method) {
                            AbilityMethod.STANDARD_ARRAY -> R.string.wizard_method_standard
                            AbilityMethod.POINT_BUY -> R.string.wizard_method_point_buy
                            AbilityMethod.ROLL -> R.string.wizard_method_roll
                            AbilityMethod.MANUAL -> R.string.wizard_method_manual
                        },
                    ),
                    selected = draft.abilityMethod == method,
                    onClick = {
                        ui.update { it.copy(abilityMethod = method, poolAssignment = emptyMap(), baseScores = emptyMap()) }
                    },
                )
            }
        }
        when (draft.abilityMethod) {
            AbilityMethod.STANDARD_ARRAY -> HintRow(stringResource(R.string.wizard_standard_hint))
            AbilityMethod.POINT_BUY -> PointBuyBudget(WizardEngine.pointBuySpent(draft))
            AbilityMethod.ROLL -> RollPanel(ui)
            AbilityMethod.MANUAL -> HintRow(stringResource(R.string.wizard_manual_hint))
        }

        // Пул значений: что ещё не распределено.
        val pool = WizardEngine.scorePool(draft)
        if (pool.isNotEmpty()) {
            val used = draft.poolAssignment.values.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pool.forEachIndexed { index, value ->
                    NumberCircle(value.toString(), filled = index !in used, size = 34)
                }
            }
        }

        val racial = WizardEngine.racialBonuses(draft, context)
        val asi = WizardEngine.asiBonuses(draft, context)
        val final = WizardEngine.finalScores(draft, context)
        val base = WizardEngine.baseScores(draft)
        val saves = context.characterClass?.savingThrows.orEmpty()
        AbilityType.entries.forEach { ability ->
            AbilityRow(
                ui = ui,
                ability = ability,
                base = base[ability],
                racial = racial[ability] ?: 0,
                asi = asi[ability] ?: 0,
                total = final[ability] ?: 10,
                save = ability in saves,
            )
        }

        RacialBonusSection(ui)
        AsiSection(ui)

        // Хиты зависят от Телосложения, поэтому настраиваются после характеристик.
        context.characterClass?.let { rules ->
            HorizontalDivider()
            HitPointsSection(ui, rules)
        }
    }
}

@Composable
private fun PointBuyBudget(spent: Int) {
    val left = WizardEngine.POINT_BUY_BUDGET - spent
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (left < 0) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wizard_points_left), modifier = Modifier.weight(1f))
            Text(
                "$left / ${WizardEngine.POINT_BUY_BUDGET}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RollPanel(ui: WizardUi) {
    val rolls = ui.draft.rolledScores
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HintRow(stringResource(R.string.wizard_roll_hint))
        if (rolls.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rolls.forEach { roll ->
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(roll.total.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            // Отброшенная кость показана зачёркнутой — видно, откуда сумма.
                            var droppedShown = false
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                roll.dice.forEach { die ->
                                    val dropped = !droppedShown && die == roll.dropped
                                    if (dropped) droppedShown = true
                                    Text(
                                        die.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dropped) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textDecoration = if (dropped) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = ui.controller::rollAbilityScores) {
            Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (rolls.isEmpty()) R.string.wizard_roll_scores else R.string.wizard_reroll),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Строка характеристики: база (способ ввода зависит от метода), бонусы и итог с модификатором. */
@Composable
private fun AbilityRow(
    ui: WizardUi,
    ability: AbilityType,
    base: Int?,
    racial: Int,
    asi: Int,
    total: Int,
    save: Boolean,
) {
    val draft = ui.draft
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(ability.labelRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (racial != 0) Text(stringResource(R.string.wizard_racial_short, signed(racial)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (asi != 0) Text(stringResource(R.string.wizard_asi_short, signed(asi)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    if (save) Text(stringResource(R.string.wizard_save_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (draft.abilityMethod) {
                AbilityMethod.MANUAL -> OutlinedTextField(
                    value = draft.baseScores[ability]?.toString() ?: "",
                    onValueChange = { text ->
                        val value = text.filter(Char::isDigit).take(2).toIntOrNull()
                        ui.update {
                            it.copy(baseScores = if (value == null) it.baseScores - ability else it.baseScores + (ability to value.coerceIn(1, 30)))
                        }
                    },
                    placeholder = { Text(WizardEngine.MANUAL_DEFAULT.toString()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(72.dp),
                )
                AbilityMethod.POINT_BUY -> {
                    val value = base ?: WizardEngine.POINT_BUY_MIN
                    val nextCost = WizardEngine.pointBuyCost(value + 1)
                    val canRaise = nextCost != null &&
                        WizardEngine.pointBuySpent(draft) - (WizardEngine.pointBuyCost(value) ?: 0) + nextCost <= WizardEngine.POINT_BUY_BUDGET
                    IconButton(onClick = { ui.update { it.copy(baseScores = it.baseScores + (ability to value - 1)) } }, enabled = value > WizardEngine.POINT_BUY_MIN) {
                        Icon(Icons.Default.Remove, stringResource(R.string.wizard_decrease))
                    }
                    Text(value.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { ui.update { it.copy(baseScores = it.baseScores + (ability to value + 1)) } }, enabled = canRaise) {
                        Icon(Icons.Default.Add, stringResource(R.string.wizard_increase))
                    }
                }
                AbilityMethod.STANDARD_ARRAY, AbilityMethod.ROLL -> PoolPicker(ui, ability, base)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                Text(
                    if (base == null) "—" else total.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (base != null) Text(formatModifier(abilityModifier(total)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Выбор значения из пула: занятые значения у других характеристик меняются местами. */
@Composable
private fun PoolPicker(ui: WizardUi, ability: AbilityType, base: Int?) {
    val pool = WizardEngine.scorePool(ui.draft)
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = pool.isNotEmpty(), modifier = Modifier.width(72.dp), contentPadding = PaddingValues(0.dp)) {
            Text(base?.toString() ?: "?", fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface) {
            pool.forEachIndexed { index, value ->
                val owner = ui.draft.poolAssignment.entries.firstOrNull { it.value == index }?.key
                DropdownMenuItem(
                    text = {
                        Text(
                            if (owner == null || owner == ability) value.toString()
                            else stringResource(R.string.wizard_swap_with, value, stringResource(owner.shortLabelRes)),
                        )
                    },
                    onClick = {
                        open = false
                        ui.update { draft ->
                            val current = draft.poolAssignment[ability]
                            val updated = draft.poolAssignment.toMutableMap()
                            updated[ability] = index
                            // Значение было у другой характеристики — отдаём ей наше прежнее.
                            if (owner != null && owner != ability) {
                                if (current != null) updated[owner] = current else updated.remove(owner)
                            }
                            draft.copy(poolAssignment = updated)
                        }
                    },
                )
            }
            if (base != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wizard_unassign)) },
                    onClick = { open = false; ui.update { it.copy(poolAssignment = it.poolAssignment - ability) } },
                )
            }
        }
    }
}

/** Распределение бонусов расы: строго по правилам расы или свободно (Tasha). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RacialBonusSection(ui: WizardUi) {
    val draft = ui.draft
    val rule = WizardEngine.bonusRule(ui.context) ?: return
    HorizontalDivider()
    SectionTitle(stringResource(R.string.wizard_racial_bonuses))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        BonusMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = draft.bonusMode == mode,
                onClick = { ui.update { it.copy(bonusMode = mode, bonusTargets = emptyList()) } },
                shape = SegmentedButtonDefaults.itemShape(index, BonusMode.entries.size),
            ) {
                Text(stringResource(if (mode == BonusMode.RULES) R.string.wizard_bonus_rules else R.string.wizard_bonus_any))
            }
        }
    }
    HintRow(stringResource(if (draft.bonusMode == BonusMode.RULES) R.string.wizard_bonus_rules_hint else R.string.wizard_bonus_any_hint))
    if (!rule.parsed && rule.sourceText.isNotBlank()) {
        ExpandableDescription(stringResource(R.string.wizard_bonus_source), rule.sourceText, initiallyExpanded = true)
    }
    if (draft.bonusMode == BonusMode.RULES && rule.fixed.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rule.fixed.forEach { InfoPill("${stringResource(it.ability.shortLabelRes)} ${signed(it.amount)}", highlighted = true) }
        }
    }
    if (rule.patterns.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rule.patterns.forEachIndexed { index, pattern ->
                ValueChip(
                    text = pattern.joinToString(" / ") { signed(it) },
                    selected = draft.bonusPatternIndex == index,
                    onClick = { ui.update { it.copy(bonusPatternIndex = index, bonusTargets = emptyList()) } },
                )
            }
        }
    }
    val values = WizardEngine.bonusValues(draft, rule)
    val fixedAbilities = rule.fixed.map { it.ability }.toSet()
    values.forEachIndexed { index, amount ->
        val target = draft.bonusTargets.getOrNull(index)
        val takenByOthers = draft.bonusTargets.filterIndexed { i, ability -> i != index && ability != null }.filterNotNull().toSet()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.wizard_bonus_slot, signed(amount)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AbilityType.entries.forEach { ability ->
                    // Правило «другой характеристики»: свободный бонус не идёт туда, где уже есть фиксированный.
                    val blocked = ability in takenByOthers ||
                        (draft.bonusMode == BonusMode.RULES && rule.excludeFixed && ability in fixedAbilities)
                    ValueChip(
                        text = stringResource(ability.shortLabelRes),
                        selected = target == ability,
                        enabled = !blocked || target == ability,
                        onClick = {
                            ui.update { d ->
                                val targets = MutableList(values.size) { d.bonusTargets.getOrNull(it) }
                                targets[index] = if (target == ability) null else ability
                                d.copy(bonusTargets = targets)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Увеличения характеристик по уровням класса: +2, +1/+1 или черта. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AsiSection(ui: WizardUi) {
    val draft = ui.draft
    val levels = WizardEngine.asiLevels(draft, ui.context)
    if (levels.isEmpty()) return
    HorizontalDivider()
    SectionTitle(stringResource(R.string.wizard_asi_title))
    HintRow(stringResource(R.string.wizard_asi_hint))
    levels.forEach { level ->
        val choice = draft.asiChoices[level] ?: AsiChoice(level, AsiKind.PLUS_TWO)
        fun save(updated: AsiChoice) = ui.update { d ->
            val feats = if (updated.kind == AsiKind.FEAT) d.featSlots else d.featSlots - "asi.$level"
            d.copy(asiChoices = d.asiChoices + (level to updated), featSlots = feats)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.wizard_asi_level, level), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AsiKind.entries.forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = draft.asiChoices[level]?.kind == kind,
                            onClick = { save(AsiChoice(level, kind)) },
                            shape = SegmentedButtonDefaults.itemShape(index, AsiKind.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (kind) {
                                        AsiKind.PLUS_TWO -> R.string.wizard_asi_two
                                        AsiKind.PLUS_ONE_ONE -> R.string.wizard_asi_one_one
                                        AsiKind.FEAT -> R.string.wizard_asi_feat
                                    },
                                ),
                            )
                        }
                    }
                }
                if (draft.asiChoices[level] != null && choice.kind != AsiKind.FEAT) {
                    val limit = if (choice.kind == AsiKind.PLUS_TWO) 1 else 2
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AbilityType.entries.forEach { ability ->
                            val selected = ability in choice.abilities
                            ValueChip(
                                text = stringResource(ability.shortLabelRes),
                                selected = selected,
                                enabled = selected || choice.abilities.size < limit || limit == 1,
                                onClick = {
                                    val names = toggle(choice.abilities.map { it.name }, ability.name, limit)
                                    save(choice.copy(abilities = names.map(AbilityType::valueOf)))
                                },
                            )
                        }
                    }
                } else if (choice.kind == AsiKind.FEAT && draft.asiChoices[level] != null) {
                    HintRow(stringResource(R.string.wizard_asi_feat_hint))
                }
            }
        }
    }
}

// endregion

// region Черты

@Composable
fun FeatsStep(ui: WizardUi) {
    val slots = WizardEngine.featSlots(ui.draft, ui.context)
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    StepColumn {
        StepHeader(stringResource(R.string.wizard_feats_title), stringResource(R.string.wizard_feats_subtitle))
        StepIssues(ui, WizardStep.Feats)
        if (ui.feats.isEmpty()) HintRow(stringResource(R.string.wizard_library_empty))
        slots.forEach { slot ->
            val featId = ui.draft.featSlots[slot.id]
            val feat = ui.feats.firstOrNull { it.id == featId }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(if (feat != null) 2.dp else 1.dp, if (feat != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable { picking = slot.id },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoneMark(feat != null)
                        Text(
                            when (slot.source) {
                                ChoiceSource.RACE -> stringResource(R.string.wizard_feat_from_race)
                                ChoiceSource.BACKGROUND -> stringResource(R.string.wizard_feat_from_background)
                                else -> stringResource(R.string.wizard_feat_from_asi, slot.level)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { picking = slot.id }) {
                            Text(stringResource(if (feat == null) R.string.wizard_choose else R.string.wizard_change))
                        }
                    }
                    if (slot.allowedNames.isNotEmpty()) {
                        HintRow(stringResource(R.string.wizard_feat_allowed, slot.allowedNames.joinToString(", ")))
                    }
                    if (feat != null) {
                        Text(feat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (feat.book.isNotBlank()) {
                            Text(feat.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FeatEffectsSummary(ui, slot.id)
                        ExpandableDescription(stringResource(R.string.wizard_feat_text), feat.description)
                    }
                }
            }
            // Выборы черты (характеристика, заклинания, навыки) — сразу под карточкой черты.
            ui.context.featEffects[slot.id]?.choices?.forEach { spec ->
                ui.context.choices.firstOrNull { it.id == spec.id }?.let { choice -> ChoiceBlock(ui, choice) }
            }
        }
        HintRow(stringResource(R.string.wizard_feats_prereq_hint))
    }
    picking?.let { slotId ->
        val slot = slots.firstOrNull { it.id == slotId }
        FeatPickerDialog(
            // Черта от предыстории выбирается только из перечисленных («Бдительный, Одарённый или Крепкий»).
            feats = WizardEngine.featsForSlot(slot, ui.feats),
            selectedId = ui.draft.featSlots[slotId],
            takenIds = ui.draft.featSlots.values.toSet(),
            onSelect = { id ->
                ui.update { draft ->
                    // Смена черты сбрасывает выборы прежней черты в этом слоте.
                    WizardEngine.clearSelections(draft, "feat.$slotId.")
                        .copy(featSlots = draft.featSlots + (slotId to id))
                }
            },
            onClose = { picking = null },
        )
    }
}

// endregion

// region Сводка

/**
 * Что черта даст листу без выбора: «Сила +1», тяжёлые доспехи, туманный шаг, +5 к инициативе.
 * Выборы черты показываются отдельно под карточкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatEffectsSummary(ui: WizardUi, slotId: String) {
    val effects = ui.context.featEffects[slotId] ?: return
    val pills = buildList {
        effects.fixedAbilities.forEach { (ability, amount) -> add("${stringResource(ability.shortLabelRes)} ${signed(amount)}") }
        effects.grants.armor.sortedBy { it.ordinal }.forEach { add(stringResource(it.labelRes)) }
        effects.grants.weapons.sortedBy { it.ordinal }.forEach { add(stringResource(it.labelRes)) }
        addAll(effects.grants.otherWeapons)
        addAll(effects.grants.tools)
        addAll(effects.grants.languages)
        effects.grants.skills.sortedBy { it.ordinal }.forEach { add(stringResource(it.labelRes)) }
        addAll(effects.spells)
        if (effects.hpPerLevel > 0) add(stringResource(R.string.wizard_feat_hp, effects.hpPerLevel * ui.draft.level))
        if (effects.speed > 0) add(stringResource(R.string.wizard_speed_bonus, effects.speed))
        if (effects.initiative > 0) add(stringResource(R.string.wizard_initiative_bonus, effects.initiative))
    }
    if (pills.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pills.forEach { InfoPill(it, highlighted = true) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryStep(ui: WizardUi, onCreate: () -> Unit) {
    val draft = ui.draft
    val context = ui.context
    val scores = WizardEngine.finalScores(draft, context)
    val issues = WizardEngine.allIssues(draft, context).filter { it.first != WizardStep.Summary }
    StepColumn {
        StepHeader(stringResource(R.string.wizard_summary_title), stringResource(R.string.wizard_summary_subtitle))

        // Шапка персонажа.
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    draft.name.ifBlank { stringResource(R.string.character_unnamed) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val origin = listOfNotNull(
                    context.subrace?.title ?: context.race?.race?.name,
                    context.characterClass?.charClass?.name?.let { "$it ${draft.level}" },
                    context.subclass?.title,
                    context.background?.background?.name,
                ).joinToString(" · ")
                if (origin.isNotBlank()) Text(origin, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WizardEngine.maxHp(draft, context)?.let { InfoPill(stringResource(R.string.wizard_hp_value, it), highlighted = true) }
                    (context.subrace?.grants?.speed ?: context.race?.grants?.speed)?.let { InfoPill(stringResource(R.string.wizard_speed, it)) }
                    InfoPill(stringResource(R.string.wizard_proficiency, formatModifier(com.example.spellbook.data.model.proficiencyBonusFor(draft.level))))
                }
            }
        }

        // Характеристики компактной сеткой.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AbilityType.entries.forEach { ability ->
                val score = scores[ability] ?: 10
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(96.dp),
                ) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(ability.shortLabelRes), style = MaterialTheme.typography.labelMedium)
                        Text(formatModifier(abilityModifier(score)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(score.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Ресурсы и черты, которые получит персонаж.
        if (context.resources.isNotEmpty()) {
            SectionTitle(stringResource(R.string.wizard_summary_resources))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                context.resources.forEach { InfoPill("${it.name}: ${it.maximum}", highlighted = true) }
            }
        }
        val featNames = draft.featSlots.values.mapNotNull { id -> ui.feats.firstOrNull { it.id == id }?.name }
        if (featNames.isNotEmpty()) {
            SectionTitle(stringResource(R.string.wizard_step_feats))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                featNames.forEach { InfoPill(it) }
            }
        }

        // Что записать в заметки и ресурсы: лишнее можно исключить.
        val proposal = remember(context, draft) {
            com.example.spellbook.data.wizard.CharacterAssembler.proposal(draft, context, ui.spells)
        }
        ContentExclusionSection(proposal, draft.excludedContent) { key ->
            ui.update { it.copy(excludedContent = com.example.spellbook.data.wizard.ContentExclusions.toggle(it.excludedContent, key)) }
        }

        // Незавершённое со ссылками на шаги.
        if (issues.isNotEmpty()) {
            HorizontalDivider()
            SectionTitle(stringResource(R.string.wizard_summary_unfinished))
            issues.forEach { (step, stepIssues) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().clickable { ui.goTo(step) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stepLabel(step, ui), fontWeight = FontWeight.Bold)
                            stepIssues.forEach { Text(issueText(it), style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
            if (WizardEngine.canCreate(draft, context)) HintRow(stringResource(R.string.wizard_summary_can_create))
        }
        // Кнопка создания — в нижней панели; здесь только пояснение, почему она неактивна.
        if (!WizardEngine.canCreate(draft, context)) HintRow(stringResource(R.string.wizard_summary_required))
    }
}

// endregion
