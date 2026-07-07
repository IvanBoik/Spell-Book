package com.example.spellbook.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.spellbook.ui.Tab

/** Нижняя навигация между вкладками «Персонажи» и «Библиотека». Цвет совпадает с верхней панелью. */
@Composable
fun SpellBookBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = onContainer,
        selectedTextColor = onContainer,
        unselectedIconColor = onContainer.copy(alpha = 0.6f),
        unselectedTextColor = onContainer.copy(alpha = 0.6f),
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = onContainer,
    ) {
        NavigationBarItem(
            selected = selected == Tab.CHARACTERS,
            onClick = { onSelect(Tab.CHARACTERS) },
            icon = { Icon(Icons.Default.Group, contentDescription = null) },
            label = { Text("Персонажи") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selected == Tab.LIBRARY,
            onClick = { onSelect(Tab.LIBRARY) },
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            label = { Text("Библиотека") },
            colors = itemColors,
        )
    }
}
