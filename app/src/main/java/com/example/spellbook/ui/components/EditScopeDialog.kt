package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R

/** Куда сохранить правку записи, открытой из раздела персонажа. */
enum class EditScope {
    /** В общую библиотеку: изменение увидят все персонажи. */
    EVERYONE,

    /** Только этому персонажу: библиотека остаётся нетронутой. */
    THIS_CHARACTER,
}

/**
 * Спрашивает, куда сохранить правку: в библиотеку или только текущему персонажу.
 *
 * Показывается при редактировании черты или заклинания из раздела персонажа — там
 * запись берётся из общей библиотеки, и без такого выбора правка «под себя» незаметно
 * меняла бы текст у всех остальных персонажей.
 *
 * @param characterName имя персонажа для подписи варианта «только ему».
 */
@Composable
fun EditScopeDialog(
    characterName: String,
    onDismiss: () -> Unit,
    onSelect: (EditScope) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.edit_scope_title)) },
        text = { Text(stringResource(R.string.edit_scope_text)) },
        confirmButton = {
            // Оба варианта равнозначны, поэтому показываем их одинаковыми кнопками в столбик:
            // в строку длинные подписи не помещаются.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSelect(EditScope.THIS_CHARACTER) },
                    modifier = Modifier,
                ) {
                    Text(stringResource(R.string.edit_scope_character, characterName))
                }
                TextButton(onClick = { onSelect(EditScope.EVERYONE) }) {
                    Text(stringResource(R.string.edit_scope_everyone))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
