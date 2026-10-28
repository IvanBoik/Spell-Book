package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.WeaponProficiency

/**
 * Владения, которые умение даёт без выбора: навыки, доспехи, оружие, инструменты, языки.
 *
 * @param otherWeapons отдельные виды оружия текстом («длинные мечи и рапиры»).
 */
data class FeatureGrants(
    val skills: Set<SkillType> = emptySet(),
    val armor: Set<ArmorProficiency> = emptySet(),
    val weapons: Set<WeaponProficiency> = emptySet(),
    val otherWeapons: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val saves: Set<AbilityType> = emptySet(),
) {
    val isEmpty: Boolean
        get() = skills.isEmpty() && armor.isEmpty() && weapons.isEmpty() && otherWeapons.isEmpty() &&
            tools.isEmpty() && languages.isEmpty() && saves.isEmpty()

    operator fun plus(other: FeatureGrants) = FeatureGrants(
        skills = skills + other.skills,
        armor = armor + other.armor,
        weapons = weapons + other.weapons,
        otherWeapons = (otherWeapons + other.otherWeapons).distinct(),
        tools = (tools + other.tools).distinct(),
        languages = (languages + other.languages).distinct(),
        saves = saves + other.saves,
    )
}

/**
 * Разбор умений по предложениям: что из текста — владения (они уходят в поля персонажа),
 * что — выбор, а что — служебное описание механики, которому не место в заметках.
 *
 * Работает по предложениям, потому что одно умение часто смешивает владение
 * и настоящую способность: «Вы получаете владение навыками… Ваш бонус мастерства удваивается…».
 */
object FeatureRules {

    // region Предложения о владениях

    private val PROFICIENCY = Regex(
        "владе(?:ни|ете|ть|ва)|говорить, читать и писать|читать и писать|говорить на |" +
            "(?:знаете|изучаете|выучиваете)\\s.*язык",
    )

    /**
     * Предложения, которые только похожи на владение: условные проверки, временные эффекты,
     * правила мультиклассирования, удвоение бонуса мастерства.
     */
    private val NOT_PROFICIENCY = listOf(
        "начальным класс", "проверк", "отдых", "минут", "удва", "бонус мастерства", "преимуществ", "остаются владения",
        // «Если вы владеете простым оружием, можете использовать его…» — условие, а не новое владение.
        "если вы владеете", "которым вы владеете", "которыми вы владеете", "который вы знаете",
        "которым уже владеете", "компетентность",
        "телепат",
        // Временные владения (после транса) и правила замены расы наследием — не выбор при создании.
        "транс", "сохранить", "замените расу", "заменяете расу",
    )

    /**
     * Запасной вариант «если вы уже владеете — выберите другой»: выбор нужен не всегда,
     * поэтому обязательным выбором мастера не становится.
     */
    private val CONDITIONAL = listOf("если вы уже", "если у вас уже", "если вы ими уже", "если вы им уже")

    private val CHOICE_MARKERS = listOf("выбор", "выберите", "из следующ", "одним из", "одного из", "любым", "любой", "любого")

    /**
     * Части предложения: «владение навыком Магия, и вы изучаете два заговора на ваш выбор»,
     * «одним из навыков по вашему выбору и одним инструментом по вашему выбору».
     */
    private val CLAUSE_SPLIT = Regex(
        ",\\s*(?:а также|и вы|а если)\\s+|;\\s*|" +
            "\\s+и\\s+(?=(?:получаете|изучаете|владеете|осваиваете|обучаетесь)\\s)|,\\s*(?=владение\\s)|" +
            "\\s+и\\s+(?=(?:одн|дв|тр|люб)\\S*\\s+(?:\\S+\\s+)?(?:инструмент|навык|язык|игров|музыкальн|ремесленн))",
    )

    /** «из списка навыков, доступных варвару на 1-м уровне» — выбор сужается до навыков класса. */
    private val CLASS_SKILL_LIST = Regex("списк\\S*\\s+(?:классовых\\s+)?навык\\S*[^.]*(?:доступн|класс)|навык\\S*\\s+класса")

    /** Строка-вариант «**Тигр** (…). Вы получаете…»: владение даётся, только если выбран вариант. */
    private val VARIANT_LINE = Regex("^\\*\\*[^*]+\\*\\*\\s*(?:\\([^)]*\\))?\\s*[.:]")

    /**
     * Простые предложения текста без разметки.
     *
     * @param withBullets включать пункты списков. В умениях пунктами обычно идут варианты
     * (тотем тигра, свойства камня преобразования), и владения оттуда не даются сами по себе.
     */
    fun sentences(markup: String, withBullets: Boolean = true): List<String> =
        markup.lines()
            .map { it.trim() }
            .filter {
                withBullets || !(it.startsWith("-") || it.startsWith("•") || it.startsWith("*") && !it.startsWith("**") ||
                    VARIANT_LINE.containsMatchIn(it))
            }
            .map { MarkupText.plain(it.removePrefix("-").removePrefix("•")) }
            .filter { it.isNotBlank() && !it.startsWith("|") && !isLevelLine(it) }
            .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.trim() }
            .filter { it.isNotBlank() }

    /**
     * Запасная альтернатива к фиксированному языку: «на Драконьем языке или на одном другом
     * языке по вашему выбору» — даётся Драконий (запасной вариант нужен, лишь если он уже известен).
     */
    private val FALLBACK_LANGUAGE = Regex("\\s+или\\s+(?:на\\s+)?(?:одн\\S*\\s+)?друг\\S*\\s+язык\\S*[^,.;]*")

    /** Части предложений о владениях (без пунктов списков и условных запасных вариантов). */
    private fun proficiencyClauses(markup: String, withBullets: Boolean = false): List<String> =
        sentences(markup, withBullets = withBullets)
            .filter(::isProficiencySentence)
            .map { it.replace(FALLBACK_LANGUAGE, "") }
            .flatMap { sentence -> sentence.split(CLAUSE_SPLIT) }
            .map { clause ->
                // «…, если вы уже владеете — выберите другой»: хвост про запасной вариант отбрасывается.
                val lower = clause.lowercase()
                val cut = CONDITIONAL.mapNotNull { marker -> lower.indexOf(marker).takeIf { it >= 0 } }.minOrNull()
                (if (cut != null) clause.substring(0, cut) else clause).trim().trimEnd(',')
            }
            // Части наследуют «владение» предложения: «…навыками А и Б, а также одним музыкальным
            // инструментом по вашему выбору» — вторая часть тоже владение.
            .filter { clause ->
                val lower = clause.lowercase()
                clause.isNotBlank() && !lower.contains("заговор") && !lower.contains("заклинани")
            }

    /** Строка уровня «3-й уровень, умение воина» (после снятия выделения). */
    private fun isLevelLine(line: String): Boolean =
        line.length < LEVEL_LINE_MAX && Regex("^\\d+-?[а-я]*\\s+уровень", RegexOption.IGNORE_CASE).containsMatchIn(line)

    fun isProficiencySentence(sentence: String): Boolean {
        val lower = sentence.lowercase()
        return PROFICIENCY.containsMatchIn(lower) && NOT_PROFICIENCY.none { lower.contains(it) }
    }

    private fun isChoiceSentence(lower: String): Boolean =
        CHOICE_MARKERS.any { lower.contains(it) } ||
            // «навыком Убеждение или Выступление» — выбор из перечисленного.
            (lower.contains(" или ") && (MarkupText.skillMentions(lower).size > 1 || ToolCatalog.candidates(lower).size in 2..MAX_LISTED))

    /** Владения без выбора из текста умения. */
    /**
     * @param withBullets разбирать и пункты списков. У черт пункты — это сами эффекты
     * («- Вы получаете владение тяжёлыми доспехами»), а не варианты, как у умений классов.
     */
    fun grants(markup: String, withBullets: Boolean = false): FeatureGrants =
        proficiencyClauses(markup, withBullets).fold(FeatureGrants()) { acc, clause -> acc + clauseGrants(clause) }

    /** Владения одной части предложения; пусто — если это выбор. */
    private fun clauseGrants(sentence: String): FeatureGrants {
        val lower = sentence.lowercase()
        if (isChoiceSentence(lower)) return FeatureGrants()
        val armor = mutableSetOf<ArmorProficiency>()
        val weapons = mutableSetOf<WeaponProficiency>()
        val other = mutableListOf<String>()
        val tools = mutableListOf<String>()
        if (lower.contains("доспех")) {
            if (lower.contains("лёгк") || lower.contains("легк")) armor += ArmorProficiency.LIGHT
            if (lower.contains("средн")) armor += ArmorProficiency.MEDIUM
            if (lower.contains("тяжёл") || lower.contains("тяжел")) armor += ArmorProficiency.HEAVY
        }
        if (Regex("(^|[^а-я])щит").containsMatchIn(lower)) armor += ArmorProficiency.SHIELDS
        if (lower.contains("оружи")) {
            if (lower.contains("воинск")) weapons += WeaponProficiency.MARTIAL
            if (lower.contains("прост")) weapons += WeaponProficiency.SIMPLE
        }
        if (weapons.isEmpty() && WEAPON_WORDS.any { lower.contains(it) }) {
            // «владение средними доспехами и скимитарами» → только «скимитарами».
            Regex("владе(?:ние|ете)\\s+(.+)").find(sentence)?.groupValues?.get(1)
                ?.trimEnd('.')
                ?.split(Regex(",\\s*|\\s+и\\s+"))
                ?.filter { part -> WEAPON_WORDS.any { part.lowercase().contains(it) } }
                ?.let(other::addAll)
        }
        if (lower.contains("инструмент") || lower.contains("набор")) {
            tools += ToolCatalog.findAll(lower).map { it.name }
        }
        // Язык — только когда его именно изучают: «говорить, читать и писать», «знаете язык».
        val learnsLanguage = lower.contains("читать") || lower.contains("владе") && lower.contains("язык") ||
            Regex("(?:знаете|изучаете|выучиваете)\\s.*язык").containsMatchIn(lower)
        val languages = if (learnsLanguage) MarkupText.languages(sentence) else emptyList()
        val saves = when {
            !lower.contains("спасброск") -> emptySet()
            lower.contains("всеми спасброск") -> AbilityType.entries.toSet()
            else -> MarkupText.abilities(lower.substringAfter("спасброск")).toSet()
        }
        return FeatureGrants(
            skills = if (lower.contains("навык")) MarkupText.skillMentions(sentence).toSet() else emptySet(),
            saves = saves,
            armor = armor,
            weapons = weapons,
            otherWeapons = other,
            tools = tools,
            languages = languages,
        )
    }

    /**
     * Выборы навыков, инструментов и языков в тексте умения.
     *
     * @param classSkills навыки класса: из них выбирают, когда умение отсылает к «списку навыков
     * класса» («Первобытное знание» варвара), а не из всех навыков.
     */
    fun choices(
        feature: RuleFeature,
        id: String,
        source: ChoiceSource,
        classSkills: List<ChoiceOption> = emptyList(),
        withBullets: Boolean = false,
    ): List<ChoiceSpec> {
        val result = mutableListOf<ChoiceSpec>()
        proficiencyClauses(feature.text, withBullets).forEachIndexed { index, sentence ->
            val lower = sentence.lowercase()
            if (!isChoiceSentence(lower)) return@forEachIndexed
            when {
                lower.contains("навык") -> {
                    // «двумя из следующих навыков», «тремя навыками», «одним из навыков»: число — до слова «навык».
                    val count = MarkupText.firstNumber(lower.substringBefore("навык").substringAfter("владе"))
                        ?.takeIf { it in 1..MAX_LISTED }
                        ?: 1
                    val listed = MarkupText.skillMentions(sentence)
                    val options = when {
                        listed.size > count -> listed.map { ChoiceOption(it.name, it.name) }
                        CLASS_SKILL_LIST.containsMatchIn(lower) -> classSkills
                        else -> emptyList()
                    }
                    result += ChoiceSpec(
                        id = "$id.skills.$index",
                        source = source,
                        title = "${feature.title}: навыки",
                        kind = ChoiceKind.SKILL,
                        count = count,
                        sourceText = sentence,
                        options = options,
                    )
                }
                lower.contains("инструмент") || lower.contains("набор") -> result += ChoiceSpec(
                    id = "$id.tools.$index",
                    source = source,
                    title = "${feature.title}: инструменты",
                    kind = ChoiceKind.TOOL,
                    count = MarkupText.firstNumber(lower)?.takeIf { it in 1..MAX_LISTED } ?: 1,
                    sourceText = sentence,
                )
                lower.contains("язык") -> {
                    val count = MarkupText.languageChoiceCount(sentence)
                    if (count > 0) {
                        result += ChoiceSpec(
                            id = "$id.languages.$index",
                            source = source,
                            title = "${feature.title}: языки",
                            kind = ChoiceKind.LANGUAGE,
                            count = count,
                            sourceText = sentence,
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * Умение только раздаёт владения (или предлагает их выбрать): всё уже попадает
     * в поля персонажа, и в заметках оно не нужно. «Обострённые чувства. Вы владеете
     * навыком Восприятие» — да; «Благословение знаний» с удвоением бонуса — нет.
     */
    fun isGrantOnly(markup: String): Boolean {
        val sentences = sentences(markup)
        // Считается, только если предложение действительно что-то даёт или предлагает выбрать:
        // «вы владеете своими безоружными ударами (1к4)» — настоящая способность с уроном.
        return sentences.isNotEmpty() && sentences.all { sentence ->
            isProficiencySentence(sentence) &&
                (!clauseGrants(sentence).isEmpty || isChoiceSentence(sentence.lowercase()))
        }
    }

    // endregion

    // region Служебные умения

    /** Умения, которые описывают механику класса, а не способность персонажа (по началу названия). */
    private val META_PREFIXES = listOf(
        "увеличение характеристик", "использование заклинаний", "магия договора",
        "дополнительные заклинания", "расширенный список заклинаний", "заклинания домена", "заклинания круга",
        "заклинания клятвы", "заклинания покровителя", "заклинания происхождения", "заклинания архетипа",
        "заклинания коллегии", "заклинания специальности", "заклинания изобретателя",
        // «Ограничение: только для дварфов» — условие выбора архетипа, а не способность.
        "ограничение",
    )

    /** Особенности расы, которые уже стали полями персонажа или справочными сведениями (название целиком). */
    private val META_EXACT = setOf(
        "языки", "язык", "вид существа", "тип существа", "высота", "вес в фунтах", "модификатор размера",
        "подрасы", "подраса", "дополнительный язык",
    )

    /** Отсылки «выберите архетип — все описаны ниже»: умение лишь просит выбрать подкласс. */
    private val SUBCLASS_POINTERS = listOf(
        "в конце описания класса", "описаны ниже", "приведены ниже", "находятся ниже", "смотрите ниже",
        "раскрыты ниже", "подробно рассказано", "подробнее обо всех", "описаны в конце", "рассмотрены в конце",
        "перечислены в конце", "приведены в конце",
    )

    /**
     * Служебное умение: описание механики (заклинательство, увеличение характеристик),
     * правило замены при повышении уровня («Универсальность…»), расширение списка
     * заклинаний или просьба выбрать архетип.
     */
    fun isMeta(feature: RuleFeature, rules: ClassRules? = null): Boolean {
        val title = feature.title.lowercase()
        if (title in META_EXACT || META_PREFIXES.any { title.startsWith(it) }) return true
        val lower = MarkupText.plain(feature.text).lowercase()
        // «Всякий раз, когда вы получаете «Увеличение характеристик», вы можете заменить…».
        if (lower.contains("увеличение характеристик") && lower.contains("замен")) return true
        if (lower.contains("список заклинаний") && lower.contains("расширяют")) return true
        if (rules != null && feature.level == rules.subclassLevel) {
            val group = rules.subclassGroupTitle.lowercase()
            val sameName = group.length >= SUBCLASS_PREFIX && title.take(SUBCLASS_PREFIX) == group.take(SUBCLASS_PREFIX)
            if (sameName || SUBCLASS_POINTERS.any { lower.contains(it) }) return true
        }
        return false
    }

    /**
     * Умения для заметок: подходящие по уровню, без служебных и без тех, что только
     * раздают владения. Умения, полностью заменённые выбором (боевой стиль, воззвания,
     * компетентность), тоже убираются — выбранное попадает в заметки отдельно.
     */
    fun noteFeatures(
        features: List<RuleFeature>,
        level: Int,
        showOptional: Boolean,
        rules: ClassRules? = null,
        choices: List<ChoiceSpec> = emptyList(),
    ): List<RuleFeature> {
        val replaced = choices
            // Умение заменяется выбором, только если у вариантов есть свои описания (стили, воззвания);
            // у «Избранного врага» варианты — лишь названия, и само умение остаётся в заметках.
            .filter { choice -> choice.kind == ChoiceKind.OPTION && choice.options.any { it.text.isNotBlank() } }
            .map { it.title.lowercase().substringBefore(" (") }
            .toSet()
        return features.filter { feature ->
            // Умение с ограниченным использованием (арканум «раз в день») — настоящая способность,
            // даже если само заклинание выбрано отдельно.
            val replacedByChoice = feature.title.lowercase() in replaced &&
                !MarkupText.plain(feature.text).lowercase().contains("отдых")
            feature.level <= level &&
                (showOptional || !feature.extended) &&
                !isMeta(feature, rules) &&
                !isGrantOnly(feature.text) &&
                !replacedByChoice
        }
    }

    // endregion

    private val WEAPON_WORDS = listOf(
        "меч", "лук", "топор", "молот", "копь", "рапир", "арбалет", "трезуб", "сеть", "кинжал", "булав", "дубин", "серп",
        "скимитар", "пистол", "мушкет", "огнестрел",
    )

    private const val LEVEL_LINE_MAX = 120
    private const val MAX_LISTED = 6
    private const val SUBCLASS_PREFIX = 5
}
