package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.SpellComponent
import com.example.spellbook.data.SpellFilters
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.SpellSort
import com.example.spellbook.data.castingTimeOptions
import com.example.spellbook.data.filterSortSearch
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar

/**
 * Переиспользуемый экран списка заклинаний с поиском/фильтрами/сортировкой.
 * Используется как для общей библиотеки, так и для набора конкретного персонажа:
 * различаются только заголовок, FAB, нижняя навигация и заглушка пустого списка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellListScreen(
    title: String,
    spells: List<Spell>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: SpellSort,
    onSortChange: (SpellSort) -> Unit,
    filters: SpellFilters,
    onFiltersChange: (SpellFilters) -> Unit,
    onSpellClick: (String) -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    extraActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    headerContent: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    emptyContent: @Composable (Modifier) -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    // Автофокус выставляется только при явном открытии поиска, а не при восстановлении сохранённого запроса.
    var autoFocusSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val visibleSpells = spells.filterSortSearch(query, filters, sort)

    // Позиция прокрутки: восстанавливается из сохранённых значений и сообщается наружу.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    // Верхний блок (headerContent) скрывается при прокрутке вниз и появляется при прокрутке вверх.
    var headerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            onScrollChanged(index, offset)
            // Небольшой порог, чтобы блок не дёргался от микродвижений.
            when {
                index == 0 && offset < 8 -> headerVisible = true
                index > prevIndex || offset > prevOffset + 6 -> headerVisible = false
                index < prevIndex || offset < prevOffset - 6 -> headerVisible = true
            }
            prevIndex = index
            prevOffset = offset
        }
    }


    Scaffold(
        topBar = {
            SpellListTopBar(
                title = title,
                navigationIcon = navigationIcon,
                hasSpells = spells.isNotEmpty(),
                query = query,
                onQueryChange = onQueryChange,
                searchActive = searchActive,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (searchActive) {
                        autoFocusSearch = true
                        showFilters = false
                    } else {
                        onQueryChange("")
                    }
                },
                autoFocusSearch = autoFocusSearch,
                onAutoFocusConsumed = { autoFocusSearch = false },
                onSearchFocusLost = { searchActive = false },
                filtersCount = filters.activeCount,
                onToggleFilters = { showFilters = !showFilters },
                sort = sort,
                onSortChange = onSortChange,
                showSortMenu = showSortMenu,
                onToggleSortMenu = {
                    showSortMenu = it
                    if (it) showFilters = false
                },
                extraActions = extraActions,
            )
        },
        bottomBar = {
            // Нижняя навигация скрывается/появляется синхронно с верхним блоком при прокрутке.
            AnimatedVisibility(
                visible = headerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                bottomBar()
            }
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        if (spells.isEmpty()) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                headerContent()
                emptyContent(Modifier)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column { headerContent() }
                }
                if (showFilters) {
                    FilterPanel(
                        filters = filters,
                        onFiltersChange = onFiltersChange,
                        onCollapse = { showFilters = false },
                    )
                    HorizontalDivider()
                }

                // Область со списком: тап вне блока фильтров его сворачивает.
                val dismissFiltersModifier = if (showFilters) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showFilters = false },
                    )
                } else {
                    Modifier
                }

                Box(modifier = Modifier.fillMaxSize().then(dismissFiltersModifier)) {
                    if (visibleSpells.isEmpty()) {
                        NoResults(onReset = { onQueryChange(""); onFiltersChange(SpellFilters()) })
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleSpells, key = { it.id }) { spell ->
                                SpellCard(spell = spell, onClick = { onSpellClick(spell.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Верхняя панель списка: заголовок либо поле поиска, а справа — иконки
 * поиск / фильтры / сортировка (видны всегда, когда есть заклинания).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellListTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    hasSpells: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    autoFocusSearch: Boolean,
    onAutoFocusConsumed: () -> Unit,
    onSearchFocusLost: () -> Unit,
    filtersCount: Int,
    onToggleFilters: () -> Unit,
    sort: SpellSort,
    onSortChange: (SpellSort) -> Unit,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    extraActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    DndTopBar(
        title = {
            if (searchActive) {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    autoFocus = autoFocusSearch,
                    onAutoFocusConsumed = onAutoFocusConsumed,
                    onFocusLost = onSearchFocusLost,
                )
            } else {
                Text(title)
            }
        },
        navigationIcon = navigationIcon,
        actions = {
            if (!hasSpells) return@DndTopBar
            extraActions()

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchActive) "Закрыть поиск" else "Поиск",
                )
            }
            IconButton(onClick = onToggleFilters) {
                BadgedBox(
                    badge = { if (filtersCount > 0) Badge { Text("$filtersCount") } },
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Фильтры")
                }
            }
            Box {
                IconButton(onClick = { onToggleSortMenu(true) }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортировка")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onToggleSortMenu(false) },
                ) {
                    SpellSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == sort) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onSortChange(option)
                                onToggleSortMenu(false)
                            },
                        )
                    }
                }
            }
        },
    )
}

/**
 * Поле поиска в шапке: прозрачный фон. Фокус запрашивается только при [autoFocus]
 * (явном открытии поиска). При потере фокуса (нажатие вне поля) вызывается [onFocusLost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    autoFocus: Boolean,
    onAutoFocusConsumed: () -> Unit,
    onFocusLost: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            onAutoFocusConsumed()
        }
    }

    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Поиск по названию", color = onColor.copy(alpha = 0.6f)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = onColor,
            unfocusedIndicatorColor = onColor.copy(alpha = 0.4f),
            cursorColor = onColor,
            focusedTextColor = onColor,
            unfocusedTextColor = onColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    wasFocused = false
                    onFocusLost()
                }
            },
    )
}

/** FAB с раскрывающимся меню добавления. Набор действий задаётся через [actions]. */
internal data class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
internal fun AddSpellFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<FabAction>,
) {
    Column(horizontalAlignment = Alignment.End) {
        if (expanded) {
            actions.forEach { action ->
                ExtendedFloatingActionButton(
                    text = { Text(action.label) },
                    icon = { Icon(action.icon, contentDescription = null) },
                    onClick = action.onClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Закрыть" else "Добавить",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    filters: SpellFilters,
    onFiltersChange: (SpellFilters) -> Unit,
    onCollapse: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Кнопка очистки показывается только при активных фильтрах и сворачивает блок.
            if (filters.isActive) {
                TextButton(onClick = {
                    onFiltersChange(SpellFilters())
                    onCollapse()
                }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Очистить фильтры")
                }
            }
            FilterChipGroup(
                title = "Уровень",
                options = SpellOptions.levels,
                selected = filters.levels,
                onToggle = { onFiltersChange(filters.copy(levels = filters.levels.toggle(it))) },
            )
            FilterChipGroup(
                title = "Класс",
                options = SpellOptions.classes,
                selected = filters.classes,
                onToggle = { onFiltersChange(filters.copy(classes = filters.classes.toggle(it))) },
            )
            FilterChipGroup(
                title = "Школа",
                options = SpellOptions.schools,
                selected = filters.schools,
                onToggle = { onFiltersChange(filters.copy(schools = filters.schools.toggle(it))) },
            )
            FilterChipGroup(
                title = "Компоненты",
                options = SpellComponent.options,
                selected = filters.components,
                onToggle = { onFiltersChange(filters.copy(components = filters.components.toggle(it))) },
            )
            FilterChipGroup(
                title = "Время накладывания",
                options = castingTimeOptions,
                selected = filters.activationTypes,
                onToggle = { onFiltersChange(filters.copy(activationTypes = filters.activationTypes.toggle(it))) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Дополнительно", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filters.concentration,
                        onClick = { onFiltersChange(filters.copy(concentration = !filters.concentration)) },
                        label = { Text("Концентрация") },
                    )
                    FilterChip(
                        selected = filters.ritual,
                        onClick = { onFiltersChange(filters.copy(ritual = !filters.ritual)) },
                        label = { Text("Ритуал") },
                    )
                }
            }
            // Отступ снизу, чтобы плавающая кнопка не перекрывала последние чипы при прокрутке.
            Spacer(Modifier.height(40.dp))
        }

        // Фиксированная кнопка сворачивания в правом нижнем углу видимой части блока.
        FilledTonalIconButton(
            onClick = onCollapse,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Скрыть фильтры")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterChipGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (code, label) ->
                FilterChip(
                    selected = code in selected,
                    onClick = { onToggle(code) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun NoResults(onReset: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Попробуйте изменить поиск или фильтры.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onReset) { Text("Сбросить") }
        }
    }
}

@Composable
internal fun EmptySpellList(
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Пока нет заклинаний",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Создайте своё первое заклинание или загрузите JSON LSS.")
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddClick) { Text("Создать") }
                Button(onClick = onImportClick) { Text("Загрузить JSON") }
            }
        }
    }
}

@Composable
private fun SpellCard(spell: Spell, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Бордовая полоса-акцент слева, как у карточек на dnd.su.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(spell.name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${levelLabel(spell.level)} · ${SpellOptions.labelFor(SpellOptions.schools, spell.school)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (spell.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = spell.description.lineSequence().firstOrNull().orEmpty(),
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Добавляет или убирает элемент из множества (для переключения чипов фильтра). */
private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun levelLabel(level: Int): String = SpellOptions.levels.firstOrNull { it.first == level }?.second ?: "$level круг"
