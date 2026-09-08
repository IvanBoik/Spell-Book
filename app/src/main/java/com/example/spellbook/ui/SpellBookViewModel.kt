package com.example.spellbook.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spellbook.data.AddSpellResult
import com.example.spellbook.data.AppPreferences
import com.example.spellbook.data.CharacterLssCodec
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.withRecalculatedResources
import com.example.spellbook.data.DndSuException
import com.example.spellbook.data.ComboRoller
import com.example.spellbook.data.DndSuLoader
import com.example.spellbook.data.SpellBookRepository
import com.example.spellbook.data.SpellFilters
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.SpellSort
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.D20RollResult
import com.example.spellbook.data.model.ProficiencyLevel
import com.example.spellbook.data.model.RollKind
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.Spell
import com.example.spellbook.util.DiceRoller
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Две основные вкладки приложения. */
enum class Tab { CHARACTERS, LIBRARY }

/** Граней у проверочного кубика. */
private const val D20_SIDES = 20

/** Заголовок блока заметок, если пользователь его не указал. */
private const val DEFAULT_NOTE_TITLE = "Новый блок"

/** Экраны приложения. Нижняя навигация видна только на «корневых» экранах вкладок. */
sealed interface Screen {
    data object Characters : Screen
    data class CharacterSpells(val characterId: String) : Screen
    data object Library : Screen
    data class Details(val spellId: String) : Screen
    data class SpellForm(val spellId: String?) : Screen
    data class CharacterForm(val characterId: String?) : Screen
    data class AddSpells(val characterId: String) : Screen
    data class PrepareSpells(val characterId: String) : Screen
    data class SpellSlots(val characterId: String) : Screen
    data class Combos(val characterId: String) : Screen
    data class ComboEditor(val characterId: String, val comboId: String?) : Screen
    data class StepLibrary(val characterId: String) : Screen
    data class ComboResult(val characterId: String, val comboId: String) : Screen
    data class Inventory(val characterId: String) : Screen
    data class Stats(val characterId: String) : Screen
    data class Notes(val characterId: String) : Screen
    data class Feats(val characterId: String) : Screen
    data class AddFeats(val characterId: String) : Screen
}

data class SpellBookUiState(
    val tab: Tab = Tab.CHARACTERS,
    val screen: Screen = Screen.Characters,
    val characters: List<Character> = emptyList(),
    val spellCounts: Map<String, Int> = emptyMap(),
    val librarySpells: List<Spell> = emptyList(),
    /** id заклинаний, входящих в набор текущего открытого персонажа. */
    val currentCharacterSpellIds: Set<String> = emptySet(),
    /** id подготовленных заклинаний текущего персонажа. */
    val currentCharacterPreparedIds: Set<String> = emptySet(),
    val combos: List<Combo> = emptyList(),
    val comboSteps: List<ComboStep> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
    val noteBlocks: List<NoteBlock> = emptyList(),
    /** Черты текущего персонажа. */
    val feats: List<CharacterFeat> = emptyList(),
    /** Общая библиотека черт. */
    val libraryFeats: List<Feat> = emptyList(),
    /** id черт, входящих в набор текущего персонажа. */
    val currentCharacterFeatIds: Set<String> = emptySet(),
    /** Последний бросок d20: показывается небольшой плашкой слева внизу. */
    val lastD20Roll: D20RollResult? = null,
    val message: String? = null,
)

class SpellBookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpellBookRepository(application)
    private val prefs = AppPreferences(application)

    var uiState by mutableStateOf(SpellBookUiState())
        private set

    /** Параметры отображения списка (общие для библиотеки и экрана персонажа). */
    var listQuery by mutableStateOf("")
        private set
    var listSort by mutableStateOf(SpellSort.DATE_ADDED)
        private set
    var listFilters by mutableStateOf(SpellFilters())
        private set

    /**
     * Показывать ли только подготовленные (главная вкладка) или все известные заклинания.
     * Хранится в ВМ, чтобы переживать переход к деталям заклинания и обратно.
     */
    var showPreparedOnly by mutableStateOf(true)
        private set

    /**
     * Сохранённая позиция прокрутки списка (чтобы вернуться на то же место после деталей).
     * Обычные поля (не Compose-state): читаются как «начальные» при пересоздании экрана,
     * чтобы частая запись при прокрутке не вызывала рекомпозиций.
     */
    var listScrollIndex: Int = 0
        private set
    var listScrollOffset: Int = 0
        private set

    /** Джобы подписок на данные текущего персонажа. */
    private var characterSpellsJob: Job? = null
    private var combosJob: Job? = null
    private var comboStepsJob: Job? = null
    private var inventoryJob: Job? = null
    private var notesJob: Job? = null
    private var featsJob: Job? = null
    private var characterFeatIdsJob: Job? = null
    /** Не позволяет более старой записи порядка завершиться после более новой. */
    private val inventoryReorderMutex = Mutex()
    private val comboReorderMutex = Mutex()
    private val resourceReorderMutex = Mutex()
    private val noteReorderMutex = Mutex()
    private val featReorderMutex = Mutex()

    var editingCombo by mutableStateOf<Combo?>(null)
        private set
    var editingComboStepIds by mutableStateOf<List<String>>(emptyList())
        private set
    var comboRollResult by mutableStateOf<ComboRollResult?>(null)
        private set

    /** Последняя корневая страница раздела «Персонажи»: список или конкретный персонаж. */
    private var lastCharactersScreen: Screen = Screen.Characters

    /**
     * id персонажа, в контексте которого создаётся/импортируется заклинание.
     * Если задан, после сохранения заклинание автоматически добавляется в его набор.
     */
    private var pendingCharacterId: String? = null

    init {
        viewModelScope.launch {
            repository.migrateLegacyIfNeeded()
            restoreLastCharacter()
        }
        repository.observeCharacters()
            .onEach { uiState = uiState.copy(characters = it) }
            .launchIn(viewModelScope)
        repository.observeSpellCounts()
            .onEach { counts ->
                uiState = uiState.copy(spellCounts = counts.associate { it.characterId to it.count })
            }
            .launchIn(viewModelScope)
        repository.observeAllSpells()
            .onEach { uiState = uiState.copy(librarySpells = it) }
            .launchIn(viewModelScope)
        repository.observeAllFeats()
            .onEach { uiState = uiState.copy(libraryFeats = it) }
            .launchIn(viewModelScope)
    }

    /** При старте открываем набор последнего выбранного персонажа, если он ещё существует. */
    private suspend fun restoreLastCharacter() {
        val lastId = prefs.lastCharacterId ?: return
        val character = repository.getCharacter(lastId)
        if (character != null) {
            observeCharacterSpells(lastId)
            val characterScreen = Screen.CharacterSpells(lastId)
            lastCharactersScreen = characterScreen
            uiState = uiState.copy(
                tab = Tab.CHARACTERS,
                screen = characterScreen,
            )
        } else {
            prefs.lastCharacterId = null
        }
    }

    /** Джоба подписки на подготовленные заклинания текущего персонажа. */
    private var characterPreparedJob: Job? = null

    private fun observeCharacterSpells(characterId: String) {
        characterSpellsJob?.cancel()
        characterSpellsJob = repository.observeSpellIdsForCharacter(characterId)
            .onEach { uiState = uiState.copy(currentCharacterSpellIds = it.toSet()) }
            .launchIn(viewModelScope)
        characterPreparedJob?.cancel()
        characterPreparedJob = repository.observePreparedSpellIdsForCharacter(characterId)
            .onEach { uiState = uiState.copy(currentCharacterPreparedIds = it.toSet()) }
            .launchIn(viewModelScope)
        combosJob?.cancel()
        combosJob = repository.observeCombos(characterId)
            .onEach { uiState = uiState.copy(combos = it) }
            .launchIn(viewModelScope)
        comboStepsJob?.cancel()
        comboStepsJob = repository.observeComboSteps(characterId)
            .onEach { uiState = uiState.copy(comboSteps = it) }
            .launchIn(viewModelScope)
        inventoryJob?.cancel()
        inventoryJob = repository.observeInventoryItems(characterId)
            .onEach { uiState = uiState.copy(inventoryItems = it) }
            .launchIn(viewModelScope)
        notesJob?.cancel()
        notesJob = repository.observeNoteBlocks(characterId)
            .onEach { uiState = uiState.copy(noteBlocks = it) }
            .launchIn(viewModelScope)
        featsJob?.cancel()
        featsJob = repository.observeFeatsForCharacter(characterId)
            .onEach { uiState = uiState.copy(feats = it) }
            .launchIn(viewModelScope)
        characterFeatIdsJob?.cancel()
        characterFeatIdsJob = repository.observeFeatIdsForCharacter(characterId)
            .onEach { uiState = uiState.copy(currentCharacterFeatIds = it.toSet()) }
            .launchIn(viewModelScope)
    }

    // region Список: поиск / сортировка / фильтры

    fun updateListQuery(query: String) { listQuery = query }
    fun updateListSort(sort: SpellSort) { listSort = sort }
    fun updateListFilters(filters: SpellFilters) { listFilters = filters }

    /** Переключает вкладку «Подготовленные / Все известные» (сбрасывает прокрутку). */
    fun changePreparedTab(value: Boolean) {
        if (showPreparedOnly != value) {
            showPreparedOnly = value
            resetListScroll()
        }
    }

    /** Запоминает позицию прокрутки перед переходом к деталям заклинания. */
    fun saveListScroll(index: Int, offset: Int) {
        listScrollIndex = index
        listScrollOffset = offset
    }

    /**
     * Позиция панели разделов. Панель пересоздаётся на каждом экране,
     * поэтому её прокрутка хранится здесь, а не в композиции.
     */
    var sectionsScrollIndex: Int = 0
        private set
    var sectionsScrollOffset: Int = 0
        private set

    fun saveSectionsScroll(index: Int, offset: Int) {
        sectionsScrollIndex = index
        sectionsScrollOffset = offset
    }

    private fun resetListScroll() {
        listScrollIndex = 0
        listScrollOffset = 0
    }

    private fun resetListControls() {
        listQuery = ""
        listFilters = SpellFilters()
        resetListScroll()
    }

    // endregion

    // region Навигация

    fun selectTab(tab: Tab) {
        uiState = when (tab) {
            Tab.LIBRARY -> uiState.copy(tab = tab, screen = Screen.Library)
            Tab.CHARACTERS -> {
                // Восстанавливаем последнюю страницу самого раздела: список либо персонажа.
                val target = when (val saved = lastCharactersScreen) {
                    is Screen.CharacterSpells -> saved.takeIf { characterScreen ->
                        uiState.characters.any { it.id == characterScreen.characterId }
                    } ?: Screen.Characters
                    else -> Screen.Characters
                }
                lastCharactersScreen = target
                uiState.copy(tab = tab, screen = target)
            }
        }
        resetListControls()
    }

    fun openCharacters() {
        lastCharactersScreen = Screen.Characters
        uiState = uiState.copy(tab = Tab.CHARACTERS, screen = Screen.Characters)
    }

    /**
     * Открывает экран заклинаний персонажа. [resetView] = true (при выборе персонажа)
     * сбрасывает поиск/фильтры/прокрутку и открывает главную вкладку «Подготовленные».
     * При возврате из деталей (resetView = false) состояние списка сохраняется.
     */
    fun openCharacterSpells(characterId: String, resetView: Boolean = true) {
        val changingCharacter = (uiState.screen as? Screen.CharacterSpells)?.characterId != characterId
        prefs.lastCharacterId = characterId
        if (changingCharacter) observeCharacterSpells(characterId)
        if (resetView || changingCharacter) {
            resetListControls()
            showPreparedOnly = true
        }
        val characterScreen = Screen.CharacterSpells(characterId)
        lastCharactersScreen = characterScreen
        uiState = uiState.copy(tab = Tab.CHARACTERS, screen = characterScreen)
    }

    fun openLibrary() {
        uiState = uiState.copy(tab = Tab.LIBRARY, screen = Screen.Library)
    }

    /** Экран, с которого открыли детали заклинания (чтобы вернуться именно туда). */
    private var detailsOrigin: Screen? = null

    fun openDetails(spellId: String) {
        detailsOrigin = uiState.screen
        uiState = uiState.copy(screen = Screen.Details(spellId))
    }

    /**
     * Возврат из деталей заклинания. Если детали открывали с не-списочного экрана
     * (например, переподготовки) — возвращаемся именно туда, иначе к списку вкладки.
     */
    fun navigateBackFromDetails() {
        when (val origin = detailsOrigin) {
            is Screen.PrepareSpells, is Screen.AddSpells, is Screen.SpellSlots -> {
                detailsOrigin = null
                uiState = uiState.copy(screen = origin)
            }
            else -> navigateBackToList()
        }
    }

    /** Возврат к корневому списку текущей вкладки. */
    fun navigateBackToList() {
        val screen = if (uiState.tab == Tab.LIBRARY) {
            Screen.Library
        } else {
            val lastId = prefs.lastCharacterId
            if (lastId != null) Screen.CharacterSpells(lastId) else Screen.Characters
        }
        uiState = uiState.copy(screen = screen)
    }

    /**
     * Открывает форму создания заклинания. Если [forCharacterId] задан, после сохранения
     * заклинание попадёт и в библиотеку, и в набор этого персонажа.
     */
    fun openCreateSpellForm(forCharacterId: String? = null) {
        pendingCharacterId = forCharacterId
        uiState = uiState.copy(screen = Screen.SpellForm(spellId = null))
    }

    /** Запоминает персонажа, для которого выполняется импорт JSON (см. [importSpellJson]). */
    fun prepareImportForCharacter(characterId: String?) {
        pendingCharacterId = characterId
    }

    fun openEditSpellForm(spellId: String) {
        uiState = uiState.copy(screen = Screen.SpellForm(spellId = spellId))
    }

    fun openCreateCharacterForm() {
        uiState = uiState.copy(screen = Screen.CharacterForm(characterId = null))
    }

    fun openEditCharacterForm(characterId: String) {
        uiState = uiState.copy(screen = Screen.CharacterForm(characterId = characterId))
    }

    fun openAddSpells(characterId: String) {
        uiState = uiState.copy(screen = Screen.AddSpells(characterId))
    }

    fun openPrepareSpells(characterId: String) {
        uiState = uiState.copy(screen = Screen.PrepareSpells(characterId))
    }

    fun openSpellSlots(characterId: String) {
        uiState = uiState.copy(screen = Screen.SpellSlots(characterId))
    }

    fun openInventory(characterId: String) {
        uiState = uiState.copy(screen = Screen.Inventory(characterId))
    }

    fun openStats(characterId: String) {
        uiState = uiState.copy(screen = Screen.Stats(characterId), lastD20Roll = null)
    }

    fun openNotes(characterId: String) {
        uiState = uiState.copy(screen = Screen.Notes(characterId))
    }

    fun openFeats(characterId: String) {
        uiState = uiState.copy(screen = Screen.Feats(characterId))
    }

    fun openAddFeats(characterId: String) {
        uiState = uiState.copy(screen = Screen.AddFeats(characterId))
    }

    // region Черты

    /** Создаёт черту в библиотеке и сразу добавляет её персонажу. */
    fun addFeat(characterId: String, name: String, description: String) {
        if (name.isBlank()) {
            uiState = uiState.copy(message = "Укажите название черты")
            return
        }
        viewModelScope.launch {
            val feat = Feat(name = name.trim(), description = description.trim())
            repository.saveFeat(feat)
            repository.addFeatToCharacter(characterId, feat.id)
        }
    }

    fun saveFeat(feat: Feat) {
        viewModelScope.launch { repository.saveFeat(feat) }
    }

    /** Удаляет черту только у персонажа; в библиотеке она остаётся. */
    fun removeFeatFromCharacter(characterId: String, featId: String) {
        viewModelScope.launch { repository.removeFeatFromCharacter(characterId, featId) }
    }

    /** Удаляет черту из библиотеки и у всех персонажей. */
    fun deleteFeat(featId: String) {
        viewModelScope.launch { repository.deleteFeat(featId) }
    }

    fun toggleFeatCollapsed(characterId: String, featId: String, collapsed: Boolean) {
        viewModelScope.launch { repository.setFeatCollapsed(characterId, featId, collapsed) }
    }

    fun toggleFeatForCharacter(characterId: String, featId: String, add: Boolean) {
        viewModelScope.launch {
            if (add) repository.addFeatToCharacter(characterId, featId)
            else repository.removeFeatFromCharacter(characterId, featId)
        }
    }

    fun reorderFeats(characterId: String, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val snapshot = orderedIds.toList()
        viewModelScope.launch {
            featReorderMutex.withLock { repository.reorderFeats(characterId, snapshot) }
        }
    }

    /**
     * Загружает черту с dnd.su в общую библиотеку.
     * Если задан [characterId], черта сразу добавляется этому персонажу.
     */
    fun importFeatFromDndSu(characterId: String?, url: String) {
        viewModelScope.launch {
            uiState = uiState.copy(message = "Загрузка черты…")
            val parsed = runCatching { DndSuLoader.loadFeat(url) }.getOrElse { error ->
                uiState = uiState.copy(
                    message = (error as? DndSuException)?.message
                        ?: "Не удалось загрузить черту: ${error.localizedMessage ?: "ошибка"}",
                )
                return@launch
            }
            // Повторная загрузка обновляет текст, а не создаёт дубль.
            val existing = repository.findFeatByName(parsed.name)
            val feat = existing?.copy(description = parsed.description, source = url.trim())
                ?: Feat(name = parsed.name, description = parsed.description, source = url.trim())
            repository.saveFeat(feat)

            val target = characterId ?: pendingCharacterId
            if (target != null) repository.addFeatToCharacter(target, feat.id)

            uiState = uiState.copy(
                message = when {
                    target != null -> "Черта «${parsed.name}» добавлена персонажу"
                    existing == null -> "Черта «${parsed.name}» добавлена в библиотеку"
                    else -> "Черта «${parsed.name}» обновлена"
                },
            )
        }
    }

    /**
     * Разбирает ссылку из «Поделиться»: адрес черты грузится как черта,
     * остальное — как заклинание.
     */
    fun importFromDndSuUrl(url: String) {
        if (DndSuLoader.isFeatUrl(url)) importFeatFromDndSu(characterId = null, url = url)
        else importFromDndSu(url)
    }

    // endregion

    // region Заметки

    fun addNoteBlock(characterId: String, title: String) {
        val block = NoteBlock(
            characterId = characterId,
            title = title.trim().ifBlank { DEFAULT_NOTE_TITLE },
            paragraphs = listOf(NoteParagraph()),
        )
        viewModelScope.launch { repository.saveNoteBlock(block) }
    }

    /** Сохраняет блок целиком: заголовок, абзацы и состояние сворачивания. */
    fun saveNoteBlock(block: NoteBlock) {
        viewModelScope.launch { repository.saveNoteBlock(block) }
    }

    fun deleteNoteBlock(blockId: String) {
        viewModelScope.launch { repository.deleteNoteBlock(blockId) }
    }

    fun reorderNoteBlocks(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val snapshot = orderedIds.toList()
        viewModelScope.launch {
            noteReorderMutex.withLock { repository.reorderNoteBlocks(snapshot) }
        }
    }

    // endregion

    fun openCombos(characterId: String) {
        uiState = uiState.copy(screen = Screen.Combos(characterId))
    }

    fun openStepLibrary(characterId: String) {
        uiState = uiState.copy(screen = Screen.StepLibrary(characterId))
    }

    fun openComboEditor(characterId: String, comboId: String? = null) {
        viewModelScope.launch {
            editingCombo = comboId?.let { repository.getCombo(it) } ?: Combo(characterId = characterId, name = "")
            editingComboStepIds = comboId?.let { repository.getComboWithSteps(it)?.steps?.map(ComboStep::id) }.orEmpty()
            uiState = uiState.copy(screen = Screen.ComboEditor(characterId, comboId))
        }
    }

    // endregion

    fun getSpell(spellId: String?): Spell? =
        uiState.librarySpells.firstOrNull { it.id == spellId }

    fun getCharacter(characterId: String?): Character? =
        uiState.characters.firstOrNull { it.id == characterId }

    /** Заклинания набора персонажа с учётом текущего состава (для экрана персонажа). */
    fun spellsForCharacter(characterId: String): List<Spell> {
        val ids = uiState.currentCharacterSpellIds
        return uiState.librarySpells.filter { it.id in ids }
    }

    /**
     * Подготовленные заклинания персонажа. Заговоры (уровень 0) не подготавливаются,
     * но всегда доступны — поэтому включаем все известные заговоры персонажа.
     */
    fun preparedSpellsForCharacter(characterId: String): List<Spell> {
        val prepared = uiState.currentCharacterPreparedIds
        val known = uiState.currentCharacterSpellIds
        return uiState.librarySpells.filter {
            it.id in prepared || (it.level == 0 && it.id in known)
        }
    }

    // region Заклинания

    fun saveSpell(spell: Spell) {
        // Оборачиваем кости (1d6, 2d8 и т.п.) в LSS-токены, чтобы они стали кликабельными.
        val normalized = spell.copy(description = DiceRoller.wrapDiceTokens(spell.description))
        val characterId = pendingCharacterId
        pendingCharacterId = null
        viewModelScope.launch {
            // Проверка уникальности: другое заклинание с таким же названием блокирует сохранение.
            val duplicate = repository.findSpellByName(normalized.name)
            if (duplicate != null && duplicate.id != normalized.id) {
                pendingCharacterId = characterId // возвращаем контекст, пользователь остаётся в форме
                uiState = uiState.copy(
                    message = "Заклинание «${normalized.name}» уже есть в библиотеке",
                )
                return@launch
            }
            repository.upsertSpell(normalized)
            val cantripError = characterId != null &&
                !addSpellToCharacterChecked(characterId, normalized)
            uiState = uiState.copy(
                screen = Screen.Details(normalized.id),
                message = if (cantripError) cantripLimitMessage(characterId!!) else "Заклинание сохранено",
            )
        }
    }

    /**
     * Добавляет заклинание персонажу с учётом лимита заговоров.
     * Возвращает false, если добавить не удалось из-за лимита заговоров.
     */
    private suspend fun addSpellToCharacterChecked(characterId: String, spell: Spell): Boolean {
        val max = getCharacter(characterId)?.maxCantrips ?: 0
        val result = repository.tryAddSpellToCharacter(characterId, spell.id, spell.level, max)
        return result != AddSpellResult.CANTRIP_LIMIT_REACHED
    }

    private fun cantripLimitMessage(characterId: String): String {
        val max = getCharacter(characterId)?.maxCantrips ?: 0
        return "Заклинание сохранено в библиотеку, но достигнут лимит заговоров ($max)"
    }

    fun deleteSpell(spellId: String) {
        viewModelScope.launch {
            repository.deleteSpell(spellId)
            uiState = uiState.copy(
                screen = if (uiState.tab == Tab.LIBRARY) Screen.Library else Screen.Characters,
                message = "Заклинание удалено",
            )
        }
    }

    fun exportSpellJson(spellId: String): String? = getSpell(spellId)?.let(SpellLssCodec::encodeSpell)

    fun importSpellJson(json: String) {
        val imported = runCatching { SpellLssCodec.decodeSpell(json) }
            .getOrElse {
                uiState = uiState.copy(
                    message = "Не удалось прочитать JSON: ${it.localizedMessage ?: "ошибка формата"}",
                )
                return
            }
        if (imported.name.isBlank()) {
            uiState = uiState.copy(message = "В JSON нет названия заклинания")
            return
        }
        val characterId = pendingCharacterId
        pendingCharacterId = null
        viewModelScope.launch {
            saveUniqueOrReuse(
                spell = imported,
                characterId = characterId,
                newMessage = "Заклинание импортировано",
            )
        }
    }

    /**
     * Сохраняет заклинание, проверяя уникальность по названию. Если такое уже есть в
     * библиотеке — переиспользуем его (без дубля) и при необходимости добавляем в набор персонажа.
     */
    private suspend fun saveUniqueOrReuse(spell: Spell, characterId: String?, newMessage: String) {
        val normalized = spell.copy(description = DiceRoller.wrapDiceTokens(spell.description))
        val existing = repository.findSpellByName(normalized.name)
        if (existing != null) {
            val limited = characterId != null && !addSpellToCharacterChecked(characterId, existing)
            uiState = uiState.copy(
                screen = Screen.Details(existing.id),
                message = when {
                    limited -> "Заклинание уже есть. ${cantripLimitMessage(characterId!!)}"
                    characterId != null -> "Заклинание уже есть — добавлено персонажу"
                    else -> "Заклинание «${normalized.name}» уже есть в библиотеке"
                },
            )
            return
        }
        repository.upsertSpell(normalized)
        val cantripError = characterId != null && !addSpellToCharacterChecked(characterId, normalized)
        uiState = uiState.copy(
            screen = Screen.Details(normalized.id),
            message = if (cantripError) cantripLimitMessage(characterId!!) else newMessage,
        )
    }

    /**
     * Загружает заклинание по ссылке с dnd.su, сохраняет в библиотеку и, если задан
     * [pendingCharacterId] (создание из контекста персонажа), добавляет его в набор персонажа.
     */
    fun importFromDndSu(url: String) {
        if (!DndSuLoader.isSpellUrl(url)) {
            uiState = uiState.copy(message = "Ссылка должна вести на заклинание с сайта dnd.su")
            return
        }
        val characterId = pendingCharacterId
        pendingCharacterId = null
        uiState = uiState.copy(message = "Загрузка заклинания…")
        viewModelScope.launch {
            val spell = try {
                DndSuLoader.load(url)
            } catch (e: DndSuException) {
                uiState = uiState.copy(message = e.message)
                return@launch
            } catch (e: Exception) {
                uiState = uiState.copy(
                    message = "Не удалось загрузить заклинание: ${e.localizedMessage ?: "неизвестная ошибка"}",
                )
                return@launch
            }
            if (spell.name.isBlank()) {
                uiState = uiState.copy(message = "Не удалось распознать заклинание на странице")
                return@launch
            }
            saveUniqueOrReuse(
                spell = spell,
                characterId = characterId,
                newMessage = "Заклинание загружено с dnd.su",
            )
        }
    }

    // endregion

    // region Персонажи и связи

    fun saveCharacter(character: Character) {
        // Редактирование (открыто с экрана заклинаний) — возвращаемся к ним, создание — к списку персонажей.
        val editing = (uiState.screen as? Screen.CharacterForm)?.characterId != null
        viewModelScope.launch {
            // Уровень и характеристики могли измениться — пересчитываем ресурсы на формулах.
            repository.upsertCharacter(character.withRecalculatedResources())
            val target = if (editing) Screen.CharacterSpells(character.id) else Screen.Characters
            uiState = uiState.copy(screen = target, message = "Персонаж сохранён")
        }
    }

    /** Выгружает лист персонажа в формате LSS; null — персонаж не найден. */
    fun exportCharacterJson(characterId: String): String? =
        getCharacter(characterId)?.let(CharacterLssCodec::encode)

    /**
     * Загружает лист персонажа из JSON формата LSS.
     *
     * @param characterId если задан, лист применяется к существующему персонажу,
     * иначе создаётся новый.
     */
    fun importCharacterJson(json: String, characterId: String? = null) {
        val existing = characterId?.let(::getCharacter)
        val imported = runCatching { CharacterLssCodec.decode(json, existing) }
            .getOrElse {
                uiState = uiState.copy(
                    message = "Не удалось прочитать лист: ${it.localizedMessage ?: "ошибка формата"}",
                )
                return
            }
        viewModelScope.launch {
            repository.upsertCharacter(imported)
            val message = if (existing != null) "Лист персонажа обновлён" else "Персонаж загружен"
            openCharacterSpells(imported.id)
            uiState = uiState.copy(message = message)
        }
    }

    /** Возврат с формы персонажа туда, откуда её открыли (без сохранения). */
    fun exitCharacterForm() {
        val editingId = (uiState.screen as? Screen.CharacterForm)?.characterId
        if (editingId != null) openCharacterSpells(editingId, resetView = false) else openCharacters()
    }

    fun deleteCharacter(characterId: String) {
        viewModelScope.launch {
            repository.deleteCharacter(characterId)
            if (prefs.lastCharacterId == characterId) prefs.lastCharacterId = null
            uiState = uiState.copy(screen = Screen.Characters, message = "Персонаж удалён")
        }
    }

    fun addSpellToCurrentCharacter(characterId: String, spellId: String) {
        viewModelScope.launch { repository.addSpellToCharacter(characterId, spellId) }
    }

    fun removeSpellFromCharacter(characterId: String, spellId: String) {
        viewModelScope.launch { repository.removeSpellFromCharacter(characterId, spellId) }
    }

    fun toggleSpellForCharacter(characterId: String, spellId: String, add: Boolean) {
        viewModelScope.launch {
            if (add) {
                val spell = getSpell(spellId) ?: return@launch
                if (!addSpellToCharacterChecked(characterId, spell)) {
                    uiState = uiState.copy(message = cantripLimitMessage(characterId))
                }
            } else {
                repository.removeSpellFromCharacter(characterId, spellId)
            }
        }
    }

    /**
     * Меняет подготовку заклинания. Если превышен лимит подготовленных — показывает сообщение.
     */
    fun setSpellPrepared(characterId: String, spellId: String, prepared: Boolean) {
        val max = getCharacter(characterId)?.maxPreparedSpells ?: 0
        viewModelScope.launch {
            val ok = repository.setSpellPrepared(characterId, spellId, prepared, max)
            if (!ok) {
                uiState = uiState.copy(message = "Достигнут лимит подготовленных заклинаний ($max)")
            }
        }
    }

    /** Помечает одну ячейку уровня [level] как потраченную. */
    fun useSpellSlot(characterId: String, level: Int) {
        val character = getCharacter(characterId) ?: return
        if (character.availableSlots(level) <= 0) return
        val used = character.spellSlotsUsed.toMutableMap()
        used[level] = (used[level] ?: 0) + 1
        viewModelScope.launch { repository.upsertCharacter(character.copy(spellSlotsUsed = used)) }
    }

    /** Восстанавливает одну потраченную ячейку уровня [level]. */
    fun restoreSpellSlot(characterId: String, level: Int) {
        val character = getCharacter(characterId) ?: return
        val current = character.spellSlotsUsed[level] ?: 0
        if (current <= 0) return
        val used = character.spellSlotsUsed.toMutableMap()
        used[level] = current - 1
        viewModelScope.launch { repository.upsertCharacter(character.copy(spellSlotsUsed = used)) }
    }

    /** Восстанавливает все ячейки заклинаний. */
    fun restoreAllSlots(characterId: String) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch { repository.upsertCharacter(character.copy(spellSlotsUsed = emptyMap())) }
    }

    /** Добавляет новый ресурс полностью восполненным. */
    fun addCharacterResource(
        characterId: String,
        name: String,
        description: String,
        maximum: Int,
        maximumFormula: String = "",
    ) {
        val character = getCharacter(characterId) ?: return
        // Формула задаёт максимум автоматически, поэтому число вводить не обязательно.
        val resolved = resolveResourceMaximum(maximumFormula, maximum, character)
        if (name.isBlank() || resolved <= 0) {
            uiState = uiState.copy(message = "Укажите название и положительный лимит ресурса")
            return
        }
        val resource = CharacterResource(
            name = name.trim(),
            description = description.trim(),
            current = resolved,
            maximum = resolved,
            maximumFormula = maximumFormula.trim(),
        ).normalized()
        viewModelScope.launch {
            repository.upsertCharacter(character.copy(resources = character.resources + resource))
        }
    }

    /** Изменяет название, описание и максимум, сохраняя потраченное количество ресурса. */
    fun editCharacterResource(
        characterId: String,
        resourceId: String,
        name: String,
        description: String,
        maximum: Int,
        maximumFormula: String = "",
    ) {
        val character = getCharacter(characterId) ?: return
        val resolved = resolveResourceMaximum(maximumFormula, maximum, character)
        if (name.isBlank() || resolved <= 0) {
            uiState = uiState.copy(message = "Укажите название и положительный лимит ресурса")
            return
        }
        updateCharacterResource(characterId, resourceId) { resource ->
            val spent = (resource.maximum - resource.current).coerceAtLeast(0)
            resource.copy(
                name = name.trim(),
                description = description.trim(),
                maximum = resolved,
                maximumFormula = maximumFormula.trim(),
                current = (resolved - spent).coerceAtLeast(0),
            )
        }
    }

    /** Формула имеет приоритет над введённым вручную числом. */
    private fun resolveResourceMaximum(formula: String, fallback: Int, character: Character): Int =
        formula.takeIf { it.isNotBlank() }
            ?.let { StatFormula.evaluateValue(it, character) }
            ?: fallback

    /** Расходует одну единицу пользовательского ресурса. */
    fun useCharacterResource(characterId: String, resourceId: String) =
        updateCharacterResource(characterId, resourceId) { resource ->
            resource.copy(current = (resource.current - 1).coerceAtLeast(0))
        }

    /** Возвращает одну единицу пользовательского ресурса. */
    fun restoreCharacterResourceUnit(characterId: String, resourceId: String) =
        updateCharacterResource(characterId, resourceId) { resource ->
            resource.copy(current = (resource.current + 1).coerceAtMost(resource.maximum))
        }

    /** Полностью восполняет один пользовательский ресурс. */
    fun restoreCharacterResource(characterId: String, resourceId: String) =
        updateCharacterResource(characterId, resourceId) { it.copy(current = it.maximum) }

    /**
     * Сохраняет пользовательский порядок ресурсов после перетаскивания.
     * Отдельное поле порядка не нужно: ресурсы хранятся списком внутри персонажа.
     */
    fun reorderCharacterResources(characterId: String, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val character = getCharacter(characterId) ?: return
        val byId = character.resources.associateBy { it.id }
        val reordered = orderedIds.mapNotNull(byId::get) +
            character.resources.filterNot { it.id in orderedIds }
        if (reordered.size != character.resources.size) return
        viewModelScope.launch {
            resourceReorderMutex.withLock {
                repository.upsertCharacter(character.copy(resources = reordered))
            }
        }
    }

    // region Характеристики, хиты и броски d20

    /**
     * Изменяет текущие хиты. Урон сначала снимает временные хиты,
     * лечение не превышает максимум.
     */
    fun changeHp(characterId: String, delta: Int) {
        val character = getCharacter(characterId) ?: return
        if (delta == 0) return
        val updated = if (delta < 0) {
            val fromTemp = minOf(character.tempHp, -delta)
            val rest = -delta - fromTemp
            character.copy(
                tempHp = character.tempHp - fromTemp,
                currentHp = (character.currentHp - rest).coerceAtLeast(0),
            )
        } else {
            character.copy(currentHp = (character.currentHp + delta).coerceAtMost(character.maxHp))
        }
        viewModelScope.launch { repository.upsertCharacter(updated) }
    }

    /**
     * Записывает временные хиты и максимум за одну операцию.
     * Раздельные вызовы читали один и тот же снимок персонажа, поэтому вторая
     * запись затирала результат первой — временные хиты не сохранялись.
     */
    fun setHpValues(characterId: String, tempHp: Int, maxHp: Int) {
        val character = getCharacter(characterId) ?: return
        val safeMax = maxHp.coerceAtLeast(0)
        viewModelScope.launch {
            repository.upsertCharacter(
                character.copy(
                    tempHp = tempHp.coerceAtLeast(0),
                    maxHp = safeMax,
                    currentHp = character.currentHp.coerceAtMost(safeMax),
                ),
            )
        }
    }

    fun setArmorClass(characterId: String, value: Int) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch {
            repository.upsertCharacter(character.copy(armorClass = value.coerceAtLeast(0)))
        }
    }

    fun setSpeed(characterId: String, value: Int) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch {
            repository.upsertCharacter(character.copy(speed = value.coerceAtLeast(0)))
        }
    }

    fun setAbilityScore(characterId: String, ability: AbilityType, value: Int) {
        val character = getCharacter(characterId) ?: return
        val scores = character.abilityScores + (ability.ordinal to value.coerceIn(1, 30))
        viewModelScope.launch { repository.upsertCharacter(character.copy(abilityScores = scores)) }
    }

    /** Спасброски знают только владение, без экспертизы. */
    fun toggleSaveProficiency(characterId: String, ability: AbilityType) {
        val character = getCharacter(characterId) ?: return
        val next = when (character.saveProficiency(ability)) {
            ProficiencyLevel.NONE -> ProficiencyLevel.PROFICIENT
            else -> ProficiencyLevel.NONE
        }
        val updated = character.saveProficiencies.toMutableMap().apply {
            if (next == ProficiencyLevel.NONE) remove(ability.ordinal) else put(ability.ordinal, next.multiplier)
        }
        viewModelScope.launch { repository.upsertCharacter(character.copy(saveProficiencies = updated)) }
    }

    /** Навыки переключаются по кругу: нет → владение → экспертиза. */
    fun cycleSkillProficiency(characterId: String, skill: SkillType) {
        val character = getCharacter(characterId) ?: return
        val next = when (character.skillProficiency(skill)) {
            ProficiencyLevel.NONE -> ProficiencyLevel.PROFICIENT
            ProficiencyLevel.PROFICIENT -> ProficiencyLevel.EXPERTISE
            ProficiencyLevel.EXPERTISE -> ProficiencyLevel.NONE
        }
        val updated = character.skillProficiencies.toMutableMap().apply {
            if (next == ProficiencyLevel.NONE) remove(skill.ordinal) else put(skill.ordinal, next.multiplier)
        }
        viewModelScope.launch { repository.upsertCharacter(character.copy(skillProficiencies = updated)) }
    }

    /** Бросает d20 и показывает результат всплывающей плашкой. */
    fun rollD20(title: String, kind: RollKind, bonus: Int) {
        uiState = uiState.copy(
            lastD20Roll = D20RollResult(
                title = title,
                kind = kind,
                roll = (1..D20_SIDES).random(),
                bonus = bonus,
            ),
        )
    }

    fun dismissD20Roll() {
        uiState = uiState.copy(lastD20Roll = null)
    }

    // endregion

    /** Удаляет пользовательский ресурс. */
    fun deleteCharacterResource(characterId: String, resourceId: String) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch {
            repository.upsertCharacter(
                character.copy(resources = character.resources.filterNot { it.id == resourceId }),
            )
        }
    }

    /** Восстанавливает ячейки и вообще все пользовательские ресурсы персонажа. */
    fun restoreAllResources(characterId: String) {
        val character = getCharacter(characterId) ?: return
        val restored = character.resources.map { it.copy(current = it.maximum) }
        viewModelScope.launch {
            repository.upsertCharacter(
                character.copy(spellSlotsUsed = emptyMap(), resources = restored),
            )
        }
    }

    private fun updateCharacterResource(
        characterId: String,
        resourceId: String,
        transform: (CharacterResource) -> CharacterResource,
    ) {
        val character = getCharacter(characterId) ?: return
        val updated = character.resources.map { resource ->
            if (resource.id == resourceId) transform(resource).normalized() else resource
        }
        viewModelScope.launch { repository.upsertCharacter(character.copy(resources = updated)) }
    }

    // endregion

    // region Комбинации

    fun setEditingComboName(name: String) {
        editingCombo = editingCombo?.copy(name = name)
    }

    fun toggleStepInEditingCombo(stepId: String) {
        editingComboStepIds = if (stepId in editingComboStepIds) {
            editingComboStepIds - stepId
        } else {
            editingComboStepIds + stepId
        }
    }

    fun moveEditingComboStep(stepId: String, direction: Int) {
        val list = editingComboStepIds.toMutableList()
        val index = list.indexOf(stepId)
        val target = index + direction
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        editingComboStepIds = list
    }

    fun saveEditingCombo() {
        val combo = editingCombo ?: return
        if (combo.name.isBlank()) {
            uiState = uiState.copy(message = "Укажите название комбинации")
            return
        }
        viewModelScope.launch {
            repository.saveCombo(combo.copy(name = combo.name.trim()), editingComboStepIds)
            uiState = uiState.copy(screen = Screen.Combos(combo.characterId), message = "Комбинация сохранена")
        }
    }

    fun saveComboStep(step: ComboStep, addToCurrentCombo: Boolean = false) {
        if (step.name.isBlank()) {
            uiState = uiState.copy(message = "Укажите название шага")
            return
        }
        viewModelScope.launch {
            repository.saveComboStep(step.copy(name = step.name.trim()))
            if (addToCurrentCombo && step.id !in editingComboStepIds) {
                editingComboStepIds = editingComboStepIds + step.id
            }
            uiState = uiState.copy(message = "Шаг сохранён")
        }
    }

    fun deleteComboStep(stepId: String) {
        viewModelScope.launch {
            repository.deleteComboStep(stepId)
            editingComboStepIds = editingComboStepIds - stepId
            uiState = uiState.copy(message = "Шаг удалён")
        }
    }

    /** Сохраняет пользовательский порядок комбинаций после перетаскивания. */
    fun reorderCombos(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val snapshot = orderedIds.toList()
        viewModelScope.launch {
            comboReorderMutex.withLock {
                repository.reorderCombos(snapshot)
            }
        }
    }

    fun deleteCombo(comboId: String, characterId: String) {
        viewModelScope.launch {
            repository.deleteCombo(comboId)
            uiState = uiState.copy(screen = Screen.Combos(characterId), message = "Комбинация удалена")
        }
    }

    fun rollCombo(characterId: String, comboId: String, mode: ComboRollMode) {
        viewModelScope.launch {
            val combo = repository.getComboWithSteps(comboId)
            if (combo == null || combo.steps.isEmpty()) {
                uiState = uiState.copy(message = "Добавьте хотя бы один шаг в комбинацию")
                return@launch
            }
            // Формулы шагов вычисляются по текущим характеристикам персонажа.
            comboRollResult = ComboRoller.roll(combo, mode, getCharacter(characterId))
            uiState = uiState.copy(screen = Screen.ComboResult(characterId, comboId))
        }
    }

    // endregion

    // region Инвентарь

    fun saveInventoryItem(item: InventoryItem) {
        if (item.name.isBlank() || item.quantity <= 0) {
            uiState = uiState.copy(message = "Укажите название и положительное количество")
            return
        }
        val normalized = item.normalized()
        if (normalized.attuned && !canAttune(normalized.characterId, normalized.id)) {
            val limit = getCharacter(normalized.characterId)?.maxAttunedItems ?: 0
            uiState = uiState.copy(message = "Достигнут лимит настройки ($limit)")
            return
        }
        viewModelScope.launch {
            repository.saveInventoryItem(normalized)
            uiState = uiState.copy(message = "Предмет сохранён")
        }
    }

    fun changeInventoryQuantity(itemId: String, delta: Int) {
        viewModelScope.launch {
            val item = repository.getInventoryItem(itemId) ?: return@launch
            repository.saveInventoryItem(item.copy(quantity = (item.quantity + delta).coerceAtLeast(1)))
        }
    }

    fun setInventoryQuantity(itemId: String, quantity: Int) {
        if (quantity <= 0) return
        viewModelScope.launch {
            val item = repository.getInventoryItem(itemId) ?: return@launch
            repository.saveInventoryItem(item.copy(quantity = quantity))
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteInventoryItem(itemId)
            uiState = uiState.copy(message = "Предмет удалён")
        }
    }

    /** Сохраняет пользовательский порядок предметов текущей вкладки. */
    fun reorderInventoryItems(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val snapshot = orderedIds.toList()
        viewModelScope.launch {
            inventoryReorderMutex.withLock {
                repository.reorderInventoryItems(snapshot)
            }
        }
    }

    fun toggleItemAttunement(itemId: String) {
        viewModelScope.launch {
            val item = repository.getInventoryItem(itemId) ?: return@launch
            if (!item.requiresAttunement) return@launch
            if (!item.attuned && !canAttune(item.characterId, item.id)) {
                val limit = getCharacter(item.characterId)?.maxAttunedItems ?: 0
                uiState = uiState.copy(message = "Достигнут лимит настройки ($limit)")
                return@launch
            }
            repository.saveInventoryItem(item.copy(attuned = !item.attuned))
        }
    }

    fun updateAttunementLimit(characterId: String, newLimit: Int) {
        val character = getCharacter(characterId) ?: return
        val attunedCount = uiState.inventoryItems.count { it.characterId == characterId && it.attuned }
        if (newLimit < attunedCount) {
            uiState = uiState.copy(
                message = "Нельзя установить лимит $newLimit: сейчас настроено $attunedCount предметов",
            )
            return
        }
        viewModelScope.launch {
            repository.upsertCharacter(character.copy(maxAttunedItems = newLimit.coerceAtLeast(0)))
        }
    }

    fun updateCoins(characterId: String, changes: Map<CoinType, Int>, subtract: Boolean) {
        val character = getCharacter(characterId) ?: return
        val updated = character.coins.toMutableMap()
        if (subtract && changes.any { (coin, value) -> (updated[coin.ordinal] ?: 0) < value }) {
            uiState = uiState.copy(message = "Недостаточно монет для этой операции")
            return
        }
        changes.forEach { (coin, value) ->
            val delta = if (subtract) -value else value
            updated[coin.ordinal] = ((updated[coin.ordinal] ?: 0) + delta).coerceAtLeast(0)
        }
        viewModelScope.launch { repository.upsertCharacter(character.copy(coins = updated)) }
    }

    fun setCoinAmount(characterId: String, coin: CoinType, amount: Int) {
        val character = getCharacter(characterId) ?: return
        val updated = character.coins.toMutableMap()
        updated[coin.ordinal] = amount.coerceAtLeast(0)
        viewModelScope.launch { repository.upsertCharacter(character.copy(coins = updated)) }
    }

    private fun canAttune(characterId: String, exceptItemId: String): Boolean {
        val limit = getCharacter(characterId)?.maxAttunedItems ?: 0
        if (limit <= 0) return false
        return uiState.inventoryItems.count {
            it.characterId == characterId && it.attuned && it.id != exceptItemId
        } < limit
    }

    // endregion

    fun consumeMessage() {
        uiState = uiState.copy(message = null)
    }
}
