package com.example.spellbook.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.wizard.ContentProposal
import com.example.spellbook.data.wizard.ProposedBlock
import com.example.spellbook.data.wizard.ProposedResource

/**
 * Переключатели на итоговом шаге мастеров: какие заметки (блоки целиком и отдельные умения)
 * и ресурсы добавить персонажу. По умолчанию добавляется всё; исключённое ранее показано
 * снятым и помечено — его можно включить обратно. Блоки выбранных вариантов закреплены.
 *
 * @param excluded действующие исключения (ключи `ContentExclusions`).
 * @param onToggle переключить ключ блока, абзаца или ресурса.
 */
@Composable
fun ContentExclusionSection(
    proposal: ContentProposal,
    excluded: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (proposal.isEmpty) return
    SectionTitle(stringResource(R.string.content_exclusion_title))
    HintRow(stringResource(R.string.content_exclusion_hint))
    if (proposal.blocks.isNotEmpty()) {
        Text(
            stringResource(R.string.content_exclusion_notes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        proposal.blocks.forEach { block -> ProposedBlockCard(block, excluded, onToggle) }
    }
    if (proposal.resources.isNotEmpty()) {
        Text(
            stringResource(R.string.content_exclusion_resources),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                proposal.resources.forEach { resource -> ProposedResourceRow(resource, excluded, onToggle) }
            }
        }
    }
}

@Composable
private fun ProposedBlockCard(block: ProposedBlock, excluded: Set<String>, onToggle: (String) -> Unit) {
    var expanded by rememberSaveable(block.key) { mutableStateOf(false) }
    val blockIncluded = block.locked || block.key !in excluded
    val includedCount = if (!blockIncluded) 0 else block.paragraphs.count { block.locked || it.key !in excluded }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (block.locked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.content_exclusion_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp).size(20.dp),
                    )
                } else {
                    Checkbox(checked = blockIncluded, onCheckedChange = { onToggle(block.key) })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val notes = buildList {
                        add(stringResource(R.string.content_exclusion_count, includedCount, block.paragraphs.size))
                        if (block.existing) add(stringResource(R.string.content_exclusion_existing))
                        if (block.excludedEarlier) add(stringResource(R.string.content_exclusion_earlier))
                        if (block.locked) add(stringResource(R.string.content_exclusion_locked))
                    }
                    Text(
                        notes.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
                    block.paragraphs.forEach { paragraph ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = blockIncluded && (block.locked || paragraph.key !in excluded),
                                enabled = blockIncluded && !block.locked,
                                onCheckedChange = { onToggle(paragraph.key) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(paragraph.heading, style = MaterialTheme.typography.bodyMedium)
                                if (paragraph.excludedEarlier) {
                                    Text(
                                        stringResource(R.string.content_exclusion_earlier),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposedResourceRow(resource: ProposedResource, excluded: Set<String>, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(resource.key) }
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = resource.key !in excluded, onCheckedChange = { onToggle(resource.key) })
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.content_exclusion_resource_value, resource.name, resource.maximum),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (resource.excludedEarlier) {
                Text(
                    stringResource(R.string.content_exclusion_earlier),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
