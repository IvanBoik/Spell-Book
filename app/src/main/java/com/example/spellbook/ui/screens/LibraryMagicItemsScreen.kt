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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.ItemRarity
import com.example.spellbook.data.MagicItemFilters
import com.example.spellbook.data.availableItemBooks
import com.example.spellbook.data.availableRarities
import com.example.spellbook.data.filterSearch
import com.example.spellbook.data.model.MagicItem
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/** Предметы одного типа. */
internal data class MagicItemGroup(val title: String, val items: List<MagicItem>)

/** Отступы содержимого списка. */
private val LIST_PADDING = 16.dp

/** Промежуток между карточками внутри блока. */
private val CARD_SPACING = 8.dp

/** Промежуток между блоками типов. */
private val GROUP_SPACING = 12.dp

/** Максимальная высота блока фильтров: дальше он прокручивается. */
private val ITEM_FILTER_PANEL_MAX_HEIGHT = 280.dp

/**
 * Раздел библиотеки с магическими предметами, сгруппированными по типу.
 *
 * Предметов почти тысяча — много больше, чем в других разделах, поэтому все блоки
 * свёрнуты по умолчанию: иначе список открывался бы сплошной простынёй.
 *
 * Состояние блока всегда за пользователем: фильтры только сужают содержимое
 * и не трогают свёрнутость. Исключение одно — поиск по названию (см. ниже).
 *
 * @param expandedKinds раскрытые типы; хранятся снаружи вместе с позицией прокрутки,
 * чтобы пережить переход на экран предмета и возврат обратно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMagicItemsScreen(
    items: List<MagicItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    filters: MagicItemFilters,
    onFiltersChange: (MagicItemFilters) -> Unit,
    expandedKinds: Set<String>,
    onExpandedKindsChange: (Set<String>) -> Unit,
    onItemClick: (String) -> Unit,
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

    val otherKindTitle = stringResource(R.string.library_magic_items_other_kind)
    val groups = remember(items, query, filters, otherKindTitle) {
        groupMagicItemsByKind(items.filterSearch(query, filters), items, otherKindTitle)
    }
    // Варианты фильтров строятся по всей библиотеке, чтобы не исчезать при отборе.
    val bookOptions = remember(items) { items.availableItemBooks() }
    val rarityOptions = remember(items) { items.availableRarities() }

    // Поиск по названию раскрывает блоки с совпадениями: ищут конкретный предмет,
    // и показывать вместо него свёрнутый заголовок было бы странно.
    //
    // Это разовая правка состояния по смене запроса, а не постоянное переопределение:
    // иначе нажатие по заголовку ничего бы не меняло.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        onExpandedKindsChange(expandedKinds + groups.map { it.title })
    }

    // Позиция прокрутки восстанавливается при возврате с экрана предмета.
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
                            placeholderRes = R.string.library_magic_items_search,
                        )
                    } else {
                        Text(stringResource(R.string.library_magic_items_title))
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
                    if (items.isEmpty()) return@DndTopBar
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
                MagicItemFilterPanel(
                    filters = filters,
                    rarityOptions = rarityOptions,
                    bookOptions = bookOptions,
                    onFiltersChange = onFiltersChange,
                    onCollapse = { showFilters = false },
                )
                HorizontalDivider()
            }

            // Тап вне блока фильтров сворачивает его — как в списках заклинаний и рас.
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
                if (groups.isEmpty()) {
                    EmptyMagicItems(
                        libraryEmpty = items.isEmpty(),
                        onReset = {
                            onQueryChange("")
                            onFiltersChange(MagicItemFilters())
                        },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(LIST_PADDING),
                        verticalArrangement = Arrangement.spacedBy(GROUP_SPACING),
                    ) {
                        items(groups.size, key = { groups[it].title }) { index ->
                            val group = groups[index]
                            MagicItemKindSection(
                                group = group,
                                collapsed = group.title !in expandedKinds,
                                onToggle = {
                                    onExpandedKindsChange(
                                        expandedKinds.toMutableSet().apply {
                                            if (!add(group.title)) remove(group.title)
                                        },
                                    )
                                },
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Блок фильтров предметов: редкость, настройка и книга-источник.
 *
 * Типа среди фильтров нет: по нему строятся сами блоки списка.
 */
@Composable
private fun MagicItemFilterPanel(
    filters: MagicItemFilters,
    rarityOptions: List<ItemRarity>,
    bookOptions: List<String>,
    onFiltersChange: (MagicItemFilters) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Книг много, поэтому панель ограничена по высоте и прокручивается.
            .heightIn(max = ITEM_FILTER_PANEL_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filters.isActive) {
            TextButton(onClick = {
                onFiltersChange(MagicItemFilters())
                onCollapse()
            }) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.filter_clear))
            }
        }

        FilterChipGroup(
            title = stringResource(R.string.library_magic_items_filter_rarity),
            options = rarityOptions.map { it to stringResource(it.labelRes()) },
            selected = filters.rarities,
            onToggle = { rarity ->
                val rarities = filters.rarities.toMutableSet().apply {
                    if (!add(rarity)) remove(rarity)
                }
                onFiltersChange(filters.copy(rarities = rarities))
            },
        )

        // Настройка — признак с двумя состояниями, поэтому повторное нажатие снимает фильтр.
        FilterChipGroup(
            title = stringResource(R.string.library_magic_items_filter_attunement),
            options = listOf(
                true to stringResource(R.string.magic_item_attunement_required),
                false to stringResource(R.string.magic_item_attunement_not_required),
            ),
            selected = setOfNotNull(filters.attunement),
            onToggle = { value ->
                onFiltersChange(
                    filters.copy(attunement = value.takeIf { it != filters.attunement }),
                )
            },
        )

        FilterChipGroup(
            title = stringResource(R.string.library_magic_items_filter_book),
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

/** Подпись редкости в фильтрах. */
private fun ItemRarity.labelRes(): Int = when (this) {
    ItemRarity.COMMON -> R.string.item_rarity_common
    ItemRarity.UNCOMMON -> R.string.item_rarity_uncommon
    ItemRarity.RARE -> R.string.item_rarity_rare
    ItemRarity.VERY_RARE -> R.string.item_rarity_very_rare
    ItemRarity.LEGENDARY -> R.string.item_rarity_legendary
    ItemRarity.ARTIFACT -> R.string.item_rarity_artifact
    ItemRarity.VARIES -> R.string.item_rarity_varies
}

/** Заглушка: библиотека пуста либо нет совпадений с поиском. */
@Composable
private fun EmptyMagicItems(libraryEmpty: Boolean, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (libraryEmpty) {
                        R.string.library_magic_items_empty
                    } else {
                        R.string.library_magic_items_no_results
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

/** Блок одного типа: заголовок со счётчиком и список предметов. */
@Composable
private fun MagicItemKindSection(
    group: MagicItemGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pluralStringResource(
                        R.plurals.magic_item_count,
                        group.items.size,
                        group.items.size,
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
                group.items.forEach { item ->
                    MagicItemCard(item = item, onClick = { onItemClick(item.id) })
                }
            }
        }
    }
}

/**
 * Карточка предмета: название и подпись с редкостью.
 *
 * Тип в подпись не выносим — он уже стоит в заголовке блока. Пометку о настройке
 * показываем: без неё непонятно, можно ли пользоваться предметом сразу.
 */
@Composable
private fun MagicItemCard(item: MagicItem, onClick: () -> Unit) {
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
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = item.subtitle(stringResource(R.string.magic_item_attunement_short))
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Подпись карточки: редкость и, если нужна, пометка о настройке. */
private fun MagicItem.subtitle(attunementLabel: String): String {
    val parts = listOfNotNull(
        rarity.trim().takeIf { it.isNotEmpty() },
        attunementLabel.takeIf { requiresAttunement },
    )
    return parts.joinToString(" · ")
}


/**
 * Порядок блоков — такой же, как в фильтре «Тип» на dnd.su.
 *
 * Задан явно, а не выводится из данных: иначе блоки встали бы по алфавиту
 * или по числу записей и прыгали бы при обновлении набора.
 */
private val MAGIC_ITEM_KIND_ORDER = listOf(
    "Волшебная палочка",
    "Доспех",
    "Жезл",
    "Зелье",
    "Кольцо",
    "Оружие",
    "Посох",
    "Свиток",
    "Чудесный предмет",
)

/**
 * Группирует отобранные предметы по типу.
 *
 * Порядок блоков фиксирован ([MAGIC_ITEM_KIND_ORDER]) и не зависит ни от поиска,
 * ни от числа записей: блоки не должны прыгать при фильтрации. Пустые блоки
 * не показываются, а неизвестные типы уходят в конец по алфавиту.
 *
 * @param fallbackTitle заголовок для записей без типа — обычно добавленных вручную.
 */
internal fun groupMagicItemsByKind(
    visibleItems: List<MagicItem>,
    allItems: List<MagicItem>,
    fallbackTitle: String,
): List<MagicItemGroup> {
    // Типы вне заданного порядка ставим после известных, но стабильно.
    val extraKinds = allItems
        .map { it.groupTitle(fallbackTitle) }
        .distinct()
        .filterNot { it in MAGIC_ITEM_KIND_ORDER }
        .sorted()
    val order = (MAGIC_ITEM_KIND_ORDER + extraKinds)
        .mapIndexed { index, title -> title to index }
        .toMap()

    return visibleItems
        .groupBy { it.groupTitle(fallbackTitle) }
        .map { (title, groupItems) ->
            MagicItemGroup(title, groupItems.sortedBy { it.name.lowercase() })
        }
        .sortedWith(
            compareBy<MagicItemGroup> { order[it.title] ?: Int.MAX_VALUE }.thenBy { it.title },
        )
}

/** Заголовок блока для записи: тип предмета, иначе запасная подпись. */
private fun MagicItem.groupTitle(fallbackTitle: String): String =
    kind.trim().ifBlank { fallbackTitle }
