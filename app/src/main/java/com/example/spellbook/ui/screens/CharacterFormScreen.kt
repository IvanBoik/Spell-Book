package com.example.spellbook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.DndTopBar

/** Уровни ячеек заклинаний D&D: 1..9. */
private val SLOT_LEVELS = (1..9).toList()

/**
 * Форма создания/редактирования персонажа и страница настройки его параметров:
 * имя, фото, переподготовка заклинаний (+ лимит) и доступные ячейки по уровням.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterFormScreen(
    initial: Character,
    isNew: Boolean,
    onSave: (Character) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }
    var canPrepare by remember { mutableStateOf(initial.canPrepareSpells) }
    var maxPrepared by remember { mutableStateOf(initial.maxPreparedSpells.takeIf { it > 0 }?.toString() ?: "") }
    var maxCantrips by remember { mutableStateOf(initial.maxCantrips.takeIf { it > 0 }?.toString() ?: "") }
    var maxAttunedItems by remember { mutableStateOf(initial.maxAttunedItems.toString()) }
    // Изменяемые значения ячеек
    val slots = remember {
        mutableStateMapOf<Int, String>().apply {
            SLOT_LEVELS.forEach { level ->
                put(level, initial.spellSlots[level]?.takeIf { it > 0 }?.toString() ?: "")
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            imageUri = uri.toString()
        }
    }

    fun buildCharacter(): Character {
        val slotsMap = slots.mapNotNull { (level, value) ->
            val count = value.toIntOrNull() ?: 0
            if (count > 0) level to count else null
        }.toMap()
        // Потраченные ячейки не должны превышать доступные после изменения максимума.
        val clampedUsed = initial.spellSlotsUsed
            .mapNotNull { (level, used) ->
                val total = slotsMap[level] ?: 0
                if (total > 0) level to minOf(used, total) else null
            }.toMap()
        return initial.copy(
            name = name.trim(),
            imageUri = imageUri,
            canPrepareSpells = canPrepare,
            maxPreparedSpells = if (canPrepare) maxPrepared.toIntOrNull() ?: 0 else 0,
            maxCantrips = maxCantrips.toIntOrNull() ?: 0,
            spellSlots = slotsMap,
            spellSlotsUsed = clampedUsed,
            maxAttunedItems = maxAttunedItems.toIntOrNull()?.coerceAtLeast(0) ?: 3,
        )
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = if (isNew) "Новый персонаж" else "Настройки персонажа",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(buildCharacter()) }, enabled = name.isNotBlank()) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Аватар и имя.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.clickable {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    CharacterAvatar(character = Character(name = name, imageUri = imageUri), size = 120)
                }
                OutlinedButton(onClick = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) {
                    Text(if (imageUri.isNullOrBlank()) "Выбрать фото" else "Изменить фото")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя персонажа") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            // Настройка переподготовки заклинаний.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Переподготовка заклинаний", fontWeight = FontWeight.Bold)
                    Text(
                        "Разделять известные и подготовленные заклинания",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = canPrepare, onCheckedChange = { canPrepare = it })
            }
            if (canPrepare) {
                OutlinedTextField(
                    value = maxPrepared,
                    onValueChange = { new -> maxPrepared = new.filter { it.isDigit() } },
                    label = { Text("Максимум подготовленных (без заговоров)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = maxCantrips,
                onValueChange = { new -> maxCantrips = new.filter { it.isDigit() } },
                label = { Text("Доступно заговоров") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("Магические предметы", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = maxAttunedItems,
                onValueChange = { new -> maxAttunedItems = new.filter { it.isDigit() } },
                label = { Text("Лимит настроенных предметов") },
                supportingText = { Text("Обычно персонаж может настроиться на 3 предмета") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            // Ячейки заклинаний по уровням.
            Text("Ячейки заклинаний", fontWeight = FontWeight.Bold)
            SLOT_LEVELS.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$level уровень", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = slots[level].orEmpty(),
                        onValueChange = { new -> slots[level] = new.filter { it.isDigit() } },
                        singleLine = true,
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(buildCharacter()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            if (onDelete != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить персонажа")
                }
            }
        }
    }
}
