package com.example.spellbook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.DndTopBar

/** Форма создания/редактирования персонажа: имя и фото из галереи. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterFormScreen(
    initial: Character,
    isNew: Boolean,
    onSave: (Character) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var imageUri by remember { mutableStateOf(initial.imageUri) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Сохраняем постоянный доступ к выбранному изображению.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            imageUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            DndTopBar(
                title = if (isNew) "Новый персонаж" else "Редактирование",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave(initial.copy(name = name.trim(), imageUri = imageUri)) },
                        enabled = name.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.clickable {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                CharacterAvatar(
                    character = Character(name = name, imageUri = imageUri),
                    size = 120,
                )
            }
            OutlinedButton(onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }) {
                Text(if (imageUri.isNullOrBlank()) "Выбрать фото" else "Изменить фото")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя персонажа") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(initial.copy(name = name.trim(), imageUri = imageUri)) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            if (onDelete != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Удалить персонажа")
                }
            }
        }
    }
}
