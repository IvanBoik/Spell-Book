package com.example.spellbook.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
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



import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.ui.humanizeFormula
import com.example.spellbook.ui.spellOptionLabel
import com.example.spellbook.ui.spellOptionPairs
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets
import com.example.spellbook.ui.components.FormulaTextField
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
    sectionsBar: @Composable () -> Unit = {},
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
                title = stringResource(R.string.combos_title),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                    Icon(
                        Icons.Default.LibraryBooks,
                        contentDescription = stringResource(R.string.combos_step_library),
                    )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.combos_create),
                )
            }
        },
    ) { padding ->
        if (combos.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                sectionsBar()
                EmptyState(
                title = stringResource(R.string.combos_empty_title),
                message = stringResource(R.string.combos_empty_text),
                button = stringResource(R.string.combos_create),
                    onClick = onCreate,
                )
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
                    description = stringResource(R.string.combos_edit_named),
                    background = EDIT_ACTION_COLOR,
                    contentColor = Color.Black,
                    onClick = {
                        animateTo(0f)
                        onEdit()
                    },
                )
                SwipeActionButton(
                    icon = Icons.Default.Delete,
                    description = stringResource(R.string.combos_delete_named),
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
                combo.name.ifBlank { stringResource(R.string.combos_untitled) },
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
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.combos_roll),
                    )
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
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.combos_special_rolls),
            )
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
                    text = { Text(stringResource(mode.labelRes)) },
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
                title = stringResource(
                    if (onDelete == null) R.string.combos_new else R.string.combos_edit,
                ),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onSave, enabled = combo.name.isNotBlank()) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_save),
                    )
                    }
                },
            )
        },
        // Поле названия не должно перекрываться клавиатурой.
        contentWindowInsets = imeAwareContentInsets,
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
                    label = { Text(stringResource(R.string.combos_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStepPicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = null)
                    Text(
                        stringResource(R.string.combos_from_library),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    }
                    OutlinedButton(
                        onClick = { editingStep = ComboStep(characterId = combo.characterId, name = "") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.combos_new_step),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.combos_steps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (selected.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.combos_add_step),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(selected, key = { it.id }) { step ->
                StepCard(
                    step = step,
                    actions = {
                        IconButton(onClick = { onMoveStep(step.id, -1) }) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = stringResource(R.string.combos_move_up),
                            )
                        }
                        IconButton(onClick = { onMoveStep(step.id, 1) }) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = stringResource(R.string.combos_move_down),
                            )
                        }
                        IconButton(onClick = { editingStep = step }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.combos_edit_shared_step),
                            )
                        }
                        IconButton(onClick = { onToggleStep(step.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.combos_remove_step),
                            )
                        }
                    },
                )
            }
            item {
                Button(onClick = onSave, enabled = combo.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.combos_save))
                }
            }
            if (onDelete != null) {
                item {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(
                            stringResource(R.string.combos_delete),
                            modifier = Modifier.padding(start = 8.dp),
                        )
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
        topBar = {
            DndTopBar(
                stringResource(R.string.combos_library_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ComboStep(characterId = characterId, name = "") }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.combos_library_add),
                )
            }
        },
    ) { padding ->
        if (steps.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.combos_library_empty_title),
                message = stringResource(R.string.combos_library_empty_text),
                button = stringResource(R.string.combos_library_add),
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
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                        )
                        }
                        IconButton(onClick = { onDelete(step.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
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
                        Text(stringResource(result.mode.labelRes), style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val buttonHeightPx = with(density) { REROLL_BUTTON_HEIGHT.toPx() }
        val edgePaddingPx = with(density) { REROLL_EDGE_PADDING.toPx() }

        // Кнопка всегда одна и та же: она следует за своим местом в списке,
        // пока то не уйдёт ниже экрана, а дальше остаётся внизу. Переход привязан
        // к прокрутке, поэтому происходит плавно, без резких переключений.
        var containerTopPx by remember { mutableFloatStateOf(0f) }
        var containerHeightPx by remember { mutableFloatStateOf(0f) }
        var anchorTopPx by remember { mutableStateOf<Float?>(null) }

        val pinnedTopPx = (containerHeightPx - buttonHeightPx - edgePaddingPx).coerceAtLeast(0f)
        val buttonTopPx = anchorTopPx
            ?.minus(containerTopPx)
            ?.coerceAtMost(pinnedTopPx)
            ?: pinnedTopPx

        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    containerTopPx = coordinates.positionInWindow().y
                    containerHeightPx = coordinates.size.height.toFloat()
                },
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(stringResource(R.string.combos_total), style = MaterialTheme.typography.titleMedium)
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
                            // Слагаемые с подставленными значениями: `6 + 2 + 5`.
                            Text(
                                stepResult.breakdown.ifBlank { stepResult.total.toString() },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Исходная запись шага с читаемыми названиями переменных.
                            Text(
                                humanizeFormula(stepResult.step.formula),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (stepResult.step.damageType.isNotBlank()) {
                                Text(
                                    spellOptionLabel(SpellOptions.damageTypes, stepResult.step.damageType),
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
                item {
                    Text(
                        stringResource(R.string.combos_extra_effects),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(result.effects) { effect ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(effect, Modifier.padding(12.dp))
                    }
                }
            }
            // Место кнопки в потоке: сама кнопка рисуется поверх списка.
            item(key = REROLL_ITEM_KEY) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(REROLL_BUTTON_HEIGHT)
                        .onGloballyPositioned { anchorTopPx = it.positionInWindow().y },
                )
                // Когда место уходит из композиции, кнопка возвращается вниз экрана.
                DisposableEffect(Unit) {
                    onDispose { anchorTopPx = null }
                }
            }
        }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = buttonTopPx }
                    .padding(horizontal = REROLL_EDGE_PADDING),
            ) {
                RerollButton(mode = result.mode, onReroll = onReroll)
            }
        }
    }
}

/** Ключ элемента, резервирующего место кнопки в списке. */
private const val REROLL_ITEM_KEY = "reroll-button"

private val REROLL_BUTTON_HEIGHT = 48.dp
private val REROLL_EDGE_PADDING = 16.dp

@Composable
private fun RerollButton(mode: ComboRollMode, onReroll: (ComboRollMode) -> Unit) {
    Button(
        onClick = { onReroll(mode) },
        modifier = Modifier.fillMaxWidth().height(REROLL_BUTTON_HEIGHT),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Text(
            stringResource(R.string.combo_reroll, stringResource(mode.labelRes)),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StepCard(step: ComboStep, actions: @Composable RowScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(step.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(step.formula, color = MaterialTheme.colorScheme.primary)
                val damageLabel = spellOptionLabel(SpellOptions.damageTypes, step.damageType)
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
        title = { Text(stringResource(R.string.combos_pick_steps)) },
        text = {
            if (steps.isEmpty()) Text(stringResource(R.string.combos_pick_empty))
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.combos_done)) }
        },
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
    // Одно поле задаёт и кости, и числа, и переменные: `6d8 + [int]`.
    var expression by remember { mutableStateOf(if (isNewStep) "" else initial.formula) }
    var damageType by remember { mutableStateOf(initial.damageType) }
    var effect by remember { mutableStateOf(initial.effect) }
    var damageExpanded by remember { mutableStateOf(false) }
    val expressionValid = StatFormula.isValid(expression)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.combos_step_title)) },
        text = {
            // При открытой клавиатуре диалог сжимается, поэтому содержимое прокручивается.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.form_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormulaTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = stringResource(R.string.combos_step_value),
                    placeholder = "1d8 + [str]",
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = damageExpanded,
                    onExpandedChange = { damageExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (damageType.isBlank()) {
                            stringResource(R.string.combo_no_damage_type)
                        } else {
                            spellOptionLabel(SpellOptions.damageTypes, damageType)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.combo_damage_type)) },
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
                            text = { Text(stringResource(R.string.combo_no_damage_type)) },
                            onClick = {
                                damageType = ""
                                damageExpanded = false
                            },
                        )
                        spellOptionPairs(SpellOptions.damageTypes).forEach { (code, label) ->
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
                    label = { Text(stringResource(R.string.combos_step_effect)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && expression.isNotBlank() && expressionValid,
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            expression = expression.trim(),
                            damageType = damageType,
                            effect = effect.trim(),
                        ),
                    )
                },
        ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
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
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
        )
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
