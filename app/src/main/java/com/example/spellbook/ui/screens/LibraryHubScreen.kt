package com.example.spellbook.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.ui.components.DndTopBar

private val CONTENT_PADDING = 16.dp
private val SECTION_ICON_SIZE = 40.dp

/**
 * Корневой экран библиотеки: выбор между заклинаниями и чертами.
 *
 * Разделы разведены по отдельным экранам, потому что списки живут по разным правилам:
 * у заклинаний — фильтры и сортировка, у черт — группировка по книгам-источникам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHubScreen(
    spellCount: Int,
    featCount: Int,
    onOpenSpells: () -> Unit,
    onOpenFeats: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = { DndTopBar(title = stringResource(R.string.tab_library)) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(CONTENT_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LibrarySectionCard(
                    titleRes = R.string.library_section_spells,
                    subtitle = pluralStringResource(R.plurals.spell_count, spellCount, spellCount),
                    icon = Icons.Default.Description,
                    onClick = onOpenSpells,
                )
            }
            item {
                LibrarySectionCard(
                    titleRes = R.string.library_section_feats,
                    subtitle = pluralStringResource(R.plurals.feat_count, featCount, featCount),
                    icon = Icons.Default.Star,
                    onClick = onOpenFeats,
                )
            }
        }
    }
}

/** Карточка раздела библиотеки с иконкой, названием и счётчиком записей. */
@Composable
private fun LibrarySectionCard(
    @StringRes titleRes: Int,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CONTENT_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(SECTION_ICON_SIZE),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = CONTENT_PADDING)) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
