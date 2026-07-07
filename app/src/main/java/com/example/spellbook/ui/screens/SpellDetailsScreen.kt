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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.util.DiceRoller
import com.example.spellbook.util.HtmlUtils


/**
 * Экран просмотра заклинания: показывает все характеристики в формате LSS
 * и предоставляет действия редактирования, экспорта JSON и удаления.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellDetailsScreen(
    spell: Spell,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var rollResult by remember { mutableStateOf<DiceRoller.Result?>(null) }

    Scaffold(
        topBar = {
            DndTopBar(
                title = spell.name,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = headerLabel(spell),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                InfoRow("Время накладывания", activationLabel(spell))
                InfoRow("Длительность", durationLabel(spell))
                InfoRow("Дистанция", rangeLabel(spell))
                InfoRow("Компоненты", componentsLabel(spell))
                if (spell.classes.isNotEmpty()) {
                    InfoRow("Классы", spell.classes.joinToString { SpellOptions.labelFor(SpellOptions.classes, it) })
                }
                if (spell.source.isNotBlank()) {
                    InfoRow("Источник", spell.source)
                }

                if (spell.description.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Описание", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DescriptionText(
                        description = spell.description,
                        onDiceClick = { formula -> DiceRoller.roll(formula)?.let { rollResult = it } },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Редактировать") }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Выгрузить JSON") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            rollResult?.let { result ->
                DiceResultCard(
                    result = result,
                    onClose = { rollResult = null },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заклинание?") },
            text = { Text("«${spell.name}» будет удалено без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Описание заклинания с кликабельными костями и подсвеченными ссылками.
 * Токены `[[/r 2d6]]` заменяются на выделенную формулу `2d6` (по нажатию — [onDiceClick]),
 * а токены `[[ref Рывок]]` — на подсвеченное фоном слово, чтобы за него цеплялся глаз.
 */
@Composable
private fun DescriptionText(description: String, onDiceClick: (String) -> Unit) {
    val diceLinkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val refStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
    )

    // Собираем токены обоих типов и сортируем по позиции, чтобы пройтись по тексту один раз.
    val tokens = (
        DiceRoller.DICE_TOKEN_REGEX.findAll(description).map { DescriptionToken.Dice(it.range, it.groupValues[1]) } +
            HtmlUtils.REF_TOKEN_REGEX.findAll(description).map { DescriptionToken.Ref(it.range, it.groupValues[1]) }
        ).sortedBy { it.range.first }

    val annotated = buildAnnotatedString {
        var lastIndex = 0
        tokens.forEach { token ->
            if (token.range.first < lastIndex) return@forEach
            append(description.substring(lastIndex, token.range.first))
            when (token) {
                is DescriptionToken.Dice -> withLink(
                    LinkAnnotation.Clickable(
                        tag = "dice",
                        styles = diceLinkStyle,
                        linkInteractionListener = { onDiceClick(token.text) },
                    ),
                ) {
                    append(token.text)
                }

                is DescriptionToken.Ref -> withStyle(refStyle) {
                    append(token.text)
                }
            }
            lastIndex = token.range.last + 1
        }
        append(description.substring(lastIndex))
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}

/** Размеченный фрагмент описания: бросок костей или выделенная ссылка-слово. */
private sealed interface DescriptionToken {
    val range: IntRange
    val text: String

    data class Dice(override val range: IntRange, override val text: String) : DescriptionToken
    data class Ref(override val range: IntRange, override val text: String) : DescriptionToken
}

/** Плавающее окно с результатом броска костей в левом нижнем углу. */
@Composable
private fun DiceResultCard(
    result: DiceRoller.Result,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = 280.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.formula,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }
            Text(
                text = result.rolls.joinToString(" + "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Итого: ${result.total}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun levelLabelDetails(level: Int): String =
    SpellOptions.levels.firstOrNull { it.first == level }?.second ?: "$level круг"

private fun headerLabel(spell: Spell): String = buildString {
    append(levelLabelDetails(spell.level))
    append(" · ")
    append(SpellOptions.labelFor(SpellOptions.schools, spell.school))
    if (spell.components.ritual) append(" · ритуал")
}

private fun activationLabel(spell: Spell): String {
    val type = SpellOptions.labelFor(SpellOptions.activationTypes, spell.activationType)
    val cost = spell.activationCost?.takeIf { it > 1 }?.let { "$it " }.orEmpty()
    return (cost + type).trim()
}

private fun durationLabel(spell: Spell): String {
    val units = SpellOptions.labelFor(SpellOptions.durationUnits, spell.durationUnits)
    val value = spell.durationValue
    val concentration = if (spell.components.concentration) "Концентрация, " else ""
    return concentration + if (value != null && value > 0) "$value $units" else units
}

private fun rangeLabel(spell: Spell): String {
    val units = SpellOptions.labelFor(SpellOptions.rangeUnits, spell.rangeUnits)
    val value = spell.rangeValue
    return if (value != null && value > 0) "$value $units" else units
}

private fun componentsLabel(spell: Spell): String {
    val parts = buildList {
        if (spell.components.vocal) add("V")
        if (spell.components.somatic) add("S")
        if (spell.components.material) {
            val material = spell.materials.value.takeIf { it.isNotBlank() }
            add(if (material != null) "M ($material)" else "M")
        }
    }
    return if (parts.isEmpty()) "—" else parts.joinToString(", ")
}
