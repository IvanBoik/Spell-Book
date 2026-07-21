package com.example.spellbook.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spellbook.data.AddSpellResult
import com.example.spellbook.data.AppPreferences
import com.example.spellbook.data.DndSuException
import com.example.spellbook.data.DndSuLoader
import com.example.spellbook.data.SpellBookRepository
import com.example.spellbook.data.SpellFilters
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.SpellSort
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Spell
import com.example.spellbook.util.DiceRoller
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Две основные вкладки приложения. */
enum class Tab { CHARACTERS, LIBRARY }

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

    /** Джоба подписки на состав набора текущего персонажа (перезапускается при смене). */
    private var characterSpellsJob: Job? = null

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
    }

    /** При старте открываем набор последнего выбранного персонажа, если он ещё существует. */
    private suspend fun restoreLastCharacter() {
        val lastId = prefs.lastCharacterId ?: return
        val character = repository.getCharacter(lastId)
        if (character != null) {
            observeCharacterSpells(lastId)
            uiState = uiState.copy(
                tab = Tab.CHARACTERS,
                screen = Screen.CharacterSpells(lastId),
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
            Tab.CHARACTERS -> uiState.copy(tab = tab, screen = Screen.Characters)
        }
        resetListControls()
    }

    fun openCharacters() {
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
        uiState = uiState.copy(tab = Tab.CHARACTERS, screen = Screen.CharacterSpells(characterId))
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
            repository.upsertCharacter(character)
            val target = if (editing) Screen.CharacterSpells(character.id) else Screen.Characters
            uiState = uiState.copy(screen = target, message = "Персонаж сохранён")
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

    /** Восстанавливает все ячейки (короткий/долгий отдых). */
    fun restoreAllSlots(characterId: String) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch { repository.upsertCharacter(character.copy(spellSlotsUsed = emptyMap())) }
    }

    // endregion

    fun consumeMessage() {
        uiState = uiState.copy(message = null)
    }
}
