package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Единый отступ панели от шапки и от содержимого экрана. */
private val SECTIONS_BAR_VERTICAL_PADDING = 2.dp

/** Разделы персонажа, между которыми переключает панель. */
enum class CharacterSection(val label: String, val icon: ImageVector) {
    SETTINGS("Настройки", Icons.Default.Settings),
    SPELLS("Заклинания", Icons.Default.Description),
    STATS("Характеристики", Icons.Default.Shield),
    RESOURCES("Ресурсы", Icons.Default.Bolt),
    COMBOS("Комбинации", Icons.Default.Extension),
    INVENTORY("Инвентарь", Icons.Default.Inventory2),
    FEATS("Черты", Icons.Default.Star),
    NOTES("Заметки", Icons.Default.EditNote),
    PREPARE("Подготовка", Icons.Default.EditNote),
}

/**
 * Горизонтально прокручиваемая панель разделов персонажа.
 * Показывается на всех экранах персонажа, чтобы переключаться между ними без возврата назад.
 *
 * @param current текущий раздел — подсвечивается и не реагирует на нажатие.
 * @param initialScrollIndex и [initialScrollOffset] восстанавливают прокрутку при смене экрана.
 */
@Composable
fun CharacterSectionsBar(
    current: CharacterSection,
    showPrepare: Boolean,
    onSelect: (CharacterSection) -> Unit,
    modifier: Modifier = Modifier,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    // Раздел заклинаний — «корневой» экран персонажа, подготовка доступна не всем.
    val sections = CharacterSection.entries.filter { it != CharacterSection.PREPARE || showPrepare }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    // Сообщаем позицию наружу, чтобы следующий экран открылся на том же месте.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollChanged(index, offset) }
    }

    LazyRow(
        state = listState,
        // Вертикальный отступ задаётся здесь, чтобы панель одинаково отступала
        // от шапки на всех экранах персонажа.
        modifier = modifier.fillMaxWidth().padding(vertical = SECTIONS_BAR_VERTICAL_PADDING),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections, key = { it.name }) { section ->
            val selected = section == current
            AssistChip(
                onClick = { if (!selected) onSelect(section) },
                leadingIcon = {
                    Icon(section.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(section.label) },
                colors = if (selected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}
