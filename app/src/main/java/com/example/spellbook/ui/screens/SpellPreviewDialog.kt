package com.example.spellbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spellbook.R
import com.example.spellbook.data.SpellLinkIndex
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.util.DiceRoller

/**
 * Предоставляет ссылки на заклинания [LocalSpellLinks] всему содержимому и показывает
 * окно-превью по нажатию на ссылку.
 *
 * Индекс перестраивается только при смене списка заклинаний библиотеки.
 */
@Composable
fun ProvideSpellLinks(spells: List<Spell>, content: @Composable () -> Unit) {
    var opened by remember { mutableStateOf<Spell?>(null) }
    val index = remember(spells) { SpellLinkIndex(spells) }
    val links = remember(index) { SpellLinks(index) { opened = it } }
    CompositionLocalProvider(LocalSpellLinks provides links) {
        content()
        opened?.let { spell -> SpellPreviewDialog(spell = spell, onDismiss = { opened = null }) }
    }
}

/**
 * Окно с описанием заклинания поверх текущего экрана — по нажатию на ссылку в тексте.
 *
 * Навигации нет намеренно: ссылка может стоять в мастере создания персонажа, в диалоге
 * ресурса или в заметке, и уход с экрана потерял бы их состояние. Ссылки внутри окна
 * открывают другое заклинание в нём же: окно наследует [LocalSpellLinks] и просто меняет [spell].
 */
@Composable
fun SpellPreviewDialog(spell: Spell, onDismiss: () -> Unit) {
    var rollResult by remember(spell.id) { mutableStateOf<DiceRoller.Result?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                DndTopBar(
                    title = spell.name,
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpellInfoContent(
                        spell = spell,
                        onDiceClick = { formula -> DiceRoller.roll(formula)?.let { rollResult = it } },
                    )
                }
                rollResult?.let { result ->
                    DiceResultCard(
                        result = result,
                        onClose = { rollResult = null },
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    )
                }
            }
        }
    }
}
