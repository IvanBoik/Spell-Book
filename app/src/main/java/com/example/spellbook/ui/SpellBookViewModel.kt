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
import com.example.spellbook.data.ComboRoller
import com.example.spellbook.data.DndSuLoader
import com.example.spellbook.data.SpellBookRepository
import com.example.spellbook.data.SpellFilters
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.SpellSort
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboRollMode
import com.example.spellbook.data.model.ComboRollResult
import com.example.spellbook.data.model.ComboStep
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
    data class Combos(val characterId: String) : Screen
    data class ComboEditor(val characterId: String, val comboId: String?) : Screen
    data class StepLibrary(val characterId: String) : Screen
    data class ComboResult(val characterId: String, val comboId: String) : Screen
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

    var editingCombo by mutableStateOf<Combo?>(null)
        private set
    var editingComboStepIds by mutableStateOf<List<String>>(emptyList())
        private set
    var comboRollResult by mutableStateOf<ComboRollResult?>(null)
        private set

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
        combosJob?.cancel()
        combosJob = repository.observeCombos(characterId)
            .onEach { uiState = uiState.copy(combos = it) }
            .launchIn(viewModelScope)
        comboStepsJob?.cancel()
        comboStepsJob = repository.observeComboSteps(characterId)
            .onEach { uiState = uiState.copy(comboSteps = it) }
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

    /** Восстанавливает все ячейки заклинаний. */
    fun restoreAllSlots(characterId: String) {
        val character = getCharacter(characterId) ?: return
        viewModelScope.launch { repository.upsertCharacter(character.copy(spellSlotsUsed = emptyMap())) }
    }

    /** Добавляет новый ресурс полностью восполненным. */
    fun addCharacterResource(characterId: String, name: String, maximum: Int) {
        val character = getCharacter(characterId) ?: return
        if (name.isBlank() || maximum <= 0) {
            uiState = uiState.copy(message = "Укажите название и положительный лимит ресурса")
            return
        }
        val resource = CharacterResource(name = name.trim(), current = maximum, maximum = maximum).normalized()
        viewModelScope.launch {
            repository.upsertCharacter(character.copy(resources = character.resources + resource))
        }
    }

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
            comboRollResult = ComboRoller.roll(combo, mode)
            uiState = uiState.copy(screen = Screen.ComboResult(characterId, comboId))
        }
    }

    // endregion

    fun consumeMessage() {
        uiState = uiState.copy(message = null)
    }
}
