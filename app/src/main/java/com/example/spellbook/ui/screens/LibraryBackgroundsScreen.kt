package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.spellbook.data.model.Background
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/** Предыстории одного блока каталога. */
internal data class BackgroundBookGroup(val title: String, val backgrounds: List<Background>)

/** Отступы содержимого списка. */
private val LIST_PADDING = 16.dp

/** Промежуток между карточками внутри блока. */
private val CARD_SPACING = 8.dp

/** Промежуток между блоками книг. */
private val GROUP_SPACING = 12.dp

/** Свёрнутые книги хранятся списком названий: множество в Bundle не кладётся. */
private val collapsedBackgroundBooksSaver = listSaver<MutableState<Set<String>>, String>(
    save = { state -> state.value.toList() },
    restore = { saved -> mutableStateOf(saved.toSet()) },
)

/**
 * Раздел библиотеки с предысториями, сгруппированными по книгам-источникам.
 *
 * Устроен как раздел рас: блоки идут в постоянном порядке ([BACKGROUND_BOOK_ORDER]),
 * каждый сворачивается, а нажатие открывает отдельный экран. Фильтров нет —
 * поиска по названию достаточно, потому что записей немного и все они официальные.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBackgroundsScreen(
    backgrounds: List<Background>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBackgroundClick: (String) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    // Автофокус — только при явном открытии поиска, а не при возврате с сохранённым запросом.
    var autoFocusSearch by remember { mutableStateOf(false) }

    // Свёрнутые книги переживают поворот экрана: иначе список каждый раз раскрывался бы заново.
    val collapsedBooks = rememberSaveable(saver = collapsedBackgroundBooksSaver) {
        mutableStateOf(emptySet())
    }

    val otherBookTitle = stringResource(R.string.library_backgrounds_other_book)
    val books = remember(backgrounds, query, otherBookTitle) {
        groupBackgroundsByBook(backgrounds.filterByName(query), backgrounds, otherBookTitle)
    }

    // Позиция прокрутки восстанавливается при возврате с экрана предыстории.
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
                            placeholderRes = R.string.library_backgrounds_search,
                        )
                    } else {
                        Text(stringResource(R.string.library_backgrounds_title))
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
                    if (backgrounds.isEmpty()) return@DndTopBar
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (searchActive) autoFocusSearch = true else onQueryChange("")
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (searchActive) R.string.search_close else R.string.search_open,
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
        // Поиск не должен уходить под клавиатуру.
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (books.isEmpty()) {
                EmptyBackgrounds(
                    libraryEmpty = backgrounds.isEmpty(),
                    onReset = { onQueryChange("") },
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(LIST_PADDING),
                    verticalArrangement = Arrangement.spacedBy(GROUP_SPACING),
                ) {
                    items(books.size, key = { books[it].title }) { index ->
                        val book = books[index]
                        BackgroundBookSection(
                            book = book,
                            collapsed = book.title in collapsedBooks.value,
                            onToggle = {
                                collapsedBooks.value = collapsedBooks.value.toMutableSet().apply {
                                    if (!add(book.title)) remove(book.title)
                                }
                            },
                            onBackgroundClick = onBackgroundClick,
                        )
                    }
                }
            }
        }
    }
}

/** Заглушка: библиотека пуста либо нет совпадений с поиском. */
@Composable
private fun EmptyBackgrounds(libraryEmpty: Boolean, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (libraryEmpty) {
                        R.string.library_backgrounds_empty
                    } else {
                        R.string.library_backgrounds_no_results
                    },
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

/** Блок одной книги: заголовок со счётчиком и список её предысторий. */
@Composable
private fun BackgroundBookSection(
    book: BackgroundBookGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onBackgroundClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
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
                    pluralStringResource(
                        R.plurals.background_count,
                        book.backgrounds.size,
                        book.backgrounds.size,
                    ),
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
            Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                book.backgrounds.forEach { background ->
                    BackgroundCard(
                        background = background,
                        groupTitle = book.title,
                        onClick = { onBackgroundClick(background.id) },
                    )
                }
            }
        }
    }
}

/**
 * Карточка предыстории: название и книга-источник.
 *
 * Книга показывается, только если отличается от заголовка блока: иначе подпись
 * дублировала бы его. Расхождения встречаются — например, «Преследуемый» стоит
 * в блоке Curse of Strahd, а плашка страницы указывает Van Richten's Guide.
 */
@Composable
private fun BackgroundCard(background: Background, groupTitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    background.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val book = background.book.trim()
                if (book.isNotEmpty() && !book.equals(groupTitle, ignoreCase = true)) {
                    Text(
                        book,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Отбор по названию: фильтров у предысторий нет, поэтому достаточно поиска. */
private fun List<Background>.filterByName(query: String): List<Background> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { it.name.contains(trimmed, ignoreCase = true) }
}

/**
 * Порядок блоков библиотеки — такой же, как блоки каталога на dnd.su.
 *
 * Задан явно, а не выводится из данных: на сайте книги идут не по алфавиту и не по
 * числу записей — сначала «Книга игрока», затем остальные источники в своём порядке.
 */
private val BACKGROUND_BOOK_ORDER = listOf(
    "Player's Handbook",
    "Acquisition Incorporated",
    "Bigby Presents: Glory of the Giants",
    "The Book of Many Things",
    "Eberron: Rising from the Last War",
    "Explorer's Guide to Wildemount",
    "Guildmasters' guide to Ravnica",
    "Mythic Odysseys of Theros",
    "Planescape: Adventures in the Multiverse",
    "Spelljammer: Adventures in Space",
    "Sword Coast Adventurer's Guide",
    "Van Richten's Guide to Ravenloft",
    "Baldur's Gate: Descent Into Avernus",
    "Curse of Strahd",
    "Dragonlance: Shadow of the Dragon Queen",
    "Ghosts of Saltmarsh",
    "Hoard of the Dragon Queen",
    "Out of the Abyss",
    "Strixhaven: A Curriculum of Chaos",
    "Tomb of Annihilation",
    "The Wild Beyond The Witchlight: A Feywild Adventure",
)

/**
 * Группирует отобранные предыстории по блокам каталога.
 *
 * Порядок блоков фиксирован ([BACKGROUND_BOOK_ORDER]) и не зависит ни от поиска,
 * ни от числа записей: блоки не должны прыгать при фильтрации. Пустые блоки
 * не показываются, а неизвестные (появившиеся на сайте позже) уходят в конец
 * по алфавиту.
 *
 * Группируем по разделу каталога, а не по книге со страницы: они местами расходятся.
 *
 * @param fallbackTitle заголовок для записей без блока — обычно добавленных вручную.
 */
internal fun groupBackgroundsByBook(
    visibleBackgrounds: List<Background>,
    allBackgrounds: List<Background>,
    fallbackTitle: String,
): List<BackgroundBookGroup> {
    // Блоки вне заданного порядка ставим после известных, но стабильно.
    val extraBooks = allBackgrounds
        .map { it.groupTitle(fallbackTitle) }
        .distinct()
        .filterNot { it in BACKGROUND_BOOK_ORDER }
        .sorted()
    val order = (BACKGROUND_BOOK_ORDER + extraBooks)
        .mapIndexed { index, title -> title to index }
        .toMap()

    return visibleBackgrounds
        .groupBy { it.groupTitle(fallbackTitle) }
        .map { (title, items) ->
            BackgroundBookGroup(title, items.sortedBy { it.name.lowercase() })
        }
        .sortedWith(
            compareBy<BackgroundBookGroup> { order[it.title] ?: Int.MAX_VALUE }.thenBy { it.title },
        )
}

/**
 * Заголовок блока для записи: раздел каталога, иначе книга со страницы.
 *
 * Книга — запасной вариант для предысторий, загруженных по ссылке вручную:
 * у них раздела каталога нет, но попадать в «Прочее» из-за этого не стоит.
 */
private fun Background.groupTitle(fallbackTitle: String): String =
    category.trim().ifBlank { book.trim() }.ifBlank { fallbackTitle }
