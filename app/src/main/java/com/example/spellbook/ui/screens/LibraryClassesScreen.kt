package com.example.spellbook.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/**
 * Раздел библиотеки с классами персонажа.
 *
 * Классов всего чуть больше десятка, и все они официальные, поэтому ни группировки
 * по книгам, ни фильтров здесь нет — достаточно простого списка и поиска по названию.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryClassesScreen(
    classes: List<CharClass>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClassClick: (String) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    // Автофокус — только при явном открытии поиска, а не при возврате с сохранённым запросом.
    var autoFocusSearch by remember { mutableStateOf(false) }

    val visible = remember(classes, query) { classes.filterByName(query) }

    // Позиция прокрутки восстанавливается при возврате с экрана класса.
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
                            placeholderRes = R.string.library_classes_search,
                        )
                    } else {
                        Text(stringResource(R.string.library_classes_title))
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
                    if (classes.isEmpty()) return@DndTopBar
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
            if (visible.isEmpty()) {
                EmptyClasses(
                    libraryEmpty = classes.isEmpty(),
                    onReset = { onQueryChange("") },
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { charClass ->
                        ClassCard(charClass = charClass, onClick = { onClassClick(charClass.id) })
                    }
                }
            }
        }
    }
}

/** Заглушка: библиотека пуста либо нет совпадений с поиском. */
@Composable
private fun EmptyClasses(libraryEmpty: Boolean, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (libraryEmpty) R.string.library_classes_empty else R.string.library_classes_no_results,
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

/** Карточка класса: название и книга-источник. */
@Composable
private fun ClassCard(charClass: CharClass, onClick: () -> Unit) {
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
                    charClass.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (charClass.book.isNotBlank()) {
                    Text(
                        charClass.book,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Отбор по названию: фильтров у классов нет, поэтому достаточно поиска. */
private fun List<CharClass>.filterByName(query: String): List<CharClass> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { it.name.contains(trimmed, ignoreCase = true) }
}
