package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.SkillType

/**
 * Что черта даёт листу персонажа.
 *
 * @param fixedAbilities фиксированные прибавки («Увеличьте значение Силы на 1»).
 * @param grants владения без выбора (тяжёлые доспехи, инструменты повара).
 * @param spells заклинания без выбора (туманный шаг у «Затронутого феями»).
 * @param choices выборы черты: характеристика, заклинание, навык или инструмент.
 * @param hpPerLevel прибавка к хитам за уровень персонажа («Крепкий»).
 * @param speed прибавка к скорости («Подвижный»).
 * @param initiative прибавка к инициативе («Бдительный»).
 */
data class FeatEffects(
    val fixedAbilities: Map<AbilityType, Int> = emptyMap(),
    val grants: FeatureGrants = FeatureGrants(),
    val spells: List<String> = emptyList(),
    val choices: List<ChoiceSpec> = emptyList(),
    val hpPerLevel: Int = 0,
    val speed: Int = 0,
    val initiative: Int = 0,
    /** Владение спасбросками той же характеристики, что выбрана для увеличения («Устойчивый»). */
    val savesFollowAbility: Boolean = false,
) {
    val isEmpty: Boolean
        get() = fixedAbilities.isEmpty() && grants.isEmpty && spells.isEmpty() && choices.isEmpty() &&
            hpPerLevel == 0 && speed == 0 && initiative == 0
}

/**
 * Разбор эффектов черты из текста dnd.su.
 *
 * Черты пишутся пунктами: «- Увеличьте значение Силы или Ловкости на 1…», «- Вы получаете
 * владение…», «- Вы узнаёте заклинание туманный шаг [misty step] и одно заклинание
 * 1-го уровня на ваш выбор…». Каждый пункт разбирается отдельно, выборы получают id
 * с префиксом слота черты (`feat.<слот>.`), чтобы при смене черты их можно было сбросить.
 *
 * Сложные черты с выбором «класса» или «факультета» (Посвящённый в магию, Стриксхейвен)
 * разбираются частично: предлагается выбор заклинаний из всех подходящих списков,
 * а полный текст черты остаётся у персонажа.
 */
object FeatRules {

    private val ABILITY_INCREASE = Regex(
        "увеличьте\\s+значение\\s+(?:вашей\\s+|вашего\\s+|одной\\s+вашей\\s+)?(.+?)\\s+на\\s+(\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val ANY_ABILITY = Regex("характеристик\\S*\\s+(?:по\\s+вашему\\s+выбору|на\\s+ваш\\s+выбор)|выбранной\\s+характеристик|одной\\s+вашей\\s+характеристик")
    private val HP_PER_LEVEL = Regex("максимум\\S*\\s+ваших\\s+хитов\\s+увеличивается\\s+на\\s+количество,\\s+равное\\s+удвоенному")
    private val SPEED = Regex("ваша\\s+скорость\\s+увеличивается\\s+на\\s+(\\d+)\\s+фут")
    private val INITIATIVE = Regex("бонус\\s*\\+\\s*(\\d+)\\s+к\\s+проверкам\\s+инициативы")
    private val SPELL_LEVEL = Regex("(\\S+)\\s+заклинани\\S*\\s+(\\d)-го\\s+уровня")

    /** Ссылки на уже сделанный выбор: такие предложения новых выборов не создают. */
    private val REFERS_BACK = listOf(
        "этот заговор", "это заклинание", "этих заклинани", "эти заклинани", "выбранное заклинание",
        "базовая характеристика", "базовой характеристикой", "скопировать", "найдя заклинание",
    )
    private val CANTRIP_COUNT = Regex("(\\S+)\\s+заговор")
    private val SCHOOLS: List<Pair<String, String>> = listOf(
        "ограждени" to "abj", "вызов" to "con", "прорицани" to "div", "очаровани" to "enc",
        "воплощени" to "evo", "иллюзи" to "ill", "некромант" to "nec", "преобразовани" to "trs",
    )

    fun parse(feat: Feat, slotId: String): FeatEffects {
        val prefix = "feat.$slotId"
        val fixed = mutableMapOf<AbilityType, Int>()
        val choices = mutableListOf<ChoiceSpec>()
        val spells = mutableListOf<String>()
        var hpPerLevel = 0
        var speed = 0
        var initiative = 0
        val plainAll = MarkupText.plain(feat.description).lowercase()

        // Характеристики: «Силы на 1», «Интеллекта, Мудрости или Харизмы на 1», «характеристики по выбору».
        ABILITY_INCREASE.findAll(MarkupText.plain(feat.description)).firstOrNull()?.let { match ->
            val amount = match.groupValues[2].toIntOrNull() ?: 1
            val target = match.groupValues[1]
            val listed = MarkupText.abilities(target).distinct()
            when {
                ANY_ABILITY.containsMatchIn(match.value.lowercase()) || listed.isEmpty() -> choices += abilityChoice(
                    id = "$prefix.ability", feat = feat, options = AbilityType.entries, amount = amount,
                )
                listed.size == 1 -> fixed[listed.single()] = amount
                else -> choices += abilityChoice("$prefix.ability", feat, listed, amount)
            }
        } ?: run {
            // «Выберите одну характеристику… Увеличьте значение выбранной характеристики» («Устойчивый»).
            if (ANY_ABILITY.containsMatchIn(plainAll) && plainAll.contains("увелич")) {
                choices += abilityChoice("$prefix.ability", feat, AbilityType.entries, 1)
            }
        }


        if (HP_PER_LEVEL.containsMatchIn(plainAll)) hpPerLevel = 2
        SPEED.find(plainAll)?.let { speed = it.groupValues[1].toInt() }
        INITIATIVE.find(plainAll)?.let { initiative = it.groupValues[1].toInt() }

        // Владения и выборы владений — тем же разбором, что и у умений классов.
        val feature = RuleFeature(title = feat.name, text = feat.description)
        // У черт пункты списка — это сами эффекты, а не варианты.
        val grants = FeatureRules.grants(feat.description, withBullets = true)
        val combo = comboChoice(feat, prefix, plainAll)
        if (combo != null) {
            choices += combo
        } else {
            choices += FeatureRules.choices(feature, prefix, ChoiceSource.FEAT, withBullets = true)
        }
        // «Выберите один навык, которым уже владеете… компетентность» («Эксперт в навыке», «Вундеркинд»).
        if (plainAll.contains("компетентность") && plainAll.contains("которым вы владеете") ||
            plainAll.contains("компетентность") && plainAll.contains("которым уже владеете")
        ) {
            choices += ChoiceSpec(
                id = "$prefix.expertise",
                source = ChoiceSource.FEAT,
                title = "${feat.name}: компетентность",
                kind = ChoiceKind.EXPERTISE,
                count = 1,
                sourceText = feat.description,
            )
        }
        weaponChoice(feat, prefix, plainAll)?.let(choices::add)

        // Заклинания: названные — сразу (туманный шаг), «одно заклинание 1-го уровня на ваш выбор» — выбор.
        val named = FeatureSpells.parse(feat.description, "$prefix.named", ChoiceSource.FEAT, feat.name)
        spells += named.spells.map { it.name }
        choices += named.choices
        FeatureRules.sentences(feat.description).forEachIndexed { index, sentence ->
            choices += spellChoices(sentence, sentence.lowercase(), "$prefix.spell.$index", feat)
        }

        return FeatEffects(
            fixedAbilities = fixed,
            grants = grants,
            spells = spells.distinct(),
            choices = choices.distinctBy { it.id },
            hpPerLevel = hpPerLevel,
            speed = speed,
            initiative = initiative,
            savesFollowAbility = plainAll.contains("спасброскам") && plainAll.contains("этой характеристики"),
        )
    }

    private fun abilityChoice(id: String, feat: Feat, options: List<AbilityType>, amount: Int) = ChoiceSpec(
        id = id,
        source = ChoiceSource.FEAT,
        title = "${feat.name}: характеристика +$amount",
        kind = ChoiceKind.ABILITY,
        count = 1,
        sourceText = feat.description.lines().firstOrNull { it.lowercase().contains("увелич") }.orEmpty(),
        options = options.map { ChoiceOption(it.name, it.name) },
    )

    /** «Любая комбинация из трёх навыков или инструментов» («Одарённый»). */
    private fun comboChoice(feat: Feat, prefix: String, lower: String): ChoiceSpec? {
        val match = Regex("комбинаци\\S*\\s+из\\s+(\\S+)\\s+навык\\S*\\s+или\\s+инструмент").find(lower) ?: return null
        val count = MarkupText.number(match.groupValues[1]) ?: return null
        return ChoiceSpec(
            id = "$prefix.combo",
            source = ChoiceSource.FEAT,
            title = "${feat.name}: навыки или инструменты",
            kind = ChoiceKind.PROFICIENCY,
            count = count,
            sourceText = feat.description,
            options = SkillType.entries.map { ChoiceOption(WizardEngine.SKILL_PREFIX + it.name, MarkupText.skillTitle(it)) } +
                ToolCatalog.all.map { tool ->
                    WizardEngine.toolOption(tool).copy(id = WizardEngine.TOOL_PREFIX + tool.id)
                },
        )
    }

    /** «Владение четырьмя выбранными видами оружия… простым или воинским» («Мастер оружия»). */
    private fun weaponChoice(feat: Feat, prefix: String, lower: String): ChoiceSpec? {
        val match = Regex("владение\\s+(\\S+)\\s+выбранными\\s+видами\\s+оружия").find(lower) ?: return null
        val count = MarkupText.number(match.groupValues[1]) ?: return null
        return ChoiceSpec(
            id = "$prefix.weapons",
            source = ChoiceSource.FEAT,
            title = "${feat.name}: оружие",
            kind = ChoiceKind.WEAPON,
            count = count,
            sourceText = feat.description,
            options = EquipmentCatalog.weapons.map { ChoiceOption(it.id, it.name, it.summary()) },
        )
    }

    /**
     * Выбор заклинания в предложении: «одно заклинание 1-го уровня на ваш выбор… школы
     * Прорицания или Очарования», «два заговора на свой выбор из списка заклинаний этого класса»,
     * «один заговор, требующий броска атаки», «два заклинания 1-го уровня… ключевое слово «ритуал»».
     */
    private fun spellChoices(sentence: String, lower: String, id: String, feat: Feat): List<ChoiceSpec> {
        val isChoice = lower.contains("выбор") || lower.contains("выберите")
        if (!isChoice || lower.startsWith("|")) return emptyList()
        // Предложение должно говорить о заклинаниях, а не отсылать к уже выбранным: «Вы можете наложить
        // этот заговор… (выберите, когда выбираете эту черту)» — это не новый выбор заговора.
        val mentionsSpell = lower.contains("заговор") || lower.contains("заклинани") || lower.contains("в таблиц")
        if (!mentionsSpell || REFERS_BACK.any { lower.contains(it) }) return emptyList()
        // Выбор из названных заклинаний уже разобран [FeatureSpells.parse].
        if (MarkupText.spellNameCandidates(sentence).size > 1) return emptyList()
        val result = mutableListOf<ChoiceSpec>()
        val all = MarkupText.plain(feat.description).lowercase()
        val schools = SCHOOLS.filter { (stem, _) -> all.contains("школ") && Regex("школ\\S*[^.]*$stem").containsMatchIn(all) }
            .map { it.second }.toSet()
        // «Выберите план … заговор, указанный в таблице» («Наследник внешних планов»): выбор из заклинаний таблицы.
        if (lower.contains("в таблиц")) {
            val names = feat.description.lines()
                .filter { it.trim().startsWith("|") }
                .flatMap { row -> row.split('|').flatMap(MarkupText::spellNameCandidates) }
                .distinct()
            if (names.size > 1) {
                val cantrip = lower.contains("заговор")
                return listOf(
                    ChoiceSpec(
                        id = "$id.table",
                        source = ChoiceSource.FEAT,
                        title = "${feat.name}: ${if (cantrip) "заговор" else "заклинание"}",
                        kind = if (cantrip) ChoiceKind.CANTRIP else ChoiceKind.SPELL,
                        count = 1,
                        sourceText = sentence,
                        spellFilter = SpellFilter(minLevel = 0, maxLevel = FeatureSpells.MAX_SPELL_LEVEL, names = names),
                    ),
                )
            }
        }
        val classes = MarkupText.spellListCodes(sentence).ifEmpty {
            when {
                // «из списка заклинаний этого класса», «основанное на мировоззрении» — список задан в другом месте черты.
                lower.contains("этого класса") || lower.contains("этого же списка") || lower.contains("мировоззрени") ->
                    MarkupText.spellListCodes(all).ifEmpty { MarkupText.classMentions(all) }
                // «Выберите один из классов: бард, волшебник…» — любой из перечисленных классов.
                all.contains("выберите класс") || all.contains("выберите один из классов") ->
                    MarkupText.classMentions(all)
                // «один заговор друида на ваш выбор».
                else -> MarkupText.classAfterSpellWord(lower)
            }
        }
        val ritual = all.contains("ключевое слово «ритуал»") || all.contains("ритуальную книгу")
        SPELL_LEVEL.find(lower)?.let { match ->
            val count = MarkupText.number(match.groupValues[1]) ?: 1
            val level = match.groupValues[2].toInt()
            result += ChoiceSpec(
                id = id,
                source = ChoiceSource.FEAT,
                title = "${feat.name}: заклинание $level-го уровня",
                kind = ChoiceKind.SPELL,
                count = count,
                sourceText = sentence,
                spellFilter = SpellFilter(
                    classCodes = classes,
                    minLevel = level,
                    maxLevel = level,
                    schools = schools,
                    ritualOnly = ritual,
                ),
            )
        }
        if (lower.contains("заговор")) {
            val count = CANTRIP_COUNT.find(lower)?.let { MarkupText.number(it.groupValues[1]) } ?: 1
            result += ChoiceSpec(
                id = id,
                source = ChoiceSource.FEAT,
                title = "${feat.name}: заговоры",
                kind = ChoiceKind.CANTRIP,
                count = count,
                sourceText = sentence,
                spellFilter = SpellFilter(
                    classCodes = classes,
                    attackOnly = all.contains("требующий броска атаки"),
                ),
            )
        }
        return result.mapIndexed { index, choice -> choice.copy(id = "${choice.id}.$index") }
    }
}
