package com.example.spellbook.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.COMBO_DICE_SIDES
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.ui.components.DndTopBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ComboListScreen(
    combos: List<Combo>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onRoll: (String, ComboRollMode) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var draggingComboId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    /** Оптимистичный порядок: не даёт списку откатиться, пока Room Flow не обновился. */
    var localOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val density = LocalDensity.current

    val comboIds = combos.mapTo(mutableSetOf()) { it.id }
    val effectiveOrder = localOrder.filter { it in comboIds } +
        combos.map { it.id }.filterNot { it in localOrder }
    val orderedCombos = effectiveOrder.mapNotNull { id -> combos.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            DndTopBar(
                title = "Комбинации",
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = "Библиотека шагов")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "Создать комбинацию")
            }
        },
    ) { padding ->
        if (combos.isEmpty()) {
            EmptyState(
                title = "Комбинаций пока нет",
                message = "Соберите последовательность атак, заклинаний и дополнительных эффектов.",
                button = "Создать комбинацию",
                onClick = onCreate,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(orderedCombos, key = { it.id }) { combo ->
                    val isDragged = draggingComboId == combo.id
                    SwipeableComboCard(
                        combo = combo,
                        onRun = { mode -> onRoll(combo.id, mode) },
                        onEdit = { onEdit(combo.id) },
                        onDelete = { onDelete(combo.id) },
                        isDragging = isDragged,
                        dragTranslationY = if (isDragged) dragDistance else 0f,
                        onDragStart = {
                            localOrder = orderedCombos.map { it.id }
                            draggingComboId = combo.id
                            dragDistance = 0f
                        },
                        onDrag = { delta ->
                            dragDistance += delta
                            val stepPx = with(density) { COMBO_DRAG_STEP.toPx() }

                            // Переставляем сразу после пересечения соседней позиции: соседи освобождают
                            // место, а компенсация смещения удерживает карточку под пальцем.
                            while (dragDistance >= stepPx) {
                                val index = localOrder.indexOf(combo.id)
                                if (index < 0 || index >= localOrder.lastIndex) break
                                localOrder = localOrder.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                }
                                dragDistance -= stepPx
                            }
                            while (dragDistance <= -stepPx) {
                                val index = localOrder.indexOf(combo.id)
                                if (index <= 0) break
                                localOrder = localOrder.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                }
                                dragDistance += stepPx
                            }
                        },
                        onDragEnd = {
                            onReorder(localOrder)
                            draggingComboId = null
                            dragDistance = 0f
                        },
                    )
                }
            }
        }
    }
}

private val COMBO_CARD_SHAPE = RoundedCornerShape(12.dp)
private val SWIPE_ACTION_SIZE = 48.dp
private val SWIPE_ACTION_GAP = 12.dp
private val EDIT_ACTION_COLOR = Color(0xFFFBC02D)
private const val SWIPE_ANIMATION_MS = 220
private const val SWIPE_OPEN_THRESHOLD = 0.35f

/** Шаг перестановки при вертикальном перетаскивании — примерная высота карточки с отступом. */
private val COMBO_DRAG_STEP = 84.dp

/**
 * Карточка комбинации: обычный запуск по Play, специальные режимы в меню,
 * редактирование и удаление открываются свайпом влево,
 * а долгое нажатие включает вертикальное перетаскивание.
 */
@Composable
private fun SwipeableComboCard(
    combo: Combo,
    onRun: (ComboRollMode) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDragging: Boolean,
    dragTranslationY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(combo.id) { Animatable(0f) }
    val revealPx = with(LocalDensity.current) {
        (SWIPE_ACTION_SIZE * 2 + SWIPE_ACTION_GAP * 3).toPx()
    }

    // pointerInput живёт дольше одной рекомпозиции: без rememberUpdatedState он вызывал бы
    // callback-и, захватившие устаревший порядок комбинаций.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    fun animateTo(value: Float) {
        scope.launch { offsetX.animateTo(value, tween(SWIPE_ANIMATION_MS)) }
    }

    Box(Modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)) {
        // Во время вертикального переноса действия свайпа скрыты и не просвечивают под карточкой.
        if (!isDragging) {
            Row(
                modifier = Modifier.matchParentSize().padding(end = SWIPE_ACTION_GAP),
                horizontalArrangement = Arrangement.spacedBy(SWIPE_ACTION_GAP, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeActionButton(
                    icon = Icons.Default.Edit,
                    description = "Редактировать комбинацию",
                    background = EDIT_ACTION_COLOR,
                    contentColor = Color.Black,
                    onClick = {
                        animateTo(0f)
                        onEdit()
                    },
                )
                SwipeActionButton(
                    icon = Icons.Default.Delete,
                    description = "Удалить комбинацию",
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = {
                        animateTo(0f)
                        onDelete()
                    },
                )
            }
        }

        Card(
            shape = COMBO_CARD_SHAPE,
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
                            val shouldOpen = offsetX.value <= -revealPx * SWIPE_OPEN_THRESHOLD
                            animateTo(if (shouldOpen) -revealPx else 0f)
                        },
                        onDragCancel = { animateTo(0f) },
                    )
                }
                // Ключ не зависит от isDragging: иначе рекомпозиция отменит активный жест.
                .pointerInput(combo.id) {
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
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    combo.name.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                FilledTonalIconButton(
                    onClick = { onRun(ComboRollMode.NORMAL) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Запустить комбинацию")
                }
                SpecialRollMenu(onRun = onRun)
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SWIPE_ACTION_SIZE)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = contentColor)
    }
}

@Composable
private fun SpecialRollMenu(onRun: (ComboRollMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Особые запуски")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            listOf(
                ComboRollMode.MAXIMUM,
                ComboRollMode.CRITICAL_CLASSIC,
                ComboRollMode.CRITICAL_HOMEBREW,
            ).forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onRun(mode)
                    },
                )
            }
        }
    }
}

@Composable
fun ComboEditorScreen(
    combo: Combo,
    allSteps: List<ComboStep>,
    selectedStepIds: List<String>,
    onNameChange: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onMoveStep: (String, Int) -> Unit,
    onSaveStep: (ComboStep, Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var showStepPicker by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<ComboStep?>(null) }
    val selected = selectedStepIds.mapNotNull { id -> allSteps.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            DndTopBar(
                title = if (onDelete == null) "Новая комбинация" else "Редактор комбинации",
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onSave, enabled = combo.name.isNotBlank()) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = combo.name,
                    onValueChange = onNameChange,
                    label = { Text("Название комбинации") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStepPicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = null)
                        Text("Из библиотеки", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { editingStep = ComboStep(characterId = combo.characterId, name = "") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Новый шаг", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item { Text("Шаги комбинации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (selected.isEmpty()) {
                item { Text("Добавьте хотя бы один шаг.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(selected, key = { it.id }) { step ->
                StepCard(
                    step = step,
                    actions = {
                        IconButton(onClick = { onMoveStep(step.id, -1) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Выше")
                        }
                        IconButton(onClick = { onMoveStep(step.id, 1) }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Ниже")
                        }
                        IconButton(onClick = { editingStep = step }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать общий шаг")
                        }
                        IconButton(onClick = { onToggleStep(step.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Убрать из комбинации")
                        }
                    },
                )
            }
            item {
                Button(onClick = onSave, enabled = combo.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить комбинацию")
                }
            }
            if (onDelete != null) {
                item {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Удалить комбинацию", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (showStepPicker) {
        StepPickerDialog(
            steps = allSteps,
            selectedIds = selectedStepIds.toSet(),
            onToggle = onToggleStep,
            onDismiss = { showStepPicker = false },
        )
    }
    editingStep?.let { step ->
        ComboStepEditorDialog(
            initial = step,
            onDismiss = { editingStep = null },
            onConfirm = { saved ->
                onSaveStep(saved, step.id !in allSteps.map(ComboStep::id))
                editingStep = null
            },
        )
    }
}

@Composable
fun StepLibraryScreen(
    characterId: String,
    steps: List<ComboStep>,
    onSave: (ComboStep) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<ComboStep?>(null) }
    Scaffold(
        topBar = { DndTopBar("Библиотека шагов", navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ComboStep(characterId = characterId, name = "") }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить шаг")
            }
        },
    ) { padding ->
        if (steps.isEmpty()) {
            EmptyState(
                title = "Шагов пока нет",
                message = "Созданные шаги можно использовать сразу в нескольких комбинациях.",
                button = "Добавить шаг",
                onClick = { editing = ComboStep(characterId = characterId, name = "") },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(steps, key = { it.id }) { step ->
                    StepCard(step) {
                        IconButton(onClick = { editing = step }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
                        IconButton(onClick = { onDelete(step.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            }
        }
    }
    editing?.let { step ->
        ComboStepEditorDialog(
            initial = step,
            onDismiss = { editing = null },
            onConfirm = { onSave(it); editing = null },
        )
    }
}

@Composable
fun ComboResultScreen(
    result: ComboRollResult,
    onReroll: (ComboRollMode) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            DndTopBar(
                title = {
                    Column {
                        Text(result.combo.name)
                        Text(result.mode.label, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Итог", style = MaterialTheme.typography.titleMedium)
                        Text(result.total.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(result.steps, key = { it.step.id }) { stepResult ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stepResult.step.name, style = MaterialTheme.typography.titleMedium)
                            val details = if (stepResult.rolls.isEmpty()) {
                                stepResult.step.formula
                            } else {
                                stepResult.rolls.joinToString(" + ") +
                                    if (stepResult.step.modifier != 0) " ${if (stepResult.step.modifier > 0) "+" else "-"} ${kotlin.math.abs(stepResult.step.modifier)}" else ""
                            }
                            Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (stepResult.step.damageType.isNotBlank()) {
                                Text(
                                    SpellOptions.labelFor(SpellOptions.damageTypes, stepResult.step.damageType),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (stepResult.step.effect.isNotBlank()) {
                                Text(stepResult.step.effect, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stepResult.total.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (result.effects.isNotEmpty()) {
                item { Text("Дополнительные эффекты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(result.effects) { effect ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(effect, Modifier.padding(12.dp))
                    }
                }
            }
            item {
                Button(
                    onClick = { onReroll(result.mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Перебросить (${result.mode.label})", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: ComboStep, actions: @Composable RowScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(step.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(step.formula, color = MaterialTheme.colorScheme.primary)
                val damageLabel = SpellOptions.labelFor(SpellOptions.damageTypes, step.damageType)
                if (step.damageType.isNotBlank()) {
                    Text(damageLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (step.effect.isNotBlank()) {
                    Text(step.effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(content = actions)
        }
    }
}

@Composable
private fun StepPickerDialog(
    steps: List<ComboStep>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаги из библиотеки") },
        text = {
            if (steps.isEmpty()) Text("Библиотека пуста. Создайте новый шаг.")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(steps, key = { it.id }) { step ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = step.id in selectedIds, onCheckedChange = { onToggle(step.id) })
                        Column {
                            Text(step.name, fontWeight = FontWeight.Bold)
                            Text(step.formula, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboStepEditorDialog(
    initial: ComboStep,
    onDismiss: () -> Unit,
    onConfirm: (ComboStep) -> Unit,
) {
    val isNewStep = initial.name.isBlank()
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.stepType) }
    var diceCount by remember { mutableStateOf(if (isNewStep) "" else initial.diceCount.toString()) }
    var diceSides by remember { mutableStateOf(initial.diceSides) }
    var modifier by remember { mutableStateOf(if (isNewStep) "" else initial.modifier.toString()) }
    var flatValue by remember { mutableStateOf(if (isNewStep) "" else initial.flatValue.toString()) }
    var damageType by remember { mutableStateOf(initial.damageType) }
    var effect by remember { mutableStateOf(initial.effect) }
    var sidesExpanded by remember { mutableStateOf(false) }
    var damageExpanded by remember { mutableStateOf(false) }

    fun signed(value: String): String = value.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Шаг комбинации") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ComboStepType.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = type == value,
                            onClick = { type = value },
                            shape = SegmentedButtonDefaults.itemShape(index, ComboStepType.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        ) { Text(value.label) }
                    }
                }
                if (type == ComboStepType.DICE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepNumberField(
                            label = "Кол-во",
                            value = diceCount,
                            placeholder = "1",
                            onValueChange = { diceCount = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Куб",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                            ExposedDropdownMenuBox(
                                expanded = sidesExpanded,
                                onExpandedChange = { sidesExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = "к$diceSides",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sidesExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = sidesExpanded,
                                    onDismissRequest = { sidesExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                ) {
                                    COMBO_DICE_SIDES.forEach { sides ->
                                        DropdownMenuItem(text = { Text("к$sides") }, onClick = {
                                            diceSides = sides
                                            sidesExpanded = false
                                        })
                                    }
                                }
                            }
                        }
                        StepNumberField(
                            label = "Мод.",
                            value = modifier,
                            placeholder = "0",
                            onValueChange = { modifier = signed(it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    StepNumberField(
                        label = "Значение",
                        value = flatValue,
                        placeholder = "0",
                        onValueChange = { flatValue = signed(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = damageExpanded,
                    onExpandedChange = { damageExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (damageType.isBlank()) "Без типа" else SpellOptions.labelFor(SpellOptions.damageTypes, damageType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Тип урона") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(damageExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = damageExpanded,
                        onDismissRequest = { damageExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Без типа") },
                            onClick = {
                                damageType = ""
                                damageExpanded = false
                            },
                        )
                        SpellOptions.damageTypes.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    damageType = code
                                    damageExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = effect,
                    onValueChange = { effect = it },
                    label = { Text("Дополнительный эффект") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            type = type.name,
                            diceCount = diceCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            diceSides = diceSides,
                            modifier = modifier.toIntOrNull() ?: 0,
                            flatValue = flatValue.toIntOrNull() ?: 0,
                            damageType = damageType,
                            effect = effect.trim(),
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Числовое поле с постоянно закреплённой подписью и приглушённым плейсхолдером. */
@Composable
private fun StepNumberField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(button, Modifier.padding(start = 8.dp))
            }
        }
    }
}
