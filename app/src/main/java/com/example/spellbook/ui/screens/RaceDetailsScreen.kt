package com.example.spellbook.ui.screens

import androidx.compose.runtime.Composable
import com.example.spellbook.data.model.Race

/**
 * Экран расы из библиотеки.
 *
 * Страницы рас и классов на dnd.su устроены одинаково, поэтому вся разметка живёт
 * в общем [ArticleDetailsScreen], а здесь только подстановка полей расы.
 */
@Composable
fun RaceDetailsScreen(
    race: Race,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    ArticleDetailsScreen(
        articleId = race.id,
        title = race.name,
        book = race.book,
        description = race.description,
        onBack = onBack,
        onEdit = onEdit,
        onExport = onExport,
        onShare = onShare,
        onDelete = onDelete,
    )
}
