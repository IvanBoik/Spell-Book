package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.Race
import com.example.spellbook.ui.components.DetailsActionBar
import com.example.spellbook.ui.components.DetailsActionBarReservedHeight
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.util.DiceRoller
import kotlinx.coroutines.launch

/** Отступы содержимого экрана. */
private val CONTENT_PADDING = 16.dp

/** Сколько разделов должно быть, чтобы показывать меню навигации. */
private const val MIN_SECTIONS_FOR_NAVIGATION = 2

/**
 * Экран расы из библиотеки.
 *
 * Описание расы на dnd.su — длинная статья, поэтому страница повторяет удобные
 * элементы оригинала:
 * - меню-навигация по разделам: нажатие прокручивает список к нужному разделу;
 * - каждый раздел сворачивается нажатием на его заголовок;
 * - текст рисуется общим [DescriptionText], поэтому кости кликабельны, а термины
 *   из ссылок сайта подсвечены.
 *
 * Вступление до первого заголовка всегда раскрыто: сворачивать там нечего.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceDetailsScreen(
    race: Race,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var rollResult by remember { mutableStateOf<DiceRoller.Result?>(null) }
    var collapsedSections by remember(race.id) { mutableStateOf(emptySet<String>()) }

    val sections = remember(race.description) { splitRaceSections(race.description) }
    val navigableSections = remember(sections) { sections.filterNot { it.isIntro } }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Первый элемент списка — шапка с книгой и меню, поэтому разделы смещены на единицу.
    val headerOffset = 1

    Scaffold(
        topBar = {
            DndTopBar(
                title = race.name,
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
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CONTENT_PADDING),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    RaceHeader(
                        race = race,
                        sections = navigableSections,
                        onSectionClick = { index ->
                            // Раздел мог быть свёрнут — раскрываем, иначе переход бессмысленный.
                            val title = navigableSections[index].title
                            collapsedSections = collapsedSections - title
                            val target = sections.indexOfFirst { it.title == title }
                            scope.launch { listState.animateScrollToItem(target + headerOffset) }
                        },
                    )
                }

                items(sections.size, key = { sections[it].title.ifEmpty { INTRO_KEY } }) { index ->
                    val section = sections[index]
                    RaceSectionBlock(
                        section = section,
                        collapsed = section.title in collapsedSections,
                        onToggle = {
                            collapsedSections = collapsedSections.toMutableSet().apply {
                                if (!add(section.title)) remove(section.title)
                            }
                        },
                        onDiceClick = { formula -> rollResult = DiceRoller.roll(formula) },
                    )
                }

                // Место под плавающий блок действий: внизу он оказывается ниже текста.
                item { Spacer(Modifier.height(DetailsActionBarReservedHeight)) }
            }

            DetailsActionBar(
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare,
                onExport = onExport,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            rollResult?.let { result ->
                DiceResultCard(
                    result = result,
                    onClose = { rollResult = null },
                    // Поднимаем плашку над блоком действий, чтобы они не перекрывались.
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = CONTENT_PADDING, end = CONTENT_PADDING, bottom = DetailsActionBarReservedHeight),
                )
            }
        }
    }
}

/** Шапка: книга-источник и меню быстрого перехода по разделам. */
@Composable
private fun RaceHeader(
    race: Race,
    sections: List<RaceSection>,
    onSectionClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (race.book.isNotBlank()) {
            Text(
                race.book,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // При одном разделе меню не нужно: прокручивать всё равно некуда.
        if (sections.size >= MIN_SECTIONS_FOR_NAVIGATION) {
            Text(
                stringResource(R.string.race_sections_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections.size) { index ->
                    AssistChip(
                        onClick = { onSectionClick(index) },
                        label = { Text(sections[index].title) },
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

/** Раздел описания: заголовок со сворачиванием и текст. */
@Composable
private fun RaceSectionBlock(
    section: RaceSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDiceClick: (String) -> Unit,
) {
    // Вступление идёт без карточки и заголовка — это просто первые абзацы статьи.
    if (section.isIntro) {
        DescriptionText(description = section.body, onDiceClick = onDiceClick)
        return
    }

    // Подпись и карточка живут в одном элементе списка, поэтому нужен Column:
    // два соседних узла в слоте LazyColumn наложились бы друг на друга.
    Column(Modifier.fillMaxWidth()) {
        // Подпись-разделитель: например, «Подрасы из Unearthed Arcana» отделяет
        // неофициальные подрасы от остальных.
        if (section.label.isNotBlank()) {
            Text(
                section.label,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(
                            if (collapsed) R.string.action_expand else R.string.action_collapse,
                        ),
                    )
                }

                AnimatedVisibility(visible = !collapsed) {
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        DescriptionText(description = section.body, onDiceClick = onDiceClick)
                    }
                }
            }
        }
    }
}

/** Ключ элемента списка для вступления: у него нет заголовка. */
private const val INTRO_KEY = "__intro__"
