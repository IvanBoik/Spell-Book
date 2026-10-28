package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.model.spellSlotsFor

/**
 * Таблица правил классов, которых нет в тексте в машиночитаемом виде:
 * сколько вариантов выбирается на каком уровне, какие умения дают ресурсы
 * и как устроено заклинательство. Сами варианты (воззвания, приёмы, стили)
 * берутся из текста класса.
 */
object ClassChoiceCatalog {


    // region Выборы

    fun choices(
        rules: ClassRules,
        subclass: SubclassRules?,
        level: Int,
        selections: Map<String, List<String>>,
        showOptional: Boolean,
        showUnofficial: Boolean,
    ): List<ChoiceSpec> {
        val result = mutableListOf<ChoiceSpec>()
        val handled = mutableSetOf<String>()
        // Варианты выше уровня не скрываются: интерфейс показывает их недоступными с причиной
        // (см. [OptionRequirements]), а фильтр «Только доступные» убирает их из списка.
        fun visible(option: ChoiceOption): Boolean = when {
            option.id.startsWith("ua.") -> showUnofficial
            option.extended -> showOptional
            else -> true
        }
        val classSkills = rules.skillChoice?.options.orEmpty()

        fun optionChoice(
            id: String,
            source: ChoiceSource,
            title: String,
            count: Int,
            options: List<ChoiceOption>,
            sourceText: String,
            hint: String = "",
        ) {
            val visibleOptions = options.filter(::visible)
            // Варианты не найдены в тексте — выбор не предлагается: умение остаётся в заметках целиком.
            if (count <= 0 || visibleOptions.isEmpty()) return
            result += ChoiceSpec(
                id = id,
                source = source,
                title = title,
                kind = ChoiceKind.OPTION,
                count = count,
                hint = hint,
                sourceText = sourceText,
                options = visibleOptions,
                dedicatedStep = true,
            )
        }

        val type = rules.type
        val subTitle = subclass?.title?.lowercase().orEmpty()

        // Инструменты на выбор из владений класса (бард, монах, изобретатель).
        toolChoice(rules)?.let(result::add)

        // Боевой стиль: воин (1), паладин и следопыт (2); чемпион получает ещё один на 10.
        rules.feature("Боевой стиль")?.takeIf { it.level <= level }?.let { style ->
            handled += "боевой стиль"
            handled += "дополнительный боевой стиль"
            val extra = if (type == CharacterClass.FIGHTER && subTitle.startsWith("чемпион") && level >= 10) 1 else 0
            optionChoice("class.fighting_style", ChoiceSource.CLASS, style.title, 1 + extra, style.parts, style.text)
        }

        when (type) {
            CharacterClass.FIGHTER -> {
                if (subTitle.startsWith("мастер боевых") && level >= 3) {
                    handled += "боевое превосходство"
                    val text = subclass?.features?.firstOrNull { it.title.lowercase().startsWith("боевое превосходство") }?.text.orEmpty()
                    optionChoice(
                        id = "subclass.maneuvers",
                        source = ChoiceSource.SUBCLASS,
                        title = "Боевые приёмы",
                        count = byLevel(level, 3 to 3, 7 to 5, 10 to 7, 15 to 9),
                        options = rules.optionList("приём"),
                        sourceText = text,
                    )
                }
            }

            CharacterClass.WARLOCK -> {
                val invocations = rules.tableValue(level, "Известные воззвания")?.toIntOrNull() ?: 0
                rules.feature("Таинственные воззвания")?.let { feature ->
                    handled += "таинственные воззвания"
                    optionChoice(
                        id = "class.invocations",
                        source = ChoiceSource.CLASS,
                        title = "Таинственные воззвания",
                        count = invocations,
                        options = rules.optionList("воззван"),
                        sourceText = feature.text,
                    )
                }
                rules.feature("Предмет договора")?.takeIf { level >= it.level }?.let { feature ->
                    handled += "предмет договора"
                    optionChoice("class.pact", ChoiceSource.CLASS, "Предмет договора", 1, feature.parts, feature.text)
                    val pact = selections["class.pact"].orEmpty()
                        .mapNotNull { id -> feature.parts.firstOrNull { it.id == id } }
                    if (pact.any { it.title.lowercase().contains("гримуар") }) {
                        result += ChoiceSpec(
                            id = "class.pact_tome",
                            source = ChoiceSource.CLASS,
                            title = "Книга теней: заговоры",
                            kind = ChoiceKind.CANTRIP,
                            count = 3,
                            hint = "Заговоры из списков любых классов",
                            sourceText = pact.first { it.title.lowercase().contains("гримуар") }.text,
                            spellFilter = SpellFilter(),
                        )
                    }
                }
                listOf(11 to 6, 13 to 7, 15 to 8, 17 to 9).filter { level >= it.first }.forEach { (_, circle) ->
                    handled += "таинственный арканум"
                    result += ChoiceSpec(
                        id = "class.arcanum.$circle",
                        source = ChoiceSource.CLASS,
                        title = "Таинственный арканум ($circle круг)",
                        kind = ChoiceKind.SPELL,
                        count = 1,
                        sourceText = rules.feature("Таинственный арканум")?.text.orEmpty(),
                        spellFilter = SpellFilter(classCodes = setOf("warlock"), minLevel = circle, maxLevel = circle),
                    )
                }
            }

            CharacterClass.SORCERER -> {
                rules.feature("Метамагия")?.takeIf { level >= it.level }?.let { feature ->
                    handled += "метамагия"
                    optionChoice(
                        id = "class.metamagic",
                        source = ChoiceSource.CLASS,
                        title = "Метамагия",
                        count = byLevel(level, 3 to 2, 10 to 3, 17 to 4),
                        options = feature.parts,
                        sourceText = feature.text,
                    )
                }
            }

            CharacterClass.BARD -> {
                val expertise = byLevel(level, 3 to 2, 10 to 4)
                if (expertise > 0) {
                    handled += "компетентность"
                    result += expertiseChoice(rules, expertise)
                }
                val secrets = byLevel(level, 10 to 2, 14 to 4, 18 to 6)
                if (secrets > 0) {
                    handled += "тайны магии"
                    result += ChoiceSpec(
                        id = "class.magical_secrets",
                        source = ChoiceSource.CLASS,
                        title = "Тайны магии",
                        kind = ChoiceKind.SPELL,
                        count = secrets,
                        hint = "Заклинания из списков любых классов",
                        sourceText = rules.feature("Тайны магии")?.text.orEmpty(),
                        spellFilter = SpellFilter(minLevel = 0, maxLevel = maxCircle(rules.type, level)),
                    )
                }
                if (subTitle.contains("знаний") && level >= 6) {
                    handled += "дополнительные тайны магии"
                    result += ChoiceSpec(
                        id = "subclass.magical_secrets",
                        source = ChoiceSource.SUBCLASS,
                        title = "Дополнительные тайны магии",
                        kind = ChoiceKind.SPELL,
                        count = 2,
                        hint = "Заклинания из списков любых классов",
                        spellFilter = SpellFilter(minLevel = 0, maxLevel = maxCircle(rules.type, level)),
                    )
                }
            }

            CharacterClass.ROGUE -> {
                handled += "компетентность"
                result += expertiseChoice(rules, byLevel(level, 1 to 2, 6 to 4))
            }

            CharacterClass.RANGER -> {
                rules.feature("Избранный враг")?.let { feature ->
                    handled += "избранный враг"
                    result += ChoiceSpec(
                        id = "class.favored_enemy",
                        source = ChoiceSource.CLASS,
                        title = "Избранный враг",
                        kind = ChoiceKind.OPTION,
                        count = byLevel(level, 1 to 1, 6 to 2, 14 to 3),
                        sourceText = feature.text,
                        options = FAVORED_ENEMIES.mapIndexed { index, name -> ChoiceOption("fe$index", name) },
                    )
                }
                rules.feature("Исследователь природы")?.let { feature ->
                    handled += "исследователь природы"
                    result += ChoiceSpec(
                        id = "class.favored_terrain",
                        source = ChoiceSource.CLASS,
                        title = "Известная местность",
                        kind = ChoiceKind.OPTION,
                        count = byLevel(level, 1 to 1, 6 to 2, 10 to 3),
                        sourceText = feature.text,
                        options = TERRAINS.mapIndexed { index, name -> ChoiceOption("t$index", name) },
                    )
                }
            }

            CharacterClass.ARTIFICER -> {
                val infusions = rules.tableValue(level, "Известные инфузии")?.toIntOrNull() ?: 0
                if (infusions > 0) {
                    handled += "инфузирование предмета"
                    optionChoice(
                        id = "class.infusions",
                        source = ChoiceSource.CLASS,
                        title = "Инфузии",
                        count = infusions,
                        options = rules.optionList("инфуз"),
                        sourceText = rules.feature("Инфузирование предмета")?.text.orEmpty(),
                    )
                }
            }

            else -> Unit
        }

        // Ученик войны (мастер боевых искусств): инструмент ремесленника на выбор.
        subclass?.features?.firstOrNull { it.title.lowercase().startsWith("ученик войны") && it.level <= level }?.let {
            handled += "ученик войны"
            result += ChoiceSpec(
                id = "subclass.student_of_war",
                source = ChoiceSource.SUBCLASS,
                title = "Ученик войны: инструменты",
                kind = ChoiceKind.TOOL,
                count = 1,
                hint = "Один вид ремесленных инструментов",
                sourceText = it.text,
            )
        }

        // Остальные умения с выбором «одного из следующих вариантов».
        fun generic(features: List<RuleFeature>, source: ChoiceSource, prefix: String) {
            features.filter { it.level <= level && (showOptional || !it.extended) }.forEachIndexed { index, feature ->
                val title = feature.title.lowercase()
                if (handled.any { title.startsWith(it) }) return@forEachIndexed
                if (ClassRulesParser.isOneOfChoice(feature)) {
                    optionChoice("$prefix.feature.$index", source, feature.title, 1, feature.parts, feature.text)
                }
                result += FeatureRules.choices(feature, "$prefix.text.$index", source, classSkills)
            }
        }
        generic(rules.features, ChoiceSource.CLASS, "class")
        subclass?.let { generic(it.features, ChoiceSource.SUBCLASS, "subclass") }

        return result
    }

    private val TERRAINS = listOf("Арктика", "Болота", "Горы", "Леса", "Луга", "Побережье", "Подземье", "Пустыня")

    /** Виды избранного врага из «Книги игрока»; гуманоиды — парой рас. */
    private val FAVORED_ENEMIES = listOf(
        "Аберрации", "Великаны", "Драконы", "Звери", "Исчадия", "Конструкты", "Монстры",
        "Небожители", "Нежить", "Растения", "Слизи", "Феи", "Элементали", "Гуманоиды (две расы)",
    )

    private fun expertiseChoice(rules: ClassRules, count: Int) = ChoiceSpec(
        id = "class.expertise",
        source = ChoiceSource.CLASS,
        title = "Компетентность",
        kind = ChoiceKind.EXPERTISE,
        count = count,
        hint = "Навыки, которыми вы владеете: бонус мастерства удваивается",
        sourceText = rules.feature("Компетентность")?.text.orEmpty(),
    )

    /** Инструменты класса «на ваш выбор»: «Три музыкальных инструмента на ваш выбор». */
    private fun toolChoice(rules: ClassRules): ChoiceSpec? {
        val plain = MarkupText.plain(rules.tools)
        val lower = plain.lowercase()
        if (!(lower.contains("выбор") || lower.contains("выберите"))) return null
        val part = plain.split(',', ';').map { it.trim() }.firstOrNull { p ->
            val l = p.lowercase()
            l.contains("выбор") || l.contains("выберите")
        } ?: plain
        val count = MarkupText.firstNumber(part.lowercase().removePrefix("выберите").trim())?.takeIf { it in 1..4 } ?: 1
        return ChoiceSpec(
            id = "class.tools",
            source = ChoiceSource.CLASS,
            title = "Инструменты класса",
            kind = ChoiceKind.TOOL,
            count = count,
            hint = part,
            sourceText = rules.tools,
        )
    }



    // endregion

    // region Ресурсы

    /**
     * Ресурсы класса и архетипа на уровне.
     *
     * @param showOptional учитывать опциональные умения (Tasha): без переключателя их заряды
     * («Предпочтительный противник», «Изначальная осведомлённость») не должны попадать в лист.
     */
    fun resources(rules: ClassRules, subclass: SubclassRules?, level: Int, showOptional: Boolean): List<ResourceSpec> {
        val result = mutableListOf<ResourceSpec>()
        fun add(name: String, maximum: Int, formula: String = "", description: String = "", source: ChoiceSource = ChoiceSource.CLASS) {
            if (maximum > 0) result += ResourceSpec(name, description, maximum, formula, source)
        }
        fun text(prefix: String) = rules.feature(prefix)?.text?.let(::shortText).orEmpty()

        when (rules.type) {
            CharacterClass.BARBARIAN -> {
                rules.tableValue(level, "Ярость")?.toIntOrNull()?.let {
                    add("Ярость", it, description = text("Ярость"))
                }
            }
            CharacterClass.BARD -> add(
                name = "Вдохновение барда (${byLevelDie(level, 1 to 6, 5 to 8, 10 to 10, 15 to 12)})",
                maximum = 1,
                formula = "[cha]",
                description = text("Вдохновение барда"),
            )
            CharacterClass.CLERIC -> if (level >= 2) {
                add("Божественный канал", byLevel(level, 2 to 1, 6 to 2, 18 to 3), description = text("Божественный канал"))
            }
            CharacterClass.DRUID -> if (level >= 2) add("Дикий облик", 2, description = text("Дикий облик"))
            CharacterClass.FIGHTER -> {
                add("Второе дыхание", 1, description = text("Второе дыхание"))
                if (level >= 2) add("Всплеск действий", byLevel(level, 2 to 1, 17 to 2), description = text("Всплеск действий"))
                if (level >= 9) add("Упорный", byLevel(level, 9 to 1, 13 to 2, 17 to 3), description = text("Упорный"))
                if (subclass?.title?.lowercase()?.startsWith("мастер боевых") == true && level >= 3) {
                    add(
                        name = "Кости превосходства (${byLevelDie(level, 3 to 8, 10 to 10, 18 to 12)})",
                        maximum = byLevel(level, 3 to 4, 7 to 5, 15 to 6),
                        description = subclass.features.firstOrNull { it.title.lowercase().startsWith("боевое превосходство") }
                            ?.text?.let(::shortText).orEmpty(),
                        source = ChoiceSource.SUBCLASS,
                    )
                }
            }
            CharacterClass.MONK -> if (level >= 2) add("Очки ци", level, "[level]", text("Ци"))
            CharacterClass.PALADIN -> {
                add("Божественное чувство", 1, "1 + [cha]", text("Божественное чувство"))
                add("Наложение рук", level * 5, "[level] * 5", text("Наложение рук"))
                if (level >= 3) add("Божественный канал", 1, description = text("Божественный канал"))
            }
            CharacterClass.SORCERER -> if (level >= 2) add("Единицы чародейства", level, "[level]", text("Источник магии"))
            CharacterClass.WIZARD -> add("Магическое восстановление", 1, description = text("Магическое восстановление"))
            CharacterClass.ARTIFICER -> if (level >= 7) add("Проблеск гениальности", 1, "[int]", text("Проблеск гениальности"))
            CharacterClass.ROGUE -> if (level >= 20) add("Удача", 1, description = text("Удача"))
            else -> rules.features.filter { it.level <= level && (showOptional || !it.extended) }.forEach { feature ->
                result += FeatureResources.detect(feature, ChoiceSource.CLASS, level)
            }
        }

        subclass?.features?.filter { it.level <= level && (showOptional || !it.extended) }?.forEach { feature ->
            result += FeatureResources.detect(feature, ChoiceSource.SUBCLASS, level)
        }
        return result
    }

    /**
     * Ресурсы и заклинания из выбранных вариантов умений: воззвание «Вор пяти судеб» даёт
     * заклинание порча раз до продолжительного отдыха, «Видения далёких королевств» — магический
     * глаз без ограничений. Варианты сами по себе не умения, поэтому разбираются по выбору.
     */
    fun selectedOptionEffects(
        choices: List<ChoiceSpec>,
        selections: Map<String, List<String>>,
        level: Int,
    ): Pair<List<ResourceSpec>, List<String>> {
        val resources = mutableListOf<ResourceSpec>()
        val spells = mutableListOf<String>()
        // Инфузии дают свойства предмету, а не персонажу: их заклинания и заряды живут в предмете.
        choices.filter { it.kind == ChoiceKind.OPTION && !it.id.contains("infusion") }.forEach { choice ->
            selections[choice.id].orEmpty().forEach { id ->
                val option = choice.options.firstOrNull { it.id == id } ?: return@forEach
                val feature = RuleFeature(title = option.title, text = option.text, level = 1)
                resources += FeatureResources.detect(feature, choice.source, level)
                spells += FeatureSpells.parse(option.text, "${choice.id}.$id", choice.source, option.title).spells
                    .filter { it.minLevel <= level }
                    .map { it.name }
            }
        }
        return resources to spells.distinctBy { it.lowercase() }
    }

    /** Описание ресурса — текст умения как есть, без строки уровня и разметки. */
    private fun shortText(text: String): String = CharacterAssembler.noteText(text)

    // endregion

    // region Заклинательство

    /** Наивысший круг заклинаний класса на уровне. */
    fun maxCircle(type: CharacterClass, level: Int): Int =
        spellSlotsFor(listOf(CharacterClassLevel(type, level))).keys.maxOrNull() ?: 0

    /**
     * Заклинания из умений класса и архетипа.
     *
     * - Умения с заклинаниями в тексте: «Техника тени» монаха (за очки ци), «Дополнительный
     *   заговор» домена света, «Искатель духов» тотемного воина, «Сила великана» (выбор).
     * - Таблицы «Уровень | Заклинания» у классов, которые не готовят заклинания из списка:
     *   заклинания архетипа следопыта, псионические заклинания чародея. У готовящих классов
     *   такие таблицы попадают в «всегда подготовленные» ([spellcasting]).
     * - Несколько таблиц с подписями (круг земли: Болото, Гора…) и таблица вариантов
     *   (божественная душа: склонность) — выбор варианта, см. [tableVariantChoice].
     */
    fun featureSpells(rules: ClassRules, subclass: SubclassRules?, level: Int, showOptional: Boolean): FeatureSpells.Parsed {
        val spells = mutableListOf<GrantedSpell>()
        val choices = mutableListOf<ChoiceSpec>()
        fun scan(features: List<RuleFeature>, source: ChoiceSource, prefix: String) {
            features.filter { it.level <= level && (showOptional || !it.extended) && !FeatureRules.isMeta(it, rules) }
                .forEachIndexed { index, feature ->
                    val parsed = FeatureSpells.parse(feature.text, "$prefix.fs$index", source, feature.title, feature.level)
                    spells += parsed.spells
                    choices += parsed.choices
                }
        }
        scan(rules.features, ChoiceSource.CLASS, "class")
        if (subclass != null) {
            scan(subclass.features, ChoiceSource.SUBCLASS, "subclass")
            val tables = FeatureSpells.subclassTables(spellFeatures(subclass)).filter { it.featureLevel <= level }
            tableVariantChoice(tables, subclass, level)?.let(choices::add)
            if (!rules.type.preparesFromClassList && rules.type != CharacterClass.WIZARD) {
                tables.filter { it.kind == FeatureSpells.TableKind.CLASS_LEVEL && it.label.isBlank() }
                    .forEach { table -> spells += FeatureSpells.tableSpells(table.rows, level).map { GrantedSpell(it, 1) } }
            }
        }
        return FeatureSpells.Parsed(spells.distinctBy { it.name.lowercase() }, choices)
    }

    /**
     * Выбор варианта таблицы заклинаний архетипа: местность круга земли, склонность
     * божественной души. id варианта — подпись; в описании — заклинания на текущем уровне.
     */
    private fun tableVariantChoice(tables: List<FeatureSpells.SpellTable>, subclass: SubclassRules, level: Int): ChoiceSpec? {
        val labelled = tables.filter { it.label.isNotBlank() && it.kind == FeatureSpells.TableKind.CLASS_LEVEL }
        if (labelled.size >= 2) {
            return ChoiceSpec(
                id = VARIANT_CHOICE_ID,
                source = ChoiceSource.SUBCLASS,
                title = "${subclass.title}: заклинания",
                kind = ChoiceKind.OPTION,
                count = 1,
                options = labelled.map { table ->
                    ChoiceOption(
                        id = table.label,
                        title = table.label,
                        text = FeatureSpells.tableSpells(table.rows, level).joinToString(", "),
                    )
                },
            )
        }
        val variants = tables.firstOrNull { it.kind == FeatureSpells.TableKind.VARIANT } ?: return null
        return ChoiceSpec(
            id = VARIANT_CHOICE_ID,
            source = ChoiceSource.SUBCLASS,
            title = "${subclass.title}: ${MarkupText.plain(variants.rows.first().first()).lowercase()}",
            kind = ChoiceKind.OPTION,
            count = 1,
            options = variants.rows.drop(1).map { row ->
                val label = MarkupText.plain(row.first())
                ChoiceOption(id = label, title = label, text = MarkupText.plain(row.getOrElse(1) { "" }))
            },
        )
    }

    /**
     * Умения архетипа вместе со вступлением: у части архетипов таблица заклинаний
     * стоит до первого умения.
     */
    private fun spellFeatures(subclass: SubclassRules?): List<RuleFeature> =
        subclass?.let { listOf(RuleFeature(title = it.title, text = it.text)) + it.features }.orEmpty()

    /** Заклинания выбранного варианта таблицы (местность, склонность). */
    fun variantSpells(subclass: SubclassRules?, level: Int, selections: Map<String, List<String>>): List<String> {
        val chosen = selections[VARIANT_CHOICE_ID]?.firstOrNull() ?: return emptyList()
        val tables = FeatureSpells.subclassTables(spellFeatures(subclass)).filter { it.featureLevel <= level }
        tables.firstOrNull { it.label == chosen && it.kind == FeatureSpells.TableKind.CLASS_LEVEL }?.let {
            return FeatureSpells.tableSpells(it.rows, level)
        }
        val variants = tables.firstOrNull { it.kind == FeatureSpells.TableKind.VARIANT } ?: return emptyList()
        return variants.rows.drop(1)
            .firstOrNull { MarkupText.plain(it.first()) == chosen }
            ?.let { MarkupText.spellNameCandidates(it.getOrElse(1) { "" }) }
            .orEmpty()
    }

    /** id выбора варианта таблицы заклинаний архетипа. */
    const val VARIANT_CHOICE_ID = "subclass.spell_variant"

    fun spellcasting(
        rules: ClassRules,
        subclass: SubclassRules?,
        level: Int,
        scores: Map<AbilityType, Int>,
        selections: Map<String, List<String>> = emptyMap(),
    ): SpellcastingPlan? {
        val type = rules.type
        val code = type.spellListCode
        val classAbility = ClassRulesParser.spellAbility(type)
        if (code != null && classAbility != null) {
            val slots = spellSlotsFor(listOf(CharacterClassLevel(type, level)))
            val maxCircle = slots.keys.maxOrNull() ?: 0
            val mod = abilityModifier(scores[classAbility] ?: DEFAULT_SCORE)
            val cantrips = rules.tableValue(level, "Известные заговоры")?.toIntOrNull() ?: 0
            val knownColumn = rules.tableValue(level, "Известные заклинания")?.toIntOrNull()
            val secrets = if (type == CharacterClass.BARD) byLevel(level, 10 to 2, 14 to 4, 18 to 6) else 0
            val known = when (type) {
                CharacterClass.BARD, CharacterClass.SORCERER, CharacterClass.WARLOCK, CharacterClass.RANGER ->
                    ((knownColumn ?: 0) - secrets).coerceAtLeast(0)
                else -> null
            }
            val spellbook = if (type == CharacterClass.WIZARD) 6 + 2 * (level - 1) else null
            val prepared = if (maxCircle == 0) 0 else when (type) {
                CharacterClass.CLERIC, CharacterClass.DRUID, CharacterClass.WIZARD -> (mod + level).coerceAtLeast(1)
                CharacterClass.PALADIN, CharacterClass.ARTIFICER -> (mod + level / 2).coerceAtLeast(1)
                else -> 0
            }
            return SpellcastingPlan(
                classCode = code,
                ability = classAbility,
                cantrips = cantrips,
                knownSpells = known,
                spellbookSpells = spellbook,
                maxSpellLevel = maxCircle,
                preparesFromClassList = type.preparesFromClassList,
                preparedCount = prepared,
                // Заклинания домена/клятвы/специальности и выбранной местности круга земли.
                alwaysPrepared = (
                    subclass?.let { alwaysPrepared(it, level, type) }.orEmpty() +
                        if (type.preparesFromClassList) variantSpells(subclass, level, selections) else emptyList()
                    ).distinct(),
                slots = slots,
                extraSpellNames = if (type == CharacterClass.WARLOCK) {
                    subclass?.let { expandedList(it, maxCircle) }.orEmpty()
                } else {
                    emptyList()
                },
            )
        }

        // Треть-заклинатели: мистический рыцарь, мистический ловкач — таблица в тексте архетипа.
        val table = subclass?.let { sub ->
            MarkupText.tables(sub.text).firstOrNull { rows ->
                rows.firstOrNull()?.any { MarkupText.plain(it).equals("Известные заговоры", ignoreCase = true) } == true
            }
        } ?: return null
        val headers = table.first().map(MarkupText::plain)
        val row = table.drop(1).firstOrNull { it.firstOrNull()?.trim()?.toIntOrNull() == level } ?: return null
        fun column(name: String) = headers.indexOfFirst { it.equals(name, ignoreCase = true) }
            .takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val slots = (1..9).mapNotNull { circle ->
            val index = headers.indexOf(circle.toString())
            val count = row.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
            if (index >= 0 && count > 0) circle to count else null
        }.toMap()
        val cantrips = column("Известные заговоры")
        val known = column("Известные заклинания")
        if (cantrips == 0 && known == 0) return null
        val lowerTitle = subclass.title.lowercase()
        val schools = when {
            lowerTitle.contains("рыцар") -> setOf("evo", "abj")
            lowerTitle.contains("ловкач") -> setOf("enc", "ill")
            else -> emptySet()
        }
        return SpellcastingPlan(
            classCode = "wizard",
            ability = AbilityType.INTELLIGENCE,
            cantrips = cantrips,
            knownSpells = known,
            spellbookSpells = null,
            maxSpellLevel = slots.keys.maxOrNull() ?: 0,
            preparesFromClassList = false,
            preparedCount = 0,
            schools = schools,
            slots = slots,
        )
    }

    /** Заклинания домена или клятвы, всегда подготовленные на текущем уровне. */
    private fun alwaysPrepared(subclass: SubclassRules, level: Int, type: CharacterClass): List<String> {
        if (type != CharacterClass.CLERIC && type != CharacterClass.PALADIN &&
            type != CharacterClass.DRUID && type != CharacterClass.ARTIFICER
        ) {
            return emptyList()
        }
        // Таблица без подписи: у круга земли таблицы подписаны местностями и выбираются отдельно.
        val table = FeatureSpells.subclassTables(spellFeatures(subclass)).firstOrNull {
            it.kind == FeatureSpells.TableKind.CLASS_LEVEL && it.label.isBlank()
        } ?: return emptyList()
        return FeatureSpells.tableSpells(table.rows, level)
    }

    /** Расширенный список заклинаний покровителя колдуна до доступного круга. */
    private fun expandedList(subclass: SubclassRules, maxCircle: Int): List<String> {
        val texts = listOf(subclass.text) + subclass.features.map { it.text }
        val table = texts.asSequence().flatMap { MarkupText.tables(it) }.firstOrNull { rows ->
            val header = rows.firstOrNull()?.map { MarkupText.plain(it).lowercase() }.orEmpty()
            header.size == 2 && header[1].contains("заклинани")
        } ?: return emptyList()
        return table.drop(1)
            .filter { row -> (Regex("\\d+").find(row.first())?.value?.toIntOrNull() ?: Int.MAX_VALUE) <= maxCircle }
            .flatMap { MarkupText.spellNameCandidates(it.getOrElse(1) { "" }) }
    }

    // endregion

    /**
     * Владения без выбора из умений класса и архетипа на уровне: «Бонусное владение»
     * домена бури, «Дополнительные навыки» коллегии доблести, «Друидический язык».
     */
    fun featureGrants(rules: ClassRules, subclass: SubclassRules?, level: Int, showOptional: Boolean): FeatureGrants {
        val features = rules.features.filter { it.level <= level && (showOptional || !it.extended) } +
            subclass?.features.orEmpty().filter { it.level <= level && (showOptional || !it.extended) }
        return features.fold(FeatureGrants()) { acc, feature -> acc + FeatureRules.grants(feature.text) }
    }

    /** Значение по ступеням: `byLevel(5, 3 to 2, 10 to 3)` → 2. */
    fun byLevel(level: Int, vararg steps: Pair<Int, Int>): Int =
        steps.filter { level >= it.first }.maxByOrNull { it.first }?.second ?: 0

    private fun byLevelDie(level: Int, vararg steps: Pair<Int, Int>): String = "к${byLevel(level, *steps)}"

    private const val DEFAULT_SCORE = 10

    /** Навыки, которые нельзя выбрать для экспертизы без владения, — для подсказки в интерфейсе. */
    fun expertiseCandidates(proficient: Set<SkillType>): List<SkillType> = SkillType.entries.filter { it in proficient }
}

/**
 * Поиск ограниченных использований в тексте умения или особенности.
 *
 * Особенность, позволяющая накладывать заклинания «раз до отдыха», даёт отдельный ресурс
 * на каждое заклинание: у тифлинга «Дьявольское наследие» — это «Адское возмездие» с 3-го
 * уровня и «Тьма» с 5-го, и каждое восстанавливается само по себе («…прежде чем сможете
 * вновь наложить его таким образом»). Общий ресурс — только если текст прямо говорит,
 * что ограничено использование самой особенности.
 */
object FeatureResources {

    private val PB_USES = Regex("количество раз,? равное (?:вашему )?бонусу мастерства")
    private val MOD_USES = Regex("количество раз,? равное (1 \\+ )?(?:вашему )?модификатор\\S* (\\S+)")
    private val REST = Regex(
        "(закончите|закончить|окончите|окончить|окончания|завершите|завершить|завершения|после)\\s+" +
            "(?:короткий или |короткого или |короткого либо )?" +
            "продолжительн\\S+ отдых",
    )

    /** Ограничено использование особенности целиком, а не каждого заклинания. */
    private val SHARED_USE = Regex(
        "(?:использовать|применить|применять)\\s+(?:эту|данную)\\s+особенность\\s+(?:снова|повторно|вновь|ещё раз|еще раз)",
    )

    /**
     * Заклинание из предложения накладывается без ограничений — ресурс не нужен: «неограниченно»,
     * «по желанию», а также заговоры и известные заклинания («вы знаете заговор пляшущие
     * огоньки» в «Магии дроу» рядом с огнём фей раз до отдыха).
     */
    private val UNLIMITED = Regex("неограниченн|по желанию|заговор|знаете|известен|известн")

    private val ONCE_MARKERS = listOf("снова", "повторно", "вновь", "ещё раз", "еще раз", "один раз")

    /**
     * Ресурсы умения на уровне [level].
     *
     * @param level уровень персонажа: заклинания «начиная с 5-го уровня» раньше ресурса не дают.
     */
    fun detect(feature: RuleFeature, source: ChoiceSource, level: Int = MAX_LEVEL): List<ResourceSpec> {
        val plain = MarkupText.plain(feature.text)
        val lower = plain.lowercase()
        if (!REST.containsMatchIn(lower)) return emptyList()
        // Описание — текст умения как есть: условие восстановления в нём уже есть.
        val description = CharacterAssembler.noteText(feature.text)
        PB_USES.find(lower)?.let {
            return listOf(ResourceSpec(feature.title, description, 2, "[pb]", source))
        }
        MOD_USES.find(lower)?.let { match ->
            val ability = MarkupText.ability(match.groupValues[2])
            if (ability != null) {
                val code = listOf("str", "dex", "con", "int", "wis", "cha")[ability.ordinal]
                val formula = if (match.groupValues[1].isNotEmpty()) "1 + [$code]" else "[$code]"
                return listOf(ResourceSpec(feature.title, description, 1, formula, source))
            }
        }
        // Одноразовое использование до отдыха: «…не сможете сделать это снова, пока не закончите…».
        if (ONCE_MARKERS.none { lower.contains(it) }) return emptyList()

        val spells = limitedSpells(feature, source, level)
        if (spells.isEmpty() || SHARED_USE.containsMatchIn(lower)) {
            // Заклинания ещё не доступны по уровню — ресурса пока нет вовсе.
            if (spells.isEmpty() && hasSpells(feature, source)) return emptyList()
            return listOf(ResourceSpec(feature.title, description, 1, "", source))
        }
        return spells.map { spell ->
            ResourceSpec(
                name = spell.replaceFirstChar { it.uppercase() },
                // Заголовок — заклинание, поэтому в описании названа особенность-источник.
                description = "${feature.title}. $description",
                maximum = 1,
                source = source,
                spellName = spell,
            )
        }
    }

    /** Заклинания особенности с ограничением «раз до отдыха», доступные на [level]. */
    private fun limitedSpells(feature: RuleFeature, source: ChoiceSource, level: Int): List<String> {
        val parsed = FeatureSpells.parse(feature.text, "res", source, feature.title, feature.level)
        if (parsed.spells.isEmpty()) return emptyList()
        // Заклинания из предложений «неограниченно», «по желанию» — без ресурса.
        val unlimited = FeatureRules.sentences(feature.text, withBullets = true)
            .filter { UNLIMITED.containsMatchIn(it.lowercase()) }
            .flatMap { MarkupText.spellNameCandidates(it) }
            .map { it.lowercase() }
            .toSet()
        // Одно заклинание бывает названо в тексте дважды («Перевёртыш»: «превращение» в начале
        // и «наложив превращение» ниже) — ресурс на него один.
        return parsed.spells
            .filter { it.minLevel <= level && it.name.lowercase() !in unlimited }
            .map { it.name }
            .fold(mutableListOf<String>()) { acc, name ->
                if (acc.none { MarkupText.matchesSpellName(it, name) || MarkupText.matchesSpellName(name, it) }) acc += name
                acc
            }
    }

    private fun hasSpells(feature: RuleFeature, source: ChoiceSource): Boolean =
        FeatureSpells.parse(feature.text, "res", source, feature.title, feature.level).spells.isNotEmpty()

    private const val MAX_LEVEL = 20
}
