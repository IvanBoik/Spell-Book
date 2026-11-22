package com.example.spellbook.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.spellbook.R
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.DndTopBar

/** Стартовый экран вкладки «Персонажи»: список карточек персонажей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    characters: List<Character>,
    spellCounts: Map<String, Int>,
    onCharacterClick: (String) -> Unit,
    onImportSheet: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    /** Создание персонажа — пошаговый мастер по библиотекам. */
    onStartWizard: () -> Unit,
    /** Кнопка «Редактировать» под сдвинутой карточкой: открывает настройки персонажа. */
    onEditCharacter: (String) -> Unit = {},
    /** Удаление персонажа — вызывается только после подтверждения в диалоге. */
    onDeleteCharacter: (String) -> Unit = {},
    /** Кнопка «Повысить уровень» под сдвинутой карточкой; null — кнопки нет. */
    onLevelUp: ((String) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // Сдвинутой может быть только одна карточка: открытие другой закрывает предыдущую.
    var openedCardId by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<Character?>(null) }

    pendingDeletion?.let { character ->
        DeleteCharacterDialog(
            character = character,
            onConfirm = {
                pendingDeletion = null
                onDeleteCharacter(character.id)
            },
            onDismiss = { pendingDeletion = null },
        )
    }


    Scaffold(
        topBar = { DndTopBar(title = stringResource(R.string.tab_characters)) },
        bottomBar = bottomBar,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (menuExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = { menuExpanded = false; onImportSheet() },
                        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        text = { Text(stringResource(R.string.characters_import_sheet)) },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )

                    // Мастер — основной способ, поэтому он залит фирменным цветом.
                    ExtendedFloatingActionButton(
                        onClick = { menuExpanded = false; onStartWizard() },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        text = { Text(stringResource(R.string.characters_wizard)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                FloatingActionButton(
                    onClick = { menuExpanded = !menuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = if (menuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(
                            if (menuExpanded) R.string.action_close_menu else R.string.characters_add,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        if (characters.isEmpty()) {
            EmptyCharacters(
                onAddCharacter = onStartWizard,
                onImportSheet = onImportSheet,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    SwipeableCharacterCard(
                        character = character,
                        isOpen = openedCardId == character.id,
                        onOpenChange = { open ->
                            openedCardId = when {
                                open -> character.id
                                openedCardId == character.id -> null
                                else -> openedCardId
                            }
                        },
                        onClick = {
                            // Нажатие по открытой карточке сначала закрывает её, а не открывает персонажа.
                            if (openedCardId != null) openedCardId = null else onCharacterClick(character.id)
                        },
                        onEdit = {
                            openedCardId = null
                            onEditCharacter(character.id)
                        },
                        // На 20 уровне повышать некуда — кнопки нет.
                        onLevelUp = onLevelUp
                            ?.takeIf { character.level < com.example.spellbook.data.model.MAX_CLASS_LEVEL }
                            ?.let { action ->
                                {
                                    openedCardId = null
                                    action(character.id)
                                }
                            },
                        onDelete = {
                            openedCardId = null
                            pendingDeletion = character
                        },
                    )
                }
            }
        }
    }
}

/** Положения сдвигаемой карточки: на месте или сдвинута влево до кнопок действий. */
private enum class CardSwipe { CLOSED, OPEN }

/**
 * Кнопки действий под карточкой — как на остальных экранах со свайпом (комбинации,
 * ресурсы, инвентарь): круглые 48dp, жёлтое редактирование и красное удаление.
 */
private val SWIPE_ACTION_SIZE = 48.dp
private val SWIPE_ACTION_GAP = 12.dp
private val EDIT_ACTION_COLOR = Color(0xFFFBC02D)

/**
 * Карточка персонажа, которую можно сдвинуть влево: под ней открываются кнопки
 * «Редактировать» и «Удалить».
 *
 * Состояние «открыта» хранит список ([isOpen]): так одновременно открыта только одна
 * карточка. Жест сообщает о новом положении через [onOpenChange], а изменение [isOpen]
 * снаружи анимированно открывает или закрывает карточку.
 */
@Composable
private fun SwipeableCharacterCard(
    character: Character,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLevelUp: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val actionCount = if (onLevelUp != null) 3 else 2
    val actionsWidth = with(density) { (SWIPE_ACTION_SIZE * actionCount + SWIPE_ACTION_GAP * (actionCount + 1)).toPx() }
    val state = remember {
        AnchoredDraggableState(
            initialValue = CardSwipe.CLOSED,
            anchors = DraggableAnchors {
                CardSwipe.CLOSED at 0f
                CardSwipe.OPEN at -actionsWidth
            },
        )
    }
    // Якоря зависят от плотности экрана — обновляем их при её смене.
    LaunchedEffect(actionsWidth) {
        state.updateAnchors(
            DraggableAnchors {
                CardSwipe.CLOSED at 0f
                CardSwipe.OPEN at -actionsWidth
            },
        )
    }
    // Жест завершился — сообщаем списку, чтобы он закрыл другие карточки.
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { value -> onOpenChange(value == CardSwipe.OPEN) }
    }
    // Список закрыл карточку (открыта другая, нажата кнопка) — анимируем возврат.
    LaunchedEffect(isOpen) {
        val target = if (isOpen) CardSwipe.OPEN else CardSwipe.CLOSED
        if (state.settledValue != target) state.animateTo(target)
    }

    Box(Modifier.fillMaxWidth()) {
        // Кнопки лежат под карточкой справа и показываются по мере сдвига.
        Row(
            modifier = Modifier.matchParentSize().padding(end = SWIPE_ACTION_GAP),
            horizontalArrangement = Arrangement.spacedBy(SWIPE_ACTION_GAP, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onLevelUp != null) {
                SwipeActionButton(
                    icon = Icons.Default.Upgrade,
                    description = stringResource(R.string.levelup_action),
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onLevelUp,
                )
            }
            SwipeActionButton(
                icon = Icons.Default.Edit,
                description = stringResource(R.string.action_edit),
                background = EDIT_ACTION_COLOR,
                contentColor = Color.Black,
                onClick = onEdit,
            )
            SwipeActionButton(
                icon = Icons.Default.Delete,
                description = stringResource(R.string.action_delete),
                background = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = onDelete,
            )
        }
        CharacterCard(
            character = character,
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(state = state, orientation = Orientation.Horizontal),
        )
    }
}

/** Круглая кнопка действия под сдвинутой карточкой — такая же, как у комбинаций и ресурсов. */
@Composable
private fun SwipeActionButton(
    icon: ImageVector,
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

/** Подтверждение удаления: персонаж удаляется вместе со всеми данными без возврата. */
@Composable
private fun DeleteCharacterDialog(
    character: Character,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(
                stringResource(
                    R.string.characters_delete_title,
                    character.name.ifBlank { stringResource(R.string.character_unnamed) },
                ),
            )
        },
        text = { Text(stringResource(R.string.characters_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Карточка персонажа: имя, а под ним только раса, класс и уровень — без разновидности,
 * предыстории и счётчика заклинаний, чтобы список читался с одного взгляда.
 */
@Composable
private fun CharacterCard(character: Character, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(character = character, size = 56)
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = character.name.ifBlank { stringResource(R.string.character_unnamed) },
                    style = MaterialTheme.typography.titleLarge,
                )
                // Раса · класс · уровень. У персонажа без класса уровень показывается отдельно.
                // У штатных классов название из ресурсов, у пользовательских — заданное имя.
                val classes = character.classLevels
                    .map { it.displayName ?: stringResource(it.characterClass.labelRes) }
                    .joinToString(" / ")
                val summary = listOf(
                    character.race,
                    classes,
                    stringResource(R.string.wizard_class_level, character.level),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Аватар персонажа: фото из галереи (если задано) либо запасной кружок с инициалами.
 */
@Composable
fun CharacterAvatar(character: Character, size: Int) {
    val shape = CircleShape
    val uri = character.imageUri
    if (!uri.isNullOrBlank()) {
        AsyncImage(
            model = uri,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initials = character.name.trim().take(1).uppercase()
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyCharacters(
    onAddCharacter: () -> Unit,
    onImportSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.characters_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.characters_empty_text))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddCharacter) { Text(stringResource(R.string.characters_create)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImportSheet) {
                Text(stringResource(R.string.characters_import_sheet))
            }
        }
    }
}


