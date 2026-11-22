package com.example.spellbook.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.model.MagicItem

/**
 * Экран магического предмета из библиотеки.
 *
 * Описание предмета размечено так же, как статьи о расах и классах, поэтому вся
 * раскладка живёт в общем [ArticleDetailsScreen]. Отличия те же, что у предыстории,
 * и по той же причине — текст короткий:
 * - меню быстрого перехода по разделам не нужно: прокрутить проще, чем искать чип;
 * - термины-ссылки выделяются тише, чтобы жирного на экране было меньше.
 *
 * Сверху добавляется сводка из карточки сайта: тип с редкостью и стоимость.
 */
@Composable
fun MagicItemDetailsScreen(
    item: MagicItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    ArticleDetailsScreen(
        articleId = item.id,
        title = item.name,
        book = item.book,
        description = item.description,
        onBack = onBack,
        onEdit = onEdit,
        onExport = onExport,
        onShare = onShare,
        onDelete = onDelete,
        showSectionMenu = false,
        refEmphasis = RefEmphasis.SUBTLE,
        summary = magicItemSummary(item),
    )
}

/**
 * Описание предмета без панели действий — для окна-превью поверх другого экрана
 * ([MagicItemPreviewDialog]). Раскладка та же, что у экрана предмета.
 */
@Composable
fun MagicItemContent(item: MagicItem, onDiceClick: (String) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalRefEmphasis provides RefEmphasis.SUBTLE) {
        ArticleContent(
            articleId = item.id,
            book = item.book,
            description = item.description,
            showSectionMenu = false,
            summary = magicItemSummary(item),
            onDiceClick = onDiceClick,
            modifier = modifier,
        )
    }
}

/** Сводка над описанием: строка сайта целиком (тип, редкость, настройка) и стоимость. */
@Composable
private fun magicItemSummary(item: MagicItem): List<String> {
    // Берём строку сайта целиком: в ней есть уточнения, которых нет в разобранных
    // полях, например «требуется настройка волшебником».
    val category = item.category.trim()
    val price = item.price.trim()
    return listOfNotNull(
        category.takeIf { it.isNotEmpty() },
        price.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.magic_item_price, it) },
    )
}
