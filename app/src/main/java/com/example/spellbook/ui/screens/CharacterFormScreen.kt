package com.example.spellbook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.model.proficiencyBonusFor
import com.example.spellbook.ui.components.DndTopBar

/** Уровни ячеек заклинаний D&D: 1..9. */
private val SLOT_LEVELS = (1..9).toList()

/** Размер кружка-переключателя владения. */
private val PROFICIENCY_MARK_SIZE = 18.dp

/**
 * Компактная строка владений: название слева, справа — варианты с кружками.
 * Кружок пустой — владения нет, залитый — есть. Экспертизы здесь нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ProficiencyToggleRow(
    title: String,
    options: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            options.forEach { option ->
                val selected = isSelected(option)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(option) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val markColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(PROFICIENCY_MARK_SIZE)) {
                        val radius = size.minDimension / 2f
                        val stroke = radius * 0.25f
                        drawCircle(
                            color = markColor.copy(alpha = 0.75f),
                            radius = radius - stroke / 2f,
                            style = Stroke(width = stroke),
                        )
                        if (selected) drawCircle(color = markColor, radius = radius - stroke)
                    }
                    Text(label(option), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

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
    /** Выгрузка и загрузка листа в формате LSS; null — действие недоступно. */
    onExportSheet: (() -> Unit)? = null,
    onImportSheet: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }
    var level by remember { mutableStateOf(initial.level.toString()) }
    var canPrepare by remember { mutableStateOf(initial.canPrepareSpells) }
    var maxPrepared by remember { mutableStateOf(initial.maxPreparedSpells.takeIf { it > 0 }?.toString() ?: "") }
    var maxCantrips by remember { mutableStateOf(initial.maxCantrips.takeIf { it > 0 }?.toString() ?: "") }
    var maxAttunedItems by remember { mutableStateOf(initial.maxAttunedItems.toString()) }
    // Владения: доспехи и оружие — кружочки, инструменты и языки — свободный текст.
    val armorProficiencies = remember {
        mutableStateListOf<ArmorProficiency>().apply {
            addAll(ArmorProficiency.entries.filter { initial.hasArmorProficiency(it) })
        }
    }
    val weaponProficiencies = remember {
        mutableStateListOf<WeaponProficiency>().apply {
            addAll(WeaponProficiency.entries.filter { initial.hasWeaponProficiency(it) })
        }
    }
    var otherWeaponProficiencies by remember { mutableStateOf(initial.otherWeaponProficiencies) }
    var toolProficiencies by remember { mutableStateOf(initial.toolProficiencies) }
    var languages by remember { mutableStateOf(initial.languages) }
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
            level = level.toIntOrNull()?.coerceIn(1, 20) ?: initial.level,
            canPrepareSpells = canPrepare,
            maxPreparedSpells = if (canPrepare) maxPrepared.toIntOrNull() ?: 0 else 0,
            maxCantrips = maxCantrips.toIntOrNull() ?: 0,
            spellSlots = slotsMap,
            spellSlotsUsed = clampedUsed,
            maxAttunedItems = maxAttunedItems.toIntOrNull()?.coerceAtLeast(0) ?: 3,
            armorProficiencies = armorProficiencies.map { it.name },
            weaponProficiencies = weaponProficiencies.map { it.name },
            // Описание храним только при выбранном варианте «Другое».
            otherWeaponProficiencies = if (WeaponProficiency.OTHER in weaponProficiencies) {
                otherWeaponProficiencies.trim()
            } else {
                ""
            },
            toolProficiencies = toolProficiencies.trim(),
            languages = languages.trim(),
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

            OutlinedTextField(
                value = level,
                onValueChange = { new -> level = new.filter { it.isDigit() }.take(2) },
                label = { Text("Уровень персонажа") },
                supportingText = {
                    val bonus = level.toIntOrNull()?.coerceIn(1, 20)?.let { proficiencyBonusFor(it) }
                    Text(
                        if (bonus != null) "Бонус мастерства: ${formatModifier(bonus)}"
                        else "Укажите уровень от 1 до 20",
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

            Text("Владения", fontWeight = FontWeight.Bold)
            ProficiencyToggleRow(
                title = "Доспехи",
                options = ArmorProficiency.entries,
                label = { it.label },
                isSelected = { it in armorProficiencies },
                onToggle = { armor ->
                    if (armor in armorProficiencies) armorProficiencies.remove(armor)
                    else armorProficiencies.add(armor)
                },
            )
            ProficiencyToggleRow(
                title = "Оружие",
                options = WeaponProficiency.entries,
                label = { it.label },
                isSelected = { it in weaponProficiencies },
                onToggle = { weapon ->
                    if (weapon in weaponProficiencies) weaponProficiencies.remove(weapon)
                    else weaponProficiencies.add(weapon)
                },
            )
            if (WeaponProficiency.OTHER in weaponProficiencies) {
                // Списки владений бывают длинными: текст переносится и виден целиком.
                OutlinedTextField(
                    value = otherWeaponProficiencies,
                    onValueChange = { otherWeaponProficiencies = it },
                    label = { Text("Другое оружие") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = toolProficiencies,
                onValueChange = { toolProficiencies = it },
                label = { Text("Инструменты") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = languages,
                onValueChange = { languages = it },
                label = { Text("Языки") },
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

            // Обмен листом персонажа в формате LSS — только для созданного персонажа.
            if (!isNew && (onExportSheet != null || onImportSheet != null)) {
                HorizontalDivider()
                Text("Лист персонажа", fontWeight = FontWeight.Bold)
                Text(
                    "Формат LSS (Long Story Short).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onExportSheet?.let { export ->
                    OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выгрузить JSON")
                    }
                }
                onImportSheet?.let { import ->
                    OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Загрузить JSON")
                    }
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
