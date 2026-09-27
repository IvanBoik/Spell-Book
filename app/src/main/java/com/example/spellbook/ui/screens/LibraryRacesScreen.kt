package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.RaceFilters
import com.example.spellbook.data.availableBooks
import com.example.spellbook.data.filterSearch
import com.example.spellbook.data.model.Race
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/** Расы одной книги-источника. */
internal data class RaceBookGroup(val title: String, val races: List<Race>)

/** Максимальная высота блока фильтров: дальше он прокручивается. */
private val RACE_FILTER_PANEL_MAX_HEIGHT = 240.dp

/** Свёрнутые книги хранятся списком названий: множество в Bundle не кладётся. */
private val collapsedRaceBooksSaver = listSaver<MutableState<Set<String>>, String>(
    save = { state -> state.value.toList() },
    restore = { saved -> mutableStateOf(saved.toSet()) },
)

/**
 * Раздел библиотеки с расами, сгруппированными по разделам каталога.
 *
 * Устроен как список черт: блоки идут в постоянном порядке, каждый сворачивается,
 * а нажатие на расу открывает отдельный экран. Фильтр один — по книге-источнику:
 * числовых признаков, как у заклинаний, у расы нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRacesScreen(
    races: List<Race>,
    query: String,
    onQueryChange: (String) -> Unit,
    filters: RaceFilters,
    onFiltersChange: (RaceFilters) -> Unit,
    onRaceClick: (String) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    // Автофокус — только при явном открытии поиска, а не при возврате с сохранённым запросом.
    var autoFocusSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    // Свёрнутые книги переживают поворот экрана: иначе список каждый раз раскрывался бы заново.
    val collapsedBooks = rememberSaveable(saver = collapsedRaceBooksSaver) { mutableStateOf(emptySet()) }

    val otherBookTitle = stringResource(R.string.library_races_other_book)
    val books = remember(races, query, filters, otherBookTitle) {
        groupRacesByBook(races.filterSearch(query, filters), races, otherBookTitle)
    }
    // Список книг строится по всей библиотеке, чтобы варианты не исчезали при отборе.
    val bookOptions = remember(races) { races.availableBooks() }

    // Позиция прокрутки восстанавливается при возврате с экрана расы.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollChanged(index, offset) }
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = {
                    if (searchActive) {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            autoFocus = autoFocusSearch,
                            onAutoFocusConsumed = { autoFocusSearch = false },
                            onFocusLost = { searchActive = false },
                            placeholderRes = R.string.library_races_search,
                        )
                    } else {
                        Text(stringResource(R.string.library_races_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (races.isEmpty()) return@DndTopBar
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (searchActive) {
                            autoFocusSearch = true
                            showFilters = false
                        } else {
                            onQueryChange("")
                        }
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (searchActive) R.string.search_close else R.string.search_open,
                            ),
                        )
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        BadgedBox(
                            badge = {
                                if (filters.activeCount > 0) Badge { Text("${filters.activeCount}") }
                            },
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_title),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
        // Поиск не должен уходить под клавиатуру.
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showFilters) {
                RaceFilterPanel(
                    filters = filters,
                    bookOptions = bookOptions,
                    onFiltersChange = onFiltersChange,
                    onCollapse = { showFilters = false },
                )
                HorizontalDivider()
            }

            // Тап вне блока фильтров сворачивает его — как в списках заклинаний и черт.
            val dismissFiltersModifier = if (showFilters) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showFilters = false },
                )
            } else {
                Modifier
            }

            Box(Modifier.fillMaxSize().then(dismissFiltersModifier)) {
                if (books.isEmpty()) {
                    EmptyRaces(
                        libraryEmpty = races.isEmpty(),
                        onReset = {
                            onQueryChange("")
                            onFiltersChange(RaceFilters())
                        },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(books.size, key = { books[it].title }) { index ->
                            val book = books[index]
                            RaceBookSection(
                                book = book,
                                collapsed = book.title in collapsedBooks.value,
                                onToggle = {
                                    collapsedBooks.value = collapsedBooks.value.toMutableSet().apply {
                                        if (!add(book.title)) remove(book.title)
                                    }
                                },
                                onRaceClick = onRaceClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Блок фильтров рас: отбор по книге-источнику. */
@Composable
private fun RaceFilterPanel(
    filters: RaceFilters,
    bookOptions: List<String>,
    onFiltersChange: (RaceFilters) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Книг много, поэтому панель ограничена по высоте и прокручивается.
            .heightIn(max = RACE_FILTER_PANEL_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filters.isActive) {
            TextButton(onClick = {
                onFiltersChange(RaceFilters())
                onCollapse()
            }) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.filter_clear))
            }
        }
        FilterChipGroup(
            title = stringResource(R.string.library_races_filter_book),
            options = bookOptions.map { it to it },
            selected = filters.books,
            onToggle = { book ->
                val books = filters.books.toMutableSet().apply {
                    if (!add(book)) remove(book)
                }
                onFiltersChange(filters.copy(books = books))
            },
        )
    }
}

/** Заглушка: библиотека пуста либо нет совпадений с поиском. */
@Composable
private fun EmptyRaces(libraryEmpty: Boolean, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (libraryEmpty) R.string.library_races_empty else R.string.library_races_no_results,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!libraryEmpty) {
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
            }
        }
    }
}

/** Блок одной книги: заголовок со счётчиком и список её рас. */
@Composable
private fun RaceBookSection(
    book: RaceBookGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onRaceClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pluralStringResource(R.plurals.race_count, book.races.size, book.races.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = stringResource(
                    if (collapsed) R.string.action_expand else R.string.action_collapse,
                ),
            )
        }

        AnimatedVisibility(visible = !collapsed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                book.races.forEach { race ->
                    LibraryRaceCard(race = race, onClick = { onRaceClick(race.id) })
                }
            }
        }
    }
}

/** Карточка расы: название и первая строка описания. */
@Composable
private fun LibraryRaceCard(race: Race, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                race.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val preview = race.description.lineSequence()
                .map { it.trim() }
                // Заголовки в превью не нужны: нужна первая содержательная строка.
                .firstOrNull { it.isNotEmpty() && !it.startsWith("## ") }
                .orEmpty()
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}



/**
 * Порядок блоков библиотеки — такой же, как разделы каталога на dnd.su.
 *
 * Задан явно, а не вычисляется по числу рас: сначала идут основные расы,
 * затем дополнения в привычном по сайту порядке.
 */
private val RACE_CATEGORY_ORDER = listOf(
    "Основные расы",
    "Происхождения",
    "Mordenkainen Presents: Monsters of the Multiverse",
    "Unearthed Arcana",
    "Plane Shift: Amonkhet",
    "Plane Shift: Innistrad",
    "Midgard Heroes Handbook",
)

/**
 * Группирует отобранные расы по разделам каталога.
 *
 * Порядок блоков фиксирован ([RACE_CATEGORY_ORDER]) и не зависит ни от поиска,
 * ни от числа рас: блоки не должны прыгать при фильтрации. Разделы без совпадений
 * не показываются. Неизвестные разделы (например, появившиеся на сайте позже)
 * уходят в конец по алфавиту.
 *
 * @param fallbackTitle заголовок для рас без раздела — обычно добавленных вручную.
 */
internal fun groupRacesByBook(
    visibleRaces: List<Race>,
    allRaces: List<Race>,
    fallbackTitle: String,
): List<RaceBookGroup> {
    // Разделы вне заданного порядка ставим после известных, но стабильно.
    val extraCategories = allRaces
        .map { it.category.trim().ifBlank { fallbackTitle } }
        .distinct()
        .filterNot { it in RACE_CATEGORY_ORDER }
        .sorted()
    val order = (RACE_CATEGORY_ORDER + extraCategories)
        .mapIndexed { index, title -> title to index }
        .toMap()

    return visibleRaces
        .groupBy { it.category.trim().ifBlank { fallbackTitle } }
        .map { (title, items) -> RaceBookGroup(title, items.sortedBy { it.name.lowercase() }) }
        .sortedWith(
            compareBy<RaceBookGroup> { order[it.title] ?: Int.MAX_VALUE }.thenBy { it.title },
        )
}

