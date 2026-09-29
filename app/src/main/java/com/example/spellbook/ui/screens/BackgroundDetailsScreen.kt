package com.example.spellbook.ui.screens

import androidx.compose.runtime.Composable
import com.example.spellbook.data.model.Background

/**
 * Экран предыстории из библиотеки.
 *
 * Как раса и класс, предыстория — статья с разделами и таблицами, поэтому вся
 * разметка живёт в общем [ArticleDetailsScreen]. Отличий два, и оба из-за размера:
 * предыстория короткая — всего пара экранов текста, в отличие от класса с десятками
 * подклассов:
 * - меню быстрого перехода по разделам не нужно: прокрутить проще, чем искать чип,
 *   а ряд чипов оттесняет начало описания вниз;
 * - термины-ссылки выделяются тише: во владениях навыки и инструменты идут сплошным
 *   перечнем рядом с жирными подписями, и жирного на экране становится слишком много.
 */
@Composable
fun BackgroundDetailsScreen(
    background: Background,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    ArticleDetailsScreen(
        articleId = background.id,
        title = background.name,
        book = background.book,
        description = background.description,
        onBack = onBack,
        onEdit = onEdit,
        onExport = onExport,
        onShare = onShare,
        onDelete = onDelete,
        showSectionMenu = false,
        refEmphasis = RefEmphasis.SUBTLE,
    )
}
