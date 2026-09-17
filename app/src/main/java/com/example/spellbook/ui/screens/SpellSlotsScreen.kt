package com.example.spellbook.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.FormulaTextField
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Экран всех расходуемых ресурсов персонажа. Ячейки показываются кружками,
 * пользовательские ресурсы — компактным счётчиком «текущее / максимум».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellSlotsScreen(
    character: Character,
    onUseSlot: (level: Int) -> Unit,
    onRestoreSlot: (level: Int) -> Unit,
    onUseResource: (resourceId: String) -> Unit,
    onRestoreResourceUnit: (resourceId: String) -> Unit,
    onRestoreResource: (resourceId: String) -> Unit,
    onDeleteResource: (resourceId: String) -> Unit,
    onAddResource: (name: String, description: String, maximum: Int, formula: String) -> Unit,
    onEditResource: (
        resourceId: String,
        name: String,
        description: String,
        maximum: Int,
        formula: String,
    ) -> Unit,
    onReorderResources: (List<String>) -> Unit,
    onRestoreAll: () -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    val levels = (1..9).filter { (character.spellSlots[it] ?: 0) > 0 }
    var editingResource by remember { mutableStateOf<CharacterResource?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var viewedResource by remember { mutableStateOf<CharacterResource?>(null) }
    var draggingResourceId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    /** Оптимистичный порядок: не даёт списку откатиться до обновления из БД. */
    var localResourceOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val density = LocalDensity.current

    val resourceIds = character.resources.mapTo(mutableSetOf()) { it.id }
    val effectiveResourceOrder = localResourceOrder.filter { it in resourceIds } +
        character.resources.map { it.id }.filterNot { it in localResourceOrder }
    val orderedResources = effectiveResourceOrder.mapNotNull { id ->
        character.resources.firstOrNull { it.id == id }
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(R.string.resources_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRestoreAll) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.resources_restore_all),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.resources_add))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Панель вне списка: отступы одинаковы на всех экранах персонажа.
            sectionsBar()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (levels.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.resources_spell_slots),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(levels, key = { "slot_$it" }) { level ->
                    SlotLevelRow(
                        level = level,
                        total = character.spellSlots[level] ?: 0,
                        used = character.spellSlotsUsed[level] ?: 0,
                        onUse = { onUseSlot(level) },
                        onRestore = { onRestoreSlot(level) },
                    )
                }
            }

            if (levels.isNotEmpty() && character.resources.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            if (character.resources.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.resources_other),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(orderedResources, key = { it.id }) { resource ->
                    val isDragged = draggingResourceId == resource.id
                    SwipeableResourceRow(
                        resource = resource,
                        onOpen = { viewedResource = resource },
                        onEdit = { editingResource = resource },
                        onUse = { onUseResource(resource.id) },
                        onRestoreUnit = { onRestoreResourceUnit(resource.id) },
                        onRestore = { onRestoreResource(resource.id) },
                        onDelete = { onDeleteResource(resource.id) },
                        isDragging = isDragged,
                        dragTranslationY = if (isDragged) dragDistance else 0f,
                        onDragStart = {
                            localResourceOrder = orderedResources.map { it.id }
                            draggingResourceId = resource.id
                            dragDistance = 0f
                        },
                        onDrag = { delta ->
                            dragDistance += delta
                            val stepPx = with(density) { RESOURCE_DRAG_STEP.toPx() }

                            // Переставляем сразу после пересечения соседней позиции, а компенсация
                            // смещения удерживает карточку под пальцем.
                            while (dragDistance >= stepPx) {
                                val index = localResourceOrder.indexOf(resource.id)
                                if (index < 0 || index >= localResourceOrder.lastIndex) break
                                localResourceOrder = localResourceOrder.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                }
                                dragDistance -= stepPx
                            }
                            while (dragDistance <= -stepPx) {
                                val index = localResourceOrder.indexOf(resource.id)
                                if (index <= 0) break
                                localResourceOrder = localResourceOrder.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                }
                                dragDistance += stepPx
                            }
                        },
                        onDragEnd = {
                            onReorderResources(localResourceOrder)
                            draggingResourceId = null
                            dragDistance = 0f
                        },
                    )
                }
            }

            if (levels.isEmpty() && character.resources.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.resources_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.resources_empty_text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(
                                    stringResource(R.string.resources_add),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showCreateDialog) {
        ResourceEditorDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, maximum, formula ->
                onAddResource(name, description, maximum, formula)
                showCreateDialog = false
            },
        )
    }
    editingResource?.let { resource ->
        ResourceEditorDialog(
            initial = resource,
            onDismiss = { editingResource = null },
            onSave = { name, description, maximum, formula ->
                onEditResource(resource.id, name, description, maximum, formula)
                editingResource = null
            },
        )
    }
    viewedResource?.let { resource ->
        ResourceDescriptionDialog(resource = resource, onDismiss = { viewedResource = null })
    }
}

private val RESOURCE_CARD_SHAPE = RoundedCornerShape(12.dp)
private val RESOURCE_SWIPE_ACTION_SIZE = 48.dp
private val RESOURCE_SWIPE_GAP = 12.dp
private val RESOURCE_EDIT_COLOR = Color(0xFFFBC02D)
private const val RESOURCE_SWIPE_ANIMATION_MS = 220
private const val RESOURCE_SWIPE_THRESHOLD = 0.35f

/** Шаг перестановки при перетаскивании — примерная высота карточки ресурса с отступом. */
private val RESOURCE_DRAG_STEP = 140.dp

@Composable
private fun SwipeableResourceRow(
    resource: CharacterResource,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onUse: () -> Unit,
    onRestoreUnit: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isDragging: Boolean,
    dragTranslationY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(resource.id) { Animatable(0f) }
    val revealPx = with(LocalDensity.current) {
        (RESOURCE_SWIPE_ACTION_SIZE * 2 + RESOURCE_SWIPE_GAP * 3).toPx()
    }

    // pointerInput живёт дольше одной рекомпозиции: без rememberUpdatedState он вызывал бы
    // callback-и, захватившие устаревший порядок ресурсов.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    fun animateTo(value: Float) {
        scope.launch { offsetX.animateTo(value, tween(RESOURCE_SWIPE_ANIMATION_MS)) }
    }

    Box(Modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)) {
        // Во время вертикального переноса действия свайпа скрыты и не просвечивают под карточкой.
        if (!isDragging) {
            Row(
                modifier = Modifier.matchParentSize().padding(end = RESOURCE_SWIPE_GAP),
                horizontalArrangement = Arrangement.spacedBy(RESOURCE_SWIPE_GAP, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResourceSwipeAction(
                    icon = Icons.Default.Edit,
                    description = stringResource(R.string.resources_edit_named, resource.name),
                    background = RESOURCE_EDIT_COLOR,
                    contentColor = Color.Black,
                    onClick = { animateTo(0f); onEdit() },
                )
                ResourceSwipeAction(
                    icon = Icons.Default.Delete,
                    description = stringResource(R.string.resources_delete_named, resource.name),
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = { animateTo(0f); onDelete() },
                )
            }
        }

        Card(
            shape = RESOURCE_CARD_SHAPE,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        offsetX.value.roundToInt(),
                        if (isDragging) dragTranslationY.roundToInt() else 0,
                    )
                }
                .zIndex(if (isDragging) 1f else 0f)
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                            }
                        },
                        onDragEnd = {
                            animateTo(
                                if (offsetX.value <= -revealPx * RESOURCE_SWIPE_THRESHOLD) -revealPx else 0f,
                            )
                        },
                        onDragCancel = { animateTo(0f) },
                    )
                }
                // Ключ не зависит от isDragging: иначе рекомпозиция отменит активный жест.
                .pointerInput(resource.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            scope.launch { offsetX.snapTo(0f) }
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
                .clickable(enabled = !isDragging, onClick = onOpen),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(resource.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${resource.current} / ${resource.maximum}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onRestore, enabled = resource.current < resource.maximum) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.resources_restore, resource.name),
                )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onUse, enabled = resource.current > 0) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                        Text(stringResource(R.string.resources_spend_action))
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = onRestoreUnit, enabled = resource.current < resource.maximum) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.resources_return_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceSwipeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(RESOURCE_SWIPE_ACTION_SIZE)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = contentColor)
    }
}

@Composable
private fun ResourceEditorDialog(
    initial: CharacterResource?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, maximum: Int, maximumFormula: String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    // В одном поле допустимо число или формула вида `[pb] * 2`.
    var maximum by remember(initial?.id) {
        mutableStateOf(
            initial?.maximumFormula?.takeIf { it.isNotBlank() }
                ?: initial?.maximum?.toString().orEmpty(),
        )
    }
    val parsedMaximum = maximum.trim().toIntOrNull() ?: 0
    val isFormula = StatFormula.isFormula(maximum)
    // Лимит ресурса должен быть предсказуемым, поэтому кости здесь недопустимы.
    val hasDice = StatFormula.hasDice(maximum)
    val formulaValid = StatFormula.isValid(maximum) && !hasDice
    val valid = name.isNotBlank() && formulaValid && (parsedMaximum > 0 || isFormula)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.resources_new else R.string.resources_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                label = { Text(stringResource(R.string.form_name)) },
                placeholder = { Text(stringResource(R.string.resources_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    placeholder = { Text(stringResource(R.string.resources_description_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormulaTextField(
                    value = maximum,
                    onValueChange = { maximum = it },
                label = stringResource(R.string.resources_maximum),
                    placeholder = "[pb] + 1",
                    isError = hasDice,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        description.trim(),
                        parsedMaximum,
                        if (isFormula) maximum.trim() else "",
                    )
                },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ResourceDescriptionDialog(
    resource: CharacterResource,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(resource.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${resource.current} / ${resource.maximum}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                resource.description.ifBlank { stringResource(R.string.resources_no_description) },
                    color = if (resource.description.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun SlotLevelRow(
    level: Int,
    total: Int,
    used: Int,
    onUse: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
            stringResource(R.string.spell_slot_level, level),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${total - used} / $total", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(total) { index ->
                    val isUsed = index >= total - used
                    SlotDot(isUsed = isUsed, onClick = { if (isUsed) onRestore() else onUse() })
                }
            }
        }
    }
}

@Composable
private fun SlotDot(isUsed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            .background(
                if (isUsed) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            .clickable(onClick = onClick),
    )
}
