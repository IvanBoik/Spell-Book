package com.example.spellbook.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.spellbook.data.model.Character
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.INVENTORY_CATEGORIES
import com.example.spellbook.data.model.DEFAULT_ITEM_RARITY
import com.example.spellbook.data.model.ITEM_RARITIES
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.ui.components.DndTopBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class InventoryTab { ITEMS, MAGIC }
private enum class InventorySort(@param:androidx.annotation.StringRes val labelRes: Int) {
    MANUAL(R.string.inventory_sort_manual),
    DATE(R.string.inventory_sort_date),
    NAME(R.string.inventory_sort_name),
    QUANTITY(R.string.inventory_sort_quantity),
    RARITY(R.string.inventory_sort_rarity),
}

/** Порядок отображения монет: от наименьшего номинала к наибольшему. */
private val WALLET_COIN_ORDER = listOf(
    CoinType.COPPER,
    CoinType.SILVER,
    CoinType.GOLD,
    CoinType.ELECTRUM,
    CoinType.PLATINUM,
)

/** Единая высота сводных карточек кошелька и настройки. */
private val INVENTORY_SUMMARY_HEIGHT = 80.dp

private const val COIN_COMPACT_BASE = 1_000L

/** Жёлтый цвет действия редактирования — как у карточек комбинаций. */
private val INVENTORY_EDIT_ACTION_COLOR = Color(0xFFFBC02D)
private val INVENTORY_DRAG_STEP = 92.dp

@Composable
fun InventoryScreen(
    character: Character,
    items: List<InventoryItem>,
    onSaveItem: (InventoryItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onSetQuantity: (String, Int) -> Unit,
    onReorderItems: (List<String>) -> Unit,
    onToggleAttunement: (String) -> Unit,
    onSetAttunementLimit: (Int) -> Unit,
    onSetCoinAmount: (CoinType, Int) -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    var tab by remember { mutableStateOf(InventoryTab.ITEMS) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(InventorySort.MANUAL) }
    var showSearch by remember { mutableStateOf(false) }
    var autoFocusSearch by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var viewedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var itemPendingDeletion by remember { mutableStateOf<InventoryItem?>(null) }
    var editingCoin by remember { mutableStateOf<CoinType?>(null) }
    var showAttunementLimit by remember { mutableStateOf(false) }
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var manualVisibleOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullOrderAtDragStart by remember { mutableStateOf<List<String>>(emptyList()) }
    /**
     * Оптимистичный порядок отдельно для обычных и магических предметов.
     * Не даёт списку откатиться, пока Room Flow ещё не успел вернуть записанные позиции.
     */
    var manualOrders by remember { mutableStateOf<Map<InventoryTab, List<String>>>(emptyMap()) }
    val inventoryListState = rememberLazyListState()
    val density = LocalDensity.current

    val tabItems = items.filter { it.isMagic == (tab == InventoryTab.MAGIC) }
    val matchingItems = tabItems.filter { item ->
        (query.isBlank() || item.name.contains(query.trim(), ignoreCase = true)) &&
            (selectedCategories.isEmpty() || item.categories.any { it in selectedCategories })
    }

    fun sorted(source: List<InventoryItem>, mode: InventorySort): List<InventoryItem> = when (mode) {
        InventorySort.MANUAL -> source.sortedWith(compareBy({ it.sortOrder }, { -it.createdAt }))
        InventorySort.DATE -> source.sortedByDescending { it.createdAt }
        InventorySort.NAME -> source.sortedBy { it.name.lowercase() }
        InventorySort.QUANTITY -> source.sortedByDescending { it.quantity }
        InventorySort.RARITY -> source.sortedBy {
            ITEM_RARITIES.indexOf(it.rarity).let { index -> if (index < 0) 0 else index }
        }
    }

    /** Актуальный ручной порядок вкладки с добавлением новых и удалением отсутствующих id. */
    val persistedTabOrder = sorted(tabItems, InventorySort.MANUAL).map { it.id }
    val localTabOrder = manualOrders[tab].orEmpty()
    val tabIds = tabItems.mapTo(mutableSetOf()) { it.id }
    val effectiveManualOrder = localTabOrder.filter { it in tabIds } +
        persistedTabOrder.filterNot { it in localTabOrder }

    val filtered = when {
        draggingItemId != null && manualVisibleOrder.isNotEmpty() ->
            manualVisibleOrder.mapNotNull { id -> matchingItems.firstOrNull { it.id == id } }
        sort == InventorySort.MANUAL ->
            effectiveManualOrder.mapNotNull { id -> matchingItems.firstOrNull { it.id == id } }
        else -> sorted(matchingItems, sort)
    }

    fun persistVisibleOrder(visibleIds: List<String>, fullIds: List<String>) {
        val visibleSet = visibleIds.toSet()
        val iterator = visibleIds.iterator()
        val merged = fullIds.map { id -> if (id in visibleSet && iterator.hasNext()) iterator.next() else id }
        onReorderItems(merged)
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = {
                    if (showSearch) {
                        InventorySearchField(
                            query = query,
                            onQueryChange = { query = it },
                            autoFocus = autoFocusSearch,
                            onAutoFocusConsumed = { autoFocusSearch = false },
                            onFocusLost = { showSearch = false },
                        )
                    } else {
                        Text(stringResource(R.string.inventory_title))
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
                    IconButton(onClick = {
                        if (showSearch) {
                            showSearch = false
                            autoFocusSearch = false
                        } else {
                            showSearch = true
                            autoFocusSearch = true
                            showFilters = false
                        }
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (showSearch) R.string.search_close else R.string.search_open,
                            ),
                        )
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filter_title),
                        )
                    }
                    Box {
                        IconButton(onClick = { showSort = true }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = stringResource(R.string.sort_title),
                            )
                        }
                        DropdownMenu(
                            expanded = showSort,
                            onDismissRequest = { showSort = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            InventorySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        sort = option
                                        showSort = false
                                        val orderedIds = sorted(tabItems, option).map { it.id }
                                        manualOrders = manualOrders + (tab to orderedIds)
                                        onReorderItems(orderedIds)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = InventoryItem(
                        characterId = character.id,
                        name = "",
                        isMagic = tab == InventoryTab.MAGIC,
                    )
                },
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.inventory_add_item),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            sectionsBar()

            TabRow(selectedTabIndex = if (tab == InventoryTab.ITEMS) 0 else 1) {
                Tab(
                    selected = tab == InventoryTab.ITEMS,
                    onClick = {
                        tab = InventoryTab.ITEMS
                        selectedCategories = emptySet()
                    },
                    text = { Text(stringResource(R.string.inventory_tab_items)) },
                )
                Tab(
                    selected = tab == InventoryTab.MAGIC,
                    onClick = {
                        tab = InventoryTab.MAGIC
                        selectedCategories = emptySet()
                    },
                    text = { Text(stringResource(R.string.inventory_tab_magic)) },
                )
            }

            if (showFilters) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    INVENTORY_CATEGORIES.forEach { category ->
                        val selected = category in selectedCategories
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategories = if (selected) {
                                    selectedCategories - category
                                } else selectedCategories + category
                            },
                            label = { Text(category) },
                            colors = burgundyInventoryChipColors(),
                        )
                    }
                }
            }

            LazyColumn(
                state = inventoryListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == InventoryTab.ITEMS) {
                    item(key = "wallet") {
                        WalletCard(
                            character = character,
                            onCoinClick = { editingCoin = it },
                        )
                    }
                } else {
                    item(key = "attunement") {
                        AttunementSummary(
                            current = items.count { it.isMagic && it.attuned },
                            maximum = character.maxAttunedItems,
                            onEdit = { showAttunementLimit = true },
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Text(
                stringResource(
                    if (tab == InventoryTab.ITEMS) {
                        R.string.inventory_items_empty
                    } else {
                        R.string.inventory_magic_empty
                    },
                ),
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(filtered, key = { it.id }) { item ->
                    val isDraggedItem = draggingItemId == item.id
                    SwipeableInventoryCard(
                        item = item,
                        // Placement-анимация временно возвращала соседей и dragged-item
                        // в старые координаты. Позиции меняются сразу, без промежуточного кадра.
                        modifier = Modifier,
                        onClick = { viewedItem = item },
                        onEdit = { editingItem = item },
                        onDelete = { itemPendingDeletion = item },
                        onMinus = {
                            if (item.quantity <= 1) {
                                itemPendingDeletion = item
                            } else {
                                onChangeQuantity(item.id, -1)
                            }
                        },
                        onPlus = { onChangeQuantity(item.id, 1) },
                        onQuantityClick = { editingItem = item },
                        onToggleAttunement = { onToggleAttunement(item.id) },
                        isDragging = isDraggedItem,
                        dragTranslationY = if (isDraggedItem) dragDistance else 0f,
                        onDragStart = {
                            val currentFullOrder = when (sort) {
                                InventorySort.MANUAL -> effectiveManualOrder
                                else -> sorted(tabItems, sort).map { it.id }
                            }
                            fullOrderAtDragStart = currentFullOrder
                            manualOrders = manualOrders + (tab to currentFullOrder)
                            manualVisibleOrder = currentFullOrder.filter { id ->
                                matchingItems.any { it.id == id }
                            }
                            draggingItemId = item.id
                            dragDistance = 0f
                            sort = InventorySort.MANUAL
                        },
                        onDrag = { delta ->
                            dragDistance += delta
                            val stepPx = with(density) { INVENTORY_DRAG_STEP.toPx() }

                            // Переставляем элемент сразу после пересечения соседней позиции.
                            // Компенсация offset удерживает карточку под пальцем, пока её базовая
                            // позиция в LazyColumn меняется, а соседи анимированно сдвигаются.
                            while (dragDistance >= stepPx) {
                                val index = manualVisibleOrder.indexOf(item.id)
                                if (index < 0 || index >= manualVisibleOrder.lastIndex) break
                                manualVisibleOrder = manualVisibleOrder.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                }
                                dragDistance -= stepPx
                            }
                            while (dragDistance <= -stepPx) {
                                val index = manualVisibleOrder.indexOf(item.id)
                                if (index <= 0) break
                                manualVisibleOrder = manualVisibleOrder.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                }
                                dragDistance += stepPx
                            }
                        },
                        onDragEnd = {
                            val visibleSet = manualVisibleOrder.toSet()
                            val visibleIterator = manualVisibleOrder.iterator()
                            val mergedOrder = fullOrderAtDragStart.map { id ->
                                if (id in visibleSet && visibleIterator.hasNext()) visibleIterator.next() else id
                            }
                            manualOrders = manualOrders + (tab to mergedOrder)
                            onReorderItems(mergedOrder)
                            draggingItemId = null
                            dragDistance = 0f
                        },
                    )
                }
            }
        }
    }

    editingItem?.let { item ->
        InventoryItemDialog(
            initial = item,
            onDismiss = { editingItem = null },
            onSave = { onSaveItem(it); editingItem = null },
        )
    }
    viewedItem?.let { item ->
        ItemDetailsDialog(item = item, onDismiss = { viewedItem = null })
    }
    itemPendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingDeletion = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.inventory_delete_title)) },
            text = {
                Text(stringResource(R.string.inventory_delete_text, item.name))
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteItem(item.id)
                    itemPendingDeletion = null
                }) {
                Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDeletion = null }) {
                Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    editingCoin?.let { coin ->
        ExactCoinDialog(
            coin = coin,
            initial = character.coins[coin.ordinal] ?: 0,
            onDismiss = { editingCoin = null },
            onSave = { onSetCoinAmount(coin, it); editingCoin = null },
        )
    }
    if (showAttunementLimit) {
        NumberDialog(
            title = stringResource(R.string.inventory_attunement_title),
            initial = character.maxAttunedItems,
            onDismiss = { showAttunementLimit = false },
            onSave = { onSetAttunementLimit(it); showAttunementLimit = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySearchField(
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
        placeholder = {
            Text(stringResource(R.string.inventory_name_hint), color = onColor.copy(alpha = 0.6f))
        },
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

@Composable
private fun WalletCard(
    character: Character,
    onCoinClick: (CoinType) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(INVENTORY_SUMMARY_HEIGHT),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WALLET_COIN_ORDER.forEach { coin ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(coin.shortLabelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = compactCoinAmount(
                            amount = character.coins[coin.ordinal] ?: 0,
                            thousandSuffix = stringResource(R.string.inventory_thousand_suffix),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onCoinClick(coin) }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/**
 * Сокращает большие суммы монет с округлением вниз до одной десятичной позиции:
 * 2 199 → 2,1к; 15 582 → 15,5к; 1 000 000 → 1кк.
 *
 * @param thousandSuffix подпись «тысячи» на языке интерфейса.
 */
private fun compactCoinAmount(amount: Int, thousandSuffix: String): String {
    val safeAmount = amount.coerceAtLeast(0).toLong()
    if (safeAmount < COIN_COMPACT_BASE) return safeAmount.toString()

    var scale = 1L
    var suffix = ""
    while (safeAmount / scale >= COIN_COMPACT_BASE) {
        scale *= COIN_COMPACT_BASE
        suffix += thousandSuffix
    }

    val whole = safeAmount / scale
    val tenth = (safeAmount % scale) * 10 / scale
    return if (tenth == 0L) {
        "$whole$suffix"
    } else {
        "$whole,$tenth$suffix"
    }
}

@Composable
private fun AttunementSummary(current: Int, maximum: Int, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(INVENTORY_SUMMARY_HEIGHT),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.inventory_attunement),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.inventory_attunement_progress, current, maximum),
                    color = if (current >= maximum) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.inventory_edit_limit),
                    )
                }
        }
    }
}

@Composable
private fun SwipeableInventoryCard(
    item: InventoryItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onQuantityClick: () -> Unit,
    onToggleAttunement: () -> Unit,
    isDragging: Boolean,
    dragTranslationY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val actionWidth = 112.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = actionWidthPx * 0.35f

    // pointerInput живёт дольше одной рекомпозиции. Без rememberUpdatedState он
    // продолжал вызывать callback-и, захватившие старый порядок элементов.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)) {
        // Во время вертикального переноса действия свайпа скрыты и не просвечивают под карточкой.
        if (!isDragging) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).width(actionWidth).padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                InventoryActionButton(
                    icon = Icons.Default.Edit,
                    description = stringResource(R.string.inventory_edit_item),
                    background = INVENTORY_EDIT_ACTION_COLOR,
                    contentColor = Color.Black,
                    onClick = onEdit,
                )
                InventoryActionButton(
                    icon = Icons.Default.Delete,
                    description = stringResource(R.string.inventory_delete_item),
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = onDelete,
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        offset.value.roundToInt(),
                        if (isDragging) dragTranslationY.roundToInt() else 0,
                    )
                }
                .zIndex(if (isDragging) 1f else 0f)
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + amount).coerceIn(-actionWidthPx, 0f)) }
                        },
                        onDragEnd = {
                            scope.launch { offset.animateTo(if (-offset.value > threshold) -actionWidthPx else 0f, tween(220)) }
                        },
                    )
                }
                // Ключ не зависит от isDragging: иначе рекомпозиция отменит активный жест.
                .pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            scope.launch { offset.snapTo(0f) }
                            currentOnDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDrag(dragAmount.y)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() },
                    )
                }
                .clickable(enabled = !isDragging, onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (item.categories.isNotEmpty()) {
                            Text(item.categories.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (item.isMagic) {
                            Text(item.rarity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onMinus) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = stringResource(R.string.inventory_decrease),
                        )
                    }
                    Text(
                        item.quantity.toString(),
                        modifier = Modifier.clickable(onClick = onQuantityClick).padding(6.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onPlus) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.inventory_increase),
                        )
                    }
                }
                if (item.isMagic) {
                    // Кружок-маркер не переводится: это чисто визуальный индикатор.
                    val status = when {
                        !item.requiresAttunement -> stringResource(R.string.inventory_no_attunement)
                        item.attuned -> "● " + stringResource(R.string.inventory_attuned)
                        else -> "○ " + stringResource(R.string.inventory_not_attuned)
                    }
                    Text(
                        status,
                        modifier = if (item.requiresAttunement) Modifier.clickable(onClick = onToggleAttunement) else Modifier,
                        color = if (item.attuned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (item.attuned) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).background(background, CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = contentColor)
    }
}

@Composable
private fun burgundyInventoryChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryItemDialog(
    initial: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var quantity by remember(initial.id) { mutableStateOf(initial.quantity.toString()) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var categories by remember(initial.id) { mutableStateOf(initial.categories.toSet()) }
    val isMagic = initial.isMagic
    var rarity by remember(initial.id) { mutableStateOf(initial.rarity) }
    var requiresAttunement by remember(initial.id) { mutableStateOf(initial.requiresAttunement) }
    var attuned by remember(initial.id) { mutableStateOf(initial.attuned) }
    var showRarity by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(
                    if (initial.name.isBlank()) {
                        R.string.inventory_new_item
                    } else {
                        R.string.inventory_edit_item_title
                    },
                ),
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.inventory_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    quantity,
                    { quantity = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.inventory_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Text(stringResource(R.string.inventory_categories), fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        INVENTORY_CATEGORIES.forEach { category ->
                            FilterChip(
                                selected = category in categories,
                                onClick = { categories = if (category in categories) categories - category else categories + category },
                                label = { Text(category) },
                                colors = burgundyInventoryChipColors(),
                            )
                        }
                    }
                }
                if (isMagic) {
                    item {
                        Box {
            OutlinedButton(onClick = { showRarity = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.inventory_rarity, rarity))
            }
                            DropdownMenu(expanded = showRarity, onDismissRequest = { showRarity = false }, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                                ITEM_RARITIES.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { rarity = option; showRarity = false }) }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.inventory_requires_attunement),
                        modifier = Modifier.weight(1f),
                    )
                            Switch(
                                checked = requiresAttunement,
                                onCheckedChange = { requiresAttunement = it; if (!it) attuned = false },
                                colors = inventorySwitchColors(),
                            )
                        }
                    }
                    if (requiresAttunement) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.inventory_is_attuned), modifier = Modifier.weight(1f))
                                Switch(
                                    checked = attuned,
                                    onCheckedChange = { attuned = it },
                                    colors = inventorySwitchColors(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && (quantity.toIntOrNull() ?: 0) > 0,
                onClick = {
                    onSave(initial.copy(
                        name = name,
                        quantity = quantity.toIntOrNull() ?: 1,
                        description = description,
                        categories = categories.toList(),
                        isMagic = initial.isMagic,
                            rarity = if (initial.isMagic) rarity else DEFAULT_ITEM_RARITY,
                        requiresAttunement = initial.isMagic && requiresAttunement,
                        attuned = initial.isMagic && requiresAttunement && attuned,
                    ))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun inventorySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary,
    uncheckedBorderColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun ItemDetailsDialog(item: InventoryItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inventory_quantity_value, item.quantity))
                if (item.categories.isNotEmpty()) Text(item.categories.joinToString(" · "), color = MaterialTheme.colorScheme.primary)
                if (item.isMagic) Text(stringResource(R.string.inventory_rarity_value, item.rarity))
                Text(
                    item.description.ifBlank { stringResource(R.string.inventory_no_description) },
                    color = if (item.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ExactCoinDialog(
    coin: CoinType,
    initial: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    NumberDialog(
        title = stringResource(R.string.inventory_coin_exact, stringResource(coin.labelRes)),
        initial = initial,
        onDismiss = onDismiss,
        onSave = onSave,
        zeroAsPlaceholder = true,
    )
}

/** Простой диалог ввода числа; переиспользуется также на экране характеристик. */
@Composable
internal fun NumberDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    zeroAsPlaceholder: Boolean = false,
) {
    var value by remember(initial, zeroAsPlaceholder) {
        mutableStateOf(if (zeroAsPlaceholder && initial == 0) "" else initial.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                placeholder = if (zeroAsPlaceholder) {
                    { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
