package com.example.spellbook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spellbook.data.CharacterLssCodec
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.LibraryDownloadProgress
import com.example.spellbook.ui.LibraryDownloadReport
import com.example.spellbook.ui.ProvideAppLocale
import com.example.spellbook.ui.Screen
import com.example.spellbook.ui.SpellBookViewModel
import com.example.spellbook.ui.components.CharacterSection
import com.example.spellbook.ui.components.CharacterSectionsBar
import com.example.spellbook.ui.components.SpellBookBottomBar
import com.example.spellbook.ui.screens.AddSpellFab
import com.example.spellbook.ui.screens.AddSpellsScreen
import com.example.spellbook.ui.screens.CharacterFormScreen
import com.example.spellbook.ui.screens.CharactersScreen
import com.example.spellbook.ui.screens.ComboEditorScreen
import com.example.spellbook.ui.screens.ComboListScreen
import com.example.spellbook.ui.screens.ComboResultScreen
import com.example.spellbook.ui.screens.EmptySpellList
import com.example.spellbook.ui.screens.FabAction
import com.example.spellbook.ui.screens.FeatDetailsScreen
import com.example.spellbook.ui.screens.FeatsScreen
import com.example.spellbook.ui.screens.InventoryScreen
import com.example.spellbook.ui.screens.LibraryFeatsScreen
import com.example.spellbook.ui.screens.LibraryHubScreen
import com.example.spellbook.ui.screens.NotesScreen
import com.example.spellbook.ui.screens.PrepareSpellsScreen
import com.example.spellbook.ui.screens.SettingsScreen
import com.example.spellbook.ui.screens.SpellDetailsScreen
import com.example.spellbook.ui.screens.SpellFormScreen
import com.example.spellbook.ui.screens.SpellListScreen
import com.example.spellbook.ui.screens.SpellSlotsScreen
import com.example.spellbook.ui.screens.StatsScreen
import com.example.spellbook.ui.screens.StepLibraryScreen
import com.example.spellbook.ui.theme.SpellBookTheme
import com.example.spellbook.util.DiceRoller

class MainActivity : ComponentActivity() {

    /** URI файла, открытого через внешнее приложение (action VIEW). */
    private var incomingUri by mutableStateOf<Uri?>(null)

    /** Ссылка dnd.su, пришедшая через «Поделиться» (action SEND, text/plain). */
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            // ViewModel создаётся до темы: язык и оформление берутся из настроек.
            val viewModel: SpellBookViewModel = viewModel()
            val settings = viewModel.uiState.settings
            ProvideAppLocale(settings.language) {
                SpellBookTheme(appTheme = settings.theme) {
                    SpellBookApp(
                        incomingUri = incomingUri,
                        onIncomingUriHandled = { incomingUri = null },
                        sharedUrl = sharedUrl,
                        onSharedUrlHandled = { sharedUrl = null },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Разбирает входящий интент: открытие файла (VIEW) или «Поделиться» ссылкой (SEND). */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> incomingUri = intent.data
            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                sharedUrl = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            }
        }
    }
}

/** Что выгружается в файл: заклинание, черта, лист персонажа или вся библиотека. */
private enum class ExportKind { SPELL, FEAT, CHARACTER, LIBRARY }

/** Извлекает первую http(s)-ссылку из произвольного текста (браузеры часто шлют «название + URL»). */
private fun extractUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return Regex("https?://\\S+").find(text)?.value ?: text.trim()
}

@Composable
private fun SpellBookApp(
    incomingUri: Uri? = null,
    onIncomingUriHandled: () -> Unit = {},
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    viewModel: SpellBookViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState

    // JSON, ожидающий записи в выбранный пользователем файл (для выгрузки).
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    // Что именно выгружается — от этого зависит текст уведомления.
    var pendingExportKind by remember { mutableStateOf(ExportKind.SPELL) }

    // Чью панель разделов редактируем в настройках; null — общая для всех персонажей.
    var settingsScopeId by remember { mutableStateOf<String?>(null) }

    /** К какому персонажу применить загружаемый лист; null — создать нового. */
    var sheetImportTargetId by remember { mutableStateOf<String?>(null) }

    // Показан ли диалог ввода ссылки dnd.su.
    var showDndSuDialog by remember { mutableStateOf(false) }

    // Открытие файла, переданного другим приложением (Telegram, почта, файлы и т.п.).
    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text != null) {
            // Лист персонажа и заклинание различаются по структуре JSON.
            if (CharacterLssCodec.looksLikeCharacterSheet(text)) {
                viewModel.importCharacterJson(text)
            } else {
                viewModel.importSpellJson(text)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.file_read_failed), Toast.LENGTH_SHORT).show()
        }
        onIncomingUriHandled()
    }

    // Обработка ссылки, пришедшей через «Поделиться» из браузера.
    LaunchedEffect(sharedUrl) {
        val url = sharedUrl ?: return@LaunchedEffect
        viewModel.prepareImportForCharacter(null)
        // Тип определяется по адресу: /feats/ — черта, иначе заклинание.
        viewModel.importFromDndSuUrl(url)
        onSharedUrlHandled()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text != null) {
            if (CharacterLssCodec.looksLikeCharacterSheet(text)) {
                viewModel.importCharacterJson(text)
            } else {
                viewModel.importSpellJson(text)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.file_read_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }.isSuccess
        val successRes = when (pendingExportKind) {
            ExportKind.SPELL -> R.string.export_spell_done
            ExportKind.FEAT -> R.string.export_feat_done
            ExportKind.CHARACTER -> R.string.export_character_done
            ExportKind.LIBRARY -> R.string.export_library_done
        }
        Toast.makeText(
            context,
            context.getString(if (ok) successRes else R.string.file_save_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Загрузка листа персонажа в формате LSS.
    val sheetImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val targetId = sheetImportTargetId
        sheetImportTargetId = null
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text != null) {
            viewModel.importCharacterJson(text, targetId)
        } else {
            Toast.makeText(context, context.getString(R.string.file_read_failed), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.message) {
        // Сообщения хранятся как ресурсы и резолвятся здесь — в контексте выбранного языка.
        state.message?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val importSpell = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    val importSheet = { sheetImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }

    if (showDndSuDialog) {
        DndSuUrlDialog(
            onConfirm = { url ->
                showDndSuDialog = false
                viewModel.importFromDndSu(url)
            },
            onDismiss = {
                showDndSuDialog = false
                viewModel.prepareImportForCharacter(null)
            },
        )
    }

    // Лог показывается только если при массовой загрузке были ошибки.
    state.libraryReport?.let { report ->
        LibraryReportDialog(report = report, onDismiss = viewModel::dismissLibraryReport)
    }

    // Обработка системной кнопки «Назад»: на внутренних экранах — возврат к предыдущему,
    // на корневых экранах вкладок — выход по двойному нажатию.
    var lastBackPressMillis by remember { mutableStateOf(0L) }
    BackHandler {
        when (val screen = state.screen) {
            Screen.Characters, Screen.Library -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressMillis < EXIT_CONFIRM_WINDOW_MILLIS) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressMillis = now
                    Toast.makeText(context, context.getString(R.string.exit_confirm), Toast.LENGTH_SHORT).show()
                }
            }

            Screen.Settings -> viewModel.exitSettings()
            // Разделы персонажа: с домашнего — к списку, с остальных — к домашнему.
            is Screen.CharacterSpells ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.SPELLS)
            is Screen.PrepareSpells ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.PREPARE)
            is Screen.SpellSlots ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.RESOURCES)
            is Screen.Stats ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.STATS)
            is Screen.Notes ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.NOTES)
            is Screen.Feats ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.FEATS)
            is Screen.Combos ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.COMBOS)
            is Screen.Inventory ->
                viewModel.exitCharacterSection(screen.characterId, CharacterSection.INVENTORY)
            // Разделы библиотеки возвращают к выбору раздела, а не закрывают приложение.
            Screen.LibrarySpells, Screen.LibraryFeats -> viewModel.openLibrary()
            is Screen.FeatDetails -> viewModel.openLibraryFeats()
            is Screen.AddFeatDetails -> viewModel.openAddFeats(screen.characterId)
            is Screen.AddSpells -> viewModel.openCharacterSpells(screen.characterId)
            is Screen.AddFeats -> viewModel.openFeats(screen.characterId)
            is Screen.ComboEditor -> viewModel.openCombos(screen.characterId)
            is Screen.StepLibrary -> viewModel.openCombos(screen.characterId)
            is Screen.ComboResult -> viewModel.openCombos(screen.characterId)
            is Screen.CharacterForm -> viewModel.exitCharacterForm()
            is Screen.Details -> viewModel.navigateBackFromDetails()
            is Screen.SpellForm -> {
                val existing = viewModel.getSpell(screen.spellId)
                if (existing != null) viewModel.openDetails(existing.id)
                else viewModel.navigateBackToList()
            }
        }
    }

    val bottomBar = @Composable {
        SpellBookBottomBar(selected = state.tab, onSelect = viewModel::selectTab)
    }

    when (val screen = state.screen) {
        Screen.Characters -> CharactersScreen(
            characters = state.characters,
            spellCounts = state.spellCounts,
            // Точка входа — домашний раздел персонажа, а не всегда заклинания.
            onCharacterClick = viewModel::openCharacterHome,
            onAddCharacter = viewModel::openCreateCharacterForm,
            onImportSheet = {
                // Новый лист создаёт персонажа, а не обновляет существующего.
                sheetImportTargetId = null
                importSheet()
            },
            bottomBar = bottomBar,
        )

        // Корень библиотеки: выбор между заклинаниями и чертами.
        Screen.Library -> LibraryHubScreen(
            spellCount = state.librarySpells.size,
            featCount = state.libraryFeats.size,
            onOpenSpells = viewModel::openLibrarySpells,
            onOpenFeats = viewModel::openLibraryFeats,
            bottomBar = bottomBar,
        )

        Screen.LibraryFeats -> LibraryFeatsScreen(
            feats = state.libraryFeats,
            query = viewModel.featsQuery,
            onQueryChange = viewModel::updateFeatsQuery,
            filters = viewModel.featsFilters,
            onFiltersChange = viewModel::updateFeatsFilters,
            onFeatClick = viewModel::openFeatDetails,
            onBack = viewModel::openLibrary,
            bottomBar = bottomBar,
            initialScrollIndex = viewModel.featsScrollIndex,
            initialScrollOffset = viewModel.featsScrollOffset,
            onScrollChanged = viewModel::saveFeatsScroll,
        )

        is Screen.FeatDetails -> {
            val feat = viewModel.getLibraryFeat(screen.featId)
            if (feat == null) {
                // Черту могли удалить с другого экрана — возвращаемся к списку.
                LaunchedEffect(screen.featId) { viewModel.openLibraryFeats() }
            } else {
                FeatDetailsScreen(
                    feat = feat,
                    onBack = viewModel::openLibraryFeats,
                    // Из библиотеки правка всегда общая, поэтому scope не спрашивается.
                    onEdit = { name, description, _ ->
                        viewModel.editLibraryFeat(feat.id, name, description)
                    },
                    onExport = {
                        val json = viewModel.exportFeatJson(feat.id)
                        if (json != null) {
                            pendingExportJson = json
                            pendingExportKind = ExportKind.FEAT
                            exportLauncher.launch(suggestFileName(feat.name))
                        }
                    },
                    onShare = {
                        val json = viewModel.exportFeatJson(feat.id)
                        if (json != null) shareSpellJson(context, json, suggestFileName(feat.name))
                    },
                    onDelete = {
                        viewModel.deleteFeat(feat.id)
                        viewModel.openLibraryFeats()
                    },
                )
            }
        }

        Screen.LibrarySpells -> SpellListScreen(
            title = stringResource(R.string.library_section_spells),
            spells = state.librarySpells,
            query = viewModel.listQuery,
            onQueryChange = viewModel::updateListQuery,
            sort = viewModel.listSort,
            onSortChange = viewModel::updateListSort,
            filters = viewModel.listFilters,
            onFiltersChange = viewModel::updateListFilters,
            onSpellClick = viewModel::openDetails,
            initialScrollIndex = viewModel.listScrollIndex,
            initialScrollOffset = viewModel.listScrollOffset,
            onScrollChanged = viewModel::saveListScroll,
            bottomBar = bottomBar,
            navigationIcon = {
                IconButton(onClick = viewModel::openLibrary) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            headerContent = {
                // Пока идёт массовая загрузка — показываем прогресс над списком.
                state.libraryProgress?.let { progress ->
                    LibraryDownloadBanner(
                        progress = progress,
                        onCancel = viewModel::cancelLibraryDownload,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
            floatingActionButton = {
                LibraryFab(
                    viewModel = viewModel,
                    onImport = importSpell,
                    onLoadFromDndSu = { showDndSuDialog = true },
                    onExportLibrary = {
                        // Один файл со всей библиотекой можно передать другому человеку.
                        val json = viewModel.exportLibraryJson()
                        if (json != null) {
                            pendingExportJson = json
                            pendingExportKind = ExportKind.LIBRARY
                            exportLauncher.launch("spellbook-library.json")
                        }
                    },
                )
            },
            emptyContent = { modifier -> LibraryEmpty(modifier, viewModel, importSpell) },
        )

        is Screen.CharacterSpells -> {
            val character = viewModel.getCharacter(screen.characterId)
            CharacterSpellsScreenContent(
                character = character,
                spells = viewModel.spellsForCharacter(screen.characterId),
                viewModel = viewModel,
                onImportForCharacter = { characterId ->
                    viewModel.prepareImportForCharacter(characterId)
                    importSpell()
                },
                onLoadFromDndSu = { characterId ->
                    viewModel.prepareImportForCharacter(characterId)
                    showDndSuDialog = true
                },
                bottomBar = bottomBar,
            )
        }

        is Screen.AddSpells -> AddSpellsScreen(
            librarySpells = state.librarySpells,
            selectedIds = state.currentCharacterSpellIds,
            onToggle = { spellId, add ->
                viewModel.toggleSpellForCharacter(screen.characterId, spellId, add)
            },
            onBack = { viewModel.openCharacterSpells(screen.characterId) },
        )

        is Screen.CharacterForm -> {
            val existing = viewModel.getCharacter(screen.characterId)
            CharacterFormScreen(
                initial = existing ?: Character(),
                isNew = existing == null,
                onSave = { character, addClassSpells ->
                    viewModel.saveCharacter(character, addClassSpells)
                },
                onDelete = existing?.let { { viewModel.deleteCharacter(it.id) } },
                onBack = viewModel::exitCharacterForm,
                onExportSheet = existing?.let { character ->
                    {
                        pendingExportJson = viewModel.exportCharacterJson(character.id)
                        pendingExportKind = ExportKind.CHARACTER
                        exportLauncher.launch(suggestFileName(character.name))
                    }
                },
                onImportSheet = existing?.let { character ->
                    {
                        // Лист применяется к текущему персонажу.
                        sheetImportTargetId = character.id
                        importSheet()
                    }
                },
            )
        }

        is Screen.PrepareSpells -> {
            val character = viewModel.getCharacter(screen.characterId)
            if (character == null) {
                LaunchedEffect(screen.characterId) { viewModel.openCharacters() }
            } else {
                PrepareSpellsScreen(
                    // Заговоры (уровень 0) не подготавливаются — показываем только уровневые.
                    known = viewModel.spellsForCharacter(screen.characterId).filter { it.level > 0 },
                    preparedIds = state.currentCharacterPreparedIds,
                    maxPrepared = character.maxPreparedSpells,
                    onPrepare = { spellId -> viewModel.setSpellPrepared(screen.characterId, spellId, true) },
                    onUnprepare = { spellId -> viewModel.setSpellPrepared(screen.characterId, spellId, false) },
                    onSpellClick = viewModel::openDetails,
                    onBack = {
                        viewModel.exitCharacterSection(screen.characterId, CharacterSection.PREPARE)
                    },
                    sectionsBar = {
                        CharacterSections(viewModel, screen.characterId, CharacterSection.PREPARE)
                    },
                )
            }
        }

        is Screen.SpellSlots -> {
            val character = viewModel.getCharacter(screen.characterId)
            if (character == null) {
                LaunchedEffect(screen.characterId) { viewModel.openCharacters() }
            } else {
                SpellSlotsScreen(
                    character = character,
                    onUseSlot = { level -> viewModel.useSpellSlot(screen.characterId, level) },
                    onRestoreSlot = { level -> viewModel.restoreSpellSlot(screen.characterId, level) },
                    onUseResource = { id -> viewModel.useCharacterResource(screen.characterId, id) },
                    onRestoreResourceUnit = { id -> viewModel.restoreCharacterResourceUnit(screen.characterId, id) },
                    onRestoreResource = { id -> viewModel.restoreCharacterResource(screen.characterId, id) },
                    onDeleteResource = { id -> viewModel.deleteCharacterResource(screen.characterId, id) },
                    onAddResource = { name, description, maximum, formula ->
                        viewModel.addCharacterResource(
                            screen.characterId,
                            name,
                            description,
                            maximum,
                            formula,
                        )
                    },
                    onEditResource = { id, name, description, maximum, formula ->
                        viewModel.editCharacterResource(
                            screen.characterId,
                            id,
                            name,
                            description,
                            maximum,
                            formula,
                        )
                    },
                    onReorderResources = { orderedIds ->
                        viewModel.reorderCharacterResources(screen.characterId, orderedIds)
                    },
                    onRestoreAll = { viewModel.restoreAllResources(screen.characterId) },
                    onBack = {
                        viewModel.exitCharacterSection(screen.characterId, CharacterSection.RESOURCES)
                    },
                    sectionsBar = {
                        CharacterSections(viewModel, screen.characterId, CharacterSection.RESOURCES)
                    },
                )
            }
        }

        is Screen.Stats -> {
            val character = viewModel.getCharacter(screen.characterId)
            if (character == null) {
                LaunchedEffect(screen.characterId) { viewModel.openCharacters() }
            } else {
                StatsScreen(
                    character = character,
                    lastRoll = state.lastD20Roll,
                    onChangeHp = { delta -> viewModel.changeHp(screen.characterId, delta) },
                    onSetHpValues = { tempHp, maxHp ->
                        viewModel.setHpValues(screen.characterId, tempHp, maxHp)
                    },
                    onSetArmorClass = { value -> viewModel.setArmorClass(screen.characterId, value) },
                    onSetSpeed = { value -> viewModel.setSpeed(screen.characterId, value) },
                    onSetAbilityScore = { ability, value ->
                        viewModel.setAbilityScore(screen.characterId, ability, value)
                    },
                    onToggleSave = { ability -> viewModel.toggleSaveProficiency(screen.characterId, ability) },
                    onCycleSkill = { skill -> viewModel.cycleSkillProficiency(screen.characterId, skill) },
                    onRoll = { title, kind, bonus -> viewModel.rollD20(title, kind, bonus) },
                    onDismissRoll = viewModel::dismissD20Roll,
                    onBack = {
                        viewModel.exitCharacterSection(screen.characterId, CharacterSection.STATS)
                    },
                    sectionsBar = {
                        CharacterSections(viewModel, screen.characterId, CharacterSection.STATS)
                    },
                )
            }
        }

        is Screen.Notes -> NotesScreen(
            blocks = state.noteBlocks,
            onAddBlock = { title -> viewModel.addNoteBlock(screen.characterId, title) },
            onSaveBlock = viewModel::saveNoteBlock,
            onDeleteBlock = viewModel::deleteNoteBlock,
            onBack = { viewModel.exitCharacterSection(screen.characterId, CharacterSection.NOTES) },
            sectionsBar = {
                CharacterSections(viewModel, screen.characterId, CharacterSection.NOTES)
            },
        )

        is Screen.Feats -> {
            val character = viewModel.getCharacter(screen.characterId)
            FeatsScreen(
                feats = state.feats,
                characterName = character?.name?.ifBlank {
                    stringResource(R.string.character_unnamed)
                }.orEmpty(),
                onAddFeat = { name, description ->
                    viewModel.addFeat(screen.characterId, name, description)
                },
                onSaveFeat = { featId, name, description, scope ->
                    viewModel.editCharacterFeat(screen.characterId, featId, name, description, scope)
                },
                onToggleCollapsed = { featId, collapsed ->
                    viewModel.toggleFeatCollapsed(screen.characterId, featId, collapsed)
                },
                onRemoveFromCharacter = { featId ->
                    viewModel.removeFeatFromCharacter(screen.characterId, featId)
                },
                onReorder = { orderedIds -> viewModel.reorderFeats(screen.characterId, orderedIds) },
                onLoadFromDndSu = { url -> viewModel.importFeatFromDndSu(screen.characterId, url) },
                onAddFromLibrary = { viewModel.openAddFeats(screen.characterId) },
                onDiceClick = { formula -> DiceRoller.roll(formula) },
                onBack = {
                    viewModel.exitCharacterSection(screen.characterId, CharacterSection.FEATS)
                },
                sectionsBar = {
                    CharacterSections(viewModel, screen.characterId, CharacterSection.FEATS)
                },
            )
        }

        // Добавление черт персонажу — тот же список, что и в библиотеке:
        // с поиском, фильтрами и группировкой по книгам-источникам.
        is Screen.AddFeats -> LibraryFeatsScreen(
            feats = state.libraryFeats,
            title = stringResource(R.string.feats_add_title),
            query = viewModel.featsQuery,
            onQueryChange = viewModel::updateFeatsQuery,
            filters = viewModel.featsFilters,
            onFiltersChange = viewModel::updateFeatsFilters,
            onFeatClick = { featId ->
                viewModel.openAddFeatDetails(screen.characterId, featId)
            },
            onBack = { viewModel.openFeats(screen.characterId) },
            initialScrollIndex = viewModel.featsScrollIndex,
            initialScrollOffset = viewModel.featsScrollOffset,
            onScrollChanged = viewModel::saveFeatsScroll,
        )

        is Screen.AddFeatDetails -> {
            val feat = viewModel.getLibraryFeat(screen.featId)
            if (feat == null) {
                // Черту могли удалить из библиотеки — возвращаемся к списку.
                LaunchedEffect(screen.featId) { viewModel.openAddFeats(screen.characterId) }
            } else {
                val alreadyAdded = feat.id in state.currentCharacterFeatIds
                FeatDetailsScreen(
                    feat = feat,
                    onBack = { viewModel.openAddFeats(screen.characterId) },
                    onEdit = { name, description, _ ->
                        viewModel.editLibraryFeat(feat.id, name, description)
                    },
                    onExport = {
                        val json = viewModel.exportFeatJson(feat.id)
                        if (json != null) {
                            pendingExportJson = json
                            pendingExportKind = ExportKind.FEAT
                            exportLauncher.launch(suggestFileName(feat.name))
                        }
                    },
                    onShare = {
                        val json = viewModel.exportFeatJson(feat.id)
                        if (json != null) shareSpellJson(context, json, suggestFileName(feat.name))
                    },
                    onDelete = {
                        viewModel.deleteFeat(feat.id)
                        viewModel.openAddFeats(screen.characterId)
                    },
                    // Кнопка внизу: сразу после прочтения можно взять черту персонажу.
                    addedToCharacter = alreadyAdded,
                    onToggleForCharacter = {
                        viewModel.toggleFeatForCharacter(
                            screen.characterId,
                            feat.id,
                            !alreadyAdded,
                        )
                    },
                )
            }
        }

        is Screen.Combos -> ComboListScreen(
            combos = state.combos,
            onCreate = { viewModel.openComboEditor(screen.characterId) },
            onEdit = { comboId -> viewModel.openComboEditor(screen.characterId, comboId) },
            onOpenLibrary = { viewModel.openStepLibrary(screen.characterId) },
            onRoll = { comboId, mode -> viewModel.rollCombo(screen.characterId, comboId, mode) },
            onDelete = { comboId -> viewModel.deleteCombo(comboId, screen.characterId) },
            onReorder = { orderedIds -> viewModel.reorderCombos(orderedIds) },
            onBack = { viewModel.exitCharacterSection(screen.characterId, CharacterSection.COMBOS) },
            sectionsBar = {
                CharacterSections(viewModel, screen.characterId, CharacterSection.COMBOS)
            },
        )

        is Screen.ComboEditor -> {
            val combo = viewModel.editingCombo
            if (combo == null) {
                LaunchedEffect(screen.comboId) { viewModel.openCombos(screen.characterId) }
            } else {
                ComboEditorScreen(
                    combo = combo,
                    allSteps = state.comboSteps,
                    selectedStepIds = viewModel.editingComboStepIds,
                    onNameChange = viewModel::setEditingComboName,
                    onToggleStep = viewModel::toggleStepInEditingCombo,
                    onMoveStep = viewModel::moveEditingComboStep,
                    onSaveStep = viewModel::saveComboStep,
                    onSave = viewModel::saveEditingCombo,
                    onDelete = screen.comboId?.let {
                        { viewModel.deleteCombo(it, screen.characterId) }
                    },
                    onBack = { viewModel.openCombos(screen.characterId) },
                )
            }
        }

        is Screen.StepLibrary -> StepLibraryScreen(
            characterId = screen.characterId,
            steps = state.comboSteps,
            onSave = { viewModel.saveComboStep(it) },
            onDelete = viewModel::deleteComboStep,
            onBack = { viewModel.openCombos(screen.characterId) },
        )

        is Screen.Inventory -> {
            val character = viewModel.getCharacter(screen.characterId)
            if (character == null) {
                LaunchedEffect(screen.characterId) { viewModel.openCharacters() }
            } else {
                InventoryScreen(
                    character = character,
                    items = state.inventoryItems,
                    onSaveItem = viewModel::saveInventoryItem,
                    onDeleteItem = viewModel::deleteInventoryItem,
                    onChangeQuantity = viewModel::changeInventoryQuantity,
                    onSetQuantity = viewModel::setInventoryQuantity,
                    onReorderItems = viewModel::reorderInventoryItems,
                    onToggleAttunement = viewModel::toggleItemAttunement,
                    onSetAttunementLimit = { viewModel.updateAttunementLimit(screen.characterId, it) },
                    onSetCoinAmount = { coin, amount -> viewModel.setCoinAmount(screen.characterId, coin, amount) },
                    onBack = {
                        viewModel.exitCharacterSection(screen.characterId, CharacterSection.INVENTORY)
                    },
                    sectionsBar = {
                        CharacterSections(viewModel, screen.characterId, CharacterSection.INVENTORY)
                    },
                )
            }
        }

        is Screen.ComboResult -> {
            val result = viewModel.comboRollResult
            if (result == null) {
                LaunchedEffect(screen.comboId) { viewModel.openCombos(screen.characterId) }
            } else {
                ComboResultScreen(
                    result = result,
                    onReroll = { mode -> viewModel.rollCombo(screen.characterId, screen.comboId, mode) },
                    onBack = { viewModel.openCombos(screen.characterId) },
                )
            }
        }

        is Screen.Details -> {
            val spell = viewModel.getSpell(screen.spellId)
            if (spell == null) {
                LaunchedEffect(screen.spellId) { viewModel.navigateBackToList() }
            } else {
                SpellDetailsScreen(
                    spell = spell,
                    onBack = viewModel::navigateBackFromDetails,
                    onEdit = { viewModel.openEditSpellForm(spell.id) },
                    onExport = {
                        val json = viewModel.exportSpellJson(spell.id)
                        if (json != null) {
                            pendingExportJson = json
                            pendingExportKind = ExportKind.SPELL
                            exportLauncher.launch(suggestFileName(spell.name))
                        }
                    },
                    onShare = {
                        val json = viewModel.exportSpellJson(spell.id)
                        if (json != null) shareSpellJson(context, json, suggestFileName(spell.name))
                    },
                    onDelete = { viewModel.deleteSpell(spell.id) },
                )
            }
        }

        is Screen.SpellForm -> {
            val existing = viewModel.getSpell(screen.spellId)
            // Форма, открытая из набора персонажа, спрашивает область сохранения правки.
            val editingCharacter = screen.characterId?.let { viewModel.getCharacter(it) }
            SpellFormScreen(
                initial = existing ?: Spell(),
                isNew = existing == null,
                characterName = editingCharacter?.name?.ifBlank {
                    stringResource(R.string.character_unnamed)
                },
                onSave = { spell, scope ->
                    val characterId = editingCharacter?.id
                    if (scope == null || characterId == null) {
                        viewModel.saveSpell(spell)
                    } else {
                        viewModel.editCharacterSpell(characterId, spell, scope)
                    }
                },
                onBack = if (existing != null) {
                    { viewModel.openDetails(existing.id) }
                } else {
                    viewModel::navigateBackToList
                },
            )
        }

        Screen.Settings -> {
            val settings = state.settings
            // Существующий персонаж мог быть удалён — возвращаемся к общей области.
            val scopeId = settingsScopeId?.takeIf { id -> state.characters.any { it.id == id } }
            SettingsScreen(
                language = settings.language,
                theme = settings.theme,
                characters = state.characters,
                scopeCharacterId = scopeId,
                onScopeChange = { settingsScopeId = it },
                layout = settings.layoutFor(scopeId),
                hasOwnLayout = scopeId != null && settings.hasOwnLayout(scopeId),
                onLanguageChange = viewModel::setLanguage,
                onThemeChange = viewModel::setTheme,
                onLayoutChange = { layout -> viewModel.setSectionLayout(scopeId, layout) },
                onLayoutReset = { viewModel.setSectionLayout(scopeId, null) },
                onBack = viewModel::exitSettings,
                bottomBar = bottomBar,
            )
        }
    }
}

/** FAB библиотеки: раскрывающееся меню добавления заклинания. */
@Composable
private fun LibraryFab(
    viewModel: SpellBookViewModel,
    onImport: () -> Unit,
    onLoadFromDndSu: () -> Unit,
    onExportLibrary: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AddSpellFab(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        actions = listOf(
            FabAction(stringResource(R.string.library_download_all), Icons.Default.CloudDownload) {
                expanded = false
                viewModel.downloadOfficialLibrary()
            },
            FabAction(stringResource(R.string.library_export), Icons.Default.Save) {
                expanded = false
                onExportLibrary()
            },
            FabAction(stringResource(R.string.library_load_dndsu), Icons.Default.Link) {
                expanded = false
                viewModel.prepareImportForCharacter(null)
                onLoadFromDndSu()
            },
            FabAction(stringResource(R.string.library_import_json), Icons.Default.UploadFile) {
                expanded = false
                // Сбрасываем возможную привязку к персонажу: импорт из библиотеки — только в библиотеку.
                viewModel.prepareImportForCharacter(null)
                onImport()
            },
            FabAction(stringResource(R.string.library_add_manually), Icons.Default.Edit) {
                expanded = false
                viewModel.openCreateSpellForm()
            },
        ),
    )
}

@Composable
private fun LibraryEmpty(
    modifier: Modifier,
    viewModel: SpellBookViewModel,
    onImport: () -> Unit,
) {
    EmptySpellList(
        onAddClick = viewModel::openCreateSpellForm,
        onImportClick = {
            viewModel.prepareImportForCharacter(null)
            onImport()
        },
        modifier = modifier,
    )
}

/**
 * Плашка прогресса массовой загрузки библиотеки: загрузка идёт долго,
 * поэтому показываем счётчик и даём возможность остановить её в любой момент.
 */
@Composable
private fun LibraryDownloadBanner(
    progress: LibraryDownloadProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (progress.total > 0) {
                    stringResource(R.string.library_downloading, progress.processed, progress.total)
                } else {
                    stringResource(R.string.library_fetching_list)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.library_progress_saved, progress.saved) +
                        (if (progress.skipped > 0) {
                            stringResource(R.string.library_progress_own, progress.skipped)
                        } else {
                            ""
                        }) +
                        (if (progress.failed > 0) {
                            stringResource(R.string.library_progress_errors, progress.failed)
                        } else {
                            ""
                        }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.library_stop))
            }
        }
    }
}

/** Экран заклинаний персонажа с FAB «добавить из библиотеки» и переходом назад к персонажам. */
@Composable
private fun CharacterSpellsScreenContent(
    character: Character?,
    spells: List<Spell>,
    viewModel: SpellBookViewModel,
    onImportForCharacter: (String) -> Unit,
    onLoadFromDndSu: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val characterId = character?.id
    if (characterId == null) {
        LaunchedEffect(Unit) { viewModel.openCharacters() }
        return
    }
    // Для персонажей с переподготовкой — переключатель «Подготовленные / Все» (состояние в ВМ).
    val showPreparedOnly = viewModel.showPreparedOnly
    val displayedSpells = if (character.canPrepareSpells && showPreparedOnly) {
        viewModel.preparedSpellsForCharacter(characterId)
    } else {
        spells
    }
    SpellListScreen(
        title = character.name.ifBlank { stringResource(R.string.character_fallback_title) },
        spells = displayedSpells,
        query = viewModel.listQuery,
        onQueryChange = viewModel::updateListQuery,
        sort = viewModel.listSort,
        onSortChange = viewModel::updateListSort,
        filters = viewModel.listFilters,
        onFiltersChange = viewModel::updateListFilters,
        onSpellClick = viewModel::openDetails,
        // Заклинание убирается только из набора персонажа: в библиотеке оно остаётся.
        onSpellRemove = { spell -> viewModel.removeSpellFromCharacter(characterId, spell.id) },
        removeConfirmTextRes = R.string.spell_remove_from_character,
        navigationIcon = {
            IconButton(
                onClick = {
                    viewModel.exitCharacterSection(characterId, CharacterSection.SPELLS)
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.character_back_to_list),
                )
            }
        },
        headerContent = {
            // Отдельная панель разделов (не загромождает шапку с именем и поиском).
            CharacterSections(viewModel, characterId, CharacterSection.SPELLS)
            if (character.canPrepareSpells) {
                PreparedFilterToggle(
                    showPreparedOnly = showPreparedOnly,
                    onChange = { viewModel.changePreparedTab(it) },
                )
            }
        },
        initialScrollIndex = viewModel.listScrollIndex,
        initialScrollOffset = viewModel.listScrollOffset,
        onScrollChanged = viewModel::saveListScroll,
        bottomBar = bottomBar,
        floatingActionButton = {
            CharacterSpellsFab(
                viewModel = viewModel,
                characterId = characterId,
                onImport = { onImportForCharacter(characterId) },
                onLoadFromDndSu = { onLoadFromDndSu(characterId) },
            )
        },
        emptyContent = { modifier ->
            CharacterSpellsEmpty(
                modifier = modifier,
                onAddFromLibrary = { viewModel.openAddSpells(characterId) },
                onCreate = { viewModel.openCreateSpellForm(forCharacterId = characterId) },
            )
        },
    )
}

/**
 * Панель разделов персонажа с учётом пользовательской настройки порядка и видимости.
 * Вынесена отдельно: панель одинакова на всех экранах персонажа.
 */
@Composable
private fun CharacterSections(
    viewModel: SpellBookViewModel,
    characterId: String,
    current: CharacterSection,
) {
    CharacterSectionsBar(
        current = current,
        showPrepare = viewModel.getCharacter(characterId)?.canPrepareSpells == true,
        onSelect = { section -> viewModel.openCharacterSection(characterId, section) },
        layout = viewModel.uiState.settings.layoutFor(characterId),
        initialScrollIndex = viewModel.sectionsScrollIndex,
        initialScrollOffset = viewModel.sectionsScrollOffset,
        onScrollChanged = viewModel::saveSectionsScroll,
    )
}


/** Переключатель «Подготовленные / Все известные» на экране персонажа. */
@Composable
private fun PreparedFilterToggle(
    showPreparedOnly: Boolean,
    onChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.TabRow(selectedTabIndex = if (showPreparedOnly) 0 else 1) {
        androidx.compose.material3.Tab(
            selected = showPreparedOnly,
            onClick = { onChange(true) },
            text = { Text(stringResource(R.string.tab_prepared)) },
        )
        androidx.compose.material3.Tab(
            selected = !showPreparedOnly,
            onClick = { onChange(false) },
            text = { Text(stringResource(R.string.tab_all_known)) },
        )
    }
}

/**
 * FAB на экране персонажа: раскрывающееся меню с созданием/импортом заклинания
 * (попадает и в библиотеку, и в набор персонажа) и добавлением из библиотеки.
 */
@Composable
private fun CharacterSpellsFab(
    viewModel: SpellBookViewModel,
    characterId: String,
    onImport: () -> Unit,
    onLoadFromDndSu: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AddSpellFab(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        actions = listOf(
            FabAction(stringResource(R.string.library_add_from), Icons.AutoMirrored.Filled.MenuBook) {
                expanded = false
                viewModel.openAddSpells(characterId)
            },
            FabAction(stringResource(R.string.library_load_dndsu), Icons.Default.Link) {
                expanded = false
                onLoadFromDndSu()
            },
            FabAction(stringResource(R.string.library_import_json), Icons.Default.UploadFile) {
                expanded = false
                onImport()
            },
            FabAction(stringResource(R.string.library_add_manually), Icons.Default.Edit) {
                expanded = false
                viewModel.openCreateSpellForm(forCharacterId = characterId)
            },
        ),
    )
}

@Composable
private fun CharacterSpellsEmpty(
    modifier: Modifier,
    onAddFromLibrary: () -> Unit,
    onCreate: () -> Unit,
) {
    Box(
        modifier = modifier.then(Modifier.fillMaxSize()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.character_spells_empty))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddFromLibrary) {
                Text(stringResource(R.string.library_add_from))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCreate) {
                Text(stringResource(R.string.character_create_spell))
            }
        }
    }
}

/**
 * Лог после массовой загрузки: какие заклинания не удалось загрузить и почему.
 * Список может быть длинным, поэтому он прокручивается и ограничен по высоте.
 */
@Composable
private fun LibraryReportDialog(
    report: LibraryDownloadReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.library_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (report.skipped > 0) {
                        stringResource(R.string.msg_library_downloaded_own, report.saved, report.skipped)
                    } else {
                        stringResource(R.string.msg_library_downloaded, report.saved)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.library_report_failed, report.failures.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = LIBRARY_REPORT_MAX_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(report.failures, key = { it.url }) { failure ->
                        Column {
                            Text(
                                failure.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                failure.reason.resolve(LocalContext.current),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** Диалог ввода ссылки на заклинание с dnd.su. */
@Composable
private fun DndSuUrlDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dndsu_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dndsu_dialog_hint))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://dnd.su/spells/...") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_load))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun suggestFileName(spellName: String): String {
    val safe = spellName.ifBlank { "spell" }
        .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
        .trim('_')
        .ifBlank { "spell" }
    return "$safe.json"
}

/**
 * Сохраняет JSON во временный файл в кэше и открывает системный диалог «Поделиться»,
 * чтобы отправить файл в другое приложение (мессенджер, почту и т.п.).
 */
private fun shareSpellJson(context: Context, json: String, fileName: String) {
    runCatching {
        val shareDir = java.io.File(context.cacheDir, SHARE_DIR_NAME).apply { mkdirs() }
        val file = java.io.File(shareDir, fileName).apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val title = context.getString(R.string.action_share)
        context.startActivity(Intent.createChooser(intent, title))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
    }
}

private const val SHARE_DIR_NAME = "shared"

/** Интервал, в течение которого повторное нажатие «Назад» закрывает приложение. */
private const val EXIT_CONFIRM_WINDOW_MILLIS = 2000L

/** Максимальная высота списка ошибок в логе загрузки: дальше он прокручивается. */
private val LIBRARY_REPORT_MAX_HEIGHT = 320.dp
