package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Background
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.Race
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.WeaponProficiency

/*
 * Правила, извлечённые из текстов библиотек (раса, класс, предыстория).
 *
 * Библиотеки хранят только размеченный текст dnd.su, поэтому всё здесь — результат
 * эвристического разбора плюс небольшой таблицы в коде (см. ClassChoiceCatalog).
 * Если разобрать деталь не удалось, вместо неё создаётся выбор с запасным вариантом
 * (свободное распределение бонусов, выбор из полного списка, свободный ввод):
 * пользователь видит исходный текст особенности и делает выбор сам.
 */

/** Откуда пришёл выбор или особенность — для группировки в заметках и на экранах. */
enum class ChoiceSource { RACE, SUBRACE, CLASS, SUBCLASS, BACKGROUND, FEAT }

/**
 * Вид выбора: от него зависят элемент интерфейса и то, куда результат попадёт при сборке.
 *
 * Значения в выборе ([CharacterDraft.selections]):
 * - [SKILL], [EXPERTISE] — `SkillType.name`; → владение / экспертиза навыком;
 * - [LANGUAGE] — название языка, [TOOL] — `ToolInfo.id`; → языки / инструменты персонажа;
 * - [OPTION] — `ChoiceOption.id`; → отдельная заметка выбора;
 * - [CANTRIP], [SPELL] — `Spell.id`; → известные заклинания персонажа;
 * - [ABILITY] — `AbilityType.name`; → +1 к характеристике (черты «Увеличьте Интеллект, Мудрость или Харизму»);
 * - [PROFICIENCY] — `skill:<SkillType.name>` или `tool:<ToolInfo.id>`; → навык или инструмент
 *   («любая комбинация из трёх навыков или инструментов»);
 * - [WEAPON] — `WeaponInfo.id`; → владение отдельным видом оружия.
 *
 * Свободного ввода нет: все выборы делаются из списков (навыки, языки, справочник
 * инструментов, варианты из текста). Если варианты умения не найдены, выбор не
 * создаётся, а умение целиком попадает в заметки.
 */
enum class ChoiceKind { SKILL, EXPERTISE, LANGUAGE, TOOL, OPTION, CANTRIP, SPELL, ABILITY, PROFICIENCY, WEAPON }

/**
 * Вариант выбора: воззвание, боевой приём, стиль, договор, навык из списка и т. п.
 *
 * @param id стабильный ключ внутри выбора (для навыков — `SkillType.name`).
 * @param text описание варианта в разметке библиотеки.
 * @param requiredLevel минимальный уровень класса; варианты выше уровня недоступны.
 * @param requirement текст требования как есть (например, «умение «Договор клинка»»).
 * @param extended опциональный (Tasha) или неофициальный (UA) вариант:
 * скрыт, пока не включён соответствующий переключатель.
 */
data class ChoiceOption(
    val id: String,
    val title: String,
    val text: String = "",
    val requiredLevel: Int? = null,
    val requirement: String = "",
    val extended: Boolean = false,
    /**
     * Общий заголовок для вариантов, развёрнутых из одного (копии инфузии «Копия магического
     * предмета» — по одной на предмет); пусто — самостоятельный вариант. Интерфейс группирует
     * такие варианты под одним заголовком с текстом [groupText].
     */
    val group: String = "",
    val groupText: String = "",
)

/**
 * Фильтр заклинаний для выбора заговоров и заклинаний.
 *
 * @param classCodes коды классов из `Spell.classes` (`wizard`, `druid`…); пусто — любые классы.
 * @param schools коды школ (`evo`, `abj`…); пусто — любые.
 * @param names названия из текста («искусство друидов или чудотворство»); не пусто — только они.
 * @param attackOnly только заклинания с броском атаки («Меткие заклинания»).
 * @param ritualOnly только ритуалы («Ритуальный заклинатель»).
 */
data class SpellFilter(
    val classCodes: Set<String> = emptySet(),
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val schools: Set<String> = emptySet(),
    val names: List<String> = emptyList(),
    val attackOnly: Boolean = false,
    val ritualOnly: Boolean = false,
)

/**
 * Один выбор, который должен сделать пользователь.
 *
 * @param id стабильный ключ (`race.skills`, `class.invocations`…) — ключ в [CharacterDraft.selections].
 * @param hint короткая подсказка («Выберите 2 навыка»).
 * @param sourceText исходный текст особенности — показывается, чтобы было понятно, что выбирать.
 * @param count сколько значений нужно выбрать.
 * @param options варианты; пусто для [ChoiceKind.SKILL] — любые навыки, для заклинаний —
 * все подходящие под [spellFilter]. Языки и инструменты дополняются из справочников.
 * @param dedicatedStep показывать отдельным экраном сразу после экрана источника
 * (воззвания, боевые приёмы); иначе выбор встроен в экран источника.
 */
data class ChoiceSpec(
    val id: String,
    val source: ChoiceSource,
    val title: String,
    val kind: ChoiceKind,
    val count: Int,
    val hint: String = "",
    val sourceText: String = "",
    val options: List<ChoiceOption> = emptyList(),
    val spellFilter: SpellFilter? = null,
    val dedicatedStep: Boolean = false,
)

/**
 * Особенность или умение: при сборке персонажа попадает в заметки блока своего источника.
 *
 * @param level уровень класса, на котором умение появляется (у рас и предысторий — 1).
 * @param extended опциональное умение (Tasha) или материал UA.
 */
data class RuleFeature(
    val title: String,
    val text: String,
    val level: Int = 1,
    val extended: Boolean = false,
    /**
     * Вложенные части без собственного уровня: варианты боевого стиля, договоры колдуна,
     * правила заклинательства. Из них строятся варианты выбора.
     */
    val parts: List<ChoiceOption> = emptyList(),
    /**
     * Названия базовых умений, которые это опциональное умение заменяет: «…заменяющее
     * умение «Избранный враг»». Пусто — умение добавляется к базовым, а не заменяет их.
     */
    val replaces: List<String> = emptyList(),
)

/**
 * Выбор между базовым умением класса и опциональным, которое его заменяет
 * («Избранный враг» или «Предпочтительный противник»).
 *
 * @param id ключ решения в [CharacterDraft.optionalSwaps] — название опционального умения.
 * @param base базовые умения, которые оно заменяет (жрец 8-го уровня: «Божественный удар» или
 * «Могущественное колдовство» — из них есть только то, что даёт архетип).
 */
data class OptionalSwap(
    val id: String,
    val optional: RuleFeature,
    val base: List<RuleFeature>,
)

/**
 * Заклинание, которое даёт раса: «вы знаете заговор пляшущие огоньки [dancing lights]».
 *
 * @param name фрагмент текста перед английским названием (см. [MarkupText.matchesSpellName]).
 * @param minLevel уровень персонажа, с которого заклинание доступно («Начиная с 3-го уровня…»).
 */
/**
 * Заклинание из текста особенности или умения.
 *
 * @param source название особенности, которая даёт заклинание («Дьявольское наследие»): по нему
 * заклинания расы убираются, когда разновидность заменяет особенность своей версией.
 */
data class GrantedSpell(val name: String, val minLevel: Int = 1, val source: String = "")

/**
 * Ресурс персонажа: ограниченные использования умения, кости превосходства, ярость и т. п.
 *
 * @param maximum значение на уровне создания.
 * @param formula формула `StatFormula` (`[pb]`, `[cha]`, `[level] * 5`); пусто — максимум
 * не зависит от линейной формулы и задан числом (например, количество ярости по таблице).
 * @param spellName заклинание, которое ресурс позволяет наложить («адское возмездие» у тифлинга,
 * «порча» у воззвания «Вор пяти судеб»); пусто — ресурс самого умения. Название из текста
 * может стоять в другом падеже, поэтому при сборке оно заменяется названием из библиотеки.
 */
data class ResourceSpec(
    val name: String,
    val description: String,
    val maximum: Int,
    val formula: String = "",
    val source: ChoiceSource,
    val spellName: String = "",
)

// region Раса

data class AbilityBonus(val ability: AbilityType, val amount: Int)

/**
 * Правило бонусов характеристик.
 *
 * @param fixed фиксированные бонусы («Ловкость +2»).
 * @param patterns варианты свободных бонусов: `[[2, 1], [1, 1, 1]]` — «+2 и +1 либо три по +1»,
 * `[[1, 1]]` — «две разные характеристики +1». Каждое значение — отдельная характеристика.
 * @param excludeFixed свободные бонусы нельзя отдавать характеристикам с фиксированным
 * («значение одной **другой** характеристики»).
 * @param parsed false — строку разобрать не удалось: интерфейс предлагает свободное
 * распределение `[[2, 1], [1, 1, 1]]` и показывает исходный текст.
 */
data class AbilityBonusRule(
    val fixed: List<AbilityBonus> = emptyList(),
    val patterns: List<List<Int>> = emptyList(),
    val excludeFixed: Boolean = true,
    val parsed: Boolean = true,
    val sourceText: String = "",
)

/**
 * Разновидность расы (высший эльф, горный дварф, альтернативный человек…).
 *
 * @param replacesBaseAbilityBonus бонусы подрасы заменяют бонусы основной расы
 * (альтернативный человек, драконокровный и т. п.), а не добавляются к ним.
 * @param replacesBaseTraits это не разновидность, а полная версия расы из другой книги
 * («Кентавр (MOT)» на странице кентавра из GGR): её особенности, владения и выборы
 * заменяют основные, иначе они присутствовали бы дважды.
 */
data class SubraceRules(
    val id: String,
    val title: String,
    val text: String,
    val extended: Boolean,
    val traits: List<RuleFeature>,
    val abilityBonus: AbilityBonusRule?,
    val replacesBaseAbilityBonus: Boolean = false,
    val grants: RaceGrants = RaceGrants(),
    val choices: List<ChoiceSpec> = emptyList(),
    val replacesBaseTraits: Boolean = false,
    /**
     * Названия особенностей расы, которые разновидность явно заменяет особенностью с другим названием:
     * «Особенности выбранной подрасы заменяют «Дьявольское наследие»» (тифлинги MTF: «Наследие Авернуса»),
     * «заменяющие особенность «Универсальность навыков»» (полуэльфы). Одноимённые особенности
     * заменяются и без этого списка (см. `WizardEngine.withoutReplacedTraits`).
     */
    val replacedTraits: List<String> = emptyList(),
)

/** Что раса или разновидность даёт без выбора. */
data class RaceGrants(
    val speed: Int? = null,
    val size: String = "",
    val skills: Set<SkillType> = emptySet(),
    val languages: List<String> = emptyList(),
    val armor: Set<ArmorProficiency> = emptySet(),
    /** Владение отдельными видами оружия текстом. */
    val weapons: String = "",
    val tools: String = "",
    val featSlots: Int = 0,
    val spells: List<GrantedSpell> = emptyList(),
    /** Прибавка к хитам за уровень (холмовой дварф). */
    val hpPerLevel: Int = 0,
)

/**
 * Раса с разобранными особенностями.
 *
 * @param extended неофициальная раса (UA, сторонние книги) — скрыта без переключателя.
 * @param subraceRequired у расы есть разновидности, и одну нужно выбрать.
 */
data class RaceRules(
    val race: Race,
    val extended: Boolean,
    /** Короткое описание для карточки: первый абзац без цитат. */
    val summary: String,
    val traits: List<RuleFeature>,
    val abilityBonus: AbilityBonusRule,
    val grants: RaceGrants,
    val choices: List<ChoiceSpec>,
    val subraces: List<SubraceRules>,
    val subraceRequired: Boolean,
)

// endregion

// region Предыстория

data class BackgroundRules(
    val background: Background,
    val skills: Set<SkillType>,
    val tools: String,
    val languages: String,
    /** Предметы снаряжения по отдельности, без кошелька. */
    val equipment: List<String>,
    /** Золото из кошелька («кошель с 10 зм»). */
    val gold: Int,
    val feature: RuleFeature?,
    /** Выборы предыстории: языки «на ваш выбор», инструменты из списка, навыки из списка. */
    val choices: List<ChoiceSpec>,
    /**
     * Черта от предыстории: «Вы получаете черту Бдительный, Одарённый или Крепкий».
     * Список имён черт; одно имя — черта даётся без выбора. Пусто — черты нет.
     */
    val featOptions: List<String> = emptyList(),
)

// endregion

// region Класс

/** Вариант стартового снаряжения: «а) чешуйчатый доспех». */
data class EquipmentOption(val label: String, val items: List<String>)

/** Строка снаряжения: несколько вариантов «а) … или б) …», либо один без выбора. */
data class EquipmentGroup(val options: List<EquipmentOption>) {
    val hasChoice: Boolean get() = options.size > 1
}

/** Архетип класса. */
data class SubclassRules(
    val id: String,
    val title: String,
    val text: String,
    val extended: Boolean,
    val features: List<RuleFeature>,
)

data class ClassRules(
    val charClass: CharClass,
    /** Класс из перечисления по названию; [CharacterClass.OTHER] — неизвестный класс. */
    val type: CharacterClass,
    val hitDie: Int,
    val savingThrows: Set<AbilityType>,
    val armor: Set<ArmorProficiency>,
    val weapons: Set<WeaponProficiency>,
    /** Отдельные виды оружия, не покрытые категориями («длинные мечи, рапиры»). */
    val otherWeapons: String,
    val tools: String,
    val skillChoice: ChoiceSpec?,
    val equipment: List<EquipmentGroup>,
    /** Формула золота вместо снаряжения, например `5к4 × 10`; пусто — не найдено. */
    val startingGold: String,
    /** Названия умений по уровням из таблицы класса. */
    val levelFeatures: Map<Int, List<String>>,
    /** Таблица класса как есть: шапка (текст без токенов `[[abbr]]`, расшифровка) и строки. */
    val tableHeaders: List<String>,
    val tableRows: List<List<String>>,
    val features: List<RuleFeature>,
    val subclassLevel: Int,
    /** Название группы архетипов («Воинские архетипы», «Потусторонние покровители»). */
    val subclassGroupTitle: String,
    val subclasses: List<SubclassRules>,
    /** Уровни «Увеличения характеристик» из таблицы. */
    val asiLevels: List<Int>,
    /**
     * Списки вариантов из разделов `## `: «Таинственные воззвания», «Боевые приёмы»,
     * «Инфузии изобретателя». Ключ — название раздела.
     */
    val optionLists: Map<String, List<ChoiceOption>> = emptyMap(),
    /**
     * Неофициальный класс (UA, homebrew, сторонняя книга или создан пользователем) —
     * скрыт без переключателя; см. [UnofficialContent.isUnofficialClass].
     */
    val extended: Boolean = false,
) {
    /** Список вариантов, чьё название содержит [keyword]; пусто — не найден. */
    fun optionList(keyword: String): List<ChoiceOption> =
        optionLists.entries.firstOrNull { it.key.lowercase().contains(keyword.lowercase()) }?.value.orEmpty()

    /** Умение класса, чьё название начинается с [prefix] (без учёта регистра). */
    fun feature(prefix: String): RuleFeature? =
        features.firstOrNull { it.title.lowercase().startsWith(prefix.lowercase()) }

    /** Значение столбца таблицы на уровне; null — столбца нет или прочерк. */
    fun tableValue(level: Int, header: String): String? {
        val column = tableHeaders.indexOfFirst { it.equals(header, ignoreCase = true) }
        if (column < 0) return null
        return tableRows.getOrNull(level - 1)?.getOrNull(column)?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
    }
}

/**
 * Заклинательство класса на выбранном уровне.
 *
 * @param cantrips сколько заговоров выбрать.
 * @param knownSpells известных заклинаний (бард, колдун, чародей, следопыт…); null — класс
 * не «знает» заклинания, а готовит их.
 * @param spellbookSpells заклинаний в книге волшебника; null — не волшебник.
 * @param preparesFromClassList готовит из всего списка класса (жрец, друид, паладин, изобретатель).
 * @param preparedCount сколько заклинаний подготовить; 0 — класс не готовит заклинания.
 * @param preparedFormula формула лимита для `Character.maxPreparedSpells` на будущее (`[wis] + [level]`).
 * @param alwaysPrepared названия заклинаний, всегда подготовленных (домен, клятва).
 */
data class SpellcastingPlan(
    val classCode: String,
    val ability: AbilityType,
    val cantrips: Int,
    val knownSpells: Int?,
    val spellbookSpells: Int?,
    val maxSpellLevel: Int,
    val preparesFromClassList: Boolean,
    val preparedCount: Int,
    val preparedFormula: String = "",
    val alwaysPrepared: List<String> = emptyList(),
    /** Ограничение школ (мистический рыцарь, мистический ловкач); пусто — любые. */
    val schools: Set<String> = emptySet(),
    /**
     * Сколько известных заклинаний могут быть любой школы при ограничении [schools]: по одному
     * за 3, 8, 14 и 20 уровень (на 3-м — одно из трёх, два других — из школ архетипа).
     */
    val anySchoolSpells: Int = 0,
    /** Ячейки заклинаний на уровне создания: уровень ячейки → количество. */
    val slots: Map<Int, Int> = emptyMap(),
    /** Дополнительные заклинания, доступные для выбора (расширенный список покровителя). */
    val extraSpellNames: List<String> = emptyList(),
) {
    val canPrepare: Boolean get() = preparedCount > 0
}

// endregion
