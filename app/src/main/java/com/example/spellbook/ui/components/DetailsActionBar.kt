package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R

/** Скругление блока действий: форма «таблетки» отделяет его от содержимого экрана. */
private val ACTION_BAR_SHAPE_RADIUS = 28.dp

/** Тень блока: она же подсказывает, что блок лежит поверх содержимого. */
private val ACTION_BAR_ELEVATION = 6.dp

/** Отступ блока от нижнего края экрана. */
private val ACTION_BAR_MARGIN = 16.dp

/** Внутренние отступы вокруг ряда иконок. */
private val ACTION_BAR_PADDING = 4.dp

/**
 * Высота блока с отступами сверху и снизу.
 *
 * Ровно столько свободного места нужно оставить в конце прокручиваемого содержимого,
 * чтобы в самом низу блок оказался под контентом, а не закрывал его.
 */
val DetailsActionBarReservedHeight = 48.dp + ACTION_BAR_PADDING * 2 + ACTION_BAR_MARGIN * 2

/**
 * Плавающий блок действий над записью: редактирование, удаление, отправка и выгрузка JSON.
 *
 * Лежит поверх содержимого у нижнего края экрана, поэтому действия доступны без
 * прокрутки к началу. Чтобы блок не закрывал конец текста, вызывающий экран
 * оставляет внизу прокручиваемой области отступ [DetailsActionBarReservedHeight] —
 * тогда при прокрутке до упора блок оказывается ниже всего содержимого.
 */
@Composable
fun DetailsActionBar(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(ACTION_BAR_MARGIN),
        shape = RoundedCornerShape(ACTION_BAR_SHAPE_RADIUS),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = ACTION_BAR_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(ACTION_BAR_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ACTION_BAR_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = onExport) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.spell_export_json),
                )
            }
        }
    }
}
