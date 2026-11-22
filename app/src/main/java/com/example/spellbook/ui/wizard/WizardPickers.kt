package com.example.spellbook.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spellbook.R
import com.example.spellbook.data.SpellOptions
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.wizard.SpellFilter
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.screens.DescriptionText
import com.example.spellbook.ui.screens.SpellPreviewDialog
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import com.example.spellbook.ui.screens.groupByBook
import androidx.compose.ui.res.pluralStringResource
import com.example.spellbook.ui.spellLevelLabel
import com.example.spellbook.ui.spellOptionLabel

/** Заклинания библиотеки, подходящие под фильтр выбора. */
fun filterSpells(spells: List<Spell>, filter: SpellFilter, extraNames: List<String> = emptyList()): List<Spell> =
    spells.filter { spell ->
        // Выбор из названных в тексте заклинаний («искусство друидов или чудотворство»).
        if (filter.names.isNotEmpty()) {
            return@filter filter.names.any { com.example.spellbook.data.wizard.MarkupText.matchesSpellName(it, spell.name) }
        }
        val byClass = filter.classCodes.isEmpty() || spell.classes.any { it in filter.classCodes } ||
            extraNames.any { com.example.spellbook.data.wizard.MarkupText.matchesSpellName(it, spell.name) }
        val byLevel = spell.level in filter.minLevel..filter.maxLevel
        val bySchool = filter.schools.isEmpty() || spell.school in filter.schools
        val byAttack = !filter.attackOnly || isAttackSpell(spell)
        val byRitual = !filter.ritualOnly || spell.components.ritual
        byClass && byLevel && bySchool && byAttack && byRitual
    }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))

/** Упоминание атаки заклинанием в описании: «Совершите дальнобойную атаку заклинанием». */
private val SPELL_ATTACK = Regex("атак\\S*\\s+заклинанием", RegexOption.IGNORE_CASE)

/**
 * Требует ли заклинание броска атаки. Во встроенной библиотеке у заговоров тип действия
 * часто не заполнен (`util`), поэтому также смотрим текст описания.
 */
fun isAttackSpell(spell: Spell): Boolean =
    spell.actionType == "msak" || spell.actionType == "rsak" || SPELL_ATTACK.containsMatchIn(spell.description)

/**
 * Список заклинаний с поиском и фильтром по кругу для выбора прямо на шаге.
 *
 * Выбор — флажком; нажатие на строку открывает заклинание на весь экран в том же виде, что и
 * в библиотеке: время накладывания, дистанция, компоненты, длительность, концентрация, ритуал.
 *
 * @param locked заклинания, отмеченные без возможности снять (например, всегда подготовленные).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpellSelectionList(
    candidates: List<Spell>,
    selected: Set<String>,
    limit: Int,
    onToggle: (String) -> Unit,
    locked: Set<String> = emptySet(),
    header: @Composable () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    candidates.firstOrNull { it.id == openedId }?.let { spell ->
        SpellPreviewDialog(spell = spell, onDismiss = { openedId = null })
    }
    val levels = remember(candidates) { candidates.map { it.level }.distinct().sorted() }
    val visible = remember(candidates, query, level) {
        candidates.filter { spell ->
            (level == null || spell.level == level) &&
                (query.isBlank() || spell.name.contains(query.trim(), ignoreCase = true))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { header() }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) } }
                } else null,
                placeholder = { Text(stringResource(R.string.wizard_search_spells)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (levels.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = level == null, onClick = { level = null }, label = { Text(stringResource(R.string.wizard_all)) })
                    }
                    items(levels) { value ->
                        FilterChip(selected = level == value, onClick = { level = if (level == value) null else value }, label = {
                            Text(spellLevelLabel(value))
                        })
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            item { HintRow(stringResource(R.string.wizard_no_spells)) }
        }
        items(visible, key = { it.id }) { spell ->
            val isLocked = spell.id in locked
            val isSelected = spell.id in selected || isLocked
            val full = selected.size >= limit && !isSelected
            SpellRow(
                spell = spell,
                selected = isSelected,
                enabled = !isLocked && !full,
                onOpen = { openedId = spell.id },
                onToggle = { onToggle(spell.id) },
                locked = isLocked,
            )
        }
    }
}

@Composable
private fun SpellRow(
    spell: Spell,
    selected: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    locked: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled || (selected && !locked))
                com.example.spellbook.ui.components.SpellSummaryText(
                    spell = spell,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    summaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (locked) InfoPill(stringResource(R.string.wizard_always_prepared), highlighted = true)
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.wizard_spell_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Поле выбора заклинаний для небольших выборов (заговор расы, арканум, книга теней):
 * выбранные заклинания — чипами, полный список открывается отдельным окном.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpellChoiceField(
    title: String,
    hint: String,
    count: Int,
    selected: List<String>,
    candidates: List<Spell>,
    onChange: (List<String>) -> Unit,
    /** Полный текст умения, дающего выбор: свёрнут под подсказкой. */
    sourceText: String = "",
) {
    var open by remember { mutableStateOf(false) }
    val byId = remember(candidates) { candidates.associateBy { it.id } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title) { CountBadge(selected.size, count) }
        if (hint.isNotBlank()) HintRow(hint)
        if (sourceText.isNotBlank()) ExpandableDescription(stringResource(R.string.wizard_rules_text), sourceText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            selected.forEach { id ->
                InputChip(
                    selected = true,
                    onClick = { onChange(selected - id) },
                    label = { Text(byId[id]?.name ?: id) },
                    trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.action_remove), Modifier.size(16.dp)) },
                )
            }
        }
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.wizard_choose_spells), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (open) {
        FullScreenPicker(title = title, onClose = { open = false }) {
            SpellSelectionList(
                candidates = candidates,
                selected = selected.toSet(),
                limit = count,
                onToggle = { id -> onChange(toggle(selected, id, count)) },
                header = { Row { CountBadge(selected.size, count) } },
            )
        }
    }
}

/** Полноэкранное окно выбора с кнопкой «Готово». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPicker(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                DndTopBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Check, stringResource(R.string.action_done)) }
                    },
                )
            },
        ) { padding -> Box(Modifier.padding(padding)) { content() } }
    }
}

/**
 * Выбор черты в том же виде, что и в библиотеке: книги в постоянном порядке
 * (крупные сверху, `groupByBook`), каждая сворачивается; название черты — во всю ширину.
 */
@Composable
fun FeatPickerDialog(
    feats: List<Feat>,
    selectedId: String?,
    takenIds: Set<String>,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val collapsed = rememberCollapsedGroups()
    val otherTitle = stringResource(R.string.library_feats_other_book)
    val groups = remember(feats, query, otherTitle) {
        groupByBook(feats.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, feats, otherTitle)
    }
    FullScreenPicker(title = stringResource(R.string.wizard_choose_feat), onClose = onClose) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.wizard_search_feats)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            groups.forEach { group ->
                // При поиске свёрнутые блоки раскрываются, чтобы не спрятать совпадения.
                val isCollapsed = group.title in collapsed.value && query.isBlank()
                item(key = "h:${group.title}") {
                    GroupHeader(
                        title = group.title,
                        count = pluralStringResource(R.plurals.feat_count, group.feats.size, group.feats.size),
                        collapsed = isCollapsed,
                        onToggle = { collapsed.value = collapsed.value.toggle(group.title) },
                    )
                }
                if (isCollapsed) return@forEach
            items(group.feats, key = { it.id }) { feat ->
                val taken = feat.id in takenIds && feat.id != selectedId
                SelectableCard(
                    selected = feat.id == selectedId,
                    enabled = !taken,
                    onClick = { onSelect(feat.id); onClose() },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            feat.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (expandedId == feat.id) {
                            DescriptionText(description = feat.description, onDiceClick = {})
                        } else {
                            Text(
                                com.example.spellbook.data.wizard.MarkupText.plain(
                                    feat.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(if (expandedId == feat.id) R.string.wizard_less else R.string.wizard_more),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { expandedId = if (expandedId == feat.id) null else feat.id },
                        )
                    }
                }
            }
            }
        }
    }
}
