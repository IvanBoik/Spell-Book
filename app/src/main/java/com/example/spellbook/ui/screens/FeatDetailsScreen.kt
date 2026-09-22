package com.example.spellbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.Feat
import com.example.spellbook.ui.components.DetailsActionBar
import com.example.spellbook.ui.components.DetailsActionBarReservedHeight
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.EditScope
import com.example.spellbook.ui.components.EditScopeDialog
import com.example.spellbook.util.DiceRoller

/** Отступ снизу, чтобы последняя строка описания не прилипала к краю экрана. */
private val BOTTOM_SPACING = 16.dp

/**
 * Экран просмотра черты.
 *
 * Устроен как экран заклинания: описание рисуется общим [DescriptionText], поэтому
 * кости кликабельны, а термины из ссылок dnd.su подсвечены. Действия — в верхней
 * панели: поделиться, выгрузить JSON, отредактировать и удалить.
 *
 * Экран общий для библиотеки и раздела персонажа. В контексте персонажа ([characterName]
 * задан) правка спрашивает, менять ли текст для всех или только этому персонажу,
 * а удаление убирает черту у персонажа, оставляя её в библиотеке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatDetailsScreen(
    feat: Feat,
    onBack: () -> Unit,
    /** [EditScope] равен null, когда черта правится из библиотеки. */
    onEdit: (name: String, description: String, scope: EditScope?) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    /** Имя персонажа, из раздела которого открыта черта; null — библиотека. */
    characterName: String? = null,
    /** Правлена ли черта персонально для этого персонажа. */
    hasPersonalEdit: Boolean = false,
    /**
     * Есть ли черта у персонажа; null — взять черту нельзя (простой просмотр).
     * Задано на экране добавления черт персонажу.
     */
    addedToCharacter: Boolean? = null,
    onToggleForCharacter: () -> Unit = {},
) {
    var rollResult by remember { mutableStateOf<DiceRoller.Result?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Текст, введённый в редакторе и ожидающий выбора области сохранения.
    var pendingEdit by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            DndTopBar(
                title = feat.name,
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Книга-источник подсказывает, откуда черта, — как и в списке библиотеки.
                    if (feat.book.isNotBlank()) {
                        Text(
                            feat.book,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Явно помечаем, что текст отличается от библиотечного.
                    if (hasPersonalEdit) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.edit_personal_badge)) },
                        )
                    }
                }

                DescriptionText(
                    description = feat.description,
                    onDiceClick = { formula -> rollResult = DiceRoller.roll(formula) },
                )

                // При выборе черт персонажу её можно взять сразу после прочтения описания.
                addedToCharacter?.let { added ->
                    Spacer(Modifier.height(BOTTOM_SPACING))
                    if (added) {
                        OutlinedButton(
                            onClick = onToggleForCharacter,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.feats_remove_from_character))
                        }
                    } else {
                        Button(
                            onClick = onToggleForCharacter,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.feats_add_to_character))
                        }
                    }
                }

                // Место под плавающий блок действий. Если внизу есть кнопка «Добавить
                // персонажу», отступ остаётся таким же — тогда при прокрутке до упора
                // блок действий оказывается ниже этой кнопки, а не поверх неё.
                Spacer(Modifier.height(DetailsActionBarReservedHeight))
            }

            DetailsActionBar(
                onEdit = { editing = true },
                onDelete = { confirmDelete = true },
                onShare = onShare,
                onExport = onExport,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            rollResult?.let { result ->
                DiceResultCard(
                    result = result,
                    onClose = { rollResult = null },
                    // Поднимаем плашку над блоком действий: иначе на узких экранах они налезают друг на друга.
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = DetailsActionBarReservedHeight),
                )
            }
        }
    }

    if (editing) {
        FeatEditorDialog(
            title = stringResource(R.string.feats_edit),
            initialName = feat.name,
            initialDescription = feat.description,
            onDismiss = { editing = false },
            onConfirm = { name, description ->
                editing = false
                // Из библиотеки правка всегда общая, из раздела персонажа — спрашиваем.
                if (characterName == null) onEdit(name, description, null)
                else pendingEdit = name to description
            },
        )
    }

    pendingEdit?.let { (name, description) ->
        EditScopeDialog(
            characterName = characterName.orEmpty(),
            onDismiss = { pendingEdit = null },
            onSelect = { scope ->
                pendingEdit = null
                onEdit(name, description, scope)
            },
        )
    }

    if (confirmDelete) {
        // В библиотеке черта удаляется насовсем, у персонажа — только убирается из набора.
        val inLibrary = characterName == null
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(
                        if (inLibrary) R.string.feats_delete_from_library else R.string.feats_remove_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (inLibrary) R.string.library_feats_delete_text else R.string.feats_remove_text,
                        feat.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(if (inLibrary) R.string.action_delete else R.string.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
