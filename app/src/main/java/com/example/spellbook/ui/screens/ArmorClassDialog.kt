package com.example.spellbook.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.DEFAULT_INITIATIVE_FORMULA
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.initiativePartsOrDefault
import com.example.spellbook.data.activeStatBase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.DEFAULT_SPEED_FORMULA
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.UNARMORED_AC_FORMULA
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.data.statPartValue
import com.example.spellbook.data.statTotal
import com.example.spellbook.ui.components.FormulaTextField
import com.example.spellbook.ui.humanizeFormula

/**
 * Тексты и значения по умолчанию для окна составляющих показателя (КД, скорость).
 *
 * @param defaultBase основа, которая действует, если все основы отключены.
 * @param unit подпись единицы после значения (« фт.»); пусто — без единицы.
 */
private data class StatPartsConfig(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    @param:StringRes val defaultBaseName: Int,
    @param:StringRes val storedBaseName: Int,
    @param:StringRes val baseOverridden: Int,
    @param:StringRes val newPart: Int,
    @param:StringRes val editPart: Int,
    @param:StringRes val baseHint: Int,
    @param:StringRes val bonusHint: Int,
    @param:StringRes val baseNamePlaceholder: Int,
    @param:StringRes val bonusNamePlaceholder: Int,
    val defaultBase: String,
    val newBaseFormula: String,
    val newBonusFormula: String,
    val basePlaceholder: String,
    val bonusPlaceholder: String,
    val unit: String = "",
    /** Показывать итог и основу со знаком (инициатива: «+3»). */
    val signed: Boolean = false,
)

private val ARMOR_CLASS_CONFIG = StatPartsConfig(
    icon = Icons.Default.Shield,
    title = R.string.stats_armor_class,
    hint = R.string.ac_hint,
    defaultBaseName = R.string.ac_unarmored_default,
    storedBaseName = R.string.ac_base_default_name,
    baseOverridden = R.string.ac_base_overridden,
    newPart = R.string.ac_new_part,
    editPart = R.string.ac_edit_part,
    baseHint = R.string.ac_kind_base_hint,
    bonusHint = R.string.ac_kind_bonus_hint,
    baseNamePlaceholder = R.string.ac_part_name_base_hint,
    bonusNamePlaceholder = R.string.ac_part_name_bonus_hint,
    defaultBase = UNARMORED_AC_FORMULA,
    newBaseFormula = UNARMORED_AC_FORMULA,
    newBonusFormula = "1",
    basePlaceholder = "13 + min([dex], 2)",
    bonusPlaceholder = "1",
)

private val SPEED_CONFIG = StatPartsConfig(
    icon = Icons.AutoMirrored.Filled.DirectionsRun,
    title = R.string.speed_title,
    hint = R.string.speed_hint,
    defaultBaseName = R.string.speed_default_base,
    storedBaseName = R.string.speed_base_default_name,
    baseOverridden = R.string.speed_base_overridden,
    newPart = R.string.speed_new_part,
    editPart = R.string.speed_edit_part,
    baseHint = R.string.speed_kind_base_hint,
    bonusHint = R.string.speed_kind_bonus_hint,
    baseNamePlaceholder = R.string.speed_part_name_base_hint,
    bonusNamePlaceholder = R.string.speed_part_name_bonus_hint,
    defaultBase = DEFAULT_SPEED_FORMULA,
    newBaseFormula = DEFAULT_SPEED_FORMULA,
    newBonusFormula = "10",
    basePlaceholder = "30",
    bonusPlaceholder = "10",
)

private val INITIATIVE_CONFIG = StatPartsConfig(
    icon = Icons.Default.Bolt,
    title = R.string.stats_initiative,
    hint = R.string.initiative_hint,
    defaultBaseName = R.string.initiative_default_base,
    storedBaseName = R.string.initiative_base_default_name,
    baseOverridden = R.string.initiative_base_overridden,
    newPart = R.string.initiative_new_part,
    editPart = R.string.initiative_edit_part,
    baseHint = R.string.initiative_kind_base_hint,
    bonusHint = R.string.initiative_kind_bonus_hint,
    baseNamePlaceholder = R.string.initiative_part_name_base_hint,
    bonusNamePlaceholder = R.string.initiative_part_name_bonus_hint,
    defaultBase = DEFAULT_INITIATIVE_FORMULA,
    newBaseFormula = DEFAULT_INITIATIVE_FORMULA,
    newBonusFormula = "[pb]",
    basePlaceholder = DEFAULT_INITIATIVE_FORMULA,
    bonusPlaceholder = "5",
    signed = true,
)

/**
 * Разбор КД: из чего складывается класс доспеха.
 *
 * Основы (доспех, защита без доспехов) не складываются — действует наибольшая из
 * включённых, она отмечена. Прибавки (щит, черты, стили) суммируются. Любую
 * составляющую можно отключить переключателем (сняли щит), изменить или удалить.
 *
 * Старые персонажи без составляющих видят своё число КД и могут перевести его
 * в составляющие одной кнопкой: число станет «Базовым КД».
 */
@Composable
fun ArmorClassDialog(
    character: Character,
    onDismiss: () -> Unit,
    onSave: (List<StatPart>) -> Unit,
) = StatPartsDialog(
    character = character,
    initialParts = character.armorClassParts,
    storedValue = character.armorClass,
    config = ARMOR_CLASS_CONFIG,
    onDismiss = onDismiss,
    onSave = onSave,
)

/**
 * Разбор скорости: скорость расы и прибавки от черт и умений («Подвижный»,
 * «Движение без доспехов»). Устроен так же, как разбор КД.
 */
@Composable
fun SpeedDialog(
    character: Character,
    onDismiss: () -> Unit,
    onSave: (List<StatPart>) -> Unit,
) = StatPartsDialog(
    character = character,
    initialParts = character.speedParts,
    storedValue = character.speed,
    config = SPEED_CONFIG.copy(unit = stringResource(R.string.speed_unit_suffix)),
    onDismiss = onDismiss,
    onSave = onSave,
)

/**
 * Разбор инициативы: модификатор Ловкости и прибавки от черт и умений
 * («Бдительный», «Волшебная прозорливость»). Устроен так же, как разбор КД.
 * У персонажа без составляющих прежняя формула инициативы становится основой.
 */
@Composable
fun InitiativeDialog(
    character: Character,
    onDismiss: () -> Unit,
    onSave: (List<StatPart>) -> Unit,
) = StatPartsDialog(
    character = character,
    initialParts = character.initiativePartsOrDefault,
    storedValue = 0,
    config = INITIATIVE_CONFIG,
    onDismiss = onDismiss,
    onSave = onSave,
)

/**
 * Окно составляющих показателя.
 *
 * @param storedValue число показателя у персонажа без составляющих: становится основой.
 */
@Composable
private fun StatPartsDialog(
    character: Character,
    initialParts: List<StatPart>,
    storedValue: Int,
    config: StatPartsConfig,
    onDismiss: () -> Unit,
    onSave: (List<StatPart>) -> Unit,
) {
    var parts by remember {
        mutableStateOf(
            initialParts.ifEmpty {
                listOf(StatPart(name = "", formula = storedValue.toString(), kind = StatPartKind.BASE))
            },
        )
    }
    fun shown(value: Int) = if (config.signed) formatModifier(value) else value.toString()
    var editing by remember { mutableStateOf<StatPart?>(null) }
    val activeBase = character.activeStatBase(parts)
    val total = character.statTotal(parts, config.defaultBase)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = { Icon(config.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(config.title))
                Text(
                    shown(total) + config.unit,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(config.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeBase == null) {
                    // Все основы отключены — действует основа по умолчанию.
                    PartRow(
                        name = stringResource(config.defaultBaseName),
                        detail = humanizeFormula(config.defaultBase),
                        value = StatFormula.evaluateValue(config.defaultBase, character),
                        isBonus = config.signed,
                        enabled = true,
                        applied = true,
                        overriddenLabel = stringResource(config.baseOverridden),
                        onToggle = null,
                        onClick = null,
                    )
                }
                parts.sortedBy { it.kind }.forEach { part ->
                    PartRow(
                        name = part.name.ifBlank { stringResource(config.storedBaseName) },
                        detail = listOf(humanizeFormula(part.formula), part.description).filter { it.isNotBlank() }.joinToString(" · "),
                        value = character.statPartValue(part),
                        isBonus = part.kind == StatPartKind.BONUS || config.signed,
                        enabled = part.enabled,
                        applied = part.kind == StatPartKind.BONUS || part.id == activeBase?.id,
                        overriddenLabel = stringResource(config.baseOverridden),
                        onToggle = { enabled -> parts = parts.map { if (it.id == part.id) it.copy(enabled = enabled) else it } },
                        onClick = { editing = part },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { editing = StatPart(name = "", formula = config.newBonusFormula, kind = StatPartKind.BONUS) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ac_add_bonus))
                    }
                    OutlinedButton(
                        onClick = { editing = StatPart(name = "", formula = config.newBaseFormula, kind = StatPartKind.BASE) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ac_add_base))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(parts) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    editing?.let { part ->
        StatPartEditor(
            initial = part,
            isNew = parts.none { it.id == part.id },
            config = config,
            onDismiss = { editing = null },
            onDelete = {
                parts = parts.filterNot { it.id == part.id }
                editing = null
            },
            onSave = { updated ->
                parts = if (parts.any { it.id == updated.id }) {
                    parts.map { if (it.id == updated.id) updated else it }
                } else {
                    parts + updated
                }
                editing = null
            },
        )
    }
}

/** Строка составляющей: название, формула, значение и переключатель. */
@Composable
private fun PartRow(
    name: String,
    detail: String,
    value: Int?,
    isBonus: Boolean,
    enabled: Boolean,
    applied: Boolean,
    overriddenLabel: String,
    onToggle: ((Boolean) -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val active = enabled && applied
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Основа включена, но перекрыта большей — подсказываем, почему не учитывается.
                if (enabled && !applied) {
                    Text(overriddenLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                when {
                    value == null -> "?"
                    isBonus -> formatModifier(value)
                    else -> value.toString()
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (onToggle != null) Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** Редактор составляющей: вид, название, формула и пояснение. */
@Composable
private fun StatPartEditor(
    initial: StatPart,
    isNew: Boolean,
    config: StatPartsConfig,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (StatPart) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var formula by remember { mutableStateOf(initial.formula) }
    var kind by remember { mutableStateOf(initial.kind) }
    var description by remember { mutableStateOf(initial.description) }
    val valid = formula.isNotBlank() && StatFormula.isValid(formula) && !StatFormula.hasDice(formula)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(if (isNew) config.newPart else config.editPart)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatPartKind.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = kind == value,
                            onClick = { kind = value },
                            shape = SegmentedButtonDefaults.itemShape(index, StatPartKind.entries.size),
                        ) {
                            Text(stringResource(if (value == StatPartKind.BASE) R.string.ac_kind_base else R.string.ac_kind_bonus))
                        }
                    }
                }
                Text(
                    stringResource(if (kind == StatPartKind.BASE) config.baseHint else config.bonusHint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ac_part_name)) },
                    placeholder = {
                        Text(stringResource(if (kind == StatPartKind.BASE) config.baseNamePlaceholder else config.bonusNamePlaceholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormulaTextField(
                    value = formula,
                    onValueChange = { formula = it },
                    label = stringResource(R.string.ac_part_formula),
                    placeholder = if (kind == StatPartKind.BASE) config.basePlaceholder else config.bonusPlaceholder,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.ac_part_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { formula.trim() },
                            formula = formula.trim(),
                            kind = kind,
                            description = description.trim(),
                        ),
                    )
                },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
