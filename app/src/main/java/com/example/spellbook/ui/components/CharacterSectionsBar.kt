package com.example.spellbook.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.SectionLayout

/** Единый отступ панели от шапки и от содержимого экрана. */
private val SECTIONS_BAR_VERTICAL_PADDING = 2.dp

/**
 * Разделы персонажа, между которыми переключает панель.
 *
 * Порядок объявления — это порядок по умолчанию: сначала главная страница
 * и заклинания — то, чем пользуются чаще всего, а редко нужные настройки
 * персонажа уходят в конец панели.
 */
enum class CharacterSection(@param:StringRes val labelRes: Int, val icon: ImageVector) {
    STATS(R.string.section_stats, Icons.Default.Shield),
    SPELLS(R.string.section_spells, Icons.Default.Description),
    RESOURCES(R.string.section_resources, Icons.Default.Bolt),
    COMBOS(R.string.section_combos, Icons.Default.Extension),
    INVENTORY(R.string.section_inventory, Icons.Default.Inventory2),
    FEATS(R.string.section_feats, Icons.Default.Star),
    NOTES(R.string.section_notes, Icons.Default.EditNote),
    PREPARE(R.string.section_prepare, Icons.Default.EditNote),
    SETTINGS(R.string.section_settings, Icons.Default.Settings);

    companion object {
        /**
         * Главный раздел персонажа: точка входа при его выборе и цель кнопки «Назад»
         * на остальных экранах. Характеристики есть у любого персонажа, в отличие от
         * заклинаний, которых у немагического героя может не быть вовсе.
         */
        val HOME = STATS

        /**
         * Разделы, которые нельзя скрыть: главная страница и настройки персонажа,
         * без которых его нечем редактировать. Остальные — включая заклинания — отключаемые.
         */
        val ALWAYS_VISIBLE = setOf(HOME, SETTINGS)

        fun fromName(name: String): CharacterSection? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Все разделы в пользовательском порядке, включая скрытые.
 *
 * Сначала идёт заданный порядок, затем разделы, которых в настройке нет (например,
 * появившиеся в новой версии), — так обновление приложения не «теряет» разделы.
 */
fun CharacterSection.Companion.ordered(layout: SectionLayout): List<CharacterSection> {
    val ordered = layout.order.mapNotNull { fromName(it) }
    return ordered + CharacterSection.entries.filterNot { it in ordered }
}

/**
 * Разделы для панели: в пользовательском порядке и без скрытых.
 *
 * @param showPrepare доступна ли подготовка заклинаний у этого персонажа.
 * @param keepVisible раздел, который нельзя убрать из панели (текущий экран).
 */
fun CharacterSection.Companion.arrange(
    layout: SectionLayout,
    showPrepare: Boolean,
    keepVisible: CharacterSection? = null,
): List<CharacterSection> = ordered(layout).filter { section ->
    val available = section != CharacterSection.PREPARE || showPrepare
    val visible = section.name !in layout.hidden ||
        section in ALWAYS_VISIBLE ||
        section == keepVisible
    available && visible
}



/**
 * Горизонтально прокручиваемая панель разделов персонажа.
 * Показывается на всех экранах персонажа, чтобы переключаться между ними без возврата назад.
 *
 * @param current текущий раздел — подсвечивается и не реагирует на нажатие.
 * @param layout пользовательская настройка порядка и скрытых разделов.
 * @param initialScrollIndex и [initialScrollOffset] восстанавливают прокрутку при смене экрана.
 */
@Composable
fun CharacterSectionsBar(
    current: CharacterSection,
    showPrepare: Boolean,
    onSelect: (CharacterSection) -> Unit,
    modifier: Modifier = Modifier,
    layout: SectionLayout = SectionLayout.DEFAULT,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    // Текущий раздел остаётся в панели, даже если скрыт: иначе непонятно, где находишься.
    val sections = CharacterSection.arrange(layout, showPrepare, keepVisible = current)

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
                label = { Text(stringResource(section.labelRes)) },
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
