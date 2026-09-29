package com.example.spellbook.ui.screens

import androidx.compose.runtime.Composable
import com.example.spellbook.data.model.CharClass

/**
 * Экран класса из библиотеки.
 *
 * Как и раса, класс — длинная статья с разделами, поэтому вся разметка живёт
 * в общем [ArticleDetailsScreen], а здесь только подстановка полей класса.
 */
@Composable
fun ClassDetailsScreen(
    charClass: CharClass,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    ArticleDetailsScreen(
        articleId = charClass.id,
        title = charClass.name,
        book = charClass.book,
        description = charClass.description,
        onBack = onBack,
        onEdit = onEdit,
        onExport = onExport,
        onShare = onShare,
        onDelete = onDelete,
    )
}
