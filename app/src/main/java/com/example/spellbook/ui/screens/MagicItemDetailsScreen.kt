package com.example.spellbook.ui.screens

import androidx.compose.runtime.Composable
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
    // Берём строку сайта целиком: в ней есть уточнения, которых нет в разобранных
    // полях, например «требуется настройка волшебником».
    val category = item.category.trim()
    val price = item.price.trim()
    val summary = listOfNotNull(
        category.takeIf { it.isNotEmpty() },
        price.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.magic_item_price, it) },
    )

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
        summary = summary,
    )
}
