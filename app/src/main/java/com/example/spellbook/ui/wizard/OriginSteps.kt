package com.example.spellbook.ui.wizard

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.MAX_CLASS_LEVEL
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.model.proficiencyBonusFor
import com.example.spellbook.data.wizard.ChoiceKind
import com.example.spellbook.data.wizard.RaceSizes
import com.example.spellbook.data.wizard.ChoiceSpec
import com.example.spellbook.data.wizard.MarkupText
import com.example.spellbook.data.wizard.RaceRules
import com.example.spellbook.data.wizard.SpellFilter
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardStep
import com.example.spellbook.ui.screens.CharacterAvatar
import com.example.spellbook.ui.screens.groupRacesByBook
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.res.pluralStringResource

/** Общая разметка прокручиваемого шага. */
@Composable
fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

/** Незавершённости текущего шага сверху — подсказка, что осталось сделать. */
@Composable
fun StepIssues(ui: WizardUi, step: WizardStep) {
    val issues = WizardEngine.issues(step, ui.draft, ui.context)
    if (step.key in ui.controller.visitedSteps) IssuesBanner(issues)
    // Повторные владения показываются сразу: на шаге, где сделан выбор, и на шаге источника,
    // который принёс то же владение (выбрали предысторию — узнали о повторе тут же).
    val overlaps = com.example.spellbook.data.wizard.ProficiencyOverlaps.find(ui.draft, ui.context).filter { overlap ->
        WizardEngine.choiceStepKey(overlap.choice, ui.context) == step.key ||
            overlap.otherChoice?.let { WizardEngine.choiceStepKey(it, ui.context) } == step.key ||
            overlap.grantedBy?.source?.let { stepOfSource(it) }?.key == step.key
    }
    OverlapBanner(ui, overlaps)
}

/** Шаг, на котором выбирается источник владения. */
private fun stepOfSource(source: com.example.spellbook.data.wizard.ChoiceSource): WizardStep = when (source) {
    com.example.spellbook.data.wizard.ChoiceSource.RACE, com.example.spellbook.data.wizard.ChoiceSource.SUBRACE -> WizardStep.Race
    com.example.spellbook.data.wizard.ChoiceSource.CLASS, com.example.spellbook.data.wizard.ChoiceSource.SUBCLASS -> WizardStep.Class
    com.example.spellbook.data.wizard.ChoiceSource.BACKGROUND -> WizardStep.Background
    com.example.spellbook.data.wizard.ChoiceSource.FEAT -> WizardStep.Feats
}

/**
 * Встраивает выбор в шаг: заклинания — полем с окном выбора, остальное — [InlineChoice].
 */
@Composable
fun ChoiceBlock(ui: WizardUi, choice: ChoiceSpec) {
    val selected = ui.selection(choice.id)
    when (choice.kind) {
        ChoiceKind.CANTRIP, ChoiceKind.SPELL -> {
            val filter = choice.spellFilter ?: SpellFilter(maxLevel = if (choice.kind == ChoiceKind.CANTRIP) 0 else 9)
            val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
            SpellChoiceField(
                title = choice.title,
                hint = choice.hint,
                count = choice.count,
                selected = selected,
                candidates = remember(ui.spells, effective) { filterSpells(ui.spells, effective) },
                onChange = { ui.select(choice.id, it) },
                // Текст целиком и свёрнутым, а не обрезанной подсказкой.
                sourceText = choice.sourceText,
            )
        }
        else -> {
            val proficient = WizardEngine.proficientSkills(ui.draft, ui.context)
            InlineChoice(
                choice = choice,
                selected = selected,
                onChange = { ui.select(choice.id, it) },
                browse = { _ ->
                    OptionBrowserList(
                        ui = ui,
                        choice = choice,
                        selected = ui.selection(choice.id),
                        onChange = { ui.select(choice.id, it) },
                        header = { Row { CountBadge(ui.selection(choice.id).size, choice.count) } },
                    )
                },
                // Известные языки без текущего выбора — иначе сделанный выбор исчезал бы из подсказок.
                knownLanguages = WizardEngine.knownLanguages(ui.draft, ui.context) - selected.map { it.lowercase() }.toSet(),
                // Для навыков — уже полученные из других источников (нельзя взять дважды),
                // для компетентности — все владения: именно из них она выбирается.
                ownedSkills = if (choice.kind == ChoiceKind.EXPERTISE) {
                    proficient
                } else {
                    proficient - selected.mapNotNull {
                        runCatching { com.example.spellbook.data.model.SkillType.valueOf(it) }.getOrNull()
                    }.toSet()
                },
            )
        }
    }
}

private const val HINT_MAX = 200

// region Основное

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BasicsStep(ui: WizardUi) {
    val context = LocalContext.current
    val draft = ui.draft
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ui.update { it.copy(imageUri = uri.toString()) }
        }
    }
    StepColumn {
        StepHeader(stringResource(R.string.wizard_basics_title), stringResource(R.string.wizard_basics_subtitle))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.clickable {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                CharacterAvatar(Character(name = draft.name, imageUri = draft.imageUri), size = 112)
            }
            TextButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text(stringResource(if (draft.imageUri == null) R.string.cform_pick_photo else R.string.cform_change_photo))
            }
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> ui.update { it.copy(name = name) } },
            label = { Text(stringResource(R.string.cform_character_name)) },
            singleLine = true,
            isError = draft.name.isBlank() && WizardStep.Basics.key in ui.controller.visitedSteps && ui.controller.stepKey != WizardStep.Basics.key,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.wizard_level))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalIconButton(
                onClick = { ui.update { it.copy(level = (it.level - 1).coerceAtLeast(1)) } },
                enabled = draft.level > 1,
            ) { Icon(Icons.Default.Remove, stringResource(R.string.wizard_level_down)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    draft.level.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.wizard_proficiency, formatModifier(proficiencyBonusFor(draft.level))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { ui.update { it.copy(level = (it.level + 1).coerceAtMost(MAX_CLASS_LEVEL)) } },
                enabled = draft.level < MAX_CLASS_LEVEL,
            ) { Icon(Icons.Default.Add, stringResource(R.string.wizard_level_up)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1, 3, 5, 10, 15, 20).forEach { level ->
                androidx.compose.material3.FilterChip(
                    selected = draft.level == level,
                    onClick = { ui.update { it.copy(level = level) } },
                    label = { Text(level.toString()) },
                )
            }
        }
        HintRow(stringResource(R.string.wizard_level_hint))

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
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// endregion

// region Раса

/** Поиск по списку на шаге выбора. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) } }
        } else null,
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Шаг расы. Пока раса не выбрана — список с поиском; после выбора — карточка расы,
 * разновидности и выборы, а список сворачивается кнопкой «Сменить».
 */
@Composable
fun RaceStep(ui: WizardUi) {
    val race = ui.context.race
    var browsing by rememberSaveable { mutableStateOf(race == null) }
    if (browsing || race == null) {
        RaceList(ui) { id ->
            ui.update { WizardEngine.selectRace(it, id) }
            browsing = false
        }
    } else {
        RaceDetails(ui, race, onChange = { browsing = true })
    }
}

/**
 * Список рас в том же виде, что и в библиотеке: разделы каталога в постоянном порядке
 * (`groupRacesByBook`), каждый сворачивается, расы внутри — по алфавиту.
 */
@Composable
private fun RaceList(ui: WizardUi, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val collapsed = rememberCollapsedGroups()
    val otherTitle = stringResource(R.string.library_races_other_book)
    val rulesById = remember(ui.library.races) { ui.library.races.associate { it.id to ui.controller.cache.race(it) } }
    val allowed = ui.library.races.filter { ui.draft.showUnofficial || rulesById[it.id]?.extended != true }
    val groups = remember(allowed, query, otherTitle) {
        groupRacesByBook(allowed.filter { it.name.contains(query.trim(), ignoreCase = true) }, ui.library.races, otherTitle)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StepHeader(stringResource(R.string.wizard_race_title), stringResource(R.string.wizard_race_subtitle)) }
        item { SearchField(query, { query = it }, stringResource(R.string.wizard_search_races)) }
        if (ui.library.races.isEmpty()) item { HintRow(stringResource(R.string.wizard_library_empty)) }
        groups.forEach { group ->
            val isCollapsed = group.title in collapsed.value && query.isBlank()
            item(key = "h:${group.title}") {
                GroupHeader(
                    title = group.title,
                    count = pluralStringResource(R.plurals.race_count, group.races.size, group.races.size),
                    collapsed = isCollapsed,
                    onToggle = { collapsed.value = collapsed.value.toggle(group.title) },
                )
            }
            if (!isCollapsed) {
                items(group.races, key = { it.id }) { race ->
                    val rules = rulesById[race.id] ?: return@items
                    RaceCard(rules, selected = race.id == ui.draft.raceId, onClick = { onPick(race.id) })
                }
            }
        }
    }
}

/** Свёрнутые группы переживают поворот экрана, как в библиотеке. */
@Composable
fun rememberCollapsedGroups(): androidx.compose.runtime.MutableState<Set<String>> =
    rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { it.value.toList() },
            restore = { mutableStateOf(it.toSet()) },
        ),
    ) { mutableStateOf(emptySet()) }

fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

/** Заголовок сворачиваемой группы — такой же, как у блоков библиотеки. */
@Composable
fun GroupHeader(title: String, count: String, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceCard(rules: RaceRules, selected: Boolean, onClick: () -> Unit) {
    SelectableCard(selected = selected, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(rules.race.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val bonus = bonusSummary(rules.abilityBonus.takeIf { it.parsed })
                if (bonus.isNotBlank()) InfoPill(bonus, highlighted = true)
                rules.grants.speed?.let { InfoPill(stringResource(R.string.wizard_speed, it)) }
                if (rules.subraces.isNotEmpty()) InfoPill(stringResource(R.string.wizard_subraces_count, rules.subraces.size))
            }
            // Книга — подписью под названием: раздел каталога и книга часто расходятся.
            if (rules.race.book.isNotBlank() && rules.race.book != rules.race.category) {
                Text(rules.race.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rules.summary.isNotBlank()) {
                Text(
                    rules.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceDetails(ui: WizardUi, race: RaceRules, onChange: () -> Unit) {
    val subraces = race.subraces.filter { ui.draft.showUnofficial || !it.extended || it.id == ui.draft.subraceId }
    StepColumn {
        ChosenHeader(
            label = stringResource(R.string.wizard_step_race),
            title = race.race.name,
            subtitle = race.race.book,
            onChange = onChange,
        )
        StepIssues(ui, WizardStep.Race)
        val bonus = bonusSummary(WizardEngine.bonusRule(ui.context))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (bonus.isNotBlank()) InfoPill(bonus, highlighted = true)
            (ui.context.subrace?.grants?.speed ?: race.grants.speed)?.let { InfoPill(stringResource(R.string.wizard_speed, it)) }
            race.grants.size.takeIf { it.isNotBlank() }?.let { InfoPill(it) }
            (race.grants.languages + ui.context.subrace?.grants?.languages.orEmpty()).distinct().forEach { InfoPill(it) }
        }
        if (bonus.isNotBlank()) HintRow(stringResource(R.string.wizard_bonus_later))

        // Раса с выбором размера («Маленький или Средний»): размер выбирается здесь, он уходит в лист.
        val sizeOptions = RaceSizes.options(ui.context)
        if (sizeOptions.size > 1) {
            SectionTitle(stringResource(R.string.wizard_size_title))
            HintRow(stringResource(R.string.wizard_size_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizeOptions.forEach { option ->
                    FilterChip(
                        selected = ui.draft.size == option.code,
                        onClick = { ui.update { it.copy(size = option.code) } },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        }

        // Владения расы и разновидности без выбора (например, «Обострённые чувства» эльфа).
        val grants = listOfNotNull(race.grants, ui.context.subrace?.grants)
        GrantedProficiencies(
            skills = grants.flatMap { it.skills }.toSet(),
            rows = listOf(
                stringResource(R.string.cform_armor) to grants.flatMap { it.armor }.distinct().sortedBy { it.ordinal }
                    .map { stringResource(it.labelRes) },
                stringResource(R.string.cform_weapons) to grants.map { it.weapons }.filter { it.isNotBlank() },
                stringResource(R.string.cform_tools) to grants.map { it.tools }.filter { it.isNotBlank() },
            ),
        )

        if (subraces.isNotEmpty()) {
            SectionTitle(stringResource(if (race.subraceRequired) R.string.wizard_subrace_required else R.string.wizard_subrace_optional))
            subraces.forEach { sub ->
                val selected = sub.id == ui.draft.subraceId
                SelectableCard(
                    selected = selected,
                    onClick = {
                        ui.update { WizardEngine.selectSubrace(it, if (selected && !race.subraceRequired) null else sub.id) }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sub.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (sub.extended) InfoPill(stringResource(R.string.wizard_unofficial_badge), highlighted = true)
                        }
                        val subBonus = bonusSummary(sub.abilityBonus)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (subBonus.isNotBlank()) InfoPill(subBonus, highlighted = true)
                            if (sub.grants.featSlots > 0) InfoPill(stringResource(R.string.wizard_grants_feat))
                        }
                        if (sub.traits.isNotEmpty()) {
                            Text(
                                sub.traits.joinToString(" · ") { it.title },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Выборы расы и разновидности.
        WizardEngine.choicesOn(WizardStep.Race, ui.context).forEach { choice -> ChoiceBlock(ui, choice) }

        val traits = com.example.spellbook.data.wizard.FeatureRules.noteFeatures(
            race.traits + ui.context.subrace?.traits.orEmpty(),
            ui.draft.level,
            showOptional = true,
        )
        if (traits.isNotEmpty()) {
            SectionTitle(stringResource(R.string.wizard_traits))
            HintRow(stringResource(R.string.wizard_traits_hint))
            traits.forEach { trait -> ExpandableDescription(trait.title, trait.text) }
        }
        ExpandableDescription(stringResource(R.string.wizard_full_description), race.race.description)
    }
}

/** Шапка выбранного варианта с кнопкой смены. */
@Composable
fun ChosenHeader(label: String, title: String, subtitle: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onChange) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.wizard_change), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

// endregion

/** Отдельный шаг одного выбора: воззвания, боевые приёмы, компетентность. */
@Composable
fun ChoiceStep(ui: WizardUi, choiceId: String) {
    val choice = ui.context.choices.firstOrNull { it.id == choiceId } ?: return
    val selected = ui.selection(choice.id)
    val browser = rememberOptionBrowserState(choice.id)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StepHeader(choice.title, choice.hint.ifBlank { null })
                }
                CountBadge(selected.count { it.isNotBlank() }, choice.count)
            }
        }
        if (choice.sourceText.isNotBlank()) {
            item { ExpandableDescription(stringResource(R.string.wizard_rules_text), choice.sourceText) }
        }
        // Воззвания, приёмы, инфузии и т. п. — с поиском, фильтрами и сортировкой.
        if (choice.kind == ChoiceKind.OPTION || choice.kind == ChoiceKind.TOOL) {
            optionBrowserItems(ui, choice, selected, browser) { ui.select(choice.id, it) }
        } else {
            item { ChoiceBlock(ui, choice) }
        }
    }
}

@Composable
fun EmptyCentered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Карточка с раскрытием подробностей при выборе — для архетипа и предыстории. */
@Composable
fun ExpandOnSelect(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}
