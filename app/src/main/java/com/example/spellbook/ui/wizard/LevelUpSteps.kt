package com.example.spellbook.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.model.proficiencyBonusFor
import com.example.spellbook.data.wizard.AsiChoice
import com.example.spellbook.data.wizard.AsiKind
import com.example.spellbook.data.wizard.ChoiceKind
import com.example.spellbook.data.wizard.ChoiceOption
import com.example.spellbook.data.wizard.ChoiceSpec
import com.example.spellbook.data.wizard.FeatureRules
import com.example.spellbook.data.wizard.HpMethod
import com.example.spellbook.data.wizard.LevelUpClassEntry
import com.example.spellbook.data.wizard.LevelUpEngine
import com.example.spellbook.data.wizard.LevelUpReplacement
import com.example.spellbook.data.wizard.LevelUpStep
import com.example.spellbook.data.wizard.OptionalSwap
import com.example.spellbook.data.wizard.ReplacementKind
import com.example.spellbook.data.wizard.ReplacementSpec
import com.example.spellbook.data.wizard.SpellFilter

// region Класс

/**
 * Шаг класса: какой класс повышается (или новый — мультикласс), переключатели контента,
 * архетип, опциональные замены, простые выборы и все новые способности уровня с текстом.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelUpClassStep(ui: LevelUpUi) {
    val context = ui.context
    val base = ui.base
    val draft = ui.draft
    StepColumn {
        StepHeader(
            stringResource(R.string.levelup_class_title),
            stringResource(
                R.string.levelup_class_subtitle,
                base.oldLevel,
                context.newLevel,
                formatModifier(proficiencyBonusFor(base.oldLevel)),
                formatModifier(proficiencyBonusFor(context.newLevel)),
            ),
        )
        TextIssuesBanner(base.warnings.map { levelUpWarningText(it) })
        LevelUpIssues(ui, LevelUpStep.Class)
        if (base.atMaxLevel) return@StepColumn

        // Текущие классы персонажа.
        if (base.classes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.levelup_current_classes))
            base.classes.forEach { entry -> CurrentClassCard(ui, entry) }
        }

        // Новый класс — мультиклассирование.
        NewClassSection(ui)

        HorizontalDivider()
        SectionTitle(stringResource(R.string.wizard_content_title))
        ToggleRow(
            title = stringResource(R.string.wizard_optional_toggle),
            subtitle = stringResource(R.string.wizard_optional_hint),
            checked = draft.showOptional,
            onChange = { value -> ui.update { it.copy(showOptional = value) } },
        )
        ToggleRow(
            title = stringResource(R.string.wizard_unofficial_toggle),
            subtitle = stringResource(R.string.wizard_unofficial_hint),
            checked = draft.showUnofficial,
            onChange = { value -> ui.update { it.copy(showUnofficial = value) } },
        )

        if (!context.classChosen) return@StepColumn
        if (context.limited) HintRow(stringResource(R.string.levelup_limited))

        // Владения мультиклассирования.
        context.multiclass?.let { check ->
            HorizontalDivider()
            SectionTitle(stringResource(R.string.levelup_multiclass_grants))
            val rules = check.rules
            if (rules.requirements.isNotEmpty()) {
                HintRow(stringResource(R.string.levelup_requirements, multiclassRequirementText(rules)))
            }
            val grants = rules.grants
            if (grants.isEmpty) {
                HintRow(stringResource(R.string.levelup_multiclass_none))
            } else {
                GrantedProficiencies(
                    skills = grants.skills,
                    rows = listOf(
                        stringResource(R.string.cform_armor) to grants.armor.sortedBy { it.ordinal }.map { stringResource(it.labelRes) },
                        stringResource(R.string.cform_weapons) to grants.weapons
                            .filter { it != com.example.spellbook.data.model.WeaponProficiency.OTHER }
                            .sortedBy { it.ordinal }.map { stringResource(it.labelRes) } + grants.otherWeapons,
                        stringResource(R.string.cform_tools) to grants.tools,
                        stringResource(R.string.cform_languages) to grants.languages,
                    ),
                )
            }
        }

        // Архетип.
        if (context.needsSubclass) LevelUpSubclassSection(ui)

        // Опциональные умения, заменяющие базовые.
        context.swaps.forEach { swap -> LevelUpSwapBlock(ui, swap) }

        // Выборы, встроенные в шаг класса.
        LevelUpEngine.choicesOn(LevelUpStep.Class, context).forEach { choice -> LevelUpChoiceBlock(ui, choice) }

        // Новые способности уровня.
        HorizontalDivider()
        NewAlwaysPreparedCard(ui)
        SectionTitle(stringResource(R.string.levelup_new_features))
        if (context.features.isEmpty()) {
            HintRow(stringResource(R.string.levelup_no_features))
        } else {
            HintRow(stringResource(R.string.levelup_features_hint))
            context.features.forEach { feature ->
                ExpandableDescription(
                    title = feature.feature.title,
                    text = feature.feature.text,
                    badge = levelUpSourceLabel(feature.source),
                )
            }
        }
    }
}

@Composable
private fun classTitle(entry: LevelUpClassEntry): String =
    entry.rules?.charClass?.name ?: entry.classLevel.displayName ?: stringResource(entry.classLevel.characterClass.labelRes)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentClassCard(ui: LevelUpUi, entry: LevelUpClassEntry) {
    val selected = ui.draft.newClassId == null && ui.draft.classIndex == entry.index
    SelectableCard(selected = selected, onClick = { ui.update { LevelUpEngine.selectClass(it, entry.index) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(classTitle(entry), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoPill(
                    stringResource(R.string.levelup_class_level_change, entry.classLevel.level, entry.classLevel.level + 1),
                    highlighted = true,
                )
                entry.rules?.let { InfoPill(stringResource(R.string.wizard_hit_die, it.hitDie)) }
                entry.subclass?.let { InfoPill(it.title) }
            }
            // Что появится на следующем уровне — по таблице класса.
            entry.rules?.levelFeatures?.get(entry.classLevel.level + 1)?.takeIf { it.isNotEmpty() }?.let { names ->
                Text(
                    names.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Список классов для мультиклассирования. Классы с невыполненными требованиями видны,
 * но недоступны — с объяснением, какой характеристики не хватает.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewClassSection(ui: LevelUpUi) {
    val draft = ui.draft
    var expanded by rememberSaveable { mutableStateOf(draft.newClassId != null || ui.base.classes.isEmpty()) }
    SectionTitle(stringResource(R.string.levelup_new_class))
    HintRow(stringResource(R.string.levelup_new_class_hint))
    if (!expanded) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.levelup_show_new_classes))
        }
        return
    }
    val options = remember(ui.base, ui.library.classes) {
        LevelUpEngine.newClassOptions(ui.base, ui.library, ui.controller.cache).sortedBy { it.charClass.name }
    }
    // Неофициальные классы — только с переключателем (уже выбранный остаётся виден, как разновидности).
    val visible = options.filter { draft.showUnofficial || !it.rules.extended || it.charClass.id == draft.newClassId }
    if (visible.isEmpty()) HintRow(stringResource(R.string.wizard_library_empty))
    visible.forEach { option ->
        val selected = draft.newClassId == option.charClass.id
        val available = option.check.isMet
        SelectableCard(
            selected = selected,
            enabled = available || selected,
            onClick = { ui.update { LevelUpEngine.selectNewClass(it, option.charClass.id) } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.charClass.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoPill(stringResource(R.string.wizard_hit_die, option.rules.hitDie), highlighted = true)
                    if (option.rules.extended) InfoPill(stringResource(R.string.wizard_unofficial_badge), highlighted = true)
                }
                Text(
                    if (option.multiclass.requirements.isEmpty()) {
                        stringResource(R.string.levelup_no_requirements)
                    } else {
                        stringResource(R.string.levelup_requirements, multiclassRequirementText(option.multiclass))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                option.check.unmet.forEach { (className, groups) ->
                    HintRow(
                        stringResource(
                            R.string.levelup_unmet,
                            className,
                            multiclassRequirementText(option.multiclass, groups),
                        ),
                    )
                }
            }
        }
    }
    if (ui.base.classes.isNotEmpty()) {
        TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.levelup_hide_new_classes)) }
    }
}


@Composable
private fun LevelUpSubclassSection(ui: LevelUpUi) {
    val context = ui.context
    val rules = context.rules ?: return
    HorizontalDivider()
    SectionTitle(rules.subclassGroupTitle)
    HintRow(stringResource(R.string.levelup_subclass_hint, rules.subclassGroupTitle))
    context.subclassOptions.forEach { sub ->
        val selected = sub.id == ui.draft.subclassId
        SelectableCard(selected = selected, onClick = { ui.update { LevelUpEngine.selectSubclass(it, sub.id) } }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (sub.extended) InfoPill(stringResource(R.string.wizard_unofficial_badge), highlighted = true)
                }
                val features = sub.features.filter { it.level <= context.newClassLevel }
                if (features.isNotEmpty()) {
                    Text(
                        features.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpandOnSelect(selected) {
                    FeatureRules.noteFeatures(features, context.newClassLevel, showOptional = true).forEach { feature ->
                        ExpandableDescription(feature.title, feature.text, badge = stringResource(R.string.wizard_level_short, feature.level))
                    }
                }
            }
        }
    }
}

/** Выбор между базовым умением и заменяющим его опциональным (Tasha). */
@Composable
private fun LevelUpSwapBlock(ui: LevelUpUi, swap: OptionalSwap) {
    val useOptional = ui.draft.optionalSwaps[swap.id] == true
    fun choose(optional: Boolean) = ui.update { it.copy(optionalSwaps = it.optionalSwaps + (swap.id to optional)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.wizard_swap_title, swap.optional.title))
        HintRow(stringResource(R.string.wizard_swap_hint))
        SelectableCard(selected = !useOptional, onClick = { choose(false) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(swap.base.joinToString(" + ") { it.title }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.wizard_swap_base),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                swap.base.forEach { feature -> ExpandableDescription(feature.title, feature.text) }
            }
        }
        SelectableCard(selected = useOptional, onClick = { choose(true) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(swap.optional.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.wizard_swap_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                ExpandableDescription(swap.optional.title, swap.optional.text)
            }
        }
    }
}

// endregion

// region Выборы

/** Навыки, которыми персонаж владеет с учётом новых выборов этого уровня. */
private fun ownedSkills(ui: LevelUpUi, exceptChoice: ChoiceSpec? = null): Set<SkillType> {
    val chosen = ui.context.choices.filter { it.id != exceptChoice?.id }.flatMap { choice ->
        when (choice.kind) {
            ChoiceKind.SKILL -> ui.selection(choice.id)
            ChoiceKind.PROFICIENCY -> ui.selection(choice.id).filter { it.startsWith(com.example.spellbook.data.wizard.WizardEngine.SKILL_PREFIX) }
                .map { it.removePrefix(com.example.spellbook.data.wizard.WizardEngine.SKILL_PREFIX) }
            else -> emptyList()
        }
    }.mapNotNull { runCatching { SkillType.valueOf(it) }.getOrNull() }
    return ui.context.proficientSkills + chosen
}

/** Встроенный выбор: заклинания — полем с окном выбора, остальное — [InlineChoice]. */
@Composable
fun LevelUpChoiceBlock(ui: LevelUpUi, choice: ChoiceSpec) {
    val selected = ui.selection(choice.id)
    when (choice.kind) {
        ChoiceKind.CANTRIP, ChoiceKind.SPELL -> {
            val filter = choice.spellFilter ?: SpellFilter(maxLevel = if (choice.kind == ChoiceKind.CANTRIP) 0 else 9)
            val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
            val known = ui.context.knownSpellIds
            SpellChoiceField(
                title = choice.title,
                hint = choice.hint,
                count = choice.count,
                selected = selected,
                candidates = remember(ui.spells, effective, known) {
                    filterSpells(ui.spells, effective).filter { it.id !in known }
                },
                onChange = { ui.select(choice.id, it) },
                sourceText = choice.sourceText,
            )
        }
        else -> {
            val owned = ownedSkills(ui, exceptChoice = choice)
            val expertiseOwned = ui.context.expertiseSkills
            val languages = ui.base.character.languages.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            InlineChoice(
                // Компетентность берётся из навыков, которыми персонаж уже владеет, без уже взятой.
                choice = choice,
                selected = selected,
                onChange = { ui.select(choice.id, it) },
                browse = { _ ->
                    OptionBrowserList(
                        choice = choice,
                        selected = ui.selection(choice.id),
                        unmetOf = ui::unmet,
                        onChange = { ui.select(choice.id, it) },
                        header = { Row { CountBadge(ui.selection(choice.id).size, choice.count) } },
                    )
                },
                knownLanguages = languages - selected.map { it.lowercase() }.toSet(),
                ownedSkills = if (choice.kind == ChoiceKind.EXPERTISE) owned - expertiseOwned else owned,
            )
        }
    }
}

/** Отдельный шаг одного выбора: воззвания, боевые приёмы, метамагия, компетентность. */
@Composable
fun LevelUpChoiceStep(ui: LevelUpUi, choiceId: String) {
    val choice = ui.context.choices.firstOrNull { it.id == choiceId } ?: return
    val selected = ui.selection(choice.id)
    val browser = rememberOptionBrowserState("levelup." + choice.id)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { StepHeader(choice.title, choice.hint.ifBlank { null }) }
                CountBadge(selected.count { it.isNotBlank() }, choice.count)
            }
        }
        item { LevelUpIssues(ui, LevelUpStep.Choice(choice.id)) }
        if (choice.sourceText.isNotBlank()) {
            item { ExpandableDescription(stringResource(R.string.wizard_rules_text), choice.sourceText) }
        }
        if (choice.kind == ChoiceKind.OPTION || choice.kind == ChoiceKind.TOOL) {
            optionBrowserItems(choice, selected, browser, ui::unmet) { ui.select(choice.id, it) }
        } else {
            item { LevelUpChoiceBlock(ui, choice) }
        }
    }
}

// endregion

// region Характеристики

/** «Увеличение характеристик»: +2, +1/+1 или черта с её выборами. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelUpAbilitiesStep(ui: LevelUpUi) {
    val context = ui.context
    val draft = ui.draft
    val level = context.newClassLevel
    val asi = draft.asi
    var pickingFeat by rememberSaveable { mutableStateOf(false) }
    fun save(updated: AsiChoice) = ui.update { d ->
        d.copy(asi = updated, featId = if (updated.kind == AsiKind.FEAT) d.featId else null)
            .let { next -> if (updated.kind == AsiKind.FEAT) next else next.copy(selections = next.selections.filterKeys { !it.startsWith("feat.") }) }
    }
    StepColumn {
        StepHeader(stringResource(R.string.wizard_asi_title), stringResource(R.string.levelup_asi_subtitle))
        LevelUpIssues(ui, LevelUpStep.Abilities)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AsiKind.entries.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = asi?.kind == kind,
                    onClick = { if (asi?.kind != kind) save(AsiChoice(level, kind)) },
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
        if (asi != null && asi.kind != AsiKind.FEAT) {
            val limit = if (asi.kind == AsiKind.PLUS_TWO) 1 else 2
            val step = if (asi.kind == AsiKind.PLUS_TWO) 2 else 1
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AbilityType.entries.forEach { ability ->
                    val selected = ability in asi.abilities
                    val score = context.scoresBefore[ability] ?: 10
                    val capped = score >= com.example.spellbook.data.wizard.WizardEngine.ASI_CAP
                    ValueChip(
                        text = "${stringResource(ability.shortLabelRes)} $score",
                        selected = selected,
                        enabled = selected || (!capped && (asi.abilities.size < limit || limit == 1)),
                        onClick = {
                            val names = toggle(asi.abilities.map { it.name }, ability.name, limit)
                            save(asi.copy(abilities = names.map(AbilityType::valueOf)))
                        },
                    )
                }
            }
            HintRow(stringResource(R.string.levelup_asi_step_hint, step))
        }
        if (asi?.kind == AsiKind.FEAT) {
            val feat = ui.feats.firstOrNull { it.id == draft.featId }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    if (feat != null) 2.dp else 1.dp,
                    if (feat != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().clickable { pickingFeat = true },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoneMark(feat != null)
                        Text(
                            stringResource(R.string.wizard_feat_from_asi, level),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { pickingFeat = true }) {
                            Text(stringResource(if (feat == null) R.string.wizard_choose else R.string.wizard_change))
                        }
                    }
                    if (feat != null) {
                        Text(feat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (feat.book.isNotBlank()) {
                            Text(feat.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpandableDescription(stringResource(R.string.wizard_feat_text), feat.description)
                    }
                }
            }
            LevelUpEngine.choicesOn(LevelUpStep.Abilities, context).forEach { choice -> LevelUpChoiceBlock(ui, choice) }
            HintRow(stringResource(R.string.wizard_feats_prereq_hint))
        }

        // Характеристики «было → стало».
        HorizontalDivider()
        SectionTitle(stringResource(R.string.wizard_step_abilities))
        AbilityChangeGrid(context.scoresBefore, context.scoresAfter)
    }
    if (pickingFeat) {
        FeatPickerDialog(
            feats = remember(ui.base, ui.feats) { LevelUpEngine.availableFeats(ui.base, ui.library) },
            selectedId = draft.featId,
            takenIds = ui.base.featIds.toSet(),
            onSelect = { id ->
                ui.update { d ->
                    d.copy(featId = id, selections = d.selections.filterKeys { !it.startsWith("feat.") })
                }
            },
            onClose = { pickingFeat = false },
        )
    }
}

/** Сетка характеристик: значение и модификатор, изменившиеся подсвечены «было → стало». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbilityChangeGrid(before: Map<AbilityType, Int>, after: Map<AbilityType, Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AbilityType.entries.forEach { ability ->
            val old = before[ability] ?: 10
            val new = after[ability] ?: old
            val changed = old != new
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (changed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (changed) 1.5.dp else 1.dp,
                    if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.width(96.dp),
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(ability.shortLabelRes), style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatModifier(abilityModifier(new)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (changed) stringResource(R.string.levelup_change, old.toString(), new.toString()) else new.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// endregion

// region Хиты

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelUpHitPointsStep(ui: LevelUpUi) {
    val context = ui.context
    val draft = ui.draft
    val hitDie = context.hitDie
    val con = abilityModifier(context.scoresAfter[AbilityType.CONSTITUTION] ?: 10)
    val gain = LevelUpEngine.hpGain(context)
    val oldMax = ui.base.character.maxHp
    StepColumn {
        StepHeader(stringResource(R.string.wizard_hp), stringResource(R.string.levelup_hp_subtitle, hitDie, formatModifier(con)))
        LevelUpIssues(ui, LevelUpStep.HitPoints)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            HpMethod.entries.forEachIndexed { index, method ->
                SegmentedButton(
                    selected = draft.hpMethod == method,
                    onClick = {
                        if (method == HpMethod.ROLL && draft.hpRoll == null) {
                            ui.controller.rollHitDie(hitDie)
                        } else {
                            ui.update { it.copy(hpMethod = method) }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, HpMethod.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (method) {
                                HpMethod.AVERAGE -> R.string.wizard_hp_average
                                HpMethod.ROLL -> R.string.wizard_hp_roll
                                HpMethod.MANUAL -> R.string.wizard_hp_manual
                            },
                        ),
                    )
                }
            }
        }
        when (draft.hpMethod) {
            HpMethod.AVERAGE -> HintRow(stringResource(R.string.levelup_hp_average_hint, hitDie, LevelUpEngine.averageHitDie(hitDie)))
            HpMethod.ROLL -> {
                draft.hpRoll?.let { InfoPill(stringResource(R.string.levelup_hp_rolled, it), highlighted = true) }
                OutlinedButton(onClick = { ui.controller.rollHitDie(hitDie) }) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (draft.hpRoll == null) R.string.levelup_hp_roll else R.string.wizard_reroll, hitDie),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            HpMethod.MANUAL -> OutlinedTextField(
                value = draft.manualHp?.toString().orEmpty(),
                onValueChange = { text ->
                    val value = text.filter(Char::isDigit).take(3).toIntOrNull()?.coerceIn(1, hitDie)
                    ui.update { it.copy(manualHp = value) }
                },
                label = { Text(stringResource(R.string.levelup_hp_manual_label, hitDie)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.levelup_hp_gain, gain),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.levelup_hp_total, oldMax, oldMax + gain), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HintRow(stringResource(R.string.levelup_hp_retro_hint))
    }
}

// endregion

// region Заклинания

private enum class LevelUpSpellTab(val labelRes: Int) {
    CANTRIPS(R.string.wizard_tab_cantrips),
    KNOWN(R.string.wizard_tab_known),
    SPELLBOOK(R.string.wizard_tab_spellbook),
    EXTRA(R.string.wizard_tab_extra),
    REPLACE(R.string.levelup_tab_replace),
}

/** Замены, которые показываются на шаге заклинаний (остальные — на шаге замен). */
private fun spellStepReplacements(ui: LevelUpUi): List<ReplacementSpec> =
    ui.context.replacements.filter { it.onSpellStep }

/**
 * Шаг заклинаний: новые заговоры, известные заклинания или заклинания в книгу волшебника,
 * выборы заклинаний из умений и необязательная замена известного заклинания или заговора.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelUpSpellsStep(ui: LevelUpUi) {
    val context = ui.context
    val draft = ui.draft
    val spells = context.spells
    val extra = LevelUpEngine.choicesOn(LevelUpStep.Spells, context)
    val replacements = spellStepReplacements(ui)
    val tabs = buildList {
        if ((spells?.newCantrips ?: 0) > 0) add(LevelUpSpellTab.CANTRIPS)
        if ((spells?.newSpells ?: 0) > 0) add(if (spells?.spellbook == true) LevelUpSpellTab.SPELLBOOK else LevelUpSpellTab.KNOWN)
        if (extra.isNotEmpty()) add(LevelUpSpellTab.EXTRA)
        if (replacements.isNotEmpty()) add(LevelUpSpellTab.REPLACE)
    }
    var tab by rememberSaveable { mutableStateOf(tabs.firstOrNull() ?: LevelUpSpellTab.CANTRIPS) }
    if (tab !in tabs) tab = tabs.firstOrNull() ?: LevelUpSpellTab.CANTRIPS

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            LevelUpIssues(ui, LevelUpStep.Spells)
            spells?.plan?.let { plan ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoPill(stringResource(R.string.wizard_spell_ability, stringResource(plan.ability.labelRes)), highlighted = true)
                    if (plan.maxSpellLevel > 0) InfoPill(stringResource(R.string.wizard_max_circle, plan.maxSpellLevel))
                }
            }
            spells?.takeIf { it.addedFromClassList.isNotEmpty() }?.let {
                HintRow(stringResource(R.string.levelup_added_from_list, it.plan?.maxSpellLevel ?: 0, it.addedFromClassList.size))
            }
            spells?.alwaysPrepared?.takeIf { it.isNotEmpty() }?.let { ids ->
                HintRow(stringResource(R.string.levelup_always_prepared, ids.mapNotNull { ui.spellsById[it]?.name }.joinToString(", ")))
            }
        }
    }

    if (tabs.isEmpty()) {
        StepColumn {
            StepHeader(stringResource(R.string.wizard_step_spells))
            header()
            HintRow(stringResource(R.string.levelup_no_spell_picks))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEach { item ->
                val counter = when (item) {
                    LevelUpSpellTab.CANTRIPS -> "${draft.cantripIds.size}/${spells?.newCantrips ?: 0}"
                    LevelUpSpellTab.KNOWN, LevelUpSpellTab.SPELLBOOK -> "${draft.spellIds.size}/${spells?.newSpells ?: 0}"
                    LevelUpSpellTab.EXTRA -> "${extra.sumOf { ui.selection(it.id).size }}/${extra.sumOf { it.count }}"
                    LevelUpSpellTab.REPLACE -> "${replacements.count { draft.replacements[it.id]?.isComplete == true }}"
                }
                androidx.compose.material3.Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text("${stringResource(item.labelRes)} $counter") },
                )
            }
        }
        when (tab) {
            LevelUpSpellTab.CANTRIPS -> {
                val limit = spells?.newCantrips ?: 0
                SpellSelectionList(
                    candidates = remember(ui.spells, spells?.cantripCandidates) { spells?.cantripCandidates.orEmpty().mapNotNull(ui.spellsById::get) },
                    selected = draft.cantripIds,
                    limit = limit,
                    onToggle = { id -> ui.update { it.copy(cantripIds = toggleIds(it.cantripIds, id, limit)) } },
                    header = { header(); HintRow(stringResource(R.string.levelup_cantrips_hint, limit)) },
                )
            }
            LevelUpSpellTab.KNOWN, LevelUpSpellTab.SPELLBOOK -> {
                val limit = spells?.newSpells ?: 0
                SpellSelectionList(
                    candidates = remember(ui.spells, spells?.spellCandidates) { spells?.spellCandidates.orEmpty().mapNotNull(ui.spellsById::get) },
                    selected = draft.spellIds,
                    limit = limit,
                    onToggle = { id -> ui.update { it.copy(spellIds = toggleIds(it.spellIds, id, limit)) } },
                    header = {
                        header()
                        HintRow(
                            if (tab == LevelUpSpellTab.SPELLBOOK) {
                                stringResource(R.string.levelup_spellbook_hint, limit, spells?.plan?.maxSpellLevel ?: 0)
                            } else {
                                stringResource(R.string.levelup_known_hint, limit)
                            },
                        )
                    },
                )
            }
            LevelUpSpellTab.EXTRA -> StepColumn {
                header()
                extra.forEach { choice -> LevelUpChoiceBlock(ui, choice) }
            }
            LevelUpSpellTab.REPLACE -> StepColumn {
                header()
                HintRow(stringResource(R.string.levelup_replace_optional))
                replacements.forEach { spec -> ReplacementBlock(ui, spec) }
            }
        }
    }
}

private fun toggleIds(set: Set<String>, id: String, limit: Int): Set<String> = when {
    id in set -> set - id
    set.size < limit -> set + id
    else -> set
}

// endregion

// region Замены

/**
 * Заметная карточка «Новые всегда подготовленные заклинания» (домен, клятва, круг, специальность
 * изобретателя). Названия — ссылки: по нажатию открывается описание заклинания.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewAlwaysPreparedCard(ui: LevelUpUi, ids: List<String> = ui.context.newAlwaysPrepared) {
    val spells = ids.mapNotNull { ui.spellsById[it] }.filter { it.level > 0 }
    if (spells.isEmpty()) return
    val links = com.example.spellbook.ui.screens.LocalSpellLinks.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.levelup_new_always_prepared_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(R.string.levelup_new_always_prepared_text), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                spells.forEach { spell ->
                    androidx.compose.material3.AssistChip(
                        onClick = { links?.onOpen?.invoke(spell) },
                        label = { Text("${spell.name} (${spellCircleShort(spell.level)})") },
                    )
                }
            }
        }
    }
}

@Composable
private fun spellCircleShort(level: Int): String = com.example.spellbook.ui.spellLevelLabel(level)

/**
 * Переподготовка заклинаний (необязательно): текущие подготовленные уже отмечены, всегда
 * подготовленные — без возможности снять и вне лимита.
 */
@Composable
fun LevelUpPrepareStep(ui: LevelUpUi) {
    val prep = ui.context.preparation ?: return
    val locked = prep.alwaysPrepared.toSet()
    val candidates = remember(prep.candidates, prep.alwaysPrepared, ui.spells) {
        (prep.alwaysPrepared + prep.candidates).distinct().mapNotNull(ui.spellsById::get)
    }
    SpellSelectionList(
        candidates = candidates,
        selected = prep.selected,
        limit = prep.limit,
        locked = locked,
        onToggle = { id ->
            ui.update { draft ->
                val current = draft.preparedIds ?: prep.selected
                val next = when {
                    id in current -> current - id
                    current.size < prep.limit -> current + id
                    else -> current
                }
                draft.copy(preparedIds = next)
            }
        },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                StepHeader(
                    stringResource(R.string.levelup_prepare_title),
                    stringResource(
                        if (prep.spellbook) R.string.levelup_prepare_subtitle_book else R.string.levelup_prepare_subtitle,
                        prep.oldLimit,
                        prep.limit,
                    ),
                )
                LevelUpIssues(ui, LevelUpStep.Prepare)
                NewAlwaysPreparedCard(ui)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.levelup_prepare_count, prep.selected.size, prep.limit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (ui.draft.preparedIds != null) {
                        TextButton(onClick = { ui.update { it.copy(preparedIds = null) } }) {
                            Text(stringResource(R.string.levelup_prepare_reset))
                        }
                    }
                }
            }
        },
    )
}

/** Шаг замен вариантов: воззвания, боевые приёмы, стиль, метамагия. Все замены необязательны. */
@Composable
fun LevelUpReplacementsStep(ui: LevelUpUi) {
    val specs = ui.context.replacements.filter { !it.onSpellStep }
    StepColumn {
        StepHeader(stringResource(R.string.levelup_step_replacements), stringResource(R.string.levelup_replacements_subtitle))
        LevelUpIssues(ui, LevelUpStep.Replacements)
        specs.forEach { spec -> ReplacementBlock(ui, spec) }
    }
}

/**
 * Одна замена: что убрать и что взять взамен. Обрабатывается обобщённо: если варианты —
 * заклинания библиотеки, выбор идёт списком заклинаний с описанием, иначе — карточками вариантов.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplacementBlock(ui: LevelUpUi, spec: ReplacementSpec) {
    val current = ui.draft.replacements[spec.id] ?: LevelUpReplacement()
    var picking by rememberSaveable(spec.id) { mutableStateOf<String?>(null) }
    val isSpell = (spec.removable + spec.candidateSpellIds).any { it in ui.spellsById }
    val candidateIds = spec.candidatesFor(current.oldId)
    // Замены одной группы «одно из следующих действий» могут стоять на разных шагах:
    // если другая уже выбрана, эта недоступна, пока ту не отменят.
    val chosenInGroup = spec.group?.let { group ->
        ui.context.replacements.firstOrNull { other ->
            other.id != spec.id && other.group == group && ui.draft.replacements[other.id]?.isEmpty == false
        }
    }
    val blockedByGroup = chosenInGroup != null && current.isEmpty
    fun save(replacement: LevelUpReplacement) = ui.update { draft ->
        draft.copy(
            replacements = if (replacement.isEmpty) draft.replacements - spec.id else draft.replacements + (spec.id to replacement),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (current.isComplete) 2.dp else 1.dp,
            if (current.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            spec.group?.let { HintRow(stringResource(R.string.levelup_replace_group, it)) }
            chosenInGroup?.let { other ->
                val stepLabel = stringResource(
                    if (other.onSpellStep) R.string.wizard_step_spells else R.string.levelup_step_replacements,
                )
                TextIssuesBanner(
                    listOf(
                        stringResource(
                            if (blockedByGroup) R.string.levelup_replace_group_taken else R.string.levelup_replace_group_conflict,
                            other.title,
                            stepLabel,
                        ),
                    ),
                )
            }
            if (spec.sourceText.isNotBlank()) ExpandableDescription(stringResource(R.string.wizard_rules_text), spec.sourceText)
            ReplacementSlot(
                label = stringResource(R.string.levelup_replace_from),
                value = replacementItemTitle(ui, spec, current.oldId),
                enabled = !blockedByGroup,
                onPick = { picking = "old" },
            )
            ReplacementSlot(
                label = stringResource(R.string.levelup_replace_to),
                value = replacementItemTitle(ui, spec, current.newId),
                enabled = current.oldId != null,
                onPick = { picking = "new" },
            )
            if (current.oldId == null) HintRow(stringResource(R.string.levelup_replace_pick_old_first))
            if (!current.isEmpty) {
                TextButton(onClick = { save(LevelUpReplacement()) }) { Text(stringResource(R.string.levelup_replace_none)) }
            }
        }
    }

    val target = picking ?: return
    val ids = if (target == "old") spec.removable else candidateIds
    val selectedId = if (target == "old") current.oldId else current.newId
    fun choose(id: String) {
        if (target == "old") {
            val keepNew = current.newId?.takeIf { it in spec.candidatesFor(id) }
            save(LevelUpReplacement(oldId = id, newId = keepNew))
        } else {
            save(current.copy(newId = id))
        }
        picking = null
    }
    FullScreenPicker(
        title = if (target == "old") stringResource(R.string.levelup_replace_from) else stringResource(R.string.levelup_replace_to),
        onClose = { picking = null },
    ) {
        if (isSpell) {
            SpellSelectionList(
                candidates = remember(ids, ui.spells) { ids.mapNotNull(ui.spellsById::get) },
                selected = setOfNotNull(selectedId),
                limit = 1,
                onToggle = { id -> if (id == selectedId) picking = null else choose(id) },
                header = { Text(spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            )
        } else {
            val known = spec.removableOptions + spec.candidateOptions
            val options = ids.map { id -> known.firstOrNull { it.id == id } ?: ChoiceOption(id, replacementItemTitle(ui, spec, id).orEmpty()) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.id }) { option ->
                    val unmet = if (target == "new") ui.unmet(option) else emptyList()
                    OptionCard(
                        option = option,
                        selected = option.id == selectedId,
                        multiSelect = false,
                        enabled = unmet.isEmpty() || option.id == selectedId,
                        onClick = { choose(option.id) },
                        unavailableReason = unmet.takeIf { it.isNotEmpty() }?.let { unmetText(it) },
                    )
                }
                if (options.isEmpty()) item { HintRow(stringResource(R.string.wizard_options_missing)) }
            }
        }
    }
}

@Composable
private fun ReplacementSlot(label: String, value: String?, enabled: Boolean, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
            )
        }
        OutlinedButton(onClick = onPick, enabled = enabled) {
            Text(stringResource(if (value == null) R.string.wizard_choose else R.string.wizard_change))
        }
    }
}

// endregion

// region Итог

/** Строка «было → стало». */
@Composable
private fun ChangeRow(label: String, old: String, new: String) {
    val changed = old != new
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (changed) stringResource(R.string.levelup_change, old, new) else new,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
            color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillSection(title: String, values: List<String>) {
    if (values.isEmpty()) return
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { InfoPill(it, highlighted = true) }
    }
}

/**
 * Вопрос о ресурсе, удалённом вручную: на этом уровне он должен измениться.
 * Две кнопки — «Вернуть» и «Не возвращать»; до ответа карточка выделена.
 */
@Composable
private fun MissingResourceCard(ui: LevelUpUi, missing: com.example.spellbook.data.wizard.MissingResource) {
    val decision = ui.draft.resourceDecisions[missing.key]
    fun decide(value: Boolean) = ui.update { it.copy(resourceDecisions = it.resourceDecisions + (missing.key to value)) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (decision == null) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            if (decision == null) 1.5.dp else 1.dp,
            if (decision == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.levelup_resource_missing_title, missing.name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.levelup_resource_missing_text,
                    missing.oldMaximum,
                    missing.newMaximum,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (missing.description.isNotBlank()) {
                ExpandableDescription(stringResource(R.string.wizard_rules_text), missing.description)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueChip(stringResource(R.string.levelup_resource_restore), selected = decision == true) { decide(true) }
                ValueChip(stringResource(R.string.levelup_resource_skip), selected = decision == false) { decide(false) }
            }
        }
    }
}

/** Итог «было → стало»: всё, что изменится в листе персонажа. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelUpSummaryStep(ui: LevelUpUi) {
    val context = ui.context
    val result = remember(context) {
        if (context.classChosen && !context.base.atMaxLevel) runCatching { LevelUpEngine.build(context, ui.library) }.getOrNull() else null
    }
    val summary = result?.summary
    val issues = LevelUpEngine.allIssues(context)
    val canApply = LevelUpEngine.canApply(context)
    StepColumn {
        StepHeader(stringResource(R.string.levelup_summary_title), stringResource(R.string.levelup_summary_subtitle))
        // Ресурсы, удалённые вручную, которые на этом уровне должны измениться: спрашиваем, вернуть ли.
        context.missingResources.forEach { missing -> MissingResourceCard(ui, missing) }
        if (summary != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        ui.base.character.name.ifBlank { stringResource(R.string.character_unnamed) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (summary.isNewClass) {
                            stringResource(R.string.levelup_summary_class_new, summary.classTitle)
                        } else {
                            "${summary.classTitle} ${stringResource(R.string.levelup_change, summary.oldClassLevel.toString(), summary.newClassLevel.toString())}"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ChangeRow(stringResource(R.string.wizard_level), summary.oldLevel.toString(), summary.newLevel.toString())
                    ChangeRow(
                        stringResource(R.string.stats_proficiency),
                        formatModifier(summary.oldProficiencyBonus),
                        formatModifier(summary.newProficiencyBonus),
                    )
                    ChangeRow(stringResource(R.string.wizard_hp), summary.oldMaxHp.toString(), summary.newMaxHp.toString())
                    summary.maxPreparedChange?.let { (old, new) ->
                        ChangeRow(stringResource(R.string.levelup_summary_prepared), old.toString(), new.toString())
                    }
                    summary.subclassTitle?.let { ChangeRow(stringResource(R.string.levelup_summary_subclass), "—", it) }
                }
            }

            // Ячейки заклинаний.
            val circles = (summary.oldSlots.keys + summary.newSlots.keys).sorted()
            if (circles.isNotEmpty() && summary.oldSlots != summary.newSlots) {
                SectionTitle(stringResource(R.string.levelup_summary_slots))
                circles.forEach { circle ->
                    ChangeRow(
                        stringResource(R.string.levelup_slot_circle, circle),
                        (summary.oldSlots[circle] ?: 0).toString(),
                        (summary.newSlots[circle] ?: 0).toString(),
                    )
                }
            }

            if (summary.abilityChanges.isNotEmpty()) {
                SectionTitle(stringResource(R.string.wizard_step_abilities))
                AbilityChangeGrid(context.scoresBefore, context.scoresAfter)
            }

            if (summary.resources.isNotEmpty()) {
                SectionTitle(stringResource(R.string.wizard_summary_resources))
                summary.resources.forEach { change ->
                    ChangeRow(
                        change.name,
                        change.oldMaximum?.toString() ?: stringResource(
                            if (change.restored) R.string.levelup_resource_restored else R.string.levelup_resource_new,
                        ),
                        change.newMaximum?.toString() ?: stringResource(R.string.levelup_resource_removed),
                    )
                }
            }

            PillSection(
                stringResource(R.string.wizard_asi_feat),
                summary.newFeatIds.mapNotNull { id -> ui.feats.firstOrNull { it.id == id }?.name },
            )
            summary.newOptions.forEach { (title, names) -> PillSection(title, names) }
            if (summary.replaced.isNotEmpty()) {
                SectionTitle(stringResource(R.string.levelup_step_replacements))
                summary.replaced.forEach { (from, to) -> ChangeRow("", from, to) }
            }
            PillSection(
                stringResource(R.string.levelup_summary_spells_added),
                summary.addedSpellIds.mapNotNull { ui.spellsById[it]?.name },
            )
            PillSection(
                stringResource(R.string.levelup_summary_spells_removed),
                summary.removedSpellIds.mapNotNull { ui.spellsById[it]?.name },
            )
            NewAlwaysPreparedCard(ui, summary.newAlwaysPreparedIds)
            PillSection(
                stringResource(R.string.levelup_summary_prepared_added),
                summary.preparedAddedIds.mapNotNull { ui.spellsById[it]?.name },
            )
            PillSection(
                stringResource(R.string.levelup_summary_prepared_removed),
                summary.preparedRemovedIds.mapNotNull { ui.spellsById[it]?.name },
            )

            val proficiencies = summary.newProficiencies
            if (!proficiencies.isEmpty) {
                SectionTitle(stringResource(R.string.levelup_summary_proficiencies))
                GrantedProficiencies(
                    skills = proficiencies.skills,
                    rows = listOf(
                        stringResource(R.string.levelup_saves) to proficiencies.saves.sortedBy { it.ordinal }.map { stringResource(it.labelRes) },
                        stringResource(R.string.levelup_expertise) to proficiencies.expertise.sortedBy { it.ordinal }.map { stringResource(it.labelRes) },
                        stringResource(R.string.cform_armor) to proficiencies.armor.sortedBy { it.ordinal }.map { stringResource(it.labelRes) },
                        stringResource(R.string.cform_weapons) to proficiencies.weapons
                            .filter { it != com.example.spellbook.data.model.WeaponProficiency.OTHER }
                            .sortedBy { it.ordinal }.map { stringResource(it.labelRes) } + proficiencies.otherWeapons,
                        stringResource(R.string.cform_tools) to proficiencies.tools,
                        stringResource(R.string.cform_languages) to proficiencies.languages,
                    ),
                )
            }

            if (summary.features.isNotEmpty()) {
                SectionTitle(stringResource(R.string.levelup_new_features))
                summary.features.forEach { feature ->
                    ExpandableDescription(feature.feature.title, feature.feature.text, badge = levelUpSourceLabel(feature.source))
                }
            }

            // Что дописать в заметки и добавить в ресурсы: лишнее можно исключить, исключённое раньше — вернуть.
            ContentExclusionSection(context.proposal, context.excludedContent) { key ->
                ui.update { LevelUpEngine.toggleExcluded(context, key) }
            }
        }

        // Незавершённое со ссылками на шаги.
        if (issues.isNotEmpty()) {
            HorizontalDivider()
            SectionTitle(stringResource(R.string.wizard_summary_unfinished))
            issues.forEach { (step, stepIssues) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().clickable { if (step in ui.steps) ui.goTo(step) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(levelUpStepLabel(step, context), fontWeight = FontWeight.Bold)
                            stepIssues.forEach { Text(levelUpIssueText(it), style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
            if (canApply) HintRow(stringResource(R.string.levelup_summary_can_apply))
        }
        if (!canApply) HintRow(stringResource(R.string.levelup_summary_blocked))
    }
}

// endregion

