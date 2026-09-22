package com.example.spellbook.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spellbook.R
import com.example.spellbook.data.AddSpellResult
import com.example.spellbook.data.AppLanguage
import com.example.spellbook.data.AppPreferences
import com.example.spellbook.data.AppTheme
import com.example.spellbook.data.SectionLayout
import com.example.spellbook.ui.components.CharacterSection
import com.example.spellbook.ui.components.EditScope
import com.example.spellbook.data.CharacterLssCodec
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.withRecalculatedResources
import com.example.spellbook.data.DndSuException
import com.example.spellbook.data.ComboRoller
import com.example.spellbook.data.DndSuCatalog
import com.example.spellbook.data.DndSuLoader
import com.example.spellbook.data.FeatFilters
import com.example.spellbook.data.FeatLibraryCodec
import com.example.spellbook.data.SpellBookRepository
import com.example.spellbook.data.SpellFilters
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.SpellSort
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.classListPreparingLimits
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Вкладки нижней навигации. */
enum class Tab { CHARACTERS, LIBRARY, SETTINGS }

/** Граней у проверочного кубика. */
private const val D20_SIDES = 20

/**
 * Превращает исключение в сообщение для пользователя.
 *
 * У [DndSuException] уже есть локализуемый текст, остальные ошибки показываются
 * через [fallbackRes] с техническим описанием.
 */
private fun Throwable.toUiMessage(fallbackRes: Int): UiMessage = when (this) {
    is DndSuException -> UiMessage(messageRes, args)
    else -> UiMessage.of(fallbackRes, localizedMessage ?: message.orEmpty())
}



/**
 * Сколько заклинаний скачиваем одновременно. Последовательная загрузка занимала минуты;
 * при этом слишком большая параллельность грозит отказами со стороны сайта.
 */
private const val LIBRARY_DOWNLOAD_PARALLELISM = 8

/**
 * Версия встроенного набора черт. Повышается при обновлении `assets/feats-library.json`,
 * чтобы новые данные подтянулись и у тех, кто уже выполнил импорт раньше.
 *
 * v2 — у черт появилась книга-источник для группировки в библиотеке.
 */
private const val BUNDLED_FEATS_VERSION = 2

/** Экраны приложения. Нижняя навигация видна только на «корневых» экранах вкладок. */
sealed interface Screen {
    /** Персонаж, которому принадлежит экран; null у общих экранов (библиотека, настройки). */
    val characterId: String? get() = null

    data object Characters : Screen
    data class CharacterSpells(override val characterId: String) : Screen

    /** Корень библиотеки: выбор между заклинаниями и чертами. */
    data object Library : Screen
    data object LibrarySpells : Screen
    data object LibraryFeats : Screen

    /** Просмотр черты из библиотеки. */
    data class FeatDetails(val featId: String) : Screen

    /** Просмотр черты при добавлении её персонажу: внизу есть кнопка «Добавить». */
    data class AddFeatDetails(
        override val characterId: String,
        val featId: String,
    ) : Screen
    data class Details(val spellId: String) : Screen
    /** Форма заклинания; [characterId] задан, если её открыли из раздела персонажа. */
    data class SpellForm(
        val spellId: String?,
        override val characterId: String? = null,
    ) : Screen
    data class CharacterForm(override val characterId: String?) : Screen
    data class AddSpells(override val characterId: String) : Screen
    data class PrepareSpells(override val characterId: String) : Screen
    data class SpellSlots(override val characterId: String) : Screen
    data class Combos(override val characterId: String) : Screen
    data class ComboEditor(override val characterId: String, val comboId: String?) : Screen
    data class StepLibrary(override val characterId: String) : Screen
    data class ComboResult(override val characterId: String, val comboId: String) : Screen
    data class Inventory(override val characterId: String) : Screen
    data class Stats(override val characterId: String) : Screen
    data class Notes(override val characterId: String) : Screen
    data class Feats(override val characterId: String) : Screen
    data class AddFeats(override val characterId: String) : Screen
    data object Settings : Screen
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
    /** Прогресс массовой загрузки библиотеки; null — загрузка не идёт. */
    val libraryProgress: LibraryDownloadProgress? = null,
    /** Итог последней массовой загрузки с логом ошибок; null — показывать нечего. */
    val libraryReport: LibraryDownloadReport? = null,
    /** Пользовательские настройки приложения (язык, тема, панель разделов). */
    val settings: AppSettingsState = AppSettingsState(),
    /** Сообщение для пользователя; текст собирается на стороне UI по языку интерфейса. */
    val message: UiMessage? = null,
)

/**
 * Настройки приложения в состоянии UI.
 *
 * [characterLayouts] содержит только персонажей с личной настройкой панели;
 * для остальных действует [globalLayout].
 */
data class AppSettingsState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.SYSTEM,
    val globalLayout: SectionLayout = SectionLayout.DEFAULT,
    val characterLayouts: Map<String, SectionLayout> = emptyMap(),
) {
    /** Действующая настройка панели для персонажа: личная, иначе общая. */
    fun layoutFor(characterId: String?): SectionLayout =
        characterId?.let { characterLayouts[it] } ?: globalLayout

    /** Есть ли у персонажа собственная настройка (иначе он наследует общую). */
    fun hasOwnLayout(characterId: String): Boolean = characterId in characterLayouts
}

/** Состояние загрузки официальной библиотеки заклинаний. */
data class LibraryDownloadProgress(
    val processed: Int = 0,
    val total: Int = 0,
    /** Добавлено или обновлено записей. */
    val saved: Int = 0,
    /** Пропущено: такое заклинание создано пользователем вручную. */
    val skipped: Int = 0,
    val failed: Int = 0,
) {
    /** Доля выполненного от 0 до 1; до получения списка — 0. */
    val fraction: Float
        get() = if (total > 0) processed.toFloat() / total else 0f
}

/**
 * Заклинание, которое не удалось загрузить.
 *
 * [name] берётся из адреса страницы: если загрузка упала, настоящего названия ещё нет.
 */
data class LibraryDownloadFailure(
    val name: String,
    val url: String,
    /** Причина сбоя; текст собирается на языке интерфейса при показе лога. */
    val reason: UiMessage,
)

/** Итог массовой загрузки: сколько сохранено и что не получилось. */
data class LibraryDownloadReport(
    val saved: Int = 0,
    val skipped: Int = 0,
    val failures: List<LibraryDownloadFailure> = emptyList(),
)

class SpellBookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpellBookRepository(application)
    private val prefs = AppPreferences(application)

    /** Активная массовая загрузка библиотеки — чтобы не запустить её дважды и мочь отменить. */
    private var libraryDownload: Job? = null

    var uiState by mutableStateOf(SpellBookUiState())
        private set

    /** Параметры отображения списка (общие для библиотеки и экрана персонажа). */
    var listQuery by mutableStateOf("")
        private set
    var listSort by mutableStateOf(SpellSort.LEVEL)
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

    /** Последняя корневая страница раздела «Персонажи»: список или экран персонажа. */
    private var lastCharactersScreen: Screen = Screen.Characters

    /** Персонаж, на данные которого сейчас оформлены подписки. */
    private var currentCharacterId: String? = null

    /**
     * id персонажа, в контексте которого создаётся/импортируется заклинание.
     * Если задан, после сохранения заклинание автоматически добавляется в его набор.
     */
    private var pendingCharacterId: String? = null

    init {
        uiState = uiState.copy(settings = readSettings())
        viewModelScope.launch {
            repository.migrateLegacyIfNeeded()
            importBundledLibraryOnce()
            importBundledFeatsOnce()
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

    /**
     * При первом запуске наполняет библиотеку из встроенного файла, чтобы заклинания
     * были доступны сразу и офлайн. Повторно не выполняется: иначе удалённые
     * заклинания возвращались бы при каждом старте.
     */
    // region Настройки приложения

    /**
     * Строка ресурса с учётом выбранного языка интерфейса.
     * Нужна редко: когда текст сохраняется в базу (например, заголовок блока заметок).
     */
    private fun localizedString(resId: Int): String {
        val locale = prefs.language.locale ?: return getApplication<Application>().getString(resId)
        val configuration = android.content.res.Configuration(
            getApplication<Application>().resources.configuration,
        ).apply { setLocale(locale) }
        return getApplication<Application>()
            .createConfigurationContext(configuration)
            .getString(resId)
    }

    /** Собирает текущие настройки из хранилища. */
    private fun readSettings() = AppSettingsState(
        language = prefs.language,
        theme = prefs.theme,
        globalLayout = prefs.globalSectionLayout,
        characterLayouts = prefs.allCharacterSectionLayouts(),
    )

    fun openSettings() {
        uiState = uiState.copy(tab = Tab.SETTINGS, screen = Screen.Settings)
    }

    /**
     * Возврат с экрана настроек: настройки — самостоятельная вкладка,
     * поэтому возвращаемся к персонажам — корневому разделу приложения.
     */
    fun exitSettings() {
        selectTab(Tab.CHARACTERS)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.language = language
        uiState = uiState.copy(settings = uiState.settings.copy(language = language))
    }

    fun setTheme(theme: AppTheme) {
        prefs.theme = theme
        uiState = uiState.copy(settings = uiState.settings.copy(theme = theme))
    }

    /**
     * Сохраняет настройку панели разделов.
     *
     * [characterId] = null — общая настройка для всех персонажей без личной;
     * [layout] = null у конкретного персонажа — вернуться к общей настройке.
     */
    fun setSectionLayout(characterId: String?, layout: SectionLayout?) {
        if (characterId == null) {
            val global = layout ?: SectionLayout.DEFAULT
            prefs.globalSectionLayout = global
            uiState = uiState.copy(settings = uiState.settings.copy(globalLayout = global))
        } else {
            prefs.setSectionLayoutFor(characterId, layout)
            val layouts = uiState.settings.characterLayouts.toMutableMap()
            if (layout == null) layouts.remove(characterId) else layouts[characterId] = layout
            uiState = uiState.copy(settings = uiState.settings.copy(characterLayouts = layouts))
        }
    }

    // endregion

    private suspend fun importBundledLibraryOnce() {
        if (prefs.bundledLibraryImported) return
        // Импорт идёт молча: для пользователя библиотека просто уже есть при первом запуске.
        repository.importBundledLibrary()
        prefs.bundledLibraryImported = true
    }

    /**
     * Загружает встроенные черты, если их ещё нет или файл обновился.
     *
     * Сравнивается версия набора, а не просто факт импорта: иначе у тех, кто уже
     * получил черты раньше, не появились бы новые поля — например, книга-источник,
     * по которой черты группируются в библиотеке.
     */
    private suspend fun importBundledFeatsOnce() {
        if (prefs.bundledFeatsVersion >= BUNDLED_FEATS_VERSION) return
        repository.importBundledFeats()
        prefs.bundledFeatsVersion = BUNDLED_FEATS_VERSION
    }

    /** При старте открываем главную страницу последнего персонажа, если он ещё существует. */
    private suspend fun restoreLastCharacter() {
        val lastId = prefs.lastCharacterId ?: return
        if (repository.getCharacter(lastId) == null) {
            prefs.lastCharacterId = null
            return
        }
        openCharacterHome(lastId)
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
            Tab.SETTINGS -> uiState.copy(tab = tab, screen = Screen.Settings)
            Tab.CHARACTERS -> {
                // Восстанавливаем последнюю страницу самого раздела: список либо экран персонажа.
                val savedCharacterId = lastCharactersScreen.characterId
                val target = lastCharactersScreen.takeIf {
                    savedCharacterId != null && uiState.characters.any { it.id == savedCharacterId }
                } ?: Screen.Characters
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
        val changingCharacter = enterCharacter(characterId)
        if (resetView || changingCharacter) {
            resetListControls()
            showPreparedOnly = true
        }
        showCharacterScreen(Screen.CharacterSpells(characterId))
    }

    /**
     * Готовит переход к персонажу: запоминает его и переподписывается на данные,
     * если открывается другой персонаж.
     *
     * @return true, если персонаж сменился.
     */
    private fun enterCharacter(characterId: String): Boolean {
        val changingCharacter = currentCharacterId != characterId
        prefs.lastCharacterId = characterId
        if (changingCharacter) {
            currentCharacterId = characterId
            observeCharacterSpells(characterId)
        }
        return changingCharacter
    }

    /** Показывает экран персонажа и запоминает его для возврата на вкладку «Персонажи». */
    private fun showCharacterScreen(screen: Screen) {
        lastCharactersScreen = screen
        uiState = uiState.copy(tab = Tab.CHARACTERS, screen = screen)
    }

    /**
     * Открывает главную страницу персонажа — его характеристики.
     *
     * Используется при выборе персонажа и при возврате «Назад» с его экранов:
     * заклинания больше не обязательны, а характеристики есть у любого персонажа.
     */
    fun openCharacterHome(characterId: String) {
        openCharacterSection(characterId, CharacterSection.HOME)
    }

    /** Открывает заданный раздел персонажа (общий обработчик панели разделов). */
    fun openCharacterSection(characterId: String, section: CharacterSection) {
        when (section) {
            CharacterSection.SPELLS -> openCharacterSpells(characterId, resetView = false)
            CharacterSection.SETTINGS -> openEditCharacterForm(characterId)
            CharacterSection.STATS -> openStats(characterId)
            CharacterSection.RESOURCES -> openSpellSlots(characterId)
            CharacterSection.COMBOS -> openCombos(characterId)
            CharacterSection.INVENTORY -> openInventory(characterId)
            CharacterSection.FEATS -> openFeats(characterId)
            CharacterSection.NOTES -> openNotes(characterId)
            CharacterSection.PREPARE -> openPrepareSpells(characterId)
        }
    }

    /**
     * Возврат с экрана раздела персонажа.
     *
     * С главной страницы выходим к списку персонажей, с остальных — на главную:
     * так кнопка «Назад» никогда не зацикливается на текущем экране.
     */
    fun exitCharacterSection(characterId: String, section: CharacterSection) {
        if (section == CharacterSection.HOME) openCharacters() else openCharacterHome(characterId)
    }

    fun openLibrary() {
        uiState = uiState.copy(tab = Tab.LIBRARY, screen = Screen.Library)
    }

    /** Раздел библиотеки с заклинаниями. Поиск и фильтры начинаются с чистого листа. */
    fun openLibrarySpells() {
        resetListControls()
        uiState = uiState.copy(tab = Tab.LIBRARY, screen = Screen.LibrarySpells)
    }

    /** Раздел библиотеки с чертами, сгруппированными по книгам-источникам. */
    fun openLibraryFeats() {
        resetFeatControls()
        uiState = uiState.copy(tab = Tab.LIBRARY, screen = Screen.LibraryFeats)
    }

    /**
     * Сбрасывает поиск и фильтры черт.
     *
     * Список библиотеки и список персонажа делят одно состояние, поэтому при переходе
     * между ними условия отбора начинаются с чистого листа.
     */
    private fun resetFeatControls() {
        featsQuery = ""
        featsFilters = FeatFilters()
        featsScrollIndex = 0
        featsScrollOffset = 0
    }

    /** Поиск по библиотеке черт; хранится здесь, чтобы переживать поворот экрана. */
    var featsQuery by mutableStateOf("")
        private set

    fun updateFeatsQuery(query: String) { featsQuery = query }

    /** Фильтры библиотеки черт (повышаемые характеристики). */
    var featsFilters by mutableStateOf(FeatFilters())
        private set

    fun updateFeatsFilters(filters: FeatFilters) { featsFilters = filters }

    /**
     * Позиция списка черт. Хранится в ViewModel, чтобы возврат с экрана черты
     * открывал список на том же месте — как у заклинаний.
     */
    var featsScrollIndex: Int = 0
        private set
    var featsScrollOffset: Int = 0
        private set

    fun saveFeatsScroll(index: Int, offset: Int) {
        featsScrollIndex = index
        featsScrollOffset = offset
    }

    /** Открывает черту отдельным экраном — как детали заклинания. */
    fun openFeatDetails(featId: String) {
        uiState = uiState.copy(screen = Screen.FeatDetails(featId))
    }

    /** Открывает черту библиотеки при выборе черт для персонажа. */
    fun openAddFeatDetails(characterId: String, featId: String) {
        uiState = uiState.copy(screen = Screen.AddFeatDetails(characterId, featId))
    }

    /** Сохраняет отредактированную черту библиотеки (название и описание). */
    fun editLibraryFeat(featId: String, name: String, description: String) {
        val feat = getLibraryFeat(featId) ?: return
        // Правка общая: черта обновится у всех персонажей, которые её взяли.
        saveFeat(feat.copy(name = name.trim(), description = description.trim()))
    }

    /** JSON черты для выгрузки в файл или отправки. */
    fun exportFeatJson(featId: String): String? =
        getLibraryFeat(featId)?.let { FeatLibraryCodec.toJson(it).toString() }

    /**
     * JSON черты персонажа: выгружается именно тот текст, который видит
     * пользователь, включая персональную правку.
     */
    fun exportCharacterFeatJson(feat: CharacterFeat): String =
        FeatLibraryCodec.toJson(feat.asFeat()).toString()

    /** Черта из библиотеки по идентификатору или null, если её уже удалили. */
    fun getLibraryFeat(featId: String): Feat? = uiState.libraryFeats.firstOrNull { it.id == featId }

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

    /** Возврат к корневому экрану текущей вкладки. */
    fun navigateBackToList() {
        if (uiState.tab == Tab.LIBRARY) {
            // Заклинания живут в своём разделе, а не на корневом экране библиотеки.
            uiState = uiState.copy(screen = Screen.LibrarySpells)
            return
        }
        val lastId = prefs.lastCharacterId
        if (lastId != null) openCharacterHome(lastId) else openCharacters()
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

    /**
     * Открывает форму редактирования заклинания.
     *
     * Контекст персонажа берётся с экрана, с которого открыли детали: если это был
     * набор персонажа, правка спросит, менять ли текст для всех или только для него.
     */
    fun openEditSpellForm(spellId: String) {
        uiState = uiState.copy(
            screen = Screen.SpellForm(spellId = spellId, characterId = detailsOrigin?.characterId),
        )
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

    // Любой раздел может быть точкой входа в персонажа (см. openCharacterHome),
    // поэтому каждый оформляет подписки и запоминается для возврата на вкладку.

    fun openPrepareSpells(characterId: String) {
        enterCharacter(characterId)
        showCharacterScreen(Screen.PrepareSpells(characterId))
    }

    fun openSpellSlots(characterId: String) {
        enterCharacter(characterId)
        showCharacterScreen(Screen.SpellSlots(characterId))
    }

    fun openInventory(characterId: String) {
        enterCharacter(characterId)
        showCharacterScreen(Screen.Inventory(characterId))
    }

    fun openStats(characterId: String) {
        enterCharacter(characterId)
        lastCharactersScreen = Screen.Stats(characterId)
        uiState = uiState.copy(
            tab = Tab.CHARACTERS,
            screen = Screen.Stats(characterId),
            lastD20Roll = null,
        )
    }

    fun openNotes(characterId: String) {
        enterCharacter(characterId)
        showCharacterScreen(Screen.Notes(characterId))
    }

    fun openFeats(characterId: String) {
        enterCharacter(characterId)
        showCharacterScreen(Screen.Feats(characterId))
    }

    fun openAddFeats(characterId: String) {
        // Список добавления делит поиск и фильтры с библиотекой — начинаем с чистого листа.
        resetFeatControls()
        uiState = uiState.copy(screen = Screen.AddFeats(characterId))
    }

    // region Черты

    /** Создаёт черту в библиотеке и сразу добавляет её персонажу. */
    fun addFeat(characterId: String, name: String, description: String) {
        if (name.isBlank()) {
            uiState = uiState.copy(message = UiMessage(R.string.msg_feat_name_required))
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

    /**
     * Правка черты из раздела персонажа.
     *
     * [scope] = [EditScope.EVERYONE] меняет запись в общей библиотеке, иначе текст
     * сохраняется только этому персонажу и библиотека остаётся нетронутой.
     */
    fun editCharacterFeat(
        characterId: String,
        featId: String,
        name: String,
        description: String,
        scope: EditScope,
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedDescription = description.trim()
            when (scope) {
                EditScope.EVERYONE -> {
                    val feat = repository.getFeat(featId) ?: return@launch
                    repository.saveFeat(
                        feat.copy(name = trimmedName, description = trimmedDescription),
                    )
                    // Своя версия больше не нужна: пользователь явно выбрал общий текст.
                    repository.setFeatOverrides(characterId, featId, null, null)
                }

                EditScope.THIS_CHARACTER -> repository.setFeatOverrides(
                    characterId,
                    featId,
                    trimmedName,
                    trimmedDescription,
                )
            }
        }
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_feat_loading))
            val parsed = runCatching { DndSuLoader.loadFeat(url) }.getOrElse { error ->
                uiState = uiState.copy(message = error.toUiMessage(R.string.msg_feat_load_failed))
                return@launch
            }
            // Повторная загрузка обновляет текст, а не создаёт дубль.
            val existing = repository.findFeatByName(parsed.name)
            val feat = existing?.copy(
                description = parsed.description,
                source = url.trim(),
                book = parsed.book,
            ) ?: Feat(
                name = parsed.name,
                description = parsed.description,
                source = url.trim(),
                book = parsed.book,
            )
            repository.saveFeat(feat)

            val target = characterId ?: pendingCharacterId
            if (target != null) repository.addFeatToCharacter(target, feat.id)

            val messageRes = when {
                target != null -> R.string.msg_feat_added_to_character
                existing == null -> R.string.msg_feat_added_to_library
                else -> R.string.msg_feat_updated
            }
            uiState = uiState.copy(message = UiMessage.of(messageRes, parsed.name))
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
            // Заголовок по умолчанию берётся из ресурсов на языке интерфейса.
            title = title.trim().ifBlank { localizedString(R.string.msg_note_default_title) },
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
        enterCharacter(characterId)
        showCharacterScreen(Screen.Combos(characterId))
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

    /**
     * Правка заклинания из раздела персонажа.
     *
     * При [EditScope.THIS_CHARACTER] меняются только название и описание: остальные
     * характеристики (круг, школа, компоненты) — правила заклинания, они общие.
     */
    fun editCharacterSpell(characterId: String, spell: Spell, scope: EditScope) {
        val normalized = spell.copy(description = DiceRoller.wrapDiceTokens(spell.description))
        viewModelScope.launch {
            when (scope) {
                EditScope.EVERYONE -> {
                    repository.upsertSpell(normalized)
                    repository.setSpellOverrides(characterId, normalized.id, null, null)
                }

                EditScope.THIS_CHARACTER -> repository.setSpellOverrides(
                    characterId,
                    normalized.id,
                    normalized.name.trim(),
                    normalized.description,
                )
            }
            uiState = uiState.copy(
                screen = Screen.Details(normalized.id),
                message = UiMessage(R.string.msg_spell_saved),
            )
        }
    }

    /** Есть ли у персонажа своя версия этого заклинания. */
    suspend fun hasSpellOverrides(characterId: String, spellId: String): Boolean =
        repository.hasSpellOverrides(characterId, spellId)

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
                    message = UiMessage.of(R.string.msg_spell_already_in_library, normalized.name),
                )
                return@launch
            }
            repository.upsertSpell(normalized)
            val cantripError = characterId != null &&
                !addSpellToCharacterChecked(characterId, normalized)
            uiState = uiState.copy(
                screen = Screen.Details(normalized.id),
                message = if (cantripError) {
                    cantripLimitMessage(characterId!!)
                } else {
                    UiMessage(R.string.msg_spell_saved)
                },
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

    private fun cantripLimitMessage(characterId: String): UiMessage {
        val max = getCharacter(characterId)?.maxCantrips ?: 0
        return UiMessage.of(R.string.msg_cantrip_limit, max)
    }

    fun deleteSpell(spellId: String) {
        viewModelScope.launch {
            repository.deleteSpell(spellId)
            uiState = uiState.copy(
                screen = if (uiState.tab == Tab.LIBRARY) Screen.Library else Screen.Characters,
                message = UiMessage(R.string.msg_spell_deleted),
            )
        }
    }

    fun exportSpellJson(spellId: String): String? = getSpell(spellId)?.let(SpellLssCodec::encodeSpell)

    fun importSpellJson(json: String) {
        val imported = runCatching { SpellLssCodec.decodeSpell(json) }
            .getOrElse {
                uiState = uiState.copy(message = it.toUiMessage(R.string.msg_json_read_failed))
                return
            }
        if (imported.name.isBlank()) {
            uiState = uiState.copy(message = UiMessage(R.string.msg_json_no_spell))
            return
        }
        val characterId = pendingCharacterId
        pendingCharacterId = null
        viewModelScope.launch {
            saveUniqueOrReuse(
                spell = imported,
                characterId = characterId,
                newMessage = UiMessage(R.string.msg_spell_imported),
            )
        }
    }

    /**
     * Сохраняет заклинание, проверяя уникальность по названию. Если такое уже есть в
     * библиотеке — переиспользуем его (без дубля) и при необходимости добавляем в набор персонажа.
     */
    private suspend fun saveUniqueOrReuse(spell: Spell, characterId: String?, newMessage: UiMessage) {
        val normalized = spell.copy(description = DiceRoller.wrapDiceTokens(spell.description))
        val existing = repository.findSpellByName(normalized.name)
        if (existing != null) {
            val limited = characterId != null && !addSpellToCharacterChecked(characterId, existing)
            uiState = uiState.copy(
                screen = Screen.Details(existing.id),
                message = when {
                    // Лимит заговоров важнее: показываем причину, почему не добавилось.
                    limited -> cantripLimitMessage(characterId!!)
                    characterId != null -> UiMessage(R.string.msg_spell_added_to_character)
                    else -> UiMessage.of(R.string.msg_spell_exists_in_library, normalized.name)
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_link_must_be_dndsu))
            return
        }
        val characterId = pendingCharacterId
        pendingCharacterId = null
        uiState = uiState.copy(message = UiMessage(R.string.msg_spell_loading))
        viewModelScope.launch {
            val spell = try {
                DndSuLoader.load(url)
            } catch (e: Exception) {
                uiState = uiState.copy(message = e.toUiMessage(R.string.msg_spell_load_failed))
                return@launch
            }
            if (spell.name.isBlank()) {
                uiState = uiState.copy(message = UiMessage(R.string.msg_spell_parse_failed))
                return@launch
            }
            saveUniqueOrReuse(
                spell = spell,
                characterId = characterId,
                newMessage = UiMessage(R.string.msg_spell_loaded_dndsu),
            )
        }
    }

    /**
     * Загружает всю официальную библиотеку заклинаний, чтобы дальше пользоваться ею офлайн.
     *
     * Заклинания, созданные вручную, остаются нетронутыми; ранее загруженные обновляются
     * на месте, так что привязки к персонажам сохраняются.
     */
    fun downloadOfficialLibrary() {
        if (libraryDownload != null) return
        libraryDownload = viewModelScope.launch {
            uiState = uiState.copy(libraryProgress = LibraryDownloadProgress(), libraryReport = null)
            val urls = try {
                DndSuCatalog.loadOfficialSpellUrls()
            } catch (e: DndSuException) {
                finishLibraryDownload(e.toUiMessage(R.string.msg_spell_list_failed))
                return@launch
            }

            var processed = 0
            var saved = 0
            var skipped = 0
            val failures = mutableListOf<LibraryDownloadFailure>()
            val counters = Mutex()
            // Ограничиваем число одновременных запросов: без этого сайт может начать отказывать.
            val limiter = Semaphore(LIBRARY_DOWNLOAD_PARALLELISM)

            coroutineScope {
                urls.map { url ->
                    async {
                        limiter.withPermit {
                            ensureActive()
                            // Сохраняем причину сбоя, чтобы показать её в логе после загрузки.
                            val attempt = runCatching { DndSuLoader.load(url) }
                            val spell = attempt.getOrNull()
                            // Причина сбоя показывается в логе как есть: это техническая диагностика.
                            val failure: UiMessage? = when {
                                spell == null -> attempt.exceptionOrNull()
                                    ?.toUiMessage(R.string.msg_spell_load_failed)
                                    ?: UiMessage(R.string.msg_error_unknown)

                                spell.name.isBlank() -> UiMessage(R.string.msg_spell_parse_failed)
                                else -> null
                            }
                            val replaced = if (failure == null && spell != null) {
                                repository.saveOfficialSpell(spell)
                            } else {
                                false
                            }

                            // Счётчики общие для всех корутин, поэтому обновляем их под замком.
                            counters.withLock {
                                processed++
                                when {
                                    failure != null -> failures += LibraryDownloadFailure(
                                        name = spellNameFromUrl(url),
                                        url = url,
                                        reason = failure,
                                    )

                                    replaced -> saved++
                                    else -> skipped++
                                }
                                uiState = uiState.copy(
                                    libraryProgress = LibraryDownloadProgress(
                                        processed = processed,
                                        total = urls.size,
                                        saved = saved,
                                        skipped = skipped,
                                        failed = failures.size,
                                    ),
                                )
                            }
                        }
                    }
                }.awaitAll()
            }

            // Вариант сообщения зависит от того, были ли пропуски и ошибки.
            val summary = when {
                failures.isNotEmpty() ->
                    UiMessage.of(R.string.msg_library_downloaded_errors, saved, failures.size)

                skipped > 0 -> UiMessage.of(R.string.msg_library_downloaded_own, saved, skipped)
                else -> UiMessage.of(R.string.msg_library_downloaded, saved)
            }
            // Лог показываем только при наличии ошибок: иначе достаточно краткого итога.
            val report = failures
                .takeIf { it.isNotEmpty() }
                ?.let { LibraryDownloadReport(saved, skipped, it.sortedBy(LibraryDownloadFailure::name)) }
            finishLibraryDownload(summary, report)
        }
    }

    /** Закрывает лог ошибок последней загрузки. */
    fun dismissLibraryReport() {
        uiState = uiState.copy(libraryReport = null)
    }

    /**
     * Выгружает всю библиотеку одним файлом — его можно передать другому человеку,
     * чтобы ему не пришлось скачивать заклинания по одному.
     */
    fun exportLibraryJson(): String? {
        val spells = uiState.librarySpells
        if (spells.isEmpty()) {
            uiState = uiState.copy(message = UiMessage(R.string.msg_library_is_empty))
            return null
        }
        return SpellLssCodec.encodeList(spells)
    }

    /**
     * Загружает библиотеку из файла: мгновенная альтернатива скачиванию с сайта.
     * Собственные заклинания пользователя при этом не затираются.
     */
    fun importLibraryJson(json: String) {
        val spells = runCatching { SpellLssCodec.decodeList(json) }.getOrNull()
        if (spells.isNullOrEmpty()) {
            uiState = uiState.copy(message = UiMessage(R.string.msg_library_export_failed))
            return
        }
        viewModelScope.launch {
            var saved = 0
            var skipped = 0
            spells.forEach { spell ->
                if (spell.name.isBlank()) return@forEach
                if (repository.saveOfficialSpell(spell)) saved++ else skipped++
            }
            uiState = uiState.copy(
                message = if (skipped > 0) {
                    UiMessage.of(R.string.msg_library_imported_own, saved, skipped)
                } else {
                    UiMessage.of(R.string.msg_library_imported, saved)
                },
            )
        }
    }

    /** Прерывает массовую загрузку, сохраняя уже скачанное. */
    fun cancelLibraryDownload() {
        libraryDownload?.cancel()
        libraryDownload = null
        uiState = uiState.copy(libraryProgress = null, message = UiMessage(R.string.msg_download_stopped))
    }

    private fun finishLibraryDownload(message: UiMessage, report: LibraryDownloadReport? = null) {
        libraryDownload = null
        uiState = uiState.copy(libraryProgress = null, libraryReport = report, message = message)
    }

    /**
     * Имя заклинания из адреса вида `/spells/291-prismatic_spray/` → `prismatic spray`.
     * Используется в логе ошибок: при сбое русского названия ещё нет.
     */
    private fun spellNameFromUrl(url: String): String = url
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfter('-')
        .replace('_', ' ')
        .ifBlank { url }

    // endregion

    // region Персонажи и связи

    /**
     * Сохраняет персонажа.
     *
     * @param addClassSpells если true, в список известных добавляются все заклинания
     * классов, готовящих из полного списка, в пределах доступных кругов.
     */
    fun saveCharacter(character: Character, addClassSpells: Boolean = false) {
        // Редактирование — возвращаемся к персонажу, создание — к списку персонажей.
        val editing = (uiState.screen as? Screen.CharacterForm)?.characterId != null
        viewModelScope.launch {
            // Уровень и характеристики могли измениться — пересчитываем ресурсы на формулах.
            repository.upsertCharacter(character.withRecalculatedResources())
            val added = if (addClassSpells) addClassSpellsToCharacter(character) else 0
            val message = if (added > 0) {
                UiMessage.of(R.string.msg_character_saved_spells, added)
            } else {
                UiMessage(R.string.msg_character_saved)
            }
            // Домашний раздел зависит от настройки панели, поэтому навигируем через него.
            if (editing) openCharacterHome(character.id) else openCharacters()
            uiState = uiState.copy(message = message)
        }
    }

    /**
     * Добавляет в набор персонажа все заклинания его классов, готовящих
     * из полного списка, ограничивая их доступными кругами.
     *
     * Заговоры не добавляются: они не подготавливаются и ограничены отдельно.
     *
     * @return сколько заклинаний добавлено.
     */
    private suspend fun addClassSpellsToCharacter(character: Character): Int {
        // Круг свой у каждого класса: друид 4 уровня готовит только до 2 круга,
        // даже если суммарный уровень персонажа даёт ячейки выше.
        val limits = classListPreparingLimits(character.classLevels)
        if (limits.isEmpty()) return 0

        var added = 0
        uiState.librarySpells
            .filter { spell ->
                spell.level > 0 && spell.classes.any { code ->
                    spell.level <= (limits[code] ?: 0)
                }
            }
            .forEach { spell ->
                val result = repository.tryAddSpellToCharacter(
                    characterId = character.id,
                    spellId = spell.id,
                    spellLevel = spell.level,
                    maxCantrips = character.maxCantrips,
                )
                if (result == AddSpellResult.ADDED) added++
            }
        return added
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
                uiState = uiState.copy(message = it.toUiMessage(R.string.msg_sheet_read_failed))
                return
            }
        viewModelScope.launch {
            repository.upsertCharacter(imported)
            val messageRes = if (existing != null) {
                R.string.msg_sheet_updated
            } else {
                R.string.msg_character_imported
            }
            openCharacterHome(imported.id)
            uiState = uiState.copy(message = UiMessage(messageRes))
        }
    }

    /**
     * Возврат с формы персонажа (без сохранения).
     *
     * Форма существующего персонажа — это раздел «Настройки» в панели, поэтому
     * возвращаемся по общему правилу разделов; форма создания ведёт к списку.
     */
    fun exitCharacterForm() {
        val editingId = (uiState.screen as? Screen.CharacterForm)?.characterId
        if (editingId != null) {
            exitCharacterSection(editingId, CharacterSection.SETTINGS)
        } else {
            openCharacters()
        }
    }

    fun deleteCharacter(characterId: String) {
        viewModelScope.launch {
            repository.deleteCharacter(characterId)
            // Личная настройка панели больше ни к чему не относится.
            setSectionLayout(characterId, null)
            if (prefs.lastCharacterId == characterId) prefs.lastCharacterId = null
            uiState = uiState.copy(
                screen = Screen.Characters,
                message = UiMessage(R.string.msg_character_deleted),
            )
        }
    }

    fun addSpellToCurrentCharacter(characterId: String, spellId: String) {
        viewModelScope.launch { repository.addSpellToCharacter(characterId, spellId) }
    }

    /**
     * Убирает заклинание из набора персонажа. Само заклинание остаётся в общей библиотеке,
     * поэтому его можно добавить обратно в любой момент.
     */
    fun removeSpellFromCharacter(characterId: String, spellId: String) {
        viewModelScope.launch {
            repository.removeSpellFromCharacter(characterId, spellId)
            uiState = uiState.copy(message = UiMessage(R.string.msg_spell_removed_from_character))
        }
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
                uiState = uiState.copy(message = UiMessage.of(R.string.msg_prepared_limit, max))
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_enter_positive_number))
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_enter_positive_number))
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_enter_combo_name))
            return
        }
        viewModelScope.launch {
            repository.saveCombo(combo.copy(name = combo.name.trim()), editingComboStepIds)
            uiState = uiState.copy(
                screen = Screen.Combos(combo.characterId),
                message = UiMessage(R.string.msg_combo_saved),
            )
        }
    }

    fun saveComboStep(step: ComboStep, addToCurrentCombo: Boolean = false) {
        if (step.name.isBlank()) {
            uiState = uiState.copy(message = UiMessage(R.string.msg_enter_step_name))
            return
        }
        viewModelScope.launch {
            repository.saveComboStep(step.copy(name = step.name.trim()))
            if (addToCurrentCombo && step.id !in editingComboStepIds) {
                editingComboStepIds = editingComboStepIds + step.id
            }
            uiState = uiState.copy(message = UiMessage(R.string.msg_step_saved))
        }
    }

    fun deleteComboStep(stepId: String) {
        viewModelScope.launch {
            repository.deleteComboStep(stepId)
            editingComboStepIds = editingComboStepIds - stepId
            uiState = uiState.copy(message = UiMessage(R.string.msg_step_deleted))
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
            uiState = uiState.copy(
                screen = Screen.Combos(characterId),
                message = UiMessage(R.string.msg_combo_deleted),
            )
        }
    }

    fun rollCombo(characterId: String, comboId: String, mode: ComboRollMode) {
        viewModelScope.launch {
            val combo = repository.getComboWithSteps(comboId)
            if (combo == null || combo.steps.isEmpty()) {
                uiState = uiState.copy(message = UiMessage(R.string.msg_combo_needs_step))
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_enter_positive_number))
            return
        }
        val normalized = item.normalized()
        if (normalized.attuned && !canAttune(normalized.characterId, normalized.id)) {
            val limit = getCharacter(normalized.characterId)?.maxAttunedItems ?: 0
            uiState = uiState.copy(message = UiMessage.of(R.string.msg_attunement_limit, limit))
            return
        }
        viewModelScope.launch {
            repository.saveInventoryItem(normalized)
            uiState = uiState.copy(message = UiMessage(R.string.msg_item_saved))
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_item_deleted))
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
                uiState = uiState.copy(message = UiMessage.of(R.string.msg_attunement_limit, limit))
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
                message = UiMessage.of(R.string.msg_attunement_limit_low, newLimit, attunedCount),
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
            uiState = uiState.copy(message = UiMessage(R.string.msg_not_enough_coins))
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
