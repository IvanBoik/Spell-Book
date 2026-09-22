package com.example.spellbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.DamagePart
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.EditScope
import com.example.spellbook.ui.components.EditScopeDialog
import com.example.spellbook.ui.components.imeAwareContentInsets
import com.example.spellbook.ui.components.LabeledSwitchRow
import com.example.spellbook.ui.components.NumberField
import com.example.spellbook.ui.components.OptionField
import com.example.spellbook.ui.spellLevelPairs
import com.example.spellbook.ui.spellOptionPairs

/** Код типа действия LSS, при котором заклинание требует спасброска. */
private const val ACTION_TYPE_SAVE = "save"

/** Единицы длительности, не подразумевающие числового значения (Мгновенная, Постоянная, Особая). */
private val DURATION_UNITS_WITHOUT_VALUE = setOf("inst", "perm", "spec")

/** Единицы дистанции, не подразумевающие числового значения (На себя, Касание, Особая, Любая). */
private val RANGE_UNITS_WITHOUT_VALUE = setOf("self", "touch", "spec", "any")

/**
 * Типы цели/области, имеющие геометрический размер (Сфера, Конус и т.п.).
 * Только для них доступны поля «Размер» и «Единица».
 */
private val TARGET_TYPES_WITH_SIZE = setOf(
    "radius", "sphere", "cylinder", "cone", "cube", "line", "wall", "space",
)

/** Типы активации, не подразумевающие числового времени сотворения (Особая). */
private val ACTIVATION_TYPES_WITHOUT_VALUE = setOf("special")

/**
 * Заготовка таблицы с шапкой и двумя строками — пользователю остаётся заменить текст.
 * Подписи берутся из ресурсов: шаблон должен быть на языке интерфейса.
 */
private fun tableTemplate(header: String, cell: String): String = listOf(
    "| $header 1 | $header 2 |",
    "| --- | --- |",
    "| $cell | $cell |",
    "| $cell | $cell |",
).joinToString("\n")

/** Добавляет шаблон таблицы в конец описания, отделяя его от текста. */
private fun appendTableTemplate(description: String, template: String): String =
    if (description.isBlank()) template else "${description.trimEnd()}\n$template"

/**
 * Форма создания/редактирования заклинания. Все поля соответствуют формату LSS,
 * чтобы созданное заклинание можно было без потерь выгрузить и загрузить обратно.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpellFormScreen(
    initial: Spell,
    isNew: Boolean,
    /** [EditScope] равен null, когда заклинание правится из библиотеки. */
    onSave: (Spell, EditScope?) -> Unit,
    onBack: () -> Unit,
    /**
     * Имя персонажа, из раздела которого открыта форма; null — библиотека.
     * Если задано, перед сохранением спрашиваем, менять ли текст у всех или только у него.
     */
    characterName: String? = null,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var nameError by remember(initial.id) { mutableStateOf(false) }
    // Заклинание, ожидающее выбора области сохранения.
    var pendingSave by remember(initial.id) { mutableStateOf<Spell?>(null) }

    val levelOptions = spellLevelPairs().map { (level, label) -> level.toString() to label }
    val schoolOptions = spellOptionPairs(SpellOptions.schools)
    val activationOptions = spellOptionPairs(SpellOptions.activationTypes)
    val durationOptions = spellOptionPairs(SpellOptions.durationUnits)
    val rangeOptions = spellOptionPairs(SpellOptions.rangeUnits)
    val targetTypeOptions = spellOptionPairs(SpellOptions.targetTypes)
    val targetUnitOptions = spellOptionPairs(SpellOptions.targetUnits)
    val actionTypeOptions = spellOptionPairs(SpellOptions.actionTypes)
    val abilityOptions = spellOptionPairs(SpellOptions.abilities)
    val damageTypeOptions = spellOptionPairs(SpellOptions.damageTypes)
    val classOptions = spellOptionPairs(SpellOptions.classes)
    val tableTemplate = tableTemplate(
        header = stringResource(R.string.form_table_header),
        cell = stringResource(R.string.form_table_cell),
    )

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(
                    if (isNew) R.string.form_new_spell else R.string.form_edit_spell,
                ),
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
        // Длинная форма: поля внизу иначе оказываются под клавиатурой.
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it)
                    if (it.isNotBlank()) nameError = false
                },
                label = { Text(stringResource(R.string.form_name)) },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.form_name_required)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionField(
                    label = stringResource(R.string.form_level),
                    value = draft.level.toString(),
                    options = levelOptions,
                    onValueChange = { draft = draft.copy(level = it.toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = stringResource(R.string.form_school),
                    value = draft.school,
                    options = schoolOptions,
                    onValueChange = { draft = draft.copy(school = it) },
                    modifier = Modifier.weight(1f),
                )
            }

//            OutlinedTextField(
//                value = draft.source,
//                onValueChange = { draft = draft.copy(source = it) },
//                label = { Text("Источник") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//            )

            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.form_description)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            // Таблицы записываются строками с `|`, поэтому предлагаем готовый шаблон.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.form_markup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    draft = draft.copy(description = appendTableTemplate(draft.description, tableTemplate))
                }) {
                    Text(stringResource(R.string.form_insert_table))
                }
            }

            SectionTitle(stringResource(R.string.form_casting_time))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = stringResource(R.string.form_value),
                    value = draft.activationCost,
                    onValueChange = { draft = draft.copy(activationCost = it) },
                    enabled = draft.activationType !in ACTIVATION_TYPES_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = stringResource(R.string.form_units),
                    value = draft.activationType,
                    options = activationOptions,
                    onValueChange = { unit ->
                        draft = draft.copy(
                            activationType = unit,
                            activationCost = if (unit in ACTIVATION_TYPES_WITHOUT_VALUE) null else draft.activationCost,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = draft.activationCondition,
                onValueChange = { draft = draft.copy(activationCondition = it) },
                label = { Text(stringResource(R.string.form_casting_condition)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.form_duration))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = stringResource(R.string.form_value),
                    value = draft.durationValue,
                    onValueChange = { draft = draft.copy(durationValue = it) },
                    enabled = draft.durationUnits !in DURATION_UNITS_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = stringResource(R.string.form_units),
                    value = draft.durationUnits,
                    options = durationOptions,
                    onValueChange = { unit ->
                        draft = draft.copy(
                            durationUnits = unit,
                            durationValue = if (unit in DURATION_UNITS_WITHOUT_VALUE) null else draft.durationValue,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.form_range))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = stringResource(R.string.form_value),
                    value = draft.rangeValue,
                    onValueChange = { draft = draft.copy(rangeValue = it) },
                    enabled = draft.rangeUnits !in RANGE_UNITS_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = stringResource(R.string.form_units),
                    value = draft.rangeUnits,
                    options = rangeOptions,
                    onValueChange = { unit ->
                        draft = draft.copy(
                            rangeUnits = unit,
                            rangeValue = if (unit in RANGE_UNITS_WITHOUT_VALUE) null else draft.rangeValue,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.form_target))
            val targetHasSize = draft.target.type in TARGET_TYPES_WITH_SIZE
            OptionField(
                label = stringResource(R.string.form_target),
                value = draft.target.type,
                options = targetTypeOptions,
                onValueChange = { type ->
                    draft = if (type in TARGET_TYPES_WITH_SIZE) {
                        draft.copy(target = draft.target.copy(type = type))
                    } else {
                        // Тип без размера: сбрасываем размер и единицу.
                        draft.copy(target = draft.target.copy(type = type, value = null, units = ""))
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = stringResource(R.string.form_target_size),
                    value = draft.target.value,
                    onValueChange = { draft = draft.copy(target = draft.target.copy(value = it)) },
                    enabled = targetHasSize,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = stringResource(R.string.form_target_unit),
                    value = draft.target.units,
                    options = targetUnitOptions,
                    enabled = targetHasSize,
                    onValueChange = { draft = draft.copy(target = draft.target.copy(units = it)) },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.form_components))
            LabeledSwitchRow(
                text = stringResource(R.string.form_component_vocal),
                checked = draft.components.vocal,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(vocal = it)) },
            )
            LabeledSwitchRow(
                text = stringResource(R.string.form_component_somatic),
                checked = draft.components.somatic,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(somatic = it)) },
            )
            LabeledSwitchRow(
                text = stringResource(R.string.form_component_material),
                checked = draft.components.material,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(material = it)) },
            )
            LabeledSwitchRow(
                text = stringResource(R.string.filter_ritual),
                checked = draft.components.ritual,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(ritual = it)) },
            )
            LabeledSwitchRow(
                text = stringResource(R.string.filter_concentration),
                checked = draft.components.concentration,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(concentration = it)) },
            )
            if (draft.components.material) {
                OutlinedTextField(
                    value = draft.materials.value,
                    onValueChange = { draft = draft.copy(materials = draft.materials.copy(value = it)) },
                    label = { Text(stringResource(R.string.form_materials)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle(stringResource(R.string.form_mechanics))
            OptionField(
                label = stringResource(R.string.form_action_type),
                value = draft.actionType,
                options = actionTypeOptions,
                onValueChange = { draft = draft.copy(actionType = it) },
            )
            if (draft.actionType == ACTION_TYPE_SAVE) {
                OptionField(
                    label = stringResource(R.string.form_save),
                    value = draft.save.ability,
                    options = abilityOptions,
                    onValueChange = { draft = draft.copy(save = draft.save.copy(ability = it)) },
                )
            }

            SectionTitle(stringResource(R.string.form_damage))
            draft.damageParts.forEachIndexed { index, part ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = part.formula,
                        onValueChange = { formula ->
                            draft = draft.copy(
                                damageParts = draft.damageParts.toMutableList().also {
                                    it[index] = it[index].copy(formula = formula)
                                },
                            )
                        },
                        label = { Text(stringResource(R.string.form_formula)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OptionField(
                        label = stringResource(R.string.form_type),
                        value = part.type,
                        options = damageTypeOptions,
                        onValueChange = { type ->
                            draft = draft.copy(
                                damageParts = draft.damageParts.toMutableList().also {
                                    it[index] = it[index].copy(type = type)
                                },
                            )
                        },
                        modifier = Modifier.weight(1.2f),
                    )
                    IconButton(onClick = {
                        draft = draft.copy(
                            damageParts = draft.damageParts.filterIndexed { i, _ -> i != index },
                        )
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.form_delete_damage),
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { draft = draft.copy(damageParts = draft.damageParts + DamagePart()) },
            ) {
                Text(stringResource(R.string.form_add_damage))
            }

            SectionTitle(stringResource(R.string.form_classes))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                classOptions.forEach { (code, name) ->
                    val selected = code in draft.classes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            draft = draft.copy(
                                classes = if (selected) draft.classes - code else draft.classes + code,
                            )
                        },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (draft.name.isBlank()) {
                        nameError = true
                    } else if (characterName == null) {
                        onSave(draft, null)
                    } else {
                        // Запись общая для всех персонажей — уточняем, куда сохранить правку.
                        pendingSave = draft
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    pendingSave?.let { spell ->
        EditScopeDialog(
            characterName = characterName.orEmpty(),
            onDismiss = { pendingSave = null },
            onSelect = { scope ->
                pendingSave = null
                onSave(spell, scope)
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
