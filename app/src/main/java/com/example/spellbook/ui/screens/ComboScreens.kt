package com.example.spellbook.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.model.COMBO_DICE_SIDES
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepType
import com.example.spellbook.ui.components.DndTopBar

@Composable
fun ComboListScreen(
    combos: List<Combo>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onRoll: (String, ComboRollMode) -> Unit,
    onBack: () -> Unit,
) {
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
                items(combos, key = { it.id }) { combo ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(combo.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onEdit(combo.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ComboRollMode.entries.forEach { mode ->
                                    OutlinedButton(
                                        onClick = { onRoll(combo.id, mode) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    ) { Text(mode.label, maxLines = 1) }
                                }
                            }
                        }
                    }
                }
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
                    Button(onClick = { showStepPicker = true }, modifier = Modifier.weight(1f)) {
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
                Card {
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
                items(result.effects) { effect -> Card { Text(effect, Modifier.padding(12.dp)) } }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComboRollMode.entries.forEach { mode ->
                        OutlinedButton(onClick = { onReroll(mode) }, modifier = Modifier.weight(1f)) {
                            if (mode == result.mode) Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(mode.label, maxLines = 1)
                        }
                    }
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
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.stepType) }
    var diceCount by remember { mutableStateOf(initial.diceCount.toString()) }
    var diceSides by remember { mutableStateOf(initial.diceSides) }
    var modifier by remember { mutableStateOf(initial.modifier.toString()) }
    var flatValue by remember { mutableStateOf(initial.flatValue.toString()) }
    var effect by remember { mutableStateOf(initial.effect) }
    var sidesExpanded by remember { mutableStateOf(false) }

    fun signed(value: String): String = value.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        ) { Text(value.label) }
                    }
                }
                if (type == ComboStepType.DICE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = diceCount,
                            onValueChange = { diceCount = it.filter(Char::isDigit) },
                            label = { Text("Кол-во") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        ExposedDropdownMenuBox(
                            expanded = sidesExpanded,
                            onExpandedChange = { sidesExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = "к$diceSides",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Куб") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sidesExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = sidesExpanded, onDismissRequest = { sidesExpanded = false }) {
                                COMBO_DICE_SIDES.forEach { sides ->
                                    DropdownMenuItem(text = { Text("к$sides") }, onClick = {
                                        diceSides = sides
                                        sidesExpanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = modifier,
                            onValueChange = { modifier = signed(it) },
                            label = { Text("Мод.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = flatValue,
                        onValueChange = { flatValue = signed(it) },
                        label = { Text("Значение") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                            effect = effect.trim(),
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
