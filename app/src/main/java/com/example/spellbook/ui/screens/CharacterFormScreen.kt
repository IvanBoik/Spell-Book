package com.example.spellbook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.MAX_CLASS_LEVEL
import com.example.spellbook.data.model.maxSpellCircleFor
import com.example.spellbook.data.model.SpellcasterType
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.model.preparesSpells
import com.example.spellbook.data.model.proficiencyBonusFor
import com.example.spellbook.data.model.spellSlotsFor
import com.example.spellbook.data.model.totalLevel
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
 * Строка мультикласса в форме. Уровень хранится текстом, чтобы поле можно
 * было очистить и ввести число заново, а не дописывать к подставленной единице.
 */
private data class ClassLevelDraft(
    val characterClass: CharacterClass,
    val levelText: String = "",
    val customName: String = "",
) {
    /** Уровень, если он введён и попадает в допустимый диапазон. */
    val level: Int? get() = levelText.toIntOrNull()?.takeIf { it in 1..MAX_CLASS_LEVEL }

    /** Своёму классу нужно название, иначе строка непонятна. */
    val isValid: Boolean
        get() = level != null && (characterClass != CharacterClass.OTHER || customName.isNotBlank())

    fun toClassLevel(): CharacterClassLevel? = level
        ?.takeIf { isValid }
        ?.let { CharacterClassLevel(characterClass, it, customName.trim()) }
}

/**
 * Блок классов персонажа: карточки выбранных классов с уровнями,
 * итоговый уровень и кнопка добавления. Открыт только при создании:
 * по нему автоматически считаются уровень и ячейки.
 */
@Composable
private fun ClassLevelsSection(
    classLevels: List<ClassLevelDraft>,
    onAdd: (CharacterClass) -> Unit,
    onLevelChange: (index: Int, levelText: String) -> Unit,
    onCustomNameChange: (index: Int, name: String) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chosen = classLevels.map { it.characterClass }
    // «Другое» можно добавлять сколько угодно раз: у каждого своё название.
    val available = CharacterClass.entries.filter { it == CharacterClass.OTHER || it !in chosen }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Классы", fontWeight = FontWeight.Bold)
            Text(
                "Уровень и ячейки считаются по классам",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        classLevels.forEachIndexed { index, entry ->
            ClassLevelCard(
                entry = entry,
                onLevelChange = { new -> onLevelChange(index, new) },
                onCustomNameChange = { new -> onCustomNameChange(index, new) },
                onRemove = { onRemove(index) },
            )
        }

        if (classLevels.isEmpty()) {
            Text(
                "Классы не выбраны — уровень и ячейки придётся задать вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = available.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (classLevels.isEmpty()) "Выбрать класс" else "Добавить класс")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                available.forEach { characterClass ->
                    DropdownMenuItem(
                        text = { Text(characterClass.label) },
                        onClick = {
                            onAdd(characterClass)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Спрашивает при сохранении, нужно ли пересчитать ячейки по изменённым классам,
 * и показывает сравнение текущих и расчётных значений по уровням.
 */
@Composable
private fun RecalculateSlotsDialog(
    currentSlots: Map<Int, Int>,
    calculatedSlots: Map<Int, Int>,
    onKeep: () -> Unit,
    onRecalculate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val levels = (currentSlots.keys + calculatedSlots.keys).sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Пересчитать ячейки?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Состав классов-заклинателей изменился. Можно обновить ячейки по правилам мультикласса.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row {
                            Text(
                                "Уровень",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "Сейчас",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                "Станет",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(64.dp),
                            )
                        }
                        levels.forEach { level ->
                            val before = currentSlots[level] ?: 0
                            val after = calculatedSlots[level] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$level уровень", modifier = Modifier.weight(1f))
                                Text(before.toString(), modifier = Modifier.width(64.dp))
                                Text(
                                    after.toString(),
                                    // Изменённые значения выделяем, чтобы разница была заметна.
                                    color = if (before != after) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (before != after) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.width(64.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRecalculate) { Text("Пересчитать") }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("Оставить") }
        },
    )
}

/** Карточка одного класса: название, уровень и кнопка удаления. */
@Composable
private fun ClassLevelCard(
    entry: ClassLevelDraft,
    onLevelChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val isCustom = entry.characterClass == CharacterClass.OTHER
    val isCaster = entry.characterClass.spellcaster != SpellcasterType.NONE
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        // Заклинатели выделены бордовой рамкой: именно они влияют на ячейки.
        border = BorderStroke(
            width = if (isCaster) 1.5.dp else 1.dp,
            color = if (isCaster) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        entry.customName.ifBlank { entry.characterClass.label },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isCaster) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "Заклинатель",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = entry.levelText,
                    onValueChange = { new -> onLevelChange(new.filter { it.isDigit() }.take(2)) },
                    label = { Text("Уровень") },
                    // Пустое или неверное значение подсвечивается и блокирует сохранение.
                    isError = entry.level == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(104.dp),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Убрать класс")
                }
            }
            if (isCustom) {
                OutlinedTextField(
                    value = entry.customName,
                    onValueChange = onCustomNameChange,
                    label = { Text("Название класса") },
                    isError = entry.customName.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!entry.isValid) {
                Text(
                    if (entry.level == null) "Уровень от 1 до $MAX_CLASS_LEVEL"
                    else "Укажите название класса",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
    /**
     * @param addClassSpells нужно ли добавить в список известных все заклинания
     * классов, готовящих из полного списка (жрец, друид, паладин, изобретатель).
     */
    onSave: (character: Character, addClassSpells: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
    /** Выгрузка и загрузка листа в формате LSS; null — действие недоступно. */
    onExportSheet: (() -> Unit)? = null,
    onImportSheet: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }

    var canPrepare by remember { mutableStateOf(initial.canPrepareSpells) }
    /*
     * Предложение сразу добавить все заклинания класса в список для переподготовки.
     * По умолчанию включено: таким классам доступен весь список, и перебирать его вручную долго.
     */
    var addClassSpells by remember { mutableStateOf(true) }
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
    /*
     * Классы указываются только при создании: тогда же по правилам мультикласса
     * считаются ячейки. У существующего персонажа авторасчёт отключён,
     * чтобы не затирать ручные правки.
     */
    val classLevels = remember {
        mutableStateListOf<ClassLevelDraft>().apply {
            addAll(initial.classLevels.map { ClassLevelDraft(it.characterClass, it.level.toString()) })
        }
    }
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

    /** Классы с корректно заполненными уровнем и названием. */
    val validClassLevels = classLevels.mapNotNull { it.toClassLevel() }
    val hasInvalidClassLevel = classLevels.any { !it.isValid }

    /** Классы, готовящие заклинания из всего своего списка. */
    val classListClasses = validClassLevels.filter { it.characterClass.preparesFromClassList }

    /*
     * Круг считается отдельно по уровню каждого класса, а не по сумме уровней:
     * друид 4 уровня и чародей 1 уровня — это только 2 круг друида.
     */
    val classListSummary = classListClasses.joinToString { entry ->
        "${entry.displayName.lowercase()} — до ${maxSpellCircleFor(entry)} круга"
    }

    /** При создании уровень и ячейки подставляются из классов, если они указаны. */
    val autoFromClasses = isNew && validClassLevels.isNotEmpty()

    val classSignature = validClassLevels.joinToString(",") { it.serialize() }

    /** Подставляет в поля ячейки, рассчитанные по правилам мультикласса. */
    fun applyCalculatedSlots() {
        val calculated = spellSlotsFor(validClassLevels)
        SLOT_LEVELS.forEach { level ->
            slots[level] = calculated[level]?.toString() ?: ""
        }
    }

    /*
     * При создании ячейки сразу подставляются в поля, но остаются редактируемыми.
     * У существующего персонажа молча ничего не перезаписываем — спрашиваем ниже.
     */
    LaunchedEffect(classSignature, isNew) {
        if (!isNew || validClassLevels.isEmpty()) return@LaunchedEffect
        applyCalculatedSlots()
    }

    /*
     * При сохранении предлагаем пересчёт, если из-за правки классов появились
     * заклинатели или изменился уровень заклинателя. Сравниваем расчётные наборы
     * ячеек: они отражают и новый класс-заклинатель, и изменение его уровня.
     */
    val initialSlotsByClasses = remember { spellSlotsFor(initial.classLevels) }
    val calculatedSlots = spellSlotsFor(validClassLevels)
    val currentSlots = slots.mapNotNull { (level, value) ->
        value.toIntOrNull()?.takeIf { it > 0 }?.let { level to it }
    }.toMap()
    val casterSetupChanged = !isNew &&
        calculatedSlots.isNotEmpty() &&
        calculatedSlots != initialSlotsByClasses &&
        calculatedSlots != currentSlots

    /** Персонаж, которого сохраним после ответа на вопрос о пересчёте. */
    var pendingSave by remember { mutableStateOf<Character?>(null) }

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
            classes = validClassLevels.map { it.serialize() },
            // Уровень всегда складывается из классов; без них остаётся прежний.
            level = if (validClassLevels.isNotEmpty()) totalLevel(validClassLevels) else initial.level,
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

    /** Нужно ли наполнить список заклинаниями класса при сохранении. */
    val shouldAddClassSpells = canPrepare && addClassSpells && classListClasses.isNotEmpty()

    /** Сохраняет сразу либо спрашивает про пересчёт ячеек по изменённым классам. */
    fun requestSave() {
        val character = buildCharacter()
        if (casterSetupChanged) pendingSave = character else onSave(character, shouldAddClassSpells)
    }

    pendingSave?.let { character ->
        RecalculateSlotsDialog(
            currentSlots = currentSlots,
            calculatedSlots = calculatedSlots,
            onKeep = {
                pendingSave = null
                onSave(character, shouldAddClassSpells)
            },
            onRecalculate = {
                pendingSave = null
                // Потраченные ячейки обрезаем по новым максимумам.
                val clampedUsed = character.spellSlotsUsed.mapNotNull { (level, used) ->
                    val total = calculatedSlots[level] ?: 0
                    if (total > 0) level to minOf(used, total) else null
                }.toMap()
                applyCalculatedSlots()
                onSave(
                    character.copy(spellSlots = calculatedSlots, spellSlotsUsed = clampedUsed),
                    shouldAddClassSpells,
                )
            },
            onDismiss = { pendingSave = null },
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
                    IconButton(
                        onClick = { requestSave() },
                        enabled = name.isNotBlank() && !hasInvalidClassLevel,
                    ) {
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

            // Классы редактируются и у созданного персонажа.
            ClassLevelsSection(
                classLevels = classLevels,
                onAdd = { characterClass ->
                    classLevels.add(ClassLevelDraft(characterClass, "1"))
                    if (preparesSpells(classLevels.mapNotNull { it.toClassLevel() })) canPrepare = true
                },
                onLevelChange = { index, newLevel ->
                    classLevels[index] = classLevels[index].copy(levelText = newLevel)
                },
                onCustomNameChange = { index, newName ->
                    classLevels[index] = classLevels[index].copy(customName = newName)
                },
                onRemove = { index ->
                    classLevels.removeAt(index)
                    canPrepare = preparesSpells(classLevels.mapNotNull { it.toClassLevel() })
                },
            )

            // Уровень персонажа всегда равен сумме уровней классов — отдельное поле не нужно.
            val effectiveLevel = if (validClassLevels.isNotEmpty()) {
                totalLevel(validClassLevels)
            } else {
                initial.level
            }
            Text(
                "Уровень: $effectiveLevel · бонус мастерства ${formatModifier(proficiencyBonusFor(effectiveLevel))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
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
            // Жрец, друид, паладин и изобретатель готовят заклинания из всего списка класса,
            // поэтому им предлагаем сразу наполнить список известных заклинаний.
            if (canPrepare && classListClasses.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Добавить заклинания класса", fontWeight = FontWeight.Bold)
                        Text(
                            "Доступные круги по уровню класса: $classListSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = addClassSpells,
                        onCheckedChange = { addClassSpells = it },
                    )
                }
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
            if (autoFromClasses) {
                // Значения подставлены по правилам мультикласса, но остаются редактируемыми.
                Text(
                    "Заполнены на основе классов — можно изменить вручную.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                onClick = { requestSave() },
                enabled = name.isNotBlank() && !hasInvalidClassLevel,
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
