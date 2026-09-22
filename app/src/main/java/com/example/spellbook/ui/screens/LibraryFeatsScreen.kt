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
import com.example.spellbook.data.FeatFilters
import com.example.spellbook.data.filterSearch
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/** Черты одной книги-источника. */
internal data class FeatBook(val title: String, val feats: List<Feat>)

/** Максимальная высота блока фильтров: дальше он прокручивается. */
private val FILTER_PANEL_MAX_HEIGHT = 240.dp

/** Свёрнутые книги хранятся списком названий: множество в Bundle не кладётся. */
private val collapsedBooksSaver = listSaver<MutableState<Set<String>>, String>(
    save = { state -> state.value.toList() },
    restore = { saved -> mutableStateOf(saved.toSet()) },
)

/**
 * Раздел библиотеки с чертами, сгруппированными по книгам-источникам.
 *
 * Книги идут в порядке убывания числа черт: самые объёмные (Player's Handbook и
 * подобные) оказываются сверху, а единичные дополнения — внизу. Каждый блок
 * сворачивается, чтобы длинный список можно было пролистать до нужной книги.
 *
 * Поиск и фильтры устроены как у заклинаний, но сортировки нет: порядок задаётся
 * группировкой по книгам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFeatsScreen(
    feats: List<Feat>,
    query: String,
    onQueryChange: (String) -> Unit,
    filters: FeatFilters,
    onFiltersChange: (FeatFilters) -> Unit,
    onFeatClick: (String) -> Unit,
    onBack: () -> Unit,
    title: String = stringResource(R.string.library_feats_title),
    /** Заглушка при пустом списке: у персонажа и в библиотеке причины разные. */
    emptyTextRes: Int = R.string.feats_library_empty,
    /** Панель разделов персонажа; в библиотеке не нужна. */
    sectionsBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    // Автофокус — только при явном открытии поиска, а не при возврате с сохранённым запросом.
    var autoFocusSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    // Свёрнутые книги переживают поворот экрана: иначе список каждый раз раскрывался бы заново.
    val collapsedBooks = rememberSaveable(saver = collapsedBooksSaver) { mutableStateOf(emptySet()) }

    val otherBookTitle = stringResource(R.string.library_feats_other_book)
    val books = remember(feats, query, filters, otherBookTitle) {
        groupByBook(feats.filterSearch(query, filters), feats, otherBookTitle)
    }

    // Позиция прокрутки восстанавливается при возврате с экрана черты.
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
                        )
                    } else {
                        Text(title)
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
                    if (feats.isEmpty()) return@DndTopBar
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
        floatingActionButton = floatingActionButton,
        // Поиск не должен уходить под клавиатуру.
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Панель разделов вне списка: отступы одинаковы на всех экранах персонажа.
            sectionsBar()
            if (showFilters) {
                FeatFilterPanel(
                    filters = filters,
                    onFiltersChange = onFiltersChange,
                    onCollapse = { showFilters = false },
                )
                HorizontalDivider()
            }

            // Тап вне блока фильтров сворачивает его — как в списке заклинаний.
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
                    EmptyFeats(
                        libraryEmpty = feats.isEmpty(),
                        emptyTextRes = emptyTextRes,
                        onReset = { onQueryChange(""); onFiltersChange(FeatFilters()) },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        // Нижний отступ — чтобы плавающая кнопка не перекрывала последнюю черту.
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(books.size, key = { books[it].title }) { index ->
                            val book = books[index]
                            BookSection(
                                book = book,
                                collapsed = book.title in collapsedBooks.value,
                                onToggle = {
                                    collapsedBooks.value = collapsedBooks.value.toMutableSet().apply {
                                        if (!add(book.title)) remove(book.title)
                                    }
                                },
                                onFeatClick = onFeatClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Заглушка: список пуст либо нет совпадений с поиском и фильтрами. */
@Composable
private fun EmptyFeats(libraryEmpty: Boolean, emptyTextRes: Int, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (libraryEmpty) emptyTextRes else R.string.library_feats_no_results,
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

/** Блок фильтров черт: пока только характеристики, которые черта повышает. */
@Composable
private fun FeatFilterPanel(
    filters: FeatFilters,
    onFiltersChange: (FeatFilters) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = FILTER_PANEL_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filters.isActive) {
            TextButton(onClick = {
                onFiltersChange(FeatFilters())
                onCollapse()
            }) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.filter_clear))
            }
        }
        FilterChipGroup(
            title = stringResource(R.string.library_feats_filter_ability),
            options = AbilityType.entries.map { it to stringResource(it.labelRes) },
            selected = filters.abilities,
            onToggle = { ability ->
                val abilities = filters.abilities.toMutableSet().apply {
                    if (!add(ability)) remove(ability)
                }
                onFiltersChange(filters.copy(abilities = abilities))
            },
        )
    }
}

/** Блок одной книги: заголовок со счётчиком и список её черт. */
@Composable
private fun BookSection(
    book: FeatBook,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onFeatClick: (String) -> Unit,
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
                    pluralStringResource(R.plurals.feat_count, book.feats.size, book.feats.size),
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
                book.feats.forEach { feat ->
                    LibraryFeatCard(feat = feat, onClick = { onFeatClick(feat.id) })
                }
            }
        }
    }
}

/** Карточка черты: название и краткая выжимка; полный текст открывается отдельным экраном. */
@Composable
private fun LibraryFeatCard(feat: Feat, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                feat.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val preview = feat.description.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
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
 * Группирует отобранные черты по книгам, сохраняя постоянный порядок книг.
 *
 * Порядок считается по всей библиотеке ([allFeats]), а не по видимым чертам:
 * иначе при фильтрации блоки менялись местами вслед за числом совпадений,
 * и привычный Player's Handbook мог уехать вниз. Книги без совпадений просто не показываются.
 *
 * @param fallbackTitle заголовок для черт без книги — обычно созданных пользователем вручную.
 */
internal fun groupByBook(
    visibleFeats: List<Feat>,
    allFeats: List<Feat>,
    fallbackTitle: String,
): List<FeatBook> {
    // Эталонный порядок: крупные книги сверху, при равенстве — по алфавиту.
    val bookOrder = allFeats
        .groupingBy { it.book.trim().ifBlank { fallbackTitle } }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .mapIndexed { index, entry -> entry.key to index }
        .toMap()

    return visibleFeats
        .groupBy { it.book.trim().ifBlank { fallbackTitle } }
        .map { (title, items) -> FeatBook(title, items.sortedBy { it.name.lowercase() }) }
        .sortedWith(
            compareBy<FeatBook> { bookOrder[it.title] ?: Int.MAX_VALUE }.thenBy { it.title },
        )
}
