package com.example.spellbook.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import com.example.spellbook.util.DiceRoller
import com.example.spellbook.util.HtmlUtils

/** Расстояние между блоками описания и пунктами списка. */
private val DESCRIPTION_BLOCK_SPACING = 8.dp
private val DESCRIPTION_LIST_SPACING = 4.dp

/** Минимальная ширина маркера списка — чтобы текст пунктов был выровнен. */
private val DESCRIPTION_LIST_MARKER_WIDTH = 20.dp

/** Ширина бордовой полосы слева от справочной врезки. */
private val CALLOUT_STRIPE_WIDTH = 4.dp

/** Увеличенная высота строки: длинные описания читаются легче. */
private val DESCRIPTION_LINE_HEIGHT = 22.sp


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
internal fun DescriptionText(description: String, onDiceClick: (String) -> Unit) {
    val blocks = remember(description) { splitDescriptionBlocks(description) }
    DescriptionBlocks(blocks, onDiceClick)
}

/** Рисует разобранные блоки описания друг за другом. */
@Composable
private fun DescriptionBlocks(blocks: List<DescriptionBlock>, onDiceClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DESCRIPTION_BLOCK_SPACING)) {
        blocks.forEach { block ->
            when (block) {
                is DescriptionBlock.Paragraphs -> Text(
                    text = annotatedDescription(block.text, onDiceClick),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = DESCRIPTION_LINE_HEIGHT,
                )

                is DescriptionBlock.Heading -> Text(
                    text = annotatedDescription(block.text, onDiceClick),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                is DescriptionBlock.ListBlock -> DescriptionList(block, onDiceClick)
                is DescriptionBlock.Table -> DescriptionTable(block, onDiceClick)
                is DescriptionBlock.Callout -> DescriptionCallout(block, onDiceClick)
            }
        }
    }
}

/** Маркированный или нумерованный список с выровненными маркерами. */
@Composable
private fun DescriptionList(list: DescriptionBlock.ListBlock, onDiceClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DESCRIPTION_LIST_SPACING)) {
        list.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (list.numbered) "${index + 1}." else "\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    // Фиксированная ширина держит текст пунктов на одной вертикали.
                    modifier = Modifier.widthIn(min = DESCRIPTION_LIST_MARKER_WIDTH),
                )
                Text(
                    text = annotatedDescription(item, onDiceClick),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = DESCRIPTION_LINE_HEIGHT,
                )
            }
        }
    }
}

/**
 * Блок со справочной информацией — то, что на dnd.su оформлено как `additionalInfo`.
 * Выделен фоном и бордовой полосой слева, чтобы не путать с основным текстом.
 */
@Composable
private fun DescriptionCallout(callout: DescriptionBlock.Callout, onDiceClick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(CALLOUT_STRIPE_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(DESCRIPTION_BLOCK_SPACING),
            ) {
                if (callout.title.isNotBlank()) {
                    Text(
                        text = callout.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DescriptionBlocks(callout.blocks, onDiceClick)
            }
        }
    }
}

/** Строит текст с костями, словами-ссылками и инлайновыми выделениями. */
@Composable
private fun annotatedDescription(text: String, onDiceClick: (String) -> Unit) = buildAnnotatedString {
    val diceLinkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val refStyle = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)

    // Собираем токены всех типов и сортируем по позиции, чтобы пройтись по тексту один раз.
    val tokens = (
        DiceRoller.DICE_TOKEN_REGEX.findAll(text).map { DescriptionToken.Dice(it.range, it.groupValues[1]) } +
            HtmlUtils.REF_TOKEN_REGEX.findAll(text).map { DescriptionToken.Ref(it.range, it.groupValues[1]) } +
            HtmlUtils.BOLD_REGEX.findAll(text).map { DescriptionToken.Bold(it.range, it.groupValues[1]) } +
            HtmlUtils.ITALIC_REGEX.findAll(text).map { DescriptionToken.Italic(it.range, it.groupValues[1]) }
        ).sortedBy { it.range.first }

    var lastIndex = 0
    tokens.forEach { token ->
        if (token.range.first < lastIndex) return@forEach
        append(text.substring(lastIndex, token.range.first))
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

            is DescriptionToken.Ref -> withStyle(refStyle) { append(token.text) }
            is DescriptionToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.text)
            }

            is DescriptionToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.text)
            }
        }
        lastIndex = token.range.last + 1
    }
    append(text.substring(lastIndex))
}

/** Таблица из описания: шапка выделена фоном, колонки делят ширину поровну. */
@Composable
private fun DescriptionTable(table: DescriptionBlock.Table, onDiceClick: (String) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            table.header?.let { header ->
                TableRow(cells = header, isHeader = true, onDiceClick = onDiceClick)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            table.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                TableRow(cells = row, isHeader = false, onDiceClick = onDiceClick)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean, onDiceClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHeader) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEach { cell ->
            Text(
                text = annotatedDescription(cell, onDiceClick),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Блок описания: текст, заголовок, список, таблица или справочная врезка. */
private sealed interface DescriptionBlock {
    data class Paragraphs(val text: String) : DescriptionBlock
    data class Heading(val text: String) : DescriptionBlock
    data class ListBlock(val items: List<String>, val numbered: Boolean) : DescriptionBlock
    data class Table(val header: List<String>?, val rows: List<List<String>>) : DescriptionBlock
    data class Callout(val title: String, val blocks: List<DescriptionBlock>) : DescriptionBlock
}

/**
 * Делит описание на блоки: строки с `|` собираются в таблицу, строки с маркерами —
 * в список, а участки между `:::` — во вложенный блок со справочной информацией.
 */
private fun splitDescriptionBlocks(description: String): List<DescriptionBlock> {
    val lines = description.split("\n")
    val blocks = mutableListOf<DescriptionBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += DescriptionBlock.Paragraphs(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            HtmlUtils.isCalloutStart(line) -> {
                flushParagraph()
                val title = HtmlUtils.calloutTitle(line)
                val body = mutableListOf<String>()
                index++
                while (index < lines.size && !HtmlUtils.isCalloutEnd(lines[index])) {
                    body += lines[index]
                    index++
                }
                if (index < lines.size) index++ // пропускаем закрывающий маркер
                // Содержимое врезки разбираем тем же алгоритмом.
                blocks += DescriptionBlock.Callout(title, splitDescriptionBlocks(body.joinToString("\n")))
            }

            HtmlUtils.isHeading(line) -> {
                flushParagraph()
                blocks += DescriptionBlock.Heading(HtmlUtils.headingText(line))
                index++
            }

            HtmlUtils.isTableRow(line) -> {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                var headerSize = 0
                while (index < lines.size && HtmlUtils.isTableRow(lines[index])) {
                    if (HtmlUtils.isTableSeparator(lines[index])) {
                        // Разделитель говорит, что всё собранное выше — шапка.
                        headerSize = rows.size
                    } else {
                        rows += HtmlUtils.parseTableRow(lines[index])
                    }
                    index++
                }
                if (rows.isNotEmpty()) {
                    val header = rows.firstOrNull()?.takeIf { headerSize > 0 }
                    blocks += DescriptionBlock.Table(
                        header = header,
                        rows = if (header != null) rows.drop(1) else rows,
                    )
                }
            }

            HtmlUtils.isBulletItem(line) || HtmlUtils.isNumberedItem(line) -> {
                flushParagraph()
                val numbered = HtmlUtils.isNumberedItem(line)
                val items = mutableListOf<String>()
                // Список продолжается, пока пункты того же типа идут подряд.
                while (index < lines.size &&
                    (if (numbered) HtmlUtils.isNumberedItem(lines[index]) else HtmlUtils.isBulletItem(lines[index]))
                ) {
                    items += HtmlUtils.listItemText(lines[index])
                    index++
                }
                blocks += DescriptionBlock.ListBlock(items, numbered)
            }

            else -> {
                paragraph += line
                index++
            }
        }
    }
    flushParagraph()
    return blocks
}

/** Размеченный фрагмент описания: бросок костей или выделенная ссылка-слово. */
private sealed interface DescriptionToken {
    val range: IntRange
    val text: String

    data class Dice(override val range: IntRange, override val text: String) : DescriptionToken
    data class Ref(override val range: IntRange, override val text: String) : DescriptionToken
    data class Bold(override val range: IntRange, override val text: String) : DescriptionToken
    data class Italic(override val range: IntRange, override val text: String) : DescriptionToken
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
