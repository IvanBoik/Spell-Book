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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.spellbook.ui.components.DetailsActionBar
import com.example.spellbook.ui.components.DetailsActionBarReservedHeight
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.util.DiceRoller
import com.example.spellbook.util.HtmlUtils
import kotlinx.coroutines.launch

/** Отступы содержимого экрана. */
private val CONTENT_PADDING = 16.dp

/** Сколько разделов должно быть, чтобы показывать меню навигации. */
private const val MIN_SECTIONS_FOR_NAVIGATION = 2



/**
 * Список записей с переключателем порядка — инфузии изобретателя, воззвания колдуна.
 *
 * Как и на dnd.su, порядок выбирается выпадающим списком: он занимает одну короткую
 * строку и не вылезает за край экрана, в отличие от ряда чипов. По умолчанию —
 * «по названию»: так список и идёт на сайте.
 */
@Composable
private fun SortableEntries(section: SortableSection, onDiceClick: (String) -> Unit) {
    var order by remember(section) { mutableStateOf(EntrySortOrder.NAME) }
    var menuOpen by remember { mutableStateOf(false) }
    val entries = remember(section, order) { section.sorted(order) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (section.intro.isNotBlank()) {
            DescriptionText(description = section.intro, onDiceClick = onDiceClick)
        }

        Box {
            TextButton(
                onClick = { menuOpen = true },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(SORT_ICON_SIZE),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(order.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.entries_sort_title),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                EntrySortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            order = option
                            menuOpen = false
                        },
                        trailingIcon = if (option == order) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        entries.forEach { entry ->
            // Ключ — название: без него при смене порядка Compose переиспользует старые
            // узлы по позиции, и запомненный внутри них разбор текста остаётся от другой записи.
            key(entry.title) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DescriptionText(description = entry.body, onDiceClick = onDiceClick)
                }
            }
        }
    }
}

/** Размер значка сортировки: кнопка должна оставаться незаметной. */
private val SORT_ICON_SIZE = 18.dp

/** Подпись варианта сортировки. */
private fun EntrySortOrder.labelRes(): Int = when (this) {
    EntrySortOrder.NAME -> R.string.entries_sort_name
    EntrySortOrder.LEVEL -> R.string.entries_sort_level
}

/**
 * Экран длинной статьи библиотеки: расы или класса.
 *
 * Страницы рас и классов на dnd.su устроены одинаково — большой текст, разбитый
 * заголовками, — поэтому экран общий и повторяет удобные элементы оригинала:
 * - меню-навигация по разделам: нажатие прокручивает список к нужному разделу;
 * - сворачивается только то, что сворачивается на сайте: подклассы, инфузии,
 *   воззвания, врезка с предысторией. Остальное — обычные абзацы с заголовками;
 * - текст рисуется общим [DescriptionText], поэтому кости кликабельны, а термины
 *   из ссылок сайта подсвечены.
 *
 * @param articleId идентификатор записи: при его смене сбрасываются свёрнутые разделы.
 * @param book книга-источник; пустая строка убирает строку с подписью.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailsScreen(
    articleId: String,
    title: String,
    book: String,
    description: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var rollResult by remember { mutableStateOf<DiceRoller.Result?>(null) }

    val sections = remember(description) { splitDescriptionSections(description) }
    val items = remember(sections) { buildArticleItems(sections) }
    val navigableSections = remember(sections) { sections.indices.filterNot { sections[it].isIntro } }

    // Раскрытые блоки хранятся номерами: заголовки повторяются («Unearthed Arcana» и т. п.).
    // По умолчанию всё свёрнуто, как на сайте: иначе перед глазами сразу десятки подклассов.
    var expandedSections by remember(articleId) { mutableStateOf(emptySet<Int>()) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /** Раскрывает блок и прокручивает к нему: переход в свёрнутый блок бессмыслен. */
    fun scrollTo(sectionIndex: Int, subheadingIndex: Int? = null) {
        expandedSections = expandedSections + sectionIndex
        val target = items.indexOfTarget(sectionIndex, subheadingIndex)
        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = title,
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
                items(items.size, key = { items[it].key }) { index ->
                    val onDiceClick: (String) -> Unit = { formula -> rollResult = DiceRoller.roll(formula) }
                    // Названия умений в таблице развития ведут к их описанию.
                    val onFeatureClick: (String) -> Unit = { feature ->
                        sections.findFeature(feature)?.let { scrollTo(it.sectionIndex, it.subheadingIndex) }
                    }
                    when (val item = items[index]) {
                        ArticleItem.Header -> ArticleHeader(
                            book = book,
                            titles = navigableSections.map { sections[it].title },
                            onSectionClick = { position -> scrollTo(navigableSections[position]) },
                        )

                        is ArticleItem.PartTitle -> ArticlePartTitle(sections[item.sectionIndex])

                        is ArticleItem.Chunk -> DescriptionText(
                            description = item.text,
                            onDiceClick = onDiceClick,
                            onFeatureClick = onFeatureClick,
                        )

                        is ArticleItem.Collapsible -> CollapsibleSectionCard(
                            section = sections[item.sectionIndex],
                            collapsed = item.sectionIndex !in expandedSections,
                            onToggle = {
                                expandedSections = expandedSections.toMutableSet().apply {
                                    if (!add(item.sectionIndex)) remove(item.sectionIndex)
                                }
                            },
                            onDiceClick = onDiceClick,
                            onFeatureClick = onFeatureClick,
                        )
                    }
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
                        .padding(
                            start = CONTENT_PADDING,
                            end = CONTENT_PADDING,
                            bottom = DetailsActionBarReservedHeight,
                        ),
                )
            }
        }
    }
}

/**
 * Элемент списка статьи.
 *
 * Несворачиваемые части (вступление, «Классовые умения») режутся по подзаголовкам:
 * каждое умение — отдельный элемент. Так длинный текст не собирается целиком,
 * а переход из таблицы прокручивает ровно к нужному умению.
 */
private sealed interface ArticleItem {
    val key: String

    data object Header : ArticleItem {
        override val key = "header"
    }

    data class PartTitle(val sectionIndex: Int) : ArticleItem {
        override val key = "title-$sectionIndex"
    }

    data class Chunk(val sectionIndex: Int, val chunkIndex: Int, val text: String) : ArticleItem {
        override val key = "chunk-$sectionIndex-$chunkIndex"
    }

    data class Collapsible(val sectionIndex: Int) : ArticleItem {
        override val key = "section-$sectionIndex"
    }
}

/** Раскладывает разделы статьи в плоский список для `LazyColumn`. */
private fun buildArticleItems(sections: List<DescriptionSection>): List<ArticleItem> = buildList {
    add(ArticleItem.Header)
    sections.forEachIndexed { sectionIndex, section ->
        if (section.isCollapsible) {
            add(ArticleItem.Collapsible(sectionIndex))
            return@forEachIndexed
        }
        if (!section.isIntro) add(ArticleItem.PartTitle(sectionIndex))
        section.chunks().forEachIndexed { chunkIndex, chunk ->
            if (chunk.isNotBlank()) add(ArticleItem.Chunk(sectionIndex, chunkIndex, chunk))
        }
    }
}

/**
 * Номер элемента списка, к которому нужно прокрутить.
 *
 * Внутри сворачиваемого блока прокрутка идёт к самой карточке; в несворачиваемой
 * части — к куску с нужным подзаголовком (кусок 0 — текст до первого из них).
 */
private fun List<ArticleItem>.indexOfTarget(sectionIndex: Int, subheadingIndex: Int?): Int {
    if (subheadingIndex != null) {
        val chunk = indexOfFirst {
            it is ArticleItem.Chunk && it.sectionIndex == sectionIndex && it.chunkIndex == subheadingIndex + 1
        }
        if (chunk >= 0) return chunk
    }
    return indexOfFirst {
        when (it) {
            is ArticleItem.Collapsible -> it.sectionIndex == sectionIndex
            is ArticleItem.PartTitle -> it.sectionIndex == sectionIndex
            is ArticleItem.Chunk -> it.sectionIndex == sectionIndex
            ArticleItem.Header -> false
        }
    }
}

/** Заголовок несворачиваемой части статьи — как крупный заголовок на dnd.su. */
@Composable
private fun ArticlePartTitle(section: DescriptionSection) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (section.label.isNotBlank()) {
            Text(
                section.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = PART_DIVIDER_ALPHA))
    }
}

/** Прозрачность линии под заголовком части: разделяет, но не спорит с текстом. */
private const val PART_DIVIDER_ALPHA = 0.4f

/** Шапка: книга-источник и меню быстрого перехода по разделам. */
@Composable
private fun ArticleHeader(
    book: String,
    titles: List<String>,
    onSectionClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (book.isNotBlank()) {
            Text(
                book,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // При одном разделе меню не нужно: прокручивать всё равно некуда.
        if (titles.size >= MIN_SECTIONS_FOR_NAVIGATION) {
            Text(
                stringResource(R.string.race_sections_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(titles.size) { index ->
                    AssistChip(
                        onClick = { onSectionClick(index) },
                        label = { Text(titles[index]) },
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

/** Сворачиваемый блок: подкласс, инфузии, врезка с предысторией. */
@Composable
private fun CollapsibleSectionCard(
    section: DescriptionSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDiceClick: (String) -> Unit,
    onFeatureClick: ((String) -> Unit)? = null,
) {
    // Подпись и карточка живут в одном элементе списка, поэтому нужен Column:
    // два соседних узла в слоте LazyColumn наложились бы друг на друга.
    Column(Modifier.fillMaxWidth()) {
        // Подпись-разделитель: например, «Подрасы из Unearthed Arcana» отделяет
        // неофициальные материалы от остальных.
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
                        // Разделы вроде инфузий и воззваний на сайте сортируются — повторяем это.
                        val sortable = remember(section.body) { parseSortableSection(section.body) }
                        if (sortable != null) {
                            SortableEntries(section = sortable, onDiceClick = onDiceClick)
                        } else {
                            DescriptionText(
                                description = section.body,
                                onDiceClick = onDiceClick,
                                onFeatureClick = onFeatureClick,
                            )
                        }
                    }
                }
            }
        }
    }
}
