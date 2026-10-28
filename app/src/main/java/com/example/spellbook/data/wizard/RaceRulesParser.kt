package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Race
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.util.HtmlUtils

/**
 * Разбор особенностей расы из текста dnd.su.
 *
 * Особенности расы на сайте оформлены строками `***Название***. Текст` в разделе
 * «Особенности …»; разновидности — отдельными разделами `## ` с такими же строками.
 * Часть старых страниц (дварф, гном) свёрстана вкладками: строки особенностей идут
 * без выделения, а вкладки помечены строкой `Горный">`. Такие вкладки становятся
 * разновидностями, каждая из которых целиком описывает расу.
 */
object RaceRulesParser {

    /** Разделы каталога с неофициальными или сторонними материалами. */
    private val UNOFFICIAL_CATEGORIES = listOf("unearthed", "midgard", "plane shift", "homebrew")

    /** Признаки разделителя неофициальных материалов внутри описания. */
    private val UNOFFICIAL_MARKERS = listOf("unearthed", "homebrew", "unofficial")

    /** Названия особенностей при вёрстке вкладками (без выделения). */
    private val PLAIN_TRAIT_TITLES = listOf(
        "Увеличение характеристик", "Возраст", "Мировоззрение", "Размер", "Скорость", "Языки",
        "Тёмное зрение", "Превосходное тёмное зрение",
    )

    private val TAB_LABEL = Regex("^(.{1,60})\">$")

    fun parse(race: Race): RaceRules {
        val nodes = MarkupText.outline(race.description)
        val raceExtended = UNOFFICIAL_CATEGORIES.any { race.category.lowercase().contains(it) }

        val traitIndex = nodes.indexOfFirst { it.level == 2 && it.title.lowercase().startsWith("особенност") }
            .takeIf { it >= 0 }
            ?: nodes.indexOfFirst { node -> traitLines(node.body).any { isAbilityTrait(it.first) } }

        val summary = nodes.firstOrNull { it.level == 0 }?.body?.let(::summaryOf).orEmpty()

        if (traitIndex < 0) {
            return RaceRules(
                race = race,
                extended = raceExtended,
                summary = summary,
                traits = emptyList(),
                abilityBonus = AbilityBonusRule(parsed = false),
                grants = RaceGrants(),
                choices = emptyList(),
                subraces = emptyList(),
                subraceRequired = false,
            )
        }

        val traitNode = nodes[traitIndex]

        // Вёрстка вкладками: каждая вкладка — самостоятельная разновидность.
        val tabbed = splitTabs(traitNode.body)
        if (tabbed.size > 1) {
            val subraces = tabbed.mapIndexed { index, (title, body) ->
                buildSubrace(
                    id = "tab.$index",
                    title = title,
                    text = body,
                    extended = raceExtended,
                    replacesBase = true,
                    looseTitles = true,
                )
            }
            return RaceRules(
                race = race,
                extended = raceExtended,
                summary = summary,
                traits = emptyList(),
                abilityBonus = AbilityBonusRule(parsed = false),
                grants = RaceGrants(),
                choices = emptyList(),
                subraces = subraces,
                subraceRequired = true,
            )
        }

        // Врезки внутри раздела особенностей с собственным увеличением характеристик —
        // альтернативные варианты расы (например, альтернативный человек).
        val (mainBody, callouts) = extractCallouts(traitNode.body)
        val traits = parseTraits(mainBody)
        val base = analyze(traits, "race", ChoiceSource.RACE)

        val subraces = mutableListOf<SubraceRules>()
        callouts.forEachIndexed { index, (title, body) ->
            val calloutTraits = parseTraits(body)
            if (calloutTraits.any { isAbilityTrait(it.title) }) {
                subraces += buildSubrace(
                    id = "callout.$index",
                    title = MarkupText.titleCase(title),
                    text = body,
                    extended = raceExtended,
                    replacesBase = true,
                )
            }
        }

        var unofficial = raceExtended
        // Заголовок версии из другой книги без своих особенностей: «Кентавр (MOT)», «Багбир (ERLW)».
        // Дальше идут разделы с историей и именами и наконец «Особенности кентавра».
        var header: String? = null
        // Раздел с «Источником», за которым может следовать полная версия расы («Гоблин (GGR)»):
        // тогда он лишь даёт название, иначе — сам является разновидностью («Морской эльф (MTF)»).
        var held: SubraceRules? = null
        fun flushHeld() {
            held?.let { subraces += it }
            held = null
        }
        for (index in traitIndex + 1 until nodes.size) {
            val node = nodes[index]
            if (node.level == 1) break
            if (node.level != 2) continue
            val lowerTitle = node.title.lowercase()
            if (UNOFFICIAL_MARKERS.any { lowerTitle.contains(it) }) unofficial = true
            if (traitLines(node.body).isEmpty()) {
                // Раздел только с «Источником» открывает новую версию («Металлические драконорождённые (FTD)»):
                // предыдущая разновидность на этом заканчивается и не должна поглотить чужие особенности.
                if (hasSourceLine(node.body)) {
                    flushHeld()
                    header = node.title
                    continue
                }
                // Разделы только с историей, именами или источником — не разновидности. Первый из них
                // после предыдущей разновидности — заголовок версии («Метка поиска (RLW)»).
                if (header == null && held == null && !isLoreTitle(node.title) && !lowerTitle.contains("разновидност")) {
                    header = node.title
                }
                continue
            }
            val sub = buildSubrace(
                id = "sub.$index",
                title = node.title,
                text = node.body,
                extended = unofficial,
                replacesBase = node.body.lowercase().contains("заменя"),
                detectFullVersion = true,
            )
            if (sub.replacesBaseTraits) {
                // Полная версия расы называется по своей книге, а не «Особенности кентавра».
                // Особенности из раздела-заголовка («Гоблин (GGR)»: «Маленький и жилистый») — часть версии.
                val owner = held
                subraces += if (owner != null) {
                    sub.copy(
                        title = owner.title,
                        traits = owner.traits + sub.traits,
                        choices = owner.choices + sub.choices,
                        extended = owner.extended || sub.extended,
                    )
                } else {
                    sub.copy(title = header ?: node.title)
                }
                held = null
            } else {
                flushHeld()
                if (hasSourceLine(node.body)) held = sub else subraces += sub
            }
            header = null
        }
        flushHeld()

        val required = subraces.any { !it.replacesBaseAbilityBonus } &&
            traits.any { it.title.lowercase().startsWith("разновидност") && it.text.lowercase().contains("выберите") }

        return RaceRules(
            race = race,
            extended = raceExtended,
            summary = summary,
            traits = base.features,
            abilityBonus = base.bonus ?: AbilityBonusRule(parsed = false),
            grants = base.grants,
            choices = base.choices,
            subraces = subraces,
            subraceRequired = required,
        )
    }

    // region Строки особенностей

    private data class Trait(val title: String, val text: String)

    /** Строки особенностей раздела (без продолжений, без имён и источника). */
    private fun traitLines(body: String): List<Pair<String, String>> =
        body.lines().mapNotNull { MarkupText.traitLine(it) }.filterNot { isLoreTitle(it.first) }

    /**
     * Строки, оформленные как особенности, но не являющиеся ими: «**Источник**: …»,
     * «**Мужские имена**: …», «**Почётные звания**», таблица роста и веса.
     */
    private fun isLoreTitle(title: String): Boolean {
        val lower = title.lowercase().trim()
        return lower.startsWith("источник") || LORE_NAMES.containsMatchIn(lower) ||
            lower.startsWith("почётн") || lower.startsWith("почетн") ||
            lower.startsWith("модификатор размера") || lower.startsWith("высота") || lower.startsWith("вес ")
    }

    private val LORE_NAMES = Regex("(^|\\s)(имена|имя|фамилии|прозвища)(\\s|$)")

    /** В разделе есть строка «Источник: …» — он открывает материал из другой книги. */
    private fun hasSourceLine(body: String): Boolean =
        body.lines().any { MarkupText.traitLine(it)?.first?.lowercase()?.startsWith("источник") == true }

    private fun isAbilityTrait(title: String): Boolean =
        title.lowercase().startsWith("увеличение характеристик")

    /**
     * Особенности с продолжениями: всё до следующей строки особенности относится к ней,
     * включая таблицы заклинаний метки. Строки до первой особенности пропускаются.
     */
    /**
     * Убирает примечания — врезки без заголовка вида `:::` / «Примечание с сайта WotC…» / `:::`.
     *
     * Это разъяснения к особенностям, а не сами особенности: в примечании к зайцегону
     * строка `**Кроличий прыжок**:` превращалась во вторую особенность, а фраза «на него
     * действует эффект заклинания прыжок» — в заклинание расы.
     *
     * Одиночное `:::` считается началом примечания, только если дальше есть закрывающее `:::`
     * и следующая строка — не особенность: иначе это закрытие врезки, открытой в другом разделе.
     */
    private fun dropNotes(body: String): String {
        val lines = body.lines()
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            if (HtmlUtils.isCalloutEnd(line)) {
                val close = (index + 1 until lines.size).firstOrNull { HtmlUtils.isCalloutEnd(lines[it].trim()) }
                val next = lines.drop(index + 1).map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
                // Внутри примечания не должно быть настоящих особенностей (`***…***`) и других врезок —
                // иначе `:::` — закрытие чужой врезки, и вырезать ничего нельзя.
                val inner = if (close != null) lines.subList(index + 1, close).map { it.trim() } else emptyList()
                val safe = inner.none { HtmlUtils.isCalloutStart(it) || it.startsWith("***") }
                if (close != null && safe && MarkupText.traitLine(next) == null && !HtmlUtils.isCalloutStart(next)) {
                    index = close + 1
                    continue
                }
            }
            result += lines[index]
            index++
        }
        return result.joinToString("\n")
    }

    private fun parseTraits(rawBody: String, looseTitles: Boolean = false): List<Trait> {
        val body = dropNotes(rawBody)
        val result = mutableListOf<Trait>()
        var title: String? = null
        val text = mutableListOf<String>()
        fun flush() {
            title?.let { result += Trait(it, text.joinToString("\n").trim()) }
            text.clear()
        }
        body.lines().forEach { raw ->
            val line = raw.trim()
            val trait = MarkupText.traitLine(line) ?: plainTrait(line) ?: if (looseTitles) looseTrait(line) else null
            when {
                // Имена и источник пропускаются вместе с продолжением, чтобы не приклеиться к соседней особенности.
                trait != null && isLoreTitle(trait.first) -> {
                    flush()
                    title = null
                }
                trait != null -> {
                    flush()
                    title = trait.first
                    text += trait.second
                }
                title != null && line.isNotEmpty() && !HtmlUtils.isCalloutBoundary(line) -> text += line
            }
        }
        flush()
        return result
    }

    /** Строка особенности без выделения: «Скорость. Ваша базовая скорость…». */
    private fun plainTrait(line: String): Pair<String, String>? {
        val title = PLAIN_TRAIT_TITLES.firstOrNull { line.startsWith("$it.") } ?: return null
        return title to line.removePrefix("$title.").trim()
    }

    /**
     * Строка особенности во вкладках без выделения: «Дварфийская боевая тренировка. Вы владеете…».
     * Заголовок — короткая фраза с заглавной буквы до первой точки, дальше идёт предложение.
     * Иначе такие строки приклеиваются к предыдущей особенности и ломают разбор владений.
     */
    private fun looseTrait(line: String): Pair<String, String>? {
        val match = LOOSE_TRAIT.matchEntire(line) ?: return null
        val title = match.groupValues[1].trim()
        if (title.split(' ').size > LOOSE_TITLE_WORDS) return null
        return title to match.groupValues[2].trim()
    }

    private val LOOSE_TRAIT = Regex("^([А-ЯЁ][а-яёА-ЯЁ -]{2,50})\\.\\s+([А-ЯЁ].+)$")
    private const val LOOSE_TITLE_WORDS = 5

    /** Делит текст по меткам вкладок `Горный">`. Меньше двух вкладок — вёрстка обычная. */
    private fun splitTabs(body: String): List<Pair<String, String>> {
        val tabs = mutableListOf<Pair<String, String>>()
        var title: String? = null
        val lines = mutableListOf<String>()
        body.lines().forEach { raw ->
            val match = TAB_LABEL.matchEntire(raw.trim())
            if (match != null) {
                title?.let { tabs += it to lines.joinToString("\n") }
                lines.clear()
                title = match.groupValues[1].trim()
            } else if (title != null) {
                lines += raw
            }
        }
        title?.let { tabs += it to lines.joinToString("\n") }
        return tabs
    }

    /** Отделяет врезки `::: Заголовок … :::` от основного текста. */
    private fun extractCallouts(body: String): Pair<String, List<Pair<String, String>>> {
        val main = mutableListOf<String>()
        val callouts = mutableListOf<Pair<String, String>>()
        var calloutTitle: String? = null
        val calloutLines = mutableListOf<String>()
        body.lines().forEach { raw ->
            val line = raw.trim()
            when {
                calloutTitle == null && HtmlUtils.isCalloutStart(line) && HtmlUtils.calloutTitle(line).isNotEmpty() -> {
                    calloutTitle = HtmlUtils.calloutTitle(line)
                }
                calloutTitle != null && HtmlUtils.isCalloutEnd(line) -> {
                    callouts += calloutTitle!! to calloutLines.joinToString("\n")
                    calloutLines.clear()
                    calloutTitle = null
                }
                calloutTitle != null -> calloutLines += raw
                else -> main += raw
            }
        }
        return main.joinToString("\n") to callouts
    }

    private fun summaryOf(intro: String): String =
        intro.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !HtmlUtils.isQuote(it) && !it.startsWith(":::") && !it.startsWith("|") }
            ?.let(MarkupText::plain)
            .orEmpty()

    // endregion

    // region Анализ особенностей

    private data class Analysis(
        val features: List<RuleFeature>,
        val bonus: AbilityBonusRule?,
        val grants: RaceGrants,
        val choices: List<ChoiceSpec>,
    )

    /** Названия особенностей, которые полностью переходят в поля персонажа и в заметках не нужны. */
    private val FIELD_ONLY_TRAITS = listOf("увеличение характеристик", "возраст", "мировоззрение", "скорость", "размер")

    private fun buildSubrace(
        id: String,
        title: String,
        text: String,
        extended: Boolean,
        replacesBase: Boolean,
        looseTitles: Boolean = false,
        detectFullVersion: Boolean = false,
    ): SubraceRules {
        val traits = parseTraits(text, looseTitles)
        val analysis = analyze(traits, "subrace", ChoiceSource.SUBRACE)
        val full = detectFullVersion && isFullVersion(title, text, traits, analysis.bonus)
        return SubraceRules(
            id = id,
            title = title,
            text = text,
            extended = extended,
            traits = analysis.features,
            abilityBonus = analysis.bonus,
            // Разновидность с полным набором бонусов (+2/+1 и больше) заменяет бонусы расы, даже
            // если само слово «заменяет» стоит в общем разделе (тифлинги MTF): ни одна разновидность
            // не добавляет к расовому бонусу ещё целые +3.
            replacesBaseAbilityBonus = (replacesBase || full || bonusSum(analysis.bonus) >= FULL_BONUS) &&
                analysis.bonus != null,
            grants = analysis.grants,
            choices = analysis.choices,
            replacesBaseTraits = full,
        )
    }

    /**
     * Раздел — полная версия расы из другой книги, а не разновидность:
     * заголовок «Особенности …» либо полный набор — языки и целые +3 к характеристикам.
     * Разновидности дают часть бонуса (+1 у высшего эльфа), а драконьи метки прямо пишут,
     * что «заменяют» лишь часть особенностей — их основа сохраняется.
     */
    /** Сумма бонусов правила: фиксированные плюс наибольший свободный шаблон. */
    private fun bonusSum(bonus: AbilityBonusRule?): Int =
        bonus?.fixed.orEmpty().sumOf { it.amount } + (bonus?.patterns.orEmpty().maxOfOrNull { it.sum() } ?: 0)

    private fun isFullVersion(title: String, text: String, traits: List<Trait>, bonus: AbilityBonusRule?): Boolean {
        val lowerTitle = title.lowercase()
        val bonusSum = bonusSum(bonus)
        val hasLanguages = traits.any { it.title.lowercase().startsWith("языки") }
        // «Особенности …» — полная версия, если даёт полный бонус или свои языки без бонуса.
        // «Особенность речных эльфов» с +1 — обычная разновидность.
        if (lowerTitle.startsWith("особенност") && !lowerTitle.contains("подрас")) {
            return bonusSum >= FULL_BONUS || (bonus == null && hasLanguages)
        }
        return hasLanguages && bonusSum >= FULL_BONUS && !text.lowercase().contains("заменя")
    }

    private const val FULL_BONUS = 3

    private fun analyze(traits: List<Trait>, idPrefix: String, source: ChoiceSource): Analysis {
        var bonus: AbilityBonusRule? = null
        var speed: Int? = null
        var size = ""
        val skills = mutableSetOf<SkillType>()
        val languages = mutableListOf<String>()
        val armor = mutableSetOf<ArmorProficiency>()
        val weapons = mutableListOf<String>()
        val tools = mutableListOf<String>()
        var featSlots = 0
        var hpPerLevel = 0
        val spells = mutableListOf<GrantedSpell>()
        val choices = mutableListOf<ChoiceSpec>()
        val features = mutableListOf<RuleFeature>()

        traits.forEachIndexed { index, trait ->
            val title = trait.title
            val lowerTitle = title.lowercase()
            val plain = MarkupText.plain(trait.text)
            val lower = plain.lowercase()

            if (isAbilityTrait(title)) {
                bonus = parseAbilityBonus(trait.text)
            }
            if (lowerTitle.startsWith("скорость") || lowerTitle.contains("скорость курьера") ||
                Regex("скорость ходьбы (?:увеличивается до|составляет|равна|—)\\s*(\\d+)").containsMatchIn(lower)
            ) {
                Regex("(\\d+)\\s*фут").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { speed = it }
            }
            if (lowerTitle.startsWith("размер")) {
                Regex("ваш размер\\s*[—-]\\s*([а-яё]+(?:\\s+или\\s+[а-яё]+)?)").find(lower)?.let {
                    size = it.groupValues[1].replaceFirstChar { c -> c.uppercase() }
                }
            }
            if (lowerTitle.startsWith("языки") || lowerTitle.startsWith("дополнительный язык")) {
                val fixedPart = plain.substringBefore("на ваш выбор").substringBefore("по вашему выбору")
                val count = MarkupText.languageChoiceCount(plain)
                // «на Общем и на Эльфийском или Ведалкенском по вашему выбору»: выбор только из названных.
                val alternatives = if (count > 0) MarkupText.languageAlternatives(plain) else emptyList()
                languages += MarkupText.languages(fixedPart).filter { it !in alternatives }
                if (count > 0) {
                    choices += ChoiceSpec(
                        id = "$idPrefix.languages.$index",
                        source = source,
                        title = title,
                        kind = ChoiceKind.LANGUAGE,
                        count = count,
                        sourceText = trait.text,
                        options = alternatives.map { ChoiceOption(it, it) },
                    )
                }
            }
            // Навыки и инструменты — тем же разбором по частям предложения, что и у классов:
            // «навыками Выступление и Убеждение, а также одним музыкальным инструментом по вашему выбору» —
            // два навыка сразу и выбор инструмента из музыкальных.
            val feature = RuleFeature(title = title, text = trait.text)
            choices += FeatureRules.choices(feature, "$idPrefix.f$index", source)
                .filter { it.kind == ChoiceKind.SKILL || it.kind == ChoiceKind.TOOL }
            val traitGrants = FeatureRules.grants(trait.text)
            skills += traitGrants.skills
            tools += traitGrants.tools
            if (lowerTitle == "черта" || (lower.contains("черту") && lower.contains("на ваш выбор") && lower.contains("получаете"))) {
                featSlots += 1
            }
            // Заговор на выбор из списка класса.
            if (lower.contains("заговор") && (lower.contains("на ваш выбор") || lower.contains("из списка")) &&
                MarkupText.spellNameCandidates(trait.text).isEmpty()
            ) {
                val count = Regex("(\\S+)\\s+заговор").find(lower)?.let { MarkupText.number(it.groupValues[1]) } ?: 1
                choices += ChoiceSpec(
                    id = "$idPrefix.cantrip.$index",
                    source = source,
                    title = title,
                    kind = ChoiceKind.CANTRIP,
                    count = count,
                    sourceText = trait.text,
                    spellFilter = SpellFilter(classCodes = MarkupText.spellListCodes(trait.text)),
                )
            }

            // Доспехи и отдельные виды оружия.
            if (lower.contains("владеете") || lower.contains("владение")) {
                if (lower.contains("доспех")) {
                    if (lower.contains("лёгк") || lower.contains("легк")) armor += ArmorProficiency.LIGHT
                    if (lower.contains("средн")) armor += ArmorProficiency.MEDIUM
                    if (lower.contains("тяжёл") || lower.contains("тяжел")) armor += ArmorProficiency.HEAVY
                }
                val weaponMatch = Regex("(?:владеете|владение)\\s+(.+)").find(plain)?.groupValues?.get(1)
                if (weaponMatch != null && WEAPON_WORDS.any { weaponMatch.lowercase().contains(it) } &&
                    !lower.contains("доспех") && !lower.contains("навык") && !lower.contains("инструмент")
                ) {
                    weapons += weaponMatch.trimEnd('.')
                }
            }
            Regex("максимальное значение ваших хитов увеличивается на (\\d+)").find(lower)?.let {
                hpPerLevel = it.groupValues[1].toIntOrNull() ?: 0
            }
            // Заклинания — только те, что персонаж знает или может накладывать; «на него действует
            // эффект заклинания прыжок» или «если на вас накладывают лечение ран» — нет (см. [FeatureSpells]).
            // «Один из следующих заговоров по вашему выбору: …» — выбор из названных.
            val traitSpells = FeatureSpells.parse(trait.text, "$idPrefix.t$index", source, title)
            spells += traitSpells.spells.map { it.copy(source = title) }
            choices += traitSpells.choices

            if (FIELD_ONLY_TRAITS.none { lowerTitle.startsWith(it) } && !lowerTitle.startsWith("разновидност")) {
                features += RuleFeature(title = title, text = trait.text)
            }
        }

        return Analysis(
            features = features,
            bonus = bonus,
            grants = RaceGrants(
                speed = speed,
                size = size,
                skills = skills,
                languages = languages.distinct(),
                armor = armor,
                weapons = weapons.joinToString(", "),
                tools = tools.joinToString("; "),
                featSlots = featSlots,
                spells = spells.distinctBy { it.name },
                hpPerLevel = hpPerLevel,
            ),
            choices = choices,
        )
    }

    private val WEAPON_WORDS = listOf(
        "меч", "лук", "топор", "молот", "копь", "рапир", "арбалет", "трезуб", "сеть", "кинжал", "булав", "дубин",
    )


    // endregion

    // region Увеличение характеристик

    private val FREE_BONUS = Regex(
        "(одн\\S*|дв\\S*|тр[её]\\S*|любо\\S*|люба\\S*)\\s+" +
            "(?:(?:ваш\\S*|любо\\S*|люба\\S*|други\\S*|друга\\S*|друго\\S*|разн\\S*|различн\\S*)\\s+)*" +
            "характеристик\\S*[^.;]*?на\\s+(\\d)",
    )

    /** Разбирает строку «Увеличение характеристик». */
    fun parseAbilityBonus(rawText: String): AbilityBonusRule {
        val text = MarkupText.plain(rawText)
        val lower = text.lowercase()

        // «Либо +2 и +1, либо три по +1» — вариант Tasha и Monsters of the Multiverse.
        if (lower.contains("либо значение одной") || lower.contains("выберите одно из") ||
            lower.contains("выберете одно из") || lower.contains("(а)")
        ) {
            return AbilityBonusRule(patterns = listOf(listOf(2, 1), listOf(1, 1, 1)), sourceText = rawText)
        }
        if (lower.contains("всех ваших характеристик")) {
            val amount = Regex("на (\\d)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return AbilityBonusRule(
                fixed = AbilityType.entries.map { AbilityBonus(it, amount) },
                sourceText = rawText,
            )
        }

        val free = mutableListOf<Int>()
        var rest = lower
        FREE_BONUS.findAll(lower).forEach { match ->
            val word = match.groupValues[1]
            val count = when {
                word.startsWith("дв") -> 2
                word.startsWith("тр") -> 3
                else -> 1
            }
            val amount = match.groupValues[2].toIntOrNull() ?: 1
            repeat(count) { free += amount }
            rest = rest.replace(match.value, " ")
        }
        // «…на 2, а другой — на 1» без слова «характеристика».
        Regex("а другой\\s*[—-]\\s*на\\s+(\\d)").find(rest)?.let { match ->
            free += match.groupValues[1].toInt()
            rest = rest.replace(match.value, " ")
        }

        // Фиксированные бонусы: для каждой упомянутой характеристики — ближайшая следующая величина.
        val fixed = mutableListOf<AbilityBonus>()
        val words = Regex("[а-яё]+|\\d+").findAll(rest).map { it.value }.toList()
        words.forEachIndexed { index, word ->
            val ability = MarkupText.ability(word) ?: return@forEachIndexed
            val tail = words.drop(index + 1)
            val amountIndex = tail.indexOfFirst { it.toIntOrNull() != null }
            if (amountIndex < 0) return@forEachIndexed
            val amount = tail[amountIndex].toInt()
            val decreases = tail.take(amountIndex).any { it.startsWith("уменьша") }
            if (fixed.none { it.ability == ability }) fixed += AbilityBonus(ability, if (decreases) -amount else amount)
        }

        val patterns = if (free.isEmpty()) emptyList() else listOf(free.sortedDescending())
        return AbilityBonusRule(
            fixed = fixed,
            patterns = patterns,
            parsed = fixed.isNotEmpty() || patterns.isNotEmpty(),
            sourceText = rawText,
        )
    }

    // endregion
}
