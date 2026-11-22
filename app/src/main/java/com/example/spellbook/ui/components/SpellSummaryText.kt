package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.spellSummaryLine

/**
 * Компактное представление заклинания в списках — две строки:
 * название и «3 круг · В, С, М · концентрация · ритуал».
 *
 * Школа и описание в списках не показываются: они есть на экране заклинания.
 * Обе строки однострочные с многоточием, чтобы высота элемента была постоянной.
 */
@Composable
fun SpellSummaryText(
    spell: Spell,
    modifier: Modifier = Modifier,
    summaryColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier) {
        Text(
            text = spell.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = spellSummaryLine(spell),
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
