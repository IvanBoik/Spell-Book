package com.example.spellbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.ui.components.DndTopBar

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
    onAddResource: (name: String, maximum: Int) -> Unit,
    onRestoreAll: () -> Unit,
    onBack: () -> Unit,
) {
    val levels = (1..9).filter { (character.spellSlots[it] ?: 0) > 0 }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DndTopBar(
                title = "Ресурсы",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onRestoreAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "Восполнить все ресурсы")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить ресурс")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (levels.isNotEmpty()) {
                item {
                    Text(
                        "Ячейки заклинаний",
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
                        "Другие ресурсы",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(character.resources, key = { it.id }) { resource ->
                    ResourceRow(
                        resource = resource,
                        onUse = { onUseResource(resource.id) },
                        onRestoreUnit = { onRestoreResourceUnit(resource.id) },
                        onRestore = { onRestoreResource(resource.id) },
                        onDelete = { onDeleteResource(resource.id) },
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
                                "Ресурсы пока не добавлены",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Добавьте очки, кости, заряды или настройте ячейки заклинаний.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Добавить ресурс", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddResourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, maximum ->
                onAddResource(name, maximum)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ResourceRow(
    resource: CharacterResource,
    onUse: () -> Unit,
    onRestoreUnit: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                    Icon(Icons.Default.Refresh, contentDescription = "Восполнить ${resource.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить ${resource.name}",
                        tint = MaterialTheme.colorScheme.error,
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
                    Text("Потратить")
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRestoreUnit, enabled = resource.current < resource.maximum) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Вернуть")
                }
            }
        }
    }
}

@Composable
private fun AddResourceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, maximum: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var maximum by remember { mutableStateOf("") }
    val parsedMaximum = maximum.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && parsedMaximum > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый ресурс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Например, очки чародейства") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maximum,
                    onValueChange = { value -> maximum = value.filter(Char::isDigit) },
                    label = { Text("Максимум") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim(), parsedMaximum) }, enabled = valid) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
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
                    "$level уровень",
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
