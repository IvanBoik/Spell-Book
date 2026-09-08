package com.example.spellbook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.CharacterLssCodec
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.screens.EmptySpellList
import com.example.spellbook.ui.screens.AddSpellFab
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.Screen
import com.example.spellbook.ui.SpellBookViewModel
import com.example.spellbook.ui.Tab
import com.example.spellbook.ui.components.CharacterSection
import com.example.spellbook.ui.components.CharacterSectionsBar
import com.example.spellbook.ui.components.SpellBookBottomBar
import com.example.spellbook.ui.screens.AddSpellsScreen
import com.example.spellbook.ui.screens.CharacterFormScreen
import com.example.spellbook.ui.screens.CharactersScreen
import com.example.spellbook.ui.screens.ComboEditorScreen
import com.example.spellbook.ui.screens.ComboListScreen
import com.example.spellbook.ui.screens.ComboResultScreen
import com.example.spellbook.ui.screens.InventoryScreen
import com.example.spellbook.ui.screens.FabAction
import com.example.spellbook.ui.screens.PrepareSpellsScreen
import com.example.spellbook.util.DiceRoller
import com.example.spellbook.ui.screens.AddFeatsScreen
import com.example.spellbook.ui.screens.FeatsScreen
import com.example.spellbook.ui.screens.NotesScreen
import com.example.spellbook.ui.screens.SpellSlotsScreen
import com.example.spellbook.ui.screens.StatsScreen
import com.example.spellbook.ui.screens.SpellDetailsScreen
import com.example.spellbook.ui.screens.SpellFormScreen
import com.example.spellbook.ui.screens.SpellListScreen
import com.example.spellbook.ui.screens.StepLibraryScreen
import com.example.spellbook.ui.theme.SpellBookTheme

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
            SpellBookTheme {
                SpellBookApp(
                    incomingUri = incomingUri,
                    onIncomingUriHandled = { incomingUri = null },
                    sharedUrl = sharedUrl,
                    onSharedUrlHandled = { sharedUrl = null },
                )
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

/** Что выгружается в файл: отдельное заклинание или лист персонажа. */
private enum class ExportKind { SPELL, CHARACTER }

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
            Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
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
        val successText = when (pendingExportKind) {
            ExportKind.SPELL -> "Заклинание выгружено"
            ExportKind.CHARACTER -> "Лист персонажа выгружен"
        }
        Toast.makeText(
            context,
            if (ok) successText else "Не удалось сохранить файл",
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
            Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Нажмите ещё раз для выхода", Toast.LENGTH_SHORT).show()
                }
            }

            is Screen.CharacterSpells -> viewModel.openCharacters()
            is Screen.AddSpells -> viewModel.openCharacterSpells(screen.characterId)
            is Screen.PrepareSpells -> viewModel.openCharacterSpells(screen.characterId)
            is Screen.SpellSlots -> viewModel.openCharacterSpells(screen.characterId)
            is Screen.Stats -> viewModel.openCharacterSpells(screen.characterId, resetView = false)
            is Screen.Notes -> viewModel.openCharacterSpells(screen.characterId, resetView = false)
            is Screen.Feats -> viewModel.openCharacterSpells(screen.characterId, resetView = false)
            is Screen.AddFeats -> viewModel.openFeats(screen.characterId)
            is Screen.Combos -> viewModel.openCharacterSpells(screen.characterId, resetView = false)
            is Screen.ComboEditor -> viewModel.openCombos(screen.characterId)
            is Screen.StepLibrary -> viewModel.openCombos(screen.characterId)
            is Screen.ComboResult -> viewModel.openCombos(screen.characterId)
            is Screen.Inventory -> viewModel.openCharacterSpells(screen.characterId, resetView = false)
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
            onCharacterClick = viewModel::openCharacterSpells,
            onAddCharacter = viewModel::openCreateCharacterForm,
            onImportSheet = {
                // Новый лист создаёт персонажа, а не обновляет существующего.
                sheetImportTargetId = null
                importSheet()
            },
            bottomBar = bottomBar,
        )

        Screen.Library -> SpellListScreen(
            title = "Библиотека",
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
            floatingActionButton = {
                LibraryFab(
                    viewModel = viewModel,
                    onImport = importSpell,
                    onLoadFromDndSu = { showDndSuDialog = true },
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
                onSave = viewModel::saveCharacter,
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
                    onBack = { viewModel.openCharacterSpells(screen.characterId) },
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
                    onBack = { viewModel.openCharacterSpells(screen.characterId) },
                    sectionsBar = {
                        CharacterSectionsBar(
                            current = CharacterSection.RESOURCES,
                            showPrepare = character.canPrepareSpells,
                            onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                            initialScrollIndex = viewModel.sectionsScrollIndex,
                            initialScrollOffset = viewModel.sectionsScrollOffset,
                            onScrollChanged = viewModel::saveSectionsScroll,
                        )
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
                    onBack = { viewModel.openCharacterSpells(screen.characterId, resetView = false) },
                    sectionsBar = {
                        CharacterSectionsBar(
                            current = CharacterSection.STATS,
                            showPrepare = character.canPrepareSpells,
                            onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                            initialScrollIndex = viewModel.sectionsScrollIndex,
                            initialScrollOffset = viewModel.sectionsScrollOffset,
                            onScrollChanged = viewModel::saveSectionsScroll,
                        )
                    },
                )
            }
        }

        is Screen.Notes -> NotesScreen(
            blocks = state.noteBlocks,
            onAddBlock = { title -> viewModel.addNoteBlock(screen.characterId, title) },
            onSaveBlock = viewModel::saveNoteBlock,
            onDeleteBlock = viewModel::deleteNoteBlock,
            onBack = { viewModel.openCharacterSpells(screen.characterId, resetView = false) },
            sectionsBar = {
                CharacterSectionsBar(
                    current = CharacterSection.NOTES,
                    showPrepare = viewModel.getCharacter(screen.characterId)?.canPrepareSpells == true,
                    onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                    initialScrollIndex = viewModel.sectionsScrollIndex,
                    initialScrollOffset = viewModel.sectionsScrollOffset,
                    onScrollChanged = viewModel::saveSectionsScroll,
                )
            },
        )

        is Screen.Feats -> FeatsScreen(
            feats = state.feats,
            onAddFeat = { name, description -> viewModel.addFeat(screen.characterId, name, description) },
            onSaveFeat = viewModel::saveFeat,
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
            onBack = { viewModel.openCharacterSpells(screen.characterId, resetView = false) },
            sectionsBar = {
                CharacterSectionsBar(
                    current = CharacterSection.FEATS,
                    showPrepare = viewModel.getCharacter(screen.characterId)?.canPrepareSpells == true,
                    onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                    initialScrollIndex = viewModel.sectionsScrollIndex,
                    initialScrollOffset = viewModel.sectionsScrollOffset,
                    onScrollChanged = viewModel::saveSectionsScroll,
                )
            },
        )

        is Screen.AddFeats -> AddFeatsScreen(
            libraryFeats = state.libraryFeats,
            selectedIds = state.currentCharacterFeatIds,
            onToggle = { featId, add ->
                viewModel.toggleFeatForCharacter(screen.characterId, featId, add)
            },
            onDeleteFromLibrary = viewModel::deleteFeat,
            onBack = { viewModel.openFeats(screen.characterId) },
        )

        is Screen.Combos -> ComboListScreen(
            combos = state.combos,
            onCreate = { viewModel.openComboEditor(screen.characterId) },
            onEdit = { comboId -> viewModel.openComboEditor(screen.characterId, comboId) },
            onOpenLibrary = { viewModel.openStepLibrary(screen.characterId) },
            onRoll = { comboId, mode -> viewModel.rollCombo(screen.characterId, comboId, mode) },
            onDelete = { comboId -> viewModel.deleteCombo(comboId, screen.characterId) },
            onReorder = { orderedIds -> viewModel.reorderCombos(orderedIds) },
            onBack = { viewModel.openCharacterSpells(screen.characterId, resetView = false) },
            sectionsBar = {
                CharacterSectionsBar(
                    current = CharacterSection.COMBOS,
                    showPrepare = viewModel.getCharacter(screen.characterId)?.canPrepareSpells == true,
                    onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                    initialScrollIndex = viewModel.sectionsScrollIndex,
                    initialScrollOffset = viewModel.sectionsScrollOffset,
                    onScrollChanged = viewModel::saveSectionsScroll,
                )
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
                    onBack = { viewModel.openCharacterSpells(screen.characterId, resetView = false) },
                    sectionsBar = {
                        CharacterSectionsBar(
                            current = CharacterSection.INVENTORY,
                            showPrepare = character.canPrepareSpells,
                            onSelect = { section -> openCharacterSection(viewModel, screen.characterId, section) },
                            initialScrollIndex = viewModel.sectionsScrollIndex,
                            initialScrollOffset = viewModel.sectionsScrollOffset,
                            onScrollChanged = viewModel::saveSectionsScroll,
                        )
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
            SpellFormScreen(
                initial = existing ?: Spell(),
                isNew = existing == null,
                onSave = viewModel::saveSpell,
                onBack = if (existing != null) {
                    { viewModel.openDetails(existing.id) }
                } else {
                    viewModel::navigateBackToList
                },
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
) {
    var expanded by remember { mutableStateOf(false) }
    AddSpellFab(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        actions = listOf(
            FabAction("Загрузить с dnd.su", Icons.Default.Link) {
                expanded = false
                viewModel.prepareImportForCharacter(null)
                onLoadFromDndSu()
            },
            FabAction("Загрузить JSON", Icons.Default.UploadFile) {
                expanded = false
                // Сбрасываем возможную привязку к персонажу: импорт из библиотеки — только в библиотеку.
                viewModel.prepareImportForCharacter(null)
                onImport()
            },
            FabAction("Добавить вручную", Icons.Default.Edit) {
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
        title = character.name.ifBlank { "Персонаж" },
        spells = displayedSpells,
        query = viewModel.listQuery,
        onQueryChange = viewModel::updateListQuery,
        sort = viewModel.listSort,
        onSortChange = viewModel::updateListSort,
        filters = viewModel.listFilters,
        onFiltersChange = viewModel::updateListFilters,
        onSpellClick = viewModel::openDetails,
        navigationIcon = {
            IconButton(onClick = viewModel::openCharacters) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К персонажам")
            }
        },
        headerContent = {
            // Отдельная панель разделов (не загромождает шапку с именем и поиском).
            CharacterSectionsBar(
                current = CharacterSection.SPELLS,
                showPrepare = character.canPrepareSpells,
                onSelect = { section -> openCharacterSection(viewModel, characterId, section) },
                initialScrollIndex = viewModel.sectionsScrollIndex,
                initialScrollOffset = viewModel.sectionsScrollOffset,
                onScrollChanged = viewModel::saveSectionsScroll,
            )
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
 * Открывает выбранный раздел персонажа. Общий обработчик для панели,
 * которая показывается на всех экранах персонажа.
 */
private fun openCharacterSection(
    viewModel: SpellBookViewModel,
    characterId: String,
    section: CharacterSection,
) {
    when (section) {
        CharacterSection.SPELLS -> viewModel.openCharacterSpells(characterId, resetView = false)
        CharacterSection.SETTINGS -> viewModel.openEditCharacterForm(characterId)
        CharacterSection.STATS -> viewModel.openStats(characterId)
        CharacterSection.RESOURCES -> viewModel.openSpellSlots(characterId)
        CharacterSection.COMBOS -> viewModel.openCombos(characterId)
        CharacterSection.INVENTORY -> viewModel.openInventory(characterId)
        CharacterSection.FEATS -> viewModel.openFeats(characterId)
        CharacterSection.NOTES -> viewModel.openNotes(characterId)
        CharacterSection.PREPARE -> viewModel.openPrepareSpells(characterId)
    }
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
            text = { Text("Подготовленные") },
        )
        androidx.compose.material3.Tab(
            selected = !showPreparedOnly,
            onClick = { onChange(false) },
            text = { Text("Все известные") },
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
            FabAction("Добавить из библиотеки", Icons.AutoMirrored.Filled.MenuBook) {
                expanded = false
                viewModel.openAddSpells(characterId)
            },
            FabAction("Загрузить с dnd.su", Icons.Default.Link) {
                expanded = false
                onLoadFromDndSu()
            },
            FabAction("Загрузить JSON", Icons.Default.UploadFile) {
                expanded = false
                onImport()
            },
            FabAction("Добавить вручную", Icons.Default.Edit) {
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
            Text("У персонажа пока нет заклинаний.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddFromLibrary) {
                Text("Добавить из библиотеки")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCreate) {
                Text("Создать заклинание")
            }
        }
    }
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
        title = { Text("Загрузка с dnd.su") },
        text = {
            Column {
                Text("Вставьте ссылку на страницу заклинания")
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
                Text("Загрузить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
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
        context.startActivity(Intent.createChooser(intent, "Поделиться заклинанием"))
    }.onFailure {
        Toast.makeText(context, "Не удалось поделиться файлом", Toast.LENGTH_SHORT).show()
    }
}

private const val SHARE_DIR_NAME = "shared"

/** Интервал, в течение которого повторное нажатие «Назад» закрывает приложение. */
private const val EXIT_CONFIRM_WINDOW_MILLIS = 2000L
