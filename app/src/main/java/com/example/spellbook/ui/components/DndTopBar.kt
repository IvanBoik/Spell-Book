package com.example.spellbook.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/** Бордовый хедер в стиле dnd.su с золотистым заголовком и иконками действий. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun dndTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DndTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    DndTopBar(
        title = { Text(title) },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/** Вариант хедера с произвольным содержимым заголовка (например, полем поиска). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DndTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = dndTopBarColors(),
    )
}
