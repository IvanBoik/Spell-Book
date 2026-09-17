package com.example.spellbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.spellLevelLabel
import com.example.spellbook.ui.spellOptionLabel
import kotlin.math.roundToInt

/** Что перетаскивается: заклинание и его текущее состояние (подготовлено/нет). */
private data class DragItem(val spell: Spell, val fromPrepared: Boolean)

/**
 * Экран переподготовки: два списка — «Известные» и «Подготовленные».
 * Заклинание перетаскивается (долгое нажатие + перетаскивание) из одного списка в другой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareSpellsScreen(
    known: List<Spell>,
    preparedIds: Set<String>,
    maxPrepared: Int,
    onPrepare: (spellId: String) -> Unit,
    onUnprepare: (spellId: String) -> Unit,
    onSpellClick: (spellId: String) -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    val preparedSpells = known.filter { it.id in preparedIds }
    val unpreparedSpells = known.filter { it.id !in preparedIds }

    // Состояние текущего перетаскивания (всё — в координатах окна).
    var dragItem by remember { mutableStateOf<DragItem?>(null) }
    // Абсолютная позиция пальца в окне.
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    // Локальное смещение точки захвата внутри карточки (чтобы «призрак» не прыгал под пальцем).
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    // Позиция корневого контейнера в окне (для перевода оконных координат в локальные для offset).
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    // Границы зон сброса (в координатах окна).
    var preparedZone by remember { mutableStateOf(Rect.Zero) }
    var knownZone by remember { mutableStateOf(Rect.Zero) }


    fun handleDrop() {
        val item = dragItem ?: return
        val point = pointerOffset
        when {
            !item.fromPrepared && preparedZone.contains(point) -> onPrepare(item.spell.id)
            item.fromPrepared && knownZone.contains(point) -> onUnprepare(item.spell.id)
        }
        dragItem = null
    }

    // Общие обработчики перетаскивания для обеих зон.
    val onCardDragStart: (Spell, Boolean, Offset, Offset) -> Unit = { spell, fromPrepared, windowPos, grabLocal ->
        dragItem = DragItem(spell, fromPrepared)
        pointerOffset = windowPos
        grabOffset = grabLocal
    }
    val onCardDrag: (Offset) -> Unit = { windowPos -> pointerOffset = windowPos }

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(R.string.prepare_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.boundsInWindow().topLeft },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Панель разделов вне зон сброса: иначе она попадала бы в границы перетаскивания.
                sectionsBar()
                // Верхняя зона — подготовленные.
                DropZone(
                    // Лимит показываем только если он задан у персонажа.
                    title = if (maxPrepared > 0) {
                        stringResource(R.string.prepare_prepared_limit, preparedSpells.size, maxPrepared)
                    } else {
                        stringResource(R.string.prepare_prepared, preparedSpells.size)
                    },
                    spells = preparedSpells,
                    fromPrepared = true,
                    highlighted = dragItem?.fromPrepared == false,
                    emptyHint = stringResource(R.string.prepare_drop_hint),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { preparedZone = it.boundsInWindow() },
                    onDragStart = onCardDragStart,
                    onDrag = onCardDrag,
                    onDragEnd = ::handleDrop,
                    onClick = onSpellClick,
                )
                androidx.compose.material3.HorizontalDivider(thickness = 2.dp)
                // Нижняя зона — известные (не подготовленные).
                DropZone(
                    title = stringResource(R.string.prepare_known),
                    spells = unpreparedSpells,
                    fromPrepared = false,
                    highlighted = dragItem?.fromPrepared == true,
                    emptyHint = stringResource(R.string.prepare_known_empty),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { knownZone = it.boundsInWindow() },
                    onDragStart = onCardDragStart,
                    onDrag = onCardDrag,
                    onDragEnd = ::handleDrop,
                    onClick = onSpellClick,
                )
            }

            // «Призрак» перетаскиваемой карточки: держим точку захвата ровно под пальцем.
            dragItem?.let { item ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pointerOffset.x - grabOffset.x - rootOffset.x).roundToInt(),
                                (pointerOffset.y - grabOffset.y - rootOffset.y).roundToInt(),
                            )
                        }
                        .fillMaxWidth(0.9f),
                ) {
                    DragGhost(item.spell)
                }
            }
        }
    }
}

@Composable
private fun DropZone(
    title: String,
    spells: List<Spell>,
    fromPrepared: Boolean,
    highlighted: Boolean,
    emptyHint: String,
    modifier: Modifier = Modifier,
    onDragStart: (Spell, Boolean, windowPos: Offset, grabLocal: Offset) -> Unit,
    onDrag: (windowPos: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (spellId: String) -> Unit,
) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth().background(bg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (spells.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(emptyHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(spells, key = { it.id }) { spell ->
                    DraggableSpellCard(
                        spell = spell,
                        fromPrepared = fromPrepared,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableSpellCard(
    spell: Spell,
    fromPrepared: Boolean,
    onDragStart: (Spell, Boolean, windowPos: Offset, grabLocal: Offset) -> Unit,
    onDrag: (windowPos: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (spellId: String) -> Unit,
) {
    // Позиция карточки в окне — чтобы перевести локальную позицию пальца в оконные координаты.
    var cardPos by remember { mutableStateOf(Offset.Zero) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardPos = it.boundsInWindow().topLeft }
            // Обычное нажатие — открыть заклинание; долгое нажатие — перетаскивание.
            .clickable { onClick(spell.id) }
            .pointerInput(spell.id) {
                detectDragGesturesAfterLongPressCompat(
                    onStart = { local -> onDragStart(spell, fromPrepared, cardPos + local, local) },
                    onMove = { _, local -> onDrag(cardPos + local) },
                    onEnd = onDragEnd,
                )
            },
    ) {
        SpellRowContent(spell)
    }
}

@Composable
private fun DragGhost(spell: Spell) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clip(shape).border(2.dp, MaterialTheme.colorScheme.primary, shape),
    ) {
        SpellRowContent(spell)
    }
}

@Composable
private fun SpellRowContent(spell: Spell) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(spell.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = spellLevelLabel(spell.level) + " · " +
                spellOptionLabel(SpellOptions.schools, spell.school),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Обёртка над жестом перетаскивания после долгого нажатия.
 * [onStart]/[onMove] отдают локальную позицию пальца внутри элемента,
 * а delta — смещение с прошлого события.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDragGesturesAfterLongPressCompat(
    onStart: (Offset) -> Unit,
    onMove: (delta: Offset, local: Offset) -> Unit,
    onEnd: () -> Unit,
) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> onStart(offset) },
        onDragEnd = { onEnd() },
        onDragCancel = { onEnd() },
        onDrag = { change, dragAmount ->
            change.consume()
            onMove(dragAmount, change.position)
        },
    )
}
