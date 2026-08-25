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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spellbook.data.model.NOTE_DEFAULT_FONT_SIZE
import com.example.spellbook.data.model.NOTE_FONT_SIZES
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteCharStyle
import com.example.spellbook.data.model.NoteListStyle
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.ui.components.DndTopBar

private val NOTE_CARD_SHAPE = RoundedCornerShape(12.dp)

/**
 * Экран заметок персонажа: блоки можно сворачивать, а текст внутри —
 * форматировать (жирность, курсив, подчёркивание, размер, списки).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    blocks: List<NoteBlock>,
    onAddBlock: (String) -> Unit,
    onSaveBlock: (NoteBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var blockPendingDeletion by remember { mutableStateOf<NoteBlock?>(null) }
    // Абзац, который сейчас редактируется: только у него показывается панель форматирования.
    var focusedParagraphId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(IntRange.EMPTY) }

    Scaffold(
        topBar = {
            DndTopBar(
                title = "Заметки",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить блок")
            }
        },
    ) { padding ->
        if (blocks.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                sectionsBar()
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Заметок пока нет",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Разделяйте записи на блоки: сюжет, зацепки, NPC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                // Верхний отступ задаёт сама панель разделов — одинаково на всех экранах.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "sections") { sectionsBar() }

                items(blocks, key = { it.id }) { block ->
                    NoteBlockCard(
                        block = block,
                        focusedParagraphId = focusedParagraphId,
                        selection = selection,
                        onSelectionChange = { paragraphId, range ->
                            focusedParagraphId = paragraphId
                            selection = range
                        },
                        onSaveBlock = onSaveBlock,
                        onDelete = { blockPendingDeletion = block },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        BlockTitleDialog(
            title = "Новый блок",
            initial = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { onAddBlock(it); showAddDialog = false },
        )
    }
    blockPendingDeletion?.let { block ->
        AlertDialog(
            onDismissRequest = { blockPendingDeletion = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("Удалить блок?") },
            text = { Text("Блок «${block.title}» и его содержимое будут удалены.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteBlock(block.id)
                    blockPendingDeletion = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { blockPendingDeletion = null }) { Text("Отмена") }
            },
        )
    }
}

/** Карточка блока: заголовок со сворачиванием и абзацы с форматированием. */
@Composable
private fun NoteBlockCard(
    block: NoteBlock,
    focusedParagraphId: String?,
    selection: IntRange,
    onSelectionChange: (String?, IntRange) -> Unit,
    onSaveBlock: (NoteBlock) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }

    Card(
        shape = NOTE_CARD_SHAPE,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSaveBlock(block.copy(collapsed = !block.collapsed)) }
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        block.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    // В свёрнутом виде показываем краткую выжимку содержимого.
                    if (block.collapsed && block.preview.isNotBlank()) {
                        Text(
                            block.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Default.TextFields, contentDescription = "Переименовать блок")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить блок")
                }
                Icon(
                    imageVector = if (block.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (block.collapsed) "Развернуть" else "Свернуть",
                    modifier = Modifier.padding(end = 12.dp),
                )
            }

            AnimatedVisibility(visible = !block.collapsed) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    block.paragraphs.forEachIndexed { index, paragraph ->
                        ParagraphEditor(
                            paragraph = paragraph,
                            isFocused = paragraph.id == focusedParagraphId,
                            selection = selection,
                            onSelectionChange = { range -> onSelectionChange(paragraph.id, range) },
                            onParagraphChange = { updated ->
                                onSaveBlock(block.copy(paragraphs = block.paragraphs.replace(index, updated)))
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        BlockTitleDialog(
            title = "Название блока",
            initial = block.title,
            onDismiss = { renaming = false },
            onConfirm = { onSaveBlock(block.copy(title = it)); renaming = false },
        )
    }
}

/** Абзац с полем ввода и панелью форматирования, видимой при фокусе. */
@Composable
private fun ParagraphEditor(
    paragraph: NoteParagraph,
    isFocused: Boolean,
    selection: IntRange,
    onSelectionChange: (IntRange) -> Unit,
    onParagraphChange: (NoteParagraph) -> Unit,
) {
    /*
     * Поле ввода — источник истины во время набора. Обратная синхронизация из базы
     * здесь недопустима: Room Flow отвечает с задержкой и возвращал устаревший текст,
     * из-за чего курсор сбивался и буквы попадали в начало строки.
     */
    var fieldValue by remember(paragraph.id) {
        mutableStateOf(TextFieldValue(paragraph.text, TextRange(paragraph.text.length)))
    }

    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = fieldValue,
            onValueChange = { newValue ->
                // Внутри списка Enter продолжает его, а на пустом пункте — завершает.
                val listEnter = handleListEnter(fieldValue.text, newValue)
                val applied = listEnter?.let { TextFieldValue(it.text, TextRange(it.caret)) } ?: newValue
                fieldValue = applied
                onSelectionChange(applied.selection.start until applied.selection.end)
                if (applied.text != paragraph.text) {
                    onParagraphChange(paragraph.withText(applied.text))
                }
            },
            visualTransformation = NoteStyleTransformation(paragraph),
            placeholder = { Text("Текст заметки") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (isFocused) {
            FormattingBar(
                paragraph = paragraph,
                selection = selection,
                currentListStyle = listStyleAt(fieldValue.text, fieldValue.selection.start),
                onParagraphChange = onParagraphChange,
                onToggleList = { style ->
                    // Список применяется к той строке, где сейчас каретка.
                    val result = toggleListAtCaret(fieldValue.text, fieldValue.selection.start, style)
                    fieldValue = TextFieldValue(result.text, TextRange(result.caret))
                    onParagraphChange(paragraph.withText(result.text))
                    onSelectionChange(result.caret until result.caret)
                },
            )
        }
    }
}

/** Префикс маркированного списка в тексте строки. */
private const val BULLET_PREFIX = "• "
private val NUMBERED_PREFIX_REGEX = Regex("^(\\d+)\\.\\s")

/** Результат переключения списка: новый текст и позиция каретки. */
private data class ListToggleResult(val text: String, val caret: Int)

/**
 * Обрабатывает Enter внутри списка: непустой пункт продолжает список новым маркером,
 * пустой — удаляется вместе с маркером, завершая список.
 * Возвращает null, если правка не является переносом строки в списке.
 */
private fun handleListEnter(previousText: String, newValue: TextFieldValue): ListToggleResult? {
    val text = newValue.text
    val caret = newValue.selection.start
    // Реагируем только на одиночный только что введённый перевод строки.
    if (text.length != previousText.length + 1) return null
    if (caret !in 1..text.length || text[caret - 1] != '\n') return null

    val previousLineStart = if (caret < 2) 0 else text.lastIndexOf('\n', caret - 2) + 1
    val previousLine = text.substring(previousLineStart, caret - 1)

    val numbered = NUMBERED_PREFIX_REGEX.find(previousLine)
    val isBullet = previousLine.startsWith(BULLET_PREFIX)
    if (!isBullet && numbered == null) return null

    val prefix = if (isBullet) BULLET_PREFIX else numbered!!.value
    if (previousLine.removePrefix(prefix).isBlank()) {
        // Пустой пункт: убираем маркер и не создаём новую строку.
        val ended = text.substring(0, previousLineStart) + text.substring(caret)
        return ListToggleResult(ended, previousLineStart)
    }

    val nextPrefix = if (isBullet) {
        BULLET_PREFIX
    } else {
        "${(numbered!!.groupValues[1].toIntOrNull() ?: 0) + 1}. "
    }
    val continued = text.substring(0, caret) + nextPrefix + text.substring(caret)
    return ListToggleResult(continued, caret + nextPrefix.length)
}

/** Границы строки, в которой стоит каретка. */
private fun lineBounds(text: String, caret: Int): IntRange {
    val safeCaret = caret.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (safeCaret - 1).coerceAtLeast(0))
        .let { if (it < 0 || safeCaret == 0) 0 else it + 1 }
    val endIndex = text.indexOf('\n', safeCaret)
    val end = if (endIndex < 0) text.length else endIndex
    return start..end
}

/** Какой список у строки с кареткой — для подсветки кнопок панели. */
private fun listStyleAt(text: String, caret: Int): NoteListStyle {
    val bounds = lineBounds(text, caret)
    val line = text.substring(bounds.first, bounds.last)
    return when {
        line.startsWith(BULLET_PREFIX) -> NoteListStyle.BULLET
        NUMBERED_PREFIX_REGEX.containsMatchIn(line) -> NoteListStyle.NUMBERED
        else -> NoteListStyle.NONE
    }
}

/**
 * Добавляет или убирает маркер списка у строки с кареткой.
 * Номер продолжает нумерацию предыдущей строки, если она уже нумерованая.
 */
private fun toggleListAtCaret(text: String, caret: Int, target: NoteListStyle): ListToggleResult {
    val bounds = lineBounds(text, caret)
    val lineStart = bounds.first
    val line = text.substring(lineStart, bounds.last)

    val currentPrefix = when {
        line.startsWith(BULLET_PREFIX) -> BULLET_PREFIX
        else -> NUMBERED_PREFIX_REGEX.find(line)?.value.orEmpty()
    }
    val body = line.removePrefix(currentPrefix)
    val newPrefix = when {
        listStyleAt(text, caret) == target -> ""
        target == NoteListStyle.BULLET -> BULLET_PREFIX
        target == NoteListStyle.NUMBERED -> "${nextListNumber(text, lineStart)}. "
        else -> ""
    }

    val newText = text.substring(0, lineStart) + newPrefix + body + text.substring(bounds.last)
    val delta = newPrefix.length - currentPrefix.length
    return ListToggleResult(newText, (caret + delta).coerceIn(0, newText.length))
}

/** Номер для нумерованной строки: продолжение предыдущего списка или 1. */
private fun nextListNumber(text: String, lineStart: Int): Int {
    if (lineStart == 0) return 1
    val previousBounds = lineBounds(text, lineStart - 1)
    val previousLine = text.substring(previousBounds.first, previousBounds.last)
    val previousNumber = NUMBERED_PREFIX_REGEX.find(previousLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return (previousNumber ?: 0) + 1
}

/** Панель форматирования: стили применяются к выделенному тексту. */
@Composable
private fun FormattingBar(
    paragraph: NoteParagraph,
    selection: IntRange,
    currentListStyle: NoteListStyle,
    onParagraphChange: (NoteParagraph) -> Unit,
    onToggleList: (NoteListStyle) -> Unit,
) {
    var sizeMenuOpen by remember { mutableStateOf(false) }
    val start = selection.first
    val end = selection.last + 1
    val hasSelection = end > start

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatToggle(
            icon = Icons.Default.FormatBold,
            description = "Жирный",
            active = hasSelection && paragraph.rangeHas(start, end) { it.bold },
            enabled = hasSelection,
        ) {
            val enable = !paragraph.rangeHas(start, end) { it.bold }
            onParagraphChange(paragraph.applyStyle(start, end) { it.copy(bold = enable) })
        }
        FormatToggle(
            icon = Icons.Default.FormatItalic,
            description = "Курсив",
            active = hasSelection && paragraph.rangeHas(start, end) { it.italic },
            enabled = hasSelection,
        ) {
            val enable = !paragraph.rangeHas(start, end) { it.italic }
            onParagraphChange(paragraph.applyStyle(start, end) { it.copy(italic = enable) })
        }
        FormatToggle(
            icon = Icons.Default.FormatUnderlined,
            description = "Подчёркнутый",
            active = hasSelection && paragraph.rangeHas(start, end) { it.underline },
            enabled = hasSelection,
        ) {
            val enable = !paragraph.rangeHas(start, end) { it.underline }
            onParagraphChange(paragraph.applyStyle(start, end) { it.copy(underline = enable) })
        }

        Box {
            FormatToggle(
                icon = Icons.Default.TextFields,
                description = "Размер шрифта",
                active = false,
                enabled = hasSelection,
            ) { sizeMenuOpen = true }
            DropdownMenu(
                expanded = sizeMenuOpen,
                onDismissRequest = { sizeMenuOpen = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                NOTE_FONT_SIZES.forEach { size ->
                    DropdownMenuItem(
                        text = { Text("$size sp", fontSize = size.sp) },
                        onClick = {
                            sizeMenuOpen = false
                            onParagraphChange(paragraph.applyStyle(start, end) { it.copy(fontSize = size) })
                        },
                    )
                }
            }
        }

        FormatToggle(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            description = "Маркированный список",
            active = currentListStyle == NoteListStyle.BULLET,
            enabled = true,
        ) { onToggleList(NoteListStyle.BULLET) }
        FormatToggle(
            icon = Icons.Default.FormatListNumbered,
            description = "Нумерованный список",
            active = currentListStyle == NoteListStyle.NUMBERED,
            enabled = true,
        ) { onToggleList(NoteListStyle.NUMBERED) }
    }
}

@Composable
private fun FormatToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .padding(end = 2.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BlockTitleDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Показывает оформление прямо в поле ввода, не меняя сам текст. */
private class NoteStyleTransformation(
    private val paragraph: NoteParagraph,
) : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val styled = buildAnnotatedString {
            val styles = paragraph.toCharStyles()
            text.text.forEachIndexed { index, char ->
                val style = styles.getOrNull(index) ?: NoteCharStyle()
                withStyle(
                    SpanStyle(
                        fontWeight = if (style.bold) FontWeight.Bold else null,
                        fontStyle = if (style.italic) FontStyle.Italic else null,
                        textDecoration = if (style.underline) TextDecoration.Underline else null,
                        fontSize = style.fontSize.takeIf { it != NOTE_DEFAULT_FONT_SIZE }?.sp
                            ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    ),
                ) { append(char) }
            }
        }
        return androidx.compose.ui.text.input.TransformedText(
            styled,
            androidx.compose.ui.text.input.OffsetMapping.Identity,
        )
    }
}


private fun List<NoteParagraph>.replace(index: Int, value: NoteParagraph): List<NoteParagraph> =
    toMutableList().apply { this[index] = value }
