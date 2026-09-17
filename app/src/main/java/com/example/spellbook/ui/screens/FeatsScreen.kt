package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.Feat
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.dragToReorder
import com.example.spellbook.ui.components.rememberDragReorderState

private val FEAT_CARD_SHAPE = RoundedCornerShape(12.dp)

/** Шаг перестановки при перетаскивании — примерная высота свёрнутой карточки. */
private val FEAT_DRAG_STEP = 72.dp

/**
 * Экран черт персонажа. Черты показываются блоками, как заметки: заголовок
 * сворачивается, а содержимое можно ввести вручную или загрузить с dnd.su.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatsScreen(
    feats: List<CharacterFeat>,
    onAddFeat: (name: String, description: String) -> Unit,
    onSaveFeat: (Feat) -> Unit,
    onToggleCollapsed: (featId: String, collapsed: Boolean) -> Unit,
    onRemoveFromCharacter: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onLoadFromDndSu: (String) -> Unit,
    onAddFromLibrary: () -> Unit,
    onDiceClick: (String) -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var editingFeat by remember { mutableStateOf<CharacterFeat?>(null) }
    var featPendingDeletion by remember { mutableStateOf<CharacterFeat?>(null) }

    val dragState = rememberDragReorderState<String>(step = FEAT_DRAG_STEP)
    val orderedFeats = dragState.order(feats.map { it.id })
        .mapNotNull { id -> feats.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(R.string.feats_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (menuExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = { menuExpanded = false; onAddFromLibrary() },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        text = { Text(stringResource(R.string.feats_add_from_library)) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    ExtendedFloatingActionButton(
                        onClick = { menuExpanded = false; showUrlDialog = true },
                        icon = { Icon(Icons.Default.Link, contentDescription = null) },
                        text = { Text(stringResource(R.string.library_load_dndsu)) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    ExtendedFloatingActionButton(
                        onClick = { menuExpanded = false; showManualDialog = true },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.feats_add_manually)) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                }
                FloatingActionButton(onClick = { menuExpanded = !menuExpanded }) {
                    Icon(
                        imageVector = if (menuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(
                            if (menuExpanded) R.string.action_close_menu else R.string.feats_add,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        if (feats.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                sectionsBar()
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.feats_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.feats_empty_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Панель вне списка: отступы одинаковы на всех экранах персонажа.
                sectionsBar()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(orderedFeats, key = { it.id }) { feat ->
                    FeatCard(
                        feat = feat,
                        onDiceClick = onDiceClick,
                        isDragging = dragState.draggingId == feat.id,
                        dragModifier = Modifier.dragToReorder(
                            state = dragState,
                            id = feat.id,
                            currentOrder = orderedFeats.map { it.id },
                            onReorder = onReorder,
                        ),
                        onToggle = { onToggleCollapsed(feat.id, !feat.collapsed) },
                        onEdit = { editingFeat = feat },
                        onDelete = { featPendingDeletion = feat },
                    )
                }
            }
            }
        }
    }

    if (showManualDialog) {
        FeatEditorDialog(
            title = stringResource(R.string.feats_new),
            initialName = "",
            initialDescription = "",
            onDismiss = { showManualDialog = false },
            onConfirm = { name, description ->
                onAddFeat(name, description)
                showManualDialog = false
            },
        )
    }
    editingFeat?.let { feat ->
        FeatEditorDialog(
            title = stringResource(R.string.feats_edit),
            initialName = feat.name,
            initialDescription = feat.description,
            onDismiss = { editingFeat = null },
            onConfirm = { name, description ->
                // Черта общая: правка отразится у всех персонажей, которые её взяли.
                onSaveFeat(
                    Feat(
                        id = feat.id,
                        name = name.trim(),
                        description = description.trim(),
                        source = feat.source,
                        createdAt = feat.createdAt,
                    ),
                )
                editingFeat = null
            },
        )
    }
    if (showUrlDialog) {
        FeatUrlDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                onLoadFromDndSu(url)
                showUrlDialog = false
            },
        )
    }
    featPendingDeletion?.let { feat ->
        AlertDialog(
            onDismissRequest = { featPendingDeletion = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.feats_remove_title)) },
            text = { Text(stringResource(R.string.feats_remove_text, feat.name)) },
            confirmButton = {
                Button(onClick = {
                    onRemoveFromCharacter(feat.id)
                    featPendingDeletion = null
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { featPendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Карточка черты: заголовок со сворачиванием и текст описания. */
@Composable
private fun FeatCard(
    feat: CharacterFeat,
    onDiceClick: (String) -> Unit,
    isDragging: Boolean,
    /** Модификатор перетаскивания для смены порядка черт. */
    dragModifier: Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = FEAT_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().then(dragModifier),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isDragging, onClick = onToggle)
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        feat.name.ifBlank { stringResource(R.string.feats_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (feat.collapsed && feat.preview.isNotBlank()) {
                        Text(
                            feat.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.feats_edit_action),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.feats_remove_action),
                    )
                }
                Icon(
                    imageVector = if (feat.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = stringResource(
                        if (feat.collapsed) R.string.action_expand else R.string.action_collapse,
                    ),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }

            AnimatedVisibility(visible = !feat.collapsed) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    // Описание переиспользует разметку заклинаний: кости кликабельны,
                    // а термины из ссылок dnd.su выделяются.
                    DescriptionText(description = feat.description, onDiceClick = onDiceClick)
                }
            }
        }
    }
}

@Composable
private fun FeatEditorDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
            label = { Text(stringResource(R.string.form_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
            label = { Text(stringResource(R.string.field_description)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, description) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun FeatUrlDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.dndsu_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
            label = { Text(stringResource(R.string.feats_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                stringResource(R.string.feats_url_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_load))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
