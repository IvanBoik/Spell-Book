package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.CharClass
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.util.HtmlUtils

/**
 * Разбор класса из текста dnd.su.
 *
 * Структура страницы класса стабильна:
 * - таблица класса в начале (уровни, умения, столбцы вроде «Известные заговоры»);
 * - часть `# Классовые умения`: врезка «Хиты, владение и снаряжение», затем умения `### `
 *   со строкой уровня `*3-й уровень, умение воина*`; варианты (боевые стили, договоры)
 *   идут подзаголовками без строки уровня сразу после умения;
 * - списки вариантов `## Таинственные воззвания`, `## Боевые приёмы` внутри умений;
 * - часть с архетипами `# Воинские архетипы`, каждый архетип — `## `, его умения — `### `;
 * - часть `# Unearthed Arcana & Unofficial` с неофициальными архетипами.
 */
object ClassRulesParser {

    private val CLASS_BY_NAME: Map<String, CharacterClass> = mapOf(
        "бард" to CharacterClass.BARD,
        "варвар" to CharacterClass.BARBARIAN,
        "воин" to CharacterClass.FIGHTER,
        "волшебник" to CharacterClass.WIZARD,
        "друид" to CharacterClass.DRUID,
        "жрец" to CharacterClass.CLERIC,
        "изобретатель" to CharacterClass.ARTIFICER,
        "колдун" to CharacterClass.WARLOCK,
        "монах" to CharacterClass.MONK,
        "паладин" to CharacterClass.PALADIN,
        "плут" to CharacterClass.ROGUE,
        "следопыт" to CharacterClass.RANGER,
        "чародей" to CharacterClass.SORCERER,
    )

    /** Уровень выбора архетипа; у неизвестных классов — 3. */
    private val SUBCLASS_LEVEL: Map<CharacterClass, Int> = mapOf(
        CharacterClass.CLERIC to 1,
        CharacterClass.SORCERER to 1,
        CharacterClass.WARLOCK to 1,
        CharacterClass.DRUID to 2,
        CharacterClass.WIZARD to 2,
    )

    private val SERVICE_TITLES = listOf("хиты", "владение", "снаряжение")

    /** Заголовки частей с неофициальными архетипами («# Unearthed Arcana & Unofficial», огнестрельное оружие DMG). */
    private val UNOFFICIAL_MARKERS = UnofficialContent.BOOK_MARKERS + "огнестрел"

    /** Строка требования: `*Требование: 5-й уровень колдуна, умение «Договор клинка»*`. */
    private val REQUIREMENT = Regex("^\\*?\\s*Требование\\s*:\\s*(.+?)\\*?$")
    private val LEVEL_IN_TEXT = Regex("(\\d+)\\s*-?\\s*[а-я]*\\s+уровень")
    private val BOLD_ENTRY = Regex("^\\*\\*([^*]+?)\\*\\*\\s*[.:]\\s*(.*)$")

    /** Признак умения с выбором одного варианта из вложенных. */
    private val ONE_OF = Regex("(одн\\S*|один)\\s+из\\s+(следующ|перечисл|вариант)")

    /** Класс из перечисления по названию без разбора описания; [CharacterClass.OTHER] — неизвестный класс. */
    fun classType(name: String): CharacterClass = CLASS_BY_NAME[name.trim().lowercase()] ?: CharacterClass.OTHER

    fun parse(charClass: CharClass): ClassRules {
        val description = charClass.description
        val nodes = MarkupText.outline(description)
        val type = classType(charClass.name)

        // Таблица класса — первая таблица с уровнями.
        val table = MarkupText.tables(description).firstOrNull { rows ->
            rows.firstOrNull()?.firstOrNull()?.let { MarkupText.plain(it).lowercase().startsWith("ур") } == true
        }.orEmpty()
        val headers = table.firstOrNull().orEmpty().map(MarkupText::headerText)
        val rows = table.drop(1)
        val featuresColumn = headers.indexOfFirst { it.equals("Умения", ignoreCase = true) }
        val levelFeatures = rows.mapNotNull { row ->
            val level = row.firstOrNull()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val names = row.getOrNull(featuresColumn)
                ?.split(',')
                ?.map { MarkupText.plain(it).trim() }
                ?.filter { it.isNotEmpty() && it != "-" }
                .orEmpty()
            level to names
        }.toMap()
        val asiLevels = levelFeatures.filterValues { names ->
            names.any { it.lowercase().startsWith("увеличение характеристик") }
        }.keys.sorted()

        // Поля «Хиты, владение и снаряжение».
        val fields = mutableMapOf<String, String>()
        description.lines().forEach { raw ->
            val match = Regex("^\\*\\*([^*]+?)\\*\\*\\s*:\\s*(.*)$").matchEntire(raw.trim()) ?: return@forEach
            val key = match.groupValues[1].trim().lowercase()
            if (key !in fields) fields[key] = match.groupValues[2].trim()
        }
        val hitDie = fields["кость хитов"]?.let { Regex("к(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: DEFAULT_HIT_DIE
        val saves = MarkupText.abilities(fields["спасброски"].orEmpty()).toSet()
        val (armor, _) = parseArmor(fields["доспехи"].orEmpty())
        val (weapons, otherWeapons) = parseWeapons(fields["оружие"].orEmpty())
        val toolsText = fields["инструменты"].orEmpty()
        val skillChoice = parseSkillChoice(fields["навыки"].orEmpty())
        val (equipment, startingGold) = parseEquipment(description)

        // Умения класса и архетипы.
        val featuresStart = nodes.indexOfFirst { it.level == 1 && it.title.lowercase().contains("классовые умения") }
        val features = mutableListOf<RuleFeature>()
        val optionLists = mutableMapOf<String, List<ChoiceOption>>()
        var zoneEnd = nodes.size
        if (featuresStart >= 0) {
            var index = featuresStart + 1
            while (index < nodes.size) {
                val node = nodes[index]
                val level = MarkupText.featureLevel(node.body)
                when {
                    node.level == 1 && level == null -> { zoneEnd = index; break }
                    node.level == 2 && level == null -> {
                        val (entries, next) = collectEntries(nodes, index, extended = false)
                        optionLists[MarkupText.titleCase(node.title)] = entries
                        index = next
                        continue
                    }
                    SERVICE_TITLES.any { node.title.lowercase().startsWith(it) } -> Unit
                    level != null -> features += RuleFeature(
                        title = MarkupText.titleCase(node.title),
                        text = node.body,
                        level = level,
                        extended = MarkupText.isOptionalFeature(node.body),
                        replaces = replacedFeatures(node.body),
                    )
                    features.isNotEmpty() -> {
                        val last = features.removeAt(features.lastIndex)
                        features += last.copy(parts = last.parts + optionFrom(node, last.parts.size))
                    }
                }
                index++
            }
        }

        val subclassLevel = SUBCLASS_LEVEL[type] ?: DEFAULT_SUBCLASS_LEVEL
        val subclasses = mutableListOf<SubclassRules>()
        var groupTitle = ""
        var unofficial = false
        var index = zoneEnd
        while (index < nodes.size) {
            val node = nodes[index]
            val lowerTitle = node.title.lowercase()
            when (node.level) {
                1 -> {
                    if (UNOFFICIAL_MARKERS.any { lowerTitle.contains(it) }) {
                        unofficial = true
                    } else if (groupTitle.isEmpty()) {
                        groupTitle = node.title
                    }
                }
                2 -> {
                    if (lowerTitle.contains("воззван") || lowerTitle.contains("боевые стили")) {
                        val (entries, next) = collectEntries(nodes, index, extended = true)
                        val key = optionLists.keys.firstOrNull { key ->
                            lowerTitle.contains(key.lowercase().take(KEY_PREFIX_LENGTH))
                        } ?: MarkupText.titleCase(node.title)
                        optionLists[key] = optionLists[key].orEmpty() + entries.map { it.copy(id = "ua.${it.id}") }
                        index = next
                        continue
                    }
                    val subclassFeatures = mutableListOf<RuleFeature>()
                    var child = index + 1
                    while (child < nodes.size && nodes[child].level == 3) {
                        val featureNode = nodes[child]
                        val level = MarkupText.featureLevel(featureNode.body)
                        if (level != null || subclassFeatures.isEmpty()) {
                            subclassFeatures += RuleFeature(
                                title = MarkupText.titleCase(featureNode.title),
                                text = featureNode.body,
                                level = level ?: subclassLevel,
                            )
                        } else {
                            val last = subclassFeatures.removeAt(subclassFeatures.lastIndex)
                            subclassFeatures += last.copy(parts = last.parts + optionFrom(featureNode, last.parts.size))
                        }
                        child++
                    }
                    val extended = unofficial || node.body.lowercase().contains("unearthed")
                    subclasses += SubclassRules(
                        id = "sub.$index",
                        title = node.title,
                        text = node.body,
                        extended = extended,
                        features = subclassFeatures,
                    )
                    index = child
                    continue
                }
            }
            index++
        }

        return ClassRules(
            charClass = charClass,
            type = type,
            hitDie = hitDie,
            savingThrows = saves,
            armor = armor,
            weapons = weapons,
            otherWeapons = otherWeapons,
            tools = toolsText.takeUnless { MarkupText.plain(it).lowercase().startsWith("нет") }.orEmpty(),
            skillChoice = skillChoice,
            equipment = equipment,
            startingGold = startingGold,
            levelFeatures = levelFeatures,
            tableHeaders = headers,
            tableRows = rows,
            features = features,
            subclassLevel = subclassLevel,
            subclassGroupTitle = groupTitle.ifBlank { "Архетип" },
            subclasses = subclasses,
            asiLevels = asiLevels,
            optionLists = optionLists,
            extended = UnofficialContent.isUnofficialClass(charClass),
        )
    }

    /**
     * Умения, которые заменяет опциональное, из строки уровня: `*1-й уровень, опциональное
     * умение следопыта, заменяющее умение «Избранный враг» и работающее вместе с «Убийцей врагов»*`.
     * Умения после «работающее вместе с» не заменяются.
     */
    private fun replacedFeatures(body: String): List<String> {
        val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return emptyList()
        if (!MarkupText.isOptionalFeature(body)) return emptyList()
        val lower = MarkupText.plain(first).lowercase()
        val clause = lower.substringAfter("заменя", "").substringBefore("работающ")
        if (clause.isEmpty()) return emptyList()
        return Regex("«([^»]+)»").findAll(clause).map { it.groupValues[1].trim() }.toList()
    }

    /** Варианты умения с выбором одного из вложенных: «Выберите один из следующих вариантов». */
    fun isOneOfChoice(feature: RuleFeature): Boolean =
        feature.parts.size >= 2 && ONE_OF.containsMatchIn(MarkupText.plain(feature.text).lowercase())

    // region Варианты

    private fun optionFrom(node: MarkupText.Node, index: Int): ChoiceOption {
        val firstLine = node.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val requirement = REQUIREMENT.matchEntire(firstLine)?.groupValues?.get(1)?.let(MarkupText::plain).orEmpty()
        return ChoiceOption(
            id = "p$index",
            title = MarkupText.titleCase(node.title.replace(Regex("\\s*\\(\\d+-й уровень\\)"), "")),
            text = node.body,
            requiredLevel = LEVEL_IN_TEXT.find(requirement)?.groupValues?.get(1)?.toIntOrNull(),
            requirement = requirement,
            extended = firstLine.startsWith("*") && firstLine.lowercase().contains("опциональн"),
        )
    }

    /**
     * Список вариантов раздела `## ` (воззвания, приёмы, инфузии): либо вложенные `### `,
     * либо строки `**Название**. Текст` (боевые приёмы).
     *
     * @return варианты и индекс узла после раздела.
     */
    private fun collectEntries(
        nodes: List<MarkupText.Node>,
        start: Int,
        extended: Boolean,
    ): Pair<List<ChoiceOption>, Int> {
        val entries = mutableListOf<ChoiceOption>()
        fun addBold(body: String, optional: Boolean) {
            body.lines().forEach { raw ->
                val match = BOLD_ENTRY.matchEntire(raw.trim()) ?: return@forEach
                val title = match.groupValues[1].trim()
                // Строки вида «**Источник**: …» — служебные.
                if (title.lowercase().startsWith("источник")) return@forEach
                entries += ChoiceOption(
                    id = "e${entries.size}",
                    title = title,
                    text = match.groupValues[2].trim(),
                    extended = optional || extended,
                )
            }
        }
        addBold(nodes[start].body, optional = false)
        var index = start + 1
        // Умение со строкой уровня — уже не вариант, а следующее умение класса
        // (у друида «Изучение дикого облика» стоит посреди умений).
        while (index < nodes.size && nodes[index].level == 3 && MarkupText.featureLevel(nodes[index].body) == null) {
            val node = nodes[index]
            val optional = MarkupText.isOptionalFeature(node.body)
            val boldCount = node.body.lines().count { BOLD_ENTRY.matches(it.trim()) }
            if (boldCount >= 2) {
                addBold(node.body, optional)
            } else {
                entries += optionFrom(node, entries.size).copy(
                    id = "e${entries.size}",
                    extended = optional || extended,
                )
            }
            index++
        }
        return entries to index
    }

    // endregion

    // region Владения и снаряжение

    private fun parseArmor(text: String): Pair<Set<ArmorProficiency>, String> {
        val lower = MarkupText.plain(text).lowercase()
        if (lower.startsWith("нет")) return emptySet<ArmorProficiency>() to ""
        val result = mutableSetOf<ArmorProficiency>()
        if (lower.contains("все") && lower.contains("доспех")) {
            result += listOf(ArmorProficiency.LIGHT, ArmorProficiency.MEDIUM, ArmorProficiency.HEAVY)
        }
        if (lower.contains("лёгк") || lower.contains("легк")) result += ArmorProficiency.LIGHT
        if (lower.contains("средн")) result += ArmorProficiency.MEDIUM
        if (lower.contains("тяжёл") || lower.contains("тяжел")) result += ArmorProficiency.HEAVY
        if (lower.contains("щит")) result += ArmorProficiency.SHIELDS
        return result to ""
    }

    private fun parseWeapons(text: String): Pair<Set<WeaponProficiency>, String> {
        val result = mutableSetOf<WeaponProficiency>()
        val other = mutableListOf<String>()
        MarkupText.plain(text).split(',').map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }.forEach { part ->
            val lower = part.lowercase()
            when {
                lower.startsWith("нет") -> Unit
                lower.startsWith("простое") -> result += WeaponProficiency.SIMPLE
                lower.startsWith("воинское") -> result += WeaponProficiency.MARTIAL
                else -> other += part
            }
        }
        if (other.isNotEmpty()) result += WeaponProficiency.OTHER
        return result to other.joinToString(", ")
    }

    private fun parseSkillChoice(text: String): ChoiceSpec? {
        if (text.isBlank()) return null
        val lower = MarkupText.plain(text).lowercase()
        val count = Regex("выберите\\s+(\\S+)").find(lower)?.let { MarkupText.number(it.groupValues[1]) } ?: return null
        val options = MarkupText.skillRefs(text).ifEmpty {
            if (lower.contains("любы")) emptyList() else MarkupText.skillMentions(text)
        }
        return ChoiceSpec(
            id = "class.skills",
            source = ChoiceSource.CLASS,
            title = "Навыки класса",
            kind = ChoiceKind.SKILL,
            count = count,
            sourceText = text,
            options = options.map { ChoiceOption(it.name, it.name) },
        )
    }

    private val OPTION_MARKER = Regex("(?:^|,?\\s+или\\s+|,\\s*)([абвг])\\)\\s*")
    private val GOLD = Regex("(\\d+)\\s*к\\s*(\\d+)\\s*(?:[×xх*]\\s*(\\d+))?\\s*зм")

    /** Строки снаряжения после заголовка «Снаряжение» и формула золота. */
    private fun parseEquipment(description: String): Pair<List<EquipmentGroup>, String> {
        val lines = description.lines().map { it.trim() }
        val start = lines.indexOfFirst { line ->
            val plain = MarkupText.plain(line.removePrefix(HtmlUtils.SUBHEADING_PREFIX)).lowercase()
            plain == "снаряжение" && (line.startsWith("###") || line.startsWith("**"))
        }
        if (start < 0) return emptyList<EquipmentGroup>() to ""
        val groups = mutableListOf<EquipmentGroup>()
        var gold = ""
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            if (line.startsWith("###") || line.startsWith(":::") || HtmlUtils.isHeading(line)) break
            if (HtmlUtils.isBulletItem(line)) {
                groups += parseEquipmentLine(HtmlUtils.listItemText(line))
            } else if (line.lowercase().contains("зм")) {
                GOLD.find(MarkupText.plain(line).lowercase())?.let { match ->
                    val multiplier = match.groupValues[3].takeIf { it.isNotEmpty() }
                    gold = "${match.groupValues[1]}к${match.groupValues[2]}" + (multiplier?.let { "×$it" } ?: "")
                }
                break
            }
        }
        return groups to gold
    }

    private fun parseEquipmentLine(raw: String): EquipmentGroup {
        val text = MarkupText.plain(raw)
        val markers = OPTION_MARKER.findAll(text).toList()
        if (markers.size < 2) {
            return EquipmentGroup(listOf(EquipmentOption(text, BackgroundRulesParser.splitItems(text))))
        }
        val options = markers.mapIndexed { index, match ->
            val end = markers.getOrNull(index + 1)?.range?.first ?: text.length
            val label = text.substring(match.range.last + 1, end).trim().trimEnd(',', ';').trim()
            EquipmentOption(label, BackgroundRulesParser.splitItems(label))
        }
        return EquipmentGroup(options)
    }

    // endregion

    private const val DEFAULT_HIT_DIE = 8
    private const val DEFAULT_SUBCLASS_LEVEL = 3
    private const val KEY_PREFIX_LENGTH = 8

    /** Базовая характеристика заклинаний класса. */
    fun spellAbility(type: CharacterClass): AbilityType? = when (type) {
        CharacterClass.BARD, CharacterClass.SORCERER, CharacterClass.WARLOCK, CharacterClass.PALADIN -> AbilityType.CHARISMA
        CharacterClass.CLERIC, CharacterClass.DRUID, CharacterClass.RANGER -> AbilityType.WISDOM
        CharacterClass.WIZARD, CharacterClass.ARTIFICER -> AbilityType.INTELLIGENCE
        else -> null
    }
}
