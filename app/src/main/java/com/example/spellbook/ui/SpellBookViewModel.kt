package com.example.spellbook.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
}

data class SpellBookUiState(
    val tab: Tab = Tab.CHARACTERS,
    val screen: Screen = Screen.Characters,
    val characters: List<Character> = emptyList(),
    val spellCounts: Map<String, Int> = emptyMap(),
    val librarySpells: List<Spell> = emptyList(),
    /** id заклинаний, входящих в набор текущего открытого персонажа. */
    val currentCharacterSpellIds: Set<String> = emptySet(),
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

    private fun observeCharacterSpells(characterId: String) {
        characterSpellsJob?.cancel()
        characterSpellsJob = repository.observeSpellIdsForCharacter(characterId)
            .onEach { uiState = uiState.copy(currentCharacterSpellIds = it.toSet()) }
            .launchIn(viewModelScope)
    }

    // region Список: поиск / сортировка / фильтры

    fun updateListQuery(query: String) { listQuery = query }
    fun updateListSort(sort: SpellSort) { listSort = sort }
    fun updateListFilters(filters: SpellFilters) { listFilters = filters }

    private fun resetListControls() {
        listQuery = ""
        listFilters = SpellFilters()
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

    fun openCharacterSpells(characterId: String) {
        prefs.lastCharacterId = characterId
        observeCharacterSpells(characterId)
        resetListControls()
        uiState = uiState.copy(tab = Tab.CHARACTERS, screen = Screen.CharacterSpells(characterId))
    }

    fun openLibrary() {
        uiState = uiState.copy(tab = Tab.LIBRARY, screen = Screen.Library)
    }

    fun openDetails(spellId: String) {
        uiState = uiState.copy(screen = Screen.Details(spellId))
    }

    /** Возврат из деталей заклинания к списку той вкладки, откуда пришли. */
    fun navigateBackFromDetails() = navigateBackToList()

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
            if (characterId != null) repository.addSpellToCharacter(characterId, normalized.id)
            uiState = uiState.copy(
                screen = Screen.Details(normalized.id),
                message = "Заклинание сохранено",
            )
        }
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
            if (characterId != null) repository.addSpellToCharacter(characterId, existing.id)
            uiState = uiState.copy(
                screen = Screen.Details(existing.id),
                message = if (characterId != null) {
                    "Заклинание уже есть — добавлено персонажу"
                } else {
                    "Заклинание «${normalized.name}» уже есть в библиотеке"
                },
            )
            return
        }
        repository.upsertSpell(normalized)
        if (characterId != null) repository.addSpellToCharacter(characterId, normalized.id)
        uiState = uiState.copy(
            screen = Screen.Details(normalized.id),
            message = newMessage,
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
        viewModelScope.launch {
            repository.upsertCharacter(character)
            uiState = uiState.copy(screen = Screen.Characters, message = "Персонаж сохранён")
        }
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
            if (add) repository.addSpellToCharacter(characterId, spellId)
            else repository.removeSpellFromCharacter(characterId, spellId)
        }
    }

    // endregion

    fun consumeMessage() {
        uiState = uiState.copy(message = null)
    }
}
