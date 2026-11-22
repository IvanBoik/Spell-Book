package com.example.spellbook.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.wizard.ChoiceOption
import com.example.spellbook.data.wizard.ChoiceSpec
import com.example.spellbook.data.wizard.MarkupText
import com.example.spellbook.data.wizard.OptionRequirements
import com.example.spellbook.data.wizard.ToolCatalog
import com.example.spellbook.data.wizard.ToolCategory

/** Порядок вариантов в браузере. */
enum class OptionSort { BOOK, NAME, LEVEL }

/** Быстрые фильтры браузера вариантов. */
private enum class OptionFilter { SELECTED, AVAILABLE, NO_REQUIREMENTS }

/**
 * Состояние браузера вариантов: поиск, фильтры, сортировка и группа (вид инструмента).
 * Сохраняется при повороте экрана отдельно для каждого выбора.
 */
class OptionBrowserState(
    query: String,
    sort: OptionSort,
    filters: Set<String>,
    group: String?,
) {
    var query by mutableStateOf(query)
    var sort by mutableStateOf(sort)
    var filters by mutableStateOf(filters)
    var group by mutableStateOf(group)
}

private val OptionBrowserStateSaver = listSaver<OptionBrowserState, String>(
    save = { listOf(it.query, it.sort.name, it.group.orEmpty()) + it.filters },
    restore = { saved ->
        OptionBrowserState(
            query = saved[0],
            sort = OptionSort.valueOf(saved[1]),
            filters = saved.drop(SAVED_FIXED_FIELDS).toSet(),
            group = saved[2].ifEmpty { null },
        )
    },
)

private const val SAVED_FIXED_FIELDS = 3

@Composable
fun rememberOptionBrowserState(key: String): OptionBrowserState =
    rememberSaveable(key, saver = OptionBrowserStateSaver) {
        OptionBrowserState(query = "", sort = OptionSort.BOOK, filters = emptySet(), group = null)
    }

/** Вариант вместе с причинами недоступности (пусто — доступен). */
private data class BrowsedOption(val option: ChoiceOption, val unmet: List<OptionRequirements.Unmet>, val index: Int)

/**
 * Список вариантов выбора с поиском, фильтрами и сортировкой: воззвания, боевые приёмы,
 * инфузии, метамагия, стили, инструменты.
 *
 * Варианты, требования которых не выполнены (уровень, предмет договора, заговор), видны,
 * но недоступны с объяснением; фильтр «Доступные» убирает их из списка.
 * Панель управления показывается, только когда вариантов достаточно много.
 */
fun LazyListScope.optionBrowserItems(
    ui: WizardUi,
    choice: ChoiceSpec,
    selected: List<String>,
    state: OptionBrowserState,
    onChange: (List<String>) -> Unit,
) = optionBrowserItems(
    choice = choice,
    selected = selected,
    state = state,
    unmetOf = { option -> OptionRequirements.unmet(option, ui.draft, ui.context, ui.spells) },
    onChange = onChange,
)

/**
 * Браузер вариантов с внешней проверкой требований: так его использует и окно повышения
 * уровня, где требования считаются по персонажу, а не по черновику мастера создания.
 */
fun LazyListScope.optionBrowserItems(
    choice: ChoiceSpec,
    selected: List<String>,
    state: OptionBrowserState,
    unmetOf: (ChoiceOption) -> List<OptionRequirements.Unmet>,
    onChange: (List<String>) -> Unit,
) {
    val all = choice.options.mapIndexed { index, option ->
        BrowsedOption(option, unmetOf(option), index)
    }
    val toolGroups = choice.options.mapNotNull { ToolCatalog.byId(it.id)?.category }.distinct()
    val showControls = choice.options.size > CONTROLS_THRESHOLD
    val hasRequirements = choice.options.any { it.requirement.isNotBlank() }

    if (showControls) {
        item(key = "browser.controls") {
            BrowserControls(
                state = state,
                hasRequirements = hasRequirements,
                hasLevels = choice.options.any { it.requiredLevel != null },
                toolGroups = toolGroups.takeIf { it.size > 1 }.orEmpty(),
            )
        }
    }

    val query = state.query.trim()
    val visible = all
        .filter { item ->
            val option = item.option
            (query.isEmpty() || option.title.contains(query, ignoreCase = true) ||
                MarkupText.plain(option.text).contains(query, ignoreCase = true)) &&
                (OptionFilter.SELECTED.name !in state.filters || option.id in selected) &&
                (OptionFilter.AVAILABLE.name !in state.filters || item.unmet.isEmpty() || option.id in selected) &&
                (OptionFilter.NO_REQUIREMENTS.name !in state.filters || option.requirement.isBlank()) &&
                (state.group == null || ToolCatalog.byId(option.id)?.category?.name == state.group)
        }
        .let { list ->
            when (state.sort) {
                OptionSort.BOOK -> list.sortedBy { it.index }
                OptionSort.NAME -> list.sortedBy { it.option.title.lowercase() }
                OptionSort.LEVEL -> list.sortedWith(compareBy({ it.option.requiredLevel ?: 0 }, { it.option.title.lowercase() }))
            }
        }

    if (showControls) {
        item(key = "browser.count") {
            Text(
                stringResource(R.string.wizard_browser_shown, visible.size, all.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Варианты, развёрнутые из одного (копии «Копии магического предмета»), идут под одним
    // заголовком с общим правилом; в карточке — только своя часть названия (предмет).
    val seenGroups = mutableSetOf<String>()
    val groupStarts = visible.mapIndexedNotNull { index, item ->
        val group = item.option.group
        index.takeIf { group.isNotEmpty() && seenGroups.add(group) }
    }.toSet()
    visible.forEachIndexed { index, item ->
        val option = item.option
        if (index in groupStarts) {
            item(key = "group." + option.group) {
                val members = all.filter { it.option.group == option.group }
                OptionGroupHeader(
                    title = option.group,
                    text = option.groupText,
                    taken = members.count { it.option.id in selected },
                )
            }
        }
        item(key = "opt." + option.id) {
            val isSelected = option.id in selected
            val available = item.unmet.isEmpty()
            OptionCard(
                option = if (option.group.isNotEmpty()) {
                    option.copy(title = option.title.removePrefix(option.group).trimStart(':', ' '))
                } else {
                    option
                },
                selected = isSelected,
                multiSelect = choice.count > 1,
                // Недоступный вариант можно только снять, если он был выбран раньше.
                enabled = isSelected || (available && (selected.size < choice.count || choice.count == 1)),
                onClick = { onChange(toggle(selected, option.id, choice.count)) },
                unavailableReason = if (available) null else unmetText(item.unmet),
                indent = option.group.isNotEmpty(),
            )
        }
    }
    if (visible.isEmpty()) {
        item(key = "browser.empty") {
            HintRow(stringResource(if (all.isEmpty()) R.string.wizard_options_missing else R.string.wizard_browser_nothing))
        }
    }
}

/**
 * Заголовок группы вариантов, развёрнутых из одного: «Копия магического предмета» с правилом
 * и числом уже взятых копий.
 */
@Composable
private fun OptionGroupHeader(title: String, text: String, taken: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (taken > 0) InfoPill(stringResource(R.string.wizard_group_taken, taken), highlighted = true)
        }
        HintRow(stringResource(R.string.wizard_group_hint))
        if (text.isNotBlank()) ExpandableDescription(stringResource(R.string.wizard_rules_text), text)
    }
}

/** Сколько вариантов нужно, чтобы показывать поиск, фильтры и сортировку. */
private const val CONTROLS_THRESHOLD = 6

@Composable
private fun BrowserControls(
    state: OptionBrowserState,
    hasRequirements: Boolean,
    hasLevels: Boolean,
    toolGroups: List<ToolCategory>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { IconButton(onClick = { state.query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) } }
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.wizard_browser_search)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            SortMenu(state, hasLevels)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val filters = buildList {
                add(OptionFilter.SELECTED)
                if (hasRequirements) {
                    add(OptionFilter.AVAILABLE)
                    add(OptionFilter.NO_REQUIREMENTS)
                }
            }
            items(filters) { filter ->
                val active = filter.name in state.filters
                FilterChip(
                    selected = active,
                    onClick = { state.filters = if (active) state.filters - filter.name else state.filters + filter.name },
                    label = { Text(stringResource(filter.labelRes)) },
                    leadingIcon = if (active) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
            items(toolGroups) { group ->
                val active = state.group == group.name
                FilterChip(
                    selected = active,
                    onClick = { state.group = if (active) null else group.name },
                    label = { Text(stringResource(toolCategoryLabel(group))) },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(state: OptionBrowserState, hasLevels: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.wizard_browser_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OptionSort.entries.filter { it != OptionSort.LEVEL || hasLevels }.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    onClick = {
                        state.sort = sort
                        open = false
                    },
                    leadingIcon = if (state.sort == sort) ({ Icon(Icons.Default.Check, null) }) else null,
                )
            }
        }
    }
}

private val OptionSort.labelRes: Int
    get() = when (this) {
        OptionSort.BOOK -> R.string.wizard_sort_book
        OptionSort.NAME -> R.string.wizard_sort_name
        OptionSort.LEVEL -> R.string.wizard_sort_level
    }

private val OptionFilter.labelRes: Int
    get() = when (this) {
        OptionFilter.SELECTED -> R.string.wizard_filter_selected
        OptionFilter.AVAILABLE -> R.string.wizard_filter_available
        OptionFilter.NO_REQUIREMENTS -> R.string.wizard_filter_no_requirements
    }

fun toolCategoryLabel(category: ToolCategory): Int = when (category) {
    ToolCategory.ARTISAN -> R.string.wizard_tools_artisan
    ToolCategory.MUSICAL -> R.string.wizard_tools_musical
    ToolCategory.GAMING -> R.string.wizard_tools_gaming
    ToolCategory.OTHER -> R.string.wizard_tools_other
}

/** Текст невыполненных требований: «Нужен 5-й уровень · Нужно: Договор клинка». */
@Composable
fun unmetText(unmet: List<OptionRequirements.Unmet>): String =
    unmet.map { item ->
        when (item) {
            is OptionRequirements.Unmet.Level -> stringResource(R.string.wizard_unmet_level, item.level)
            is OptionRequirements.Unmet.Feature -> stringResource(R.string.wizard_unmet_feature, item.name)
            is OptionRequirements.Unmet.Spell -> stringResource(R.string.wizard_unmet_spell, item.name)
        }
    }.joinToString(" · ")

/** Отдельный экран-браузер вариантов во всю высоту (для инструментов и длинных списков). */
@Composable
fun OptionBrowserList(
    ui: WizardUi,
    choice: ChoiceSpec,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    header: @Composable () -> Unit = {},
) = OptionBrowserList(
    choice = choice,
    selected = selected,
    unmetOf = { option -> OptionRequirements.unmet(option, ui.draft, ui.context, ui.spells) },
    onChange = onChange,
    header = header,
)

/** Экран-браузер вариантов с внешней проверкой требований (см. [optionBrowserItems]). */
@Composable
fun OptionBrowserList(
    choice: ChoiceSpec,
    selected: List<String>,
    unmetOf: (ChoiceOption) -> List<OptionRequirements.Unmet>,
    onChange: (List<String>) -> Unit,
    header: @Composable () -> Unit = {},
) {
    val state = rememberOptionBrowserState(choice.id)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "browser.header") { Box(Modifier.fillMaxWidth()) { header() } }
        optionBrowserItems(choice, selected, state, unmetOf, onChange)
    }
}
