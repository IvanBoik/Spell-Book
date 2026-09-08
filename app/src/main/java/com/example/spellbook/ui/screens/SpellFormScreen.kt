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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.DamagePart
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.LabeledSwitchRow
import com.example.spellbook.ui.components.NumberField
import com.example.spellbook.ui.components.OptionField

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

/** Заготовка таблицы с шапкой и двумя строками — пользователю остаётся заменить текст. */
private val TABLE_TEMPLATE = listOf(
    "| Заголовок 1 | Заголовок 2 |",
    "| --- | --- |",
    "| Ячейка | Ячейка |",
    "| Ячейка | Ячейка |",
).joinToString("\n")

/** Добавляет шаблон таблицы в конец описания, отделяя его от текста. */
private fun appendTableTemplate(description: String): String =
    if (description.isBlank()) TABLE_TEMPLATE else "${description.trimEnd()}\n$TABLE_TEMPLATE"

/**
 * Форма создания/редактирования заклинания. Все поля соответствуют формату LSS,
 * чтобы созданное заклинание можно было без потерь выгрузить и загрузить обратно.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpellFormScreen(
    initial: Spell,
    isNew: Boolean,
    onSave: (Spell) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var nameError by remember(initial.id) { mutableStateOf(false) }

    val levelOptions = remember { SpellOptions.levels.map { it.first.toString() to it.second } }

    Scaffold(
        topBar = {
            DndTopBar(
                title = if (isNew) "Новое заклинание" else "Редактирование",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
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
                label = { Text("Название") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Укажите название заклинания") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionField(
                    label = "Круг",
                    value = draft.level.toString(),
                    options = levelOptions,
                    onValueChange = { draft = draft.copy(level = it.toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = "Школа",
                    value = draft.school,
                    options = SpellOptions.schools,
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
                label = { Text("Описание") },
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
                    "**жирный**, *курсив*, ## заголовок, списки через - или 1.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    draft = draft.copy(description = appendTableTemplate(draft.description))
                }) {
                    Text("Вставить таблицу")
                }
            }

            SectionTitle("Время сотворения")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Значение",
                    value = draft.activationCost,
                    onValueChange = { draft = draft.copy(activationCost = it) },
                    enabled = draft.activationType !in ACTIVATION_TYPES_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = "Единицы",
                    value = draft.activationType,
                    options = SpellOptions.activationTypes,
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
                label = { Text("Условие сотворения") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Длительность")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Значение",
                    value = draft.durationValue,
                    onValueChange = { draft = draft.copy(durationValue = it) },
                    enabled = draft.durationUnits !in DURATION_UNITS_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = "Единицы",
                    value = draft.durationUnits,
                    options = SpellOptions.durationUnits,
                    onValueChange = { unit ->
                        draft = draft.copy(
                            durationUnits = unit,
                            durationValue = if (unit in DURATION_UNITS_WITHOUT_VALUE) null else draft.durationValue,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Дистанция")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Значение",
                    value = draft.rangeValue,
                    onValueChange = { draft = draft.copy(rangeValue = it) },
                    enabled = draft.rangeUnits !in RANGE_UNITS_WITHOUT_VALUE,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = "Единицы",
                    value = draft.rangeUnits,
                    options = SpellOptions.rangeUnits,
                    onValueChange = { unit ->
                        draft = draft.copy(
                            rangeUnits = unit,
                            rangeValue = if (unit in RANGE_UNITS_WITHOUT_VALUE) null else draft.rangeValue,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Цель / область")
            val targetHasSize = draft.target.type in TARGET_TYPES_WITH_SIZE
            OptionField(
                label = "Цель/область",
                value = draft.target.type,
                options = SpellOptions.targetTypes,
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
                    label = "Размер",
                    value = draft.target.value,
                    onValueChange = { draft = draft.copy(target = draft.target.copy(value = it)) },
                    enabled = targetHasSize,
                    modifier = Modifier.weight(1f),
                )
                OptionField(
                    label = "Единица",
                    value = draft.target.units,
                    options = SpellOptions.targetUnits,
                    enabled = targetHasSize,
                    onValueChange = { draft = draft.copy(target = draft.target.copy(units = it)) },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Компоненты")
            LabeledSwitchRow(
                text = "Вербальный (V)",
                checked = draft.components.vocal,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(vocal = it)) },
            )
            LabeledSwitchRow(
                text = "Соматический (S)",
                checked = draft.components.somatic,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(somatic = it)) },
            )
            LabeledSwitchRow(
                text = "Материальный (M)",
                checked = draft.components.material,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(material = it)) },
            )
            LabeledSwitchRow(
                text = "Ритуал",
                checked = draft.components.ritual,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(ritual = it)) },
            )
            LabeledSwitchRow(
                text = "Концентрация",
                checked = draft.components.concentration,
                onCheckedChange = { draft = draft.copy(components = draft.components.copy(concentration = it)) },
            )
            if (draft.components.material) {
                OutlinedTextField(
                    value = draft.materials.value,
                    onValueChange = { draft = draft.copy(materials = draft.materials.copy(value = it)) },
                    label = { Text("Материалы") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle("Механика")
            OptionField(
                label = "Тип действия",
                value = draft.actionType,
                options = SpellOptions.actionTypes,
                onValueChange = { draft = draft.copy(actionType = it) },
            )
            if (draft.actionType == ACTION_TYPE_SAVE) {
                OptionField(
                    label = "Спасбросок",
                    value = draft.save.ability,
                    options = SpellOptions.abilities,
                    onValueChange = { draft = draft.copy(save = draft.save.copy(ability = it)) },
                )
            }

            SectionTitle("Урон / лечение")
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
                        label = { Text("Формула") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OptionField(
                        label = "Тип",
                        value = part.type,
                        options = SpellOptions.damageTypes,
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
                        Icon(Icons.Default.Delete, contentDescription = "Удалить урон")
                    }
                }
            }
            OutlinedButton(
                onClick = { draft = draft.copy(damageParts = draft.damageParts + DamagePart()) },
            ) {
                Text("Добавить тип урона")
            }

            SectionTitle("Классы")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpellOptions.classes.forEach { (code, name) ->
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
                    } else {
                        onSave(draft)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
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
