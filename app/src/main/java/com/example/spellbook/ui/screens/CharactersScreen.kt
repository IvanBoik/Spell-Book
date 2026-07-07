package com.example.spellbook.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.DndTopBar

/** Стартовый экран вкладки «Персонажи»: список карточек персонажей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    characters: List<Character>,
    spellCounts: Map<String, Int>,
    onCharacterClick: (String) -> Unit,
    onAddCharacter: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = { DndTopBar(title = "Персонажи") },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCharacter,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить персонажа")
            }
        },
    ) { padding ->
        if (characters.isEmpty()) {
            EmptyCharacters(onAddCharacter = onAddCharacter, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    CharacterCard(
                        character = character,
                        spellCount = spellCounts[character.id] ?: 0,
                        onClick = { onCharacterClick(character.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(character: Character, spellCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(character = character, size = 56)
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = character.name.ifBlank { "Без имени" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "$spellCount ${spellsWord(spellCount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Аватар персонажа: фото из галереи (если задано) либо запасной кружок с инициалами.
 */
@Composable
fun CharacterAvatar(character: Character, size: Int) {
    val shape = CircleShape
    val uri = character.imageUri
    if (!uri.isNullOrBlank()) {
        AsyncImage(
            model = uri,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initials = character.name.trim().take(1).uppercase()
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyCharacters(onAddCharacter: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Пока нет персонажей",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text("Создайте персонажа, чтобы собрать его личный список заклинаний.")
        }
    }
}

private fun spellsWord(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "заклинание"
        mod10 in 2..4 && mod100 !in 12..14 -> "заклинания"
        else -> "заклинаний"
    }
}
