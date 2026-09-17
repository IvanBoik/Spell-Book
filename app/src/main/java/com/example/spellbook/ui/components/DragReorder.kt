package com.example.spellbook.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Перестановка элементов списка удержанием и перетаскиванием.
 *
 * Логика повторяет поведение списков черт, комбинаций и инвентаря: элемент следует за
 * пальцем, соседи освобождают место сразу, а итоговый порядок сохраняется один раз —
 * при отпускании. Пока хранилище не прислало новый порядок, показывается оптимистичный
 * [order], поэтому список не «прыгает» назад.
 *
 * @param ID тип идентификатора элемента (обычно String).
 */
class DragReorderState<ID : Any> internal constructor(private val stepPx: Float) {

    /** Идентификатор перетаскиваемого элемента; null — жест не активен. */
    var draggingId: ID? by mutableStateOf(null)
        private set

    /** Смещение элемента под пальцем относительно его позиции в текущем порядке. */
    private var dragDistance by mutableFloatStateOf(0f)

    /** Порядок, изменённый жестом, но ещё не подтверждённый хранилищем. */
    private var localOrder by mutableStateOf<List<ID>>(emptyList())

    /**
     * Порядок для отрисовки: сначала известные элементы из [localOrder],
     * затем появившиеся снаружи (например, только что добавленные).
     */
    fun order(ids: List<ID>): List<ID> {
        if (localOrder.isEmpty()) return ids
        val known = ids.toSet()
        return localOrder.filter { it in known } + ids.filterNot { it in localOrder }
    }

    /** Смещение элемента по вертикали: ненулевое только у того, что под пальцем. */
    fun translationFor(id: ID): Float = if (id == draggingId) dragDistance else 0f

    internal fun onDragStart(id: ID, currentOrder: List<ID>) {
        localOrder = currentOrder
        draggingId = id
        dragDistance = 0f
    }

    /**
     * Накапливает смещение и переставляет элемент через каждый шаг [stepPx],
     * оставляя карточку под пальцем.
     */
    internal fun onDrag(id: ID, delta: Float) {
        dragDistance += delta
        while (dragDistance >= stepPx) {
            val index = localOrder.indexOf(id)
            if (index < 0 || index >= localOrder.lastIndex) break
            localOrder = localOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
            dragDistance -= stepPx
        }
        while (dragDistance <= -stepPx) {
            val index = localOrder.indexOf(id)
            if (index <= 0) break
            localOrder = localOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
            dragDistance += stepPx
        }
    }

    internal fun onDragEnd(onReorder: (List<ID>) -> Unit) {
        if (draggingId != null) onReorder(localOrder)
        draggingId = null
        dragDistance = 0f
    }
}

/**
 * Создаёт состояние перетаскивания.
 *
 * @param step примерная высота элемента: через такое расстояние элементы меняются местами.
 */
@Composable
fun <ID : Any> rememberDragReorderState(step: Dp): DragReorderState<ID> {
    val stepPx = with(LocalDensity.current) { step.toPx() }
    return remember(stepPx) { DragReorderState(stepPx) }
}

/**
 * Делает элемент перетаскиваемым: удержание начинает жест, перемещение меняет порядок,
 * отпускание сообщает результат через [onReorder].
 *
 * @param currentOrder порядок на момент начала жеста — от него отсчитываются перестановки.
 */
@Composable
fun <ID : Any> Modifier.dragToReorder(
    state: DragReorderState<ID>,
    id: ID,
    currentOrder: List<ID>,
    onReorder: (List<ID>) -> Unit,
): Modifier {
    // pointerInput живёт дольше рекомпозиции: без rememberUpdatedState жест работал бы
    // с устаревшим порядком элементов.
    val currentIds by rememberUpdatedState(currentOrder)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val isDragging = state.draggingId == id
    val translation = state.translationFor(id)

    return this
        .offset { IntOffset(0, translation.roundToInt()) }
        // Перетаскиваемый элемент рисуется поверх соседей.
        .zIndex(if (isDragging) 1f else 0f)
        // Ключ не зависит от isDragging: иначе рекомпозиция отменит активный жест.
        .pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.onDragStart(id, currentIds) },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.onDrag(id, dragAmount.y)
                },
                onDragEnd = { state.onDragEnd(currentOnReorder) },
                onDragCancel = { state.onDragEnd(currentOnReorder) },
            )
        }
}
