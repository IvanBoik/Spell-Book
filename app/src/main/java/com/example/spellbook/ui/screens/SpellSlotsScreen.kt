package com.example.spellbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.DndTopBar

/**
 * Экран ячеек заклинаний персонажа: по каждому уровню показаны кружки-ячейки.
 * Тап по доступной ячейке помечает её потраченной, по потраченной — восстанавливает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellSlotsScreen(
    character: Character,
    onUseSlot: (level: Int) -> Unit,
    onRestoreSlot: (level: Int) -> Unit,
    onRestoreAll: () -> Unit,
    onBack: () -> Unit,
) {
    val levels = (1..9).filter { (character.spellSlots[it] ?: 0) > 0 }

    Scaffold(
        topBar = {
            DndTopBar(
                title = "Ячейки заклинаний",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onRestoreAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "Восстановить все")
                    }
                },
            )
        },
    ) { padding ->
        if (levels.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ячейки не настроены. Задайте их в настройках персонажа.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(levels, key = { it }) { level ->
                    SlotLevelRow(
                        level = level,
                        total = character.spellSlots[level] ?: 0,
                        used = character.spellSlotsUsed[level] ?: 0,
                        onUse = { onUseSlot(level) },
                        onRestore = { onRestoreSlot(level) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotLevelRow(
    level: Int,
    total: Int,
    used: Int,
    onUse: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$level уровень",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${total - used} / $total", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Первые (total - used) — доступные, остальные — потраченные.
                repeat(total) { index ->
                    val isUsed = index >= total - used
                    SlotDot(isUsed = isUsed, onClick = { if (isUsed) onRestore() else onUse() })
                }
            }
        }
    }
}

@Composable
private fun SlotDot(isUsed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            .background(
                if (isUsed) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            .clickable(onClick = onClick),
    )
}
