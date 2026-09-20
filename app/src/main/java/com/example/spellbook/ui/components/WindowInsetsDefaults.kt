package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * Отступы содержимого для экранов с полями ввода.
 *
 * Приложение работает в режиме edge-to-edge, поэтому система не сжимает окно под
 * клавиатуру — это должен делать сам экран. Стандартные отступы `Scaffold` учитывают
 * только системные панели, из-за чего клавиатура закрывала набираемый текст.
 *
 * `safeDrawing` дополнительно включает клавиатуру: содержимое сжимается до видимой
 * области, а поле ввода само подкручивает каретку в зону видимости. Значение передаётся
 * именно в `contentWindowInsets`, а не применяется как `Modifier.imePadding()`: `Scaffold`
 * берёт максимум из отступов клавиатуры и навигационной панели, а не складывает их,
 * поэтому над клавиатурой не появляется пустая полоса.
 */
val imeAwareContentInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing
