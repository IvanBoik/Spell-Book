package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.ArmorProficiency
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.CharacterClassLevel
import com.example.spellbook.data.model.CharacterResource
import com.example.spellbook.data.model.CoinType
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.NoteParagraph
import com.example.spellbook.data.model.NoteSpan
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.WeaponProficiency
import com.example.spellbook.data.model.abilityModifier
import com.example.spellbook.data.DEFAULT_INITIATIVE_FORMULA
import com.example.spellbook.data.withRecalculatedResources
import com.example.spellbook.util.HtmlUtils

/**
 * Всё, что нужно записать в базу для нового персонажа.
 *
 * @param spellIds известные заклинания (включая заговоры).
 * @param preparedIds подготовленные заклинания — подмножество [spellIds].
 */
data class CharacterCreation(
    val character: Character,
    val notes: List<NoteBlock>,
    val items: List<InventoryItem>,
    val spellIds: Set<String>,
    val preparedIds: Set<String>,
    val featIds: List<String>,
)

/** Собирает персонажа из черновика мастера и разобранных правил. */
object CharacterAssembler {

    private const val EQUIPMENT_CATEGORY = "Снаряжение"
    private const val WEAPON_CATEGORY = "Оружие"
    private const val ARMOR_CATEGORY = "Доспех"
    private const val SHIELD_CATEGORY = "Щит"
    private const val DEFAULT_SPEED = 30

    fun assemble(
        draft: CharacterDraft,
        context: WizardContext,
        spells: List<Spell>,
        feats: List<Feat> = emptyList(),
    ): CharacterCreation {
        val characterId = java.util.UUID.randomUUID().toString()
        val classRules = context.characterClass
        val level = draft.level
        val scores = WizardEngine.finalScores(draft, context)
        val choices = context.choices
        fun values(kind: ChoiceKind) = choices.filter { it.kind == kind }.flatMap { choice ->
            draft.selections[choice.id].orEmpty().filter { it.isNotBlank() }
        }

        // Навыки и экспертиза.
        val skills = mutableMapOf<Int, Int>()
        val classGrants = context.classGrants
        WizardEngine.fixedSkills(context).forEach { skills[it.ordinal] = 1 }
        choices.flatMap { WizardEngine.chosenSkills(draft, it) }
            .forEach { skills[it.ordinal] = maxOf(skills[it.ordinal] ?: 0, 1) }
        values(ChoiceKind.EXPERTISE).mapNotNull { runCatching { SkillType.valueOf(it) }.getOrNull() }
            .forEach { skills[it.ordinal] = 2 }

        // Владения.
        val featGrants = context.featGrants
        val armor = classRules?.armor.orEmpty() + context.race?.grants?.armor.orEmpty() +
            context.subrace?.grants?.armor.orEmpty() + classGrants.armor + featGrants.armor
        val otherWeapons = (
            listOf(
                classRules?.otherWeapons.orEmpty(),
                context.race?.grants?.weapons.orEmpty(),
                context.subrace?.grants?.weapons.orEmpty(),
            ) + classGrants.otherWeapons + featGrants.otherWeapons +
                values(ChoiceKind.WEAPON).mapNotNull { EquipmentCatalog.weaponById(it)?.name }
            ).filter { it.isNotBlank() }.distinct()
        val weapons = (classRules?.weapons.orEmpty() + classGrants.weapons + featGrants.weapons).toMutableSet()
        if (otherWeapons.isNotEmpty()) weapons += WeaponProficiency.OTHER
        val tools = (
            listOf(
                // Части «…на ваш выбор» заменяются сделанным выбором, фиксированные остаются.
                classRules?.let(WizardEngine::fixedClassTools).orEmpty(),
                context.race?.grants?.tools.orEmpty(),
                context.subrace?.grants?.tools.orEmpty(),
                context.background?.tools.orEmpty(),
            ) + classGrants.tools + featGrants.tools
            ).filter { it.isNotBlank() } +
            choices.flatMap { WizardEngine.chosenTools(draft, it) }.map { id -> ToolCatalog.byId(id)?.name ?: id }
        val languages = (
            context.race?.grants?.languages.orEmpty() +
                context.subrace?.grants?.languages.orEmpty() +
                classGrants.languages + featGrants.languages +
                context.background?.languages?.split(',')?.map { it.trim() }.orEmpty() +
                values(ChoiceKind.LANGUAGE)
            ).filter { it.isNotBlank() }.distinct()

        // Хиты и КД.
        val hpBonus = ((context.race?.grants?.hpPerLevel ?: 0) + (context.subrace?.grants?.hpPerLevel ?: 0)) * level
        val maxHp = ((WizardEngine.maxHp(draft, context) ?: 1) + hpBonus).coerceAtLeast(1)
        val equipment = WizardEngine.classEquipment(draft, context)
        val featsById = feats.associateBy { it.id }
        val armorParts = armorClassParts(draft, context, equipment, draft.featSlots.values.mapNotNull(featsById::get))

        // Заклинания.
        val plan = context.spellcasting
        val byId = spells.associateBy { it.id }
        val known = linkedSetOf<String>()
        val prepared = linkedSetOf<String>()
        known += draft.cantripIds
        known += values(ChoiceKind.CANTRIP)
        known += values(ChoiceKind.SPELL)
        known += draft.spellIds
        if (plan != null && plan.preparesFromClassList && plan.maxSpellLevel > 0) {
            known += spells.filter { it.level in 1..plan.maxSpellLevel && plan.classCode in it.classes }.map { it.id }
        }
        val raceSpells = (context.race?.grants?.spells.orEmpty() + context.subrace?.grants?.spells.orEmpty())
            .filter { it.minLevel <= level }
        // Заклинания расы, умений («Техника тени» монаха), черт («Затронутый феями»: туманный шаг)
        // и выбранные в их выборах — персонаж знает их постоянно, без подготовки.
        val grantedSpells = (
            matchSpells(raceSpells.map { it.name }, spells) +
                matchSpells(context.featureSpells, spells) +
                matchSpells(context.featEffects.values.flatMap { it.spells }, spells) +
                values(ChoiceKind.SPELL)
            ).distinct()
        known += grantedSpells
        val alwaysPrepared = matchSpells(plan?.alwaysPrepared.orEmpty(), spells)
        known += alwaysPrepared
        // Всегда подготовленные: заклинания домена или клятвы и полученные от расы, умений
        // и черт. Они не занимают лимит подготовки, поэтому лимит увеличивается на их число.
        // Заговоры не готовятся и сюда не входят.
        val alwaysPreparedAll = (alwaysPrepared + grantedSpells).distinct().filter { (byId[it]?.level ?: 1) > 0 }
        if (plan?.canPrepare == true) {
            prepared += draft.preparedIds.filter { it in known || it in draft.spellIds }
            prepared += alwaysPreparedAll
        }
        val cantripCount = known.count { byId[it]?.level == 0 }

        val classLevel = classRules?.let {
            CharacterClassLevel(
                characterClass = it.type,
                level = level,
                customName = if (it.type == CharacterClass.OTHER) it.charClass.name else "",
            )
        }

        val gold = (context.background?.gold ?: 0) + if (draft.takeClassGold) draft.classGold ?: 0 else 0
        // Исключения — только то, что мастер реально предлагает и что можно исключить: блоки вариантов
        // и ключи, оставшиеся от прежнего выбора класса или расы, отбрасываются.
        val excluded = draft.excludedContent intersect proposal(draft, context, spells).excludableKeys

        val character = Character(
            id = characterId,
            name = draft.name.trim(),
            imageUri = draft.imageUri,
            level = level,
            classes = listOfNotNull(classLevel?.serialize()),
            canPrepareSpells = plan?.canPrepare == true,
            maxPreparedSpells = if (plan?.canPrepare == true) plan.preparedCount + alwaysPreparedAll.size else 0,
            maxCantrips = cantripCount,
            spellSlots = plan?.slots.orEmpty(),
            resources = context.resources.map { spec -> resourceOf(spec, spells, byId) }
                .filterNot { ContentExclusions.isResourceExcluded(it.name, excluded) },
            coins = if (gold > 0) mapOf(CoinType.GOLD.ordinal to gold) else emptyMap(),
            maxHp = maxHp,
            currentHp = maxHp,
            armorClassParts = armorParts,
            speedParts = speedParts(draft, context, draft.featSlots.values.mapNotNull(featsById::get)),
            initiativeParts = initiativeParts(draft, context, draft.featSlots.values.mapNotNull(featsById::get)),
            abilityScores = scores.mapKeys { it.key.ordinal },
            saveProficiencies = (classRules?.savingThrows.orEmpty() + classGrants.saves + featSaves(draft, context))
                .associate { it.ordinal to 1 },
            skillProficiencies = skills,
            armorProficiencies = ArmorProficiency.entries.filter { it in armor }.map { it.name },
            weaponProficiencies = WeaponProficiency.entries.filter { it in weapons }.map { it.name },
            otherWeaponProficiencies = otherWeapons.joinToString(", "),
            toolProficiencies = tools.joinToString(", "),
            languages = languages.joinToString(", "),
            race = context.race?.race?.name.orEmpty(),
            subrace = context.subrace?.title.orEmpty(),
            subclass = context.subclass?.title.orEmpty(),
            background = context.background?.background?.name.orEmpty(),
            excludedContent = excluded.sorted(),
        )

        return CharacterCreation(
            // Числа КД и скорости — как расчётные по составляющим: их читают выгрузка и старый код.
            // Максимумы ресурсов с формулой («количество раз, равное бонусу мастерства») считаются
            // по готовому персонажу: в правилах записана лишь заглушка, и без пересчёта следопыт 5-го
            // уровня получал 2 заряда «Предпочтительного противника» вместо 3. Заряды полные.
            character = character.withRecalculatedResources().let { recalculated ->
                recalculated.copy(resources = recalculated.resources.map { it.copy(current = it.maximum) })
            },
            notes = buildNotes(characterId, draft, context, excluded),
            items = buildItems(characterId, context, equipment),
            spellIds = known,
            preparedIds = prepared,
            featIds = draft.featSlots.values.distinct(),
        )
    }

    /**
     * Спасброски от черт: фиксированные и «Устойчивый» — спасброски той же характеристики,
     * что выбрана для увеличения.
     */
    internal fun featSaves(draft: CharacterDraft, context: WizardContext): Set<AbilityType> =
        context.featGrants.saves + context.featEffects.values
            .filter { effects -> effects.choices.any { it.kind == ChoiceKind.ABILITY } && effects.savesFollowAbility }
            .flatMap { effects ->
                effects.choices.filter { it.kind == ChoiceKind.ABILITY }
                    .flatMap { draft.selections[it.id].orEmpty() }
                    .mapNotNull { runCatching { AbilityType.valueOf(it) }.getOrNull() }
            }

    /**
     * Составляющие скорости: скорость расы (основа) и постоянные прибавки от умений
     * класса («Движение без доспехов», «Быстрое передвижение») и черт («Подвижный»).
     */
    private fun speedParts(draft: CharacterDraft, context: WizardContext, feats: List<Feat>): List<StatPart> = buildList {
        val raceSpeed = context.subrace?.grants?.speed ?: context.race?.grants?.speed ?: DEFAULT_SPEED
        add(
            StatPart(
                id = "race",
                name = context.subrace?.title ?: context.race?.race?.name ?: "",
                formula = raceSpeed.toString(),
                kind = StatPartKind.BASE,
            ),
        )
        context.characterClass?.let { addAll(SpeedRules.classParts(it, context.subclass, draft.level, draft.showOptional)) }
        feats.forEach { feat -> SpeedRules.featPart(feat)?.let(::add) }
    }

    /**
     * Составляющие инициативы: модификатор Ловкости (основа) и постоянные прибавки
     * от расы (Зайцегон), умений класса («Волшебная прозорливость») и черт («Бдительный»).
     */
    private fun initiativeParts(draft: CharacterDraft, context: WizardContext, feats: List<Feat>): List<StatPart> =
        buildList {
            add(StatPart(id = "dex", name = "", formula = DEFAULT_INITIATIVE_FORMULA, kind = StatPartKind.BASE))
            addAll(InitiativeRules.raceParts(context.race?.traits.orEmpty() + context.subrace?.traits.orEmpty()))
            context.characterClass?.let {
                addAll(InitiativeRules.classParts(it, context.subclass, draft.level, draft.showOptional))
            }
            feats.forEach { feat -> InitiativeRules.featPart(feat)?.let(::add) }
        }.distinctBy { it.id }

    /**
     * Ресурс персонажа по спецификации, с полными зарядами. Ресурс заклинания называется как
     * в библиотеке: в тексте оно бывает в другом падеже («накладывать палящую кару»).
     */
    internal fun resourceOf(
        spec: ResourceSpec,
        spells: List<Spell>,
        byId: Map<String, Spell> = spells.associateBy { it.id },
    ): CharacterResource {
        val spellTitle = spec.spellName.takeIf { it.isNotBlank() }
            ?.let { name -> matchSpells(listOf(name), spells).firstOrNull()?.let { byId[it]?.name } }
        return CharacterResource(
            name = spellTitle ?: spec.name,
            description = spec.description,
            current = spec.maximum,
            maximum = spec.maximum,
            maximumFormula = spec.formula,
        ).normalized()
    }

    /** id заклинаний библиотеки, совпадающих по названию с фрагментами из текста. */
    internal fun matchSpells(names: List<String>, spells: List<Spell>): List<String> =
        names.mapNotNull { candidate ->
            spells.filter { MarkupText.matchesSpellName(candidate, it.name) }.maxByOrNull { it.name.length }?.id
        }.distinct()

    // region Заметки

    /**
     * Блок заметок, который соберёт мастер: абзацы с заголовками — для исключений.
     *
     * @param locked блок выбранных вариантов: исключить нельзя.
     */
    internal data class PlannedBlock(
        val title: String,
        val paragraphs: List<Pair<String, NoteParagraph>>,
        val texts: List<String>,
        val locked: Boolean = false,
    )

    /**
     * Что мастер создания предлагает записать в заметки и ресурсы — для переключателей на итоговом
     * шаге. Блоки выбранных вариантов закреплены: по ним повышение восстанавливает взятое.
     */
    fun proposal(draft: CharacterDraft, context: WizardContext, spells: List<Spell>): ContentProposal {
        val byId = spells.associateBy { it.id }
        val blocks = plannedNotes(draft, context).map { block ->
            ProposedBlock(
                key = ContentExclusions.blockKey(block.title),
                title = block.title,
                paragraphs = block.paragraphs.mapIndexed { index, (heading, _) ->
                    ProposedParagraph(
                        key = ContentExclusions.paragraphKey(block.title, heading),
                        heading = heading,
                        text = block.texts.getOrElse(index) { "" },
                    )
                },
                locked = block.locked,
            )
        }
        val resources = context.resources.map { spec ->
            val resource = resourceOf(spec, spells, byId)
            ProposedResource(
                key = ContentExclusions.resourceKey(resource.name),
                name = resource.name,
                maximum = resource.maximum,
                description = resource.description,
            )
        }.distinctBy { it.key }
        return ContentProposal(blocks, resources)
    }

    private fun buildNotes(
        characterId: String,
        draft: CharacterDraft,
        context: WizardContext,
        excluded: Set<String> = emptySet(),
    ): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        // Блоки создаются по порядку; сортировка — по убыванию времени, поэтому сдвигаем вниз.
        val baseTime = System.currentTimeMillis()
        plannedNotes(draft, context).forEach { planned ->
            // Исключённый блок или абзац не записывается; блоки вариантов — всегда.
            val paragraphs = planned.paragraphs.filter { (heading, _) ->
                planned.locked || !ContentExclusions.isParagraphExcluded(planned.title, heading, excluded)
            }.map { it.second }
            if (paragraphs.isEmpty()) return@forEach
            blocks += NoteBlock(
                characterId = characterId,
                title = planned.title,
                collapsed = true,
                paragraphs = paragraphs,
                createdAt = baseTime,
                sortOrder = blocks.size.toLong() - NOTE_ORDER_BASE,
            )
        }
        return blocks
    }

    /** Блоки заметок нового персонажа до исключений, по порядку. */
    internal fun plannedNotes(draft: CharacterDraft, context: WizardContext): List<PlannedBlock> {
        val blocks = mutableListOf<PlannedBlock>()
        fun block(title: String, items: List<Pair<String, String>>, locked: Boolean = false) {
            if (items.isEmpty()) return
            blocks += PlannedBlock(
                title = title,
                paragraphs = items.map { (heading, text) -> heading to paragraph(heading, text) },
                texts = items.map { it.second },
                locked = locked,
            )
        }

        val race = context.race
        val subrace = context.subrace
        if (race != null) {
            // У расы и разновидности один блок: обе описывают одно происхождение.
            val traits = FeatureRules.noteFeatures(race.traits + subrace?.traits.orEmpty(), draft.level, showOptional = true)
            val title = listOfNotNull(race.race.name, subrace?.title).joinToString(", ")
            block("Раса: $title", traits.map { it.title to it.text })
        }
        context.characterClass?.let { rules ->
            val features = FeatureRules.noteFeatures(
                rules.features, draft.level, draft.showOptional, rules,
                context.choices.filter { it.source == ChoiceSource.CLASS },
            )
            block(
                "Класс: ${rules.charClass.name}",
                features.map { levelTitle(it) to it.text },
            )
        }
        context.subclass?.let { sub ->
            val features = FeatureRules.noteFeatures(
                sub.features, draft.level, showOptional = true, rules = null,
                choices = context.choices.filter { it.source == ChoiceSource.SUBCLASS },
            )
            block(
                "Архетип: ${sub.title}",
                features.map { levelTitle(it) to it.text },
            )
        }
        context.background?.let { bg ->
            block(
                "Предыстория: ${bg.background.name}",
                listOfNotNull(bg.feature?.let { it.title to it.text }),
            )
        }

        // Выбранные варианты — отдельными заметками: воззвания, боевые приёмы, инфузии, стиль.
        context.choices.filter { it.kind == ChoiceKind.OPTION }.forEach { choice ->
            val selected = draft.selections[choice.id].orEmpty()
                .mapNotNull { id -> choice.options.firstOrNull { it.id == id } }
            block(choice.title, selected.map { it.title to it.text }, locked = true)
        }
        return blocks
    }

    internal fun levelTitle(feature: RuleFeature): String =
        if (feature.level > 1) "${feature.title} (${feature.level} ур.)" else feature.title

    /** Абзац заметки: жирный заголовок и текст особенности без разметки. */
    internal fun paragraph(title: String, markup: String): NoteParagraph {
        val body = noteText(markup)
        val text = if (body.isBlank()) title else "$title\n$body"
        return NoteParagraph(text = text, spans = listOf(NoteSpan(start = 0, end = title.length, bold = true)))
    }

    /** Преобразует разметку библиотеки в простой текст заметки. */
    fun noteText(markup: String): String = markup.lines().mapNotNull { raw ->
        val line = raw.trim()
        when {
            HtmlUtils.isCalloutBoundary(line) -> HtmlUtils.calloutTitle(line).takeIf { it.isNotBlank() }?.let(MarkupText::plain)
            // Таблицы остаются таблицами (без разметки в ячейках): заметки и описания ресурсов
            // рисуют их сеткой — «Заклинания изначальной осведомлённости», заклинания домена.
            HtmlUtils.isTableSeparator(line) -> line
            HtmlUtils.isTableRow(line) -> HtmlUtils.buildTableRow(HtmlUtils.parseTableRow(line).map { MarkupText.plain(it) })
            HtmlUtils.isBulletItem(line) -> "• " + MarkupText.plain(HtmlUtils.listItemText(line))
            HtmlUtils.isSubheading(line) -> MarkupText.plain(HtmlUtils.subheadingText(line))
            HtmlUtils.isQuote(line) -> MarkupText.plain(HtmlUtils.quoteText(line))
            // Строка уровня «*3-й уровень, умение воина*» уже отражена в заголовке.
            line.startsWith("*") && line.contains("уровень") && line.length < LEVEL_LINE_MAX -> null
            else -> MarkupText.plain(line)
        }
    }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()

    private const val LEVEL_LINE_MAX = 120
    private const val NOTE_ORDER_BASE = 1_000_000L

    // endregion

    // region Снаряжение

    private fun buildItems(
        characterId: String,
        context: WizardContext,
        classEquipment: List<EquipmentEntry>,
    ): List<InventoryItem> {
        val entries = classEquipment + context.background?.equipment.orEmpty().map { EquipmentEntry(it) }
        val baseOrder = -System.currentTimeMillis()
        return entries
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, entry ->
                InventoryItem(
                    characterId = characterId,
                    name = entry.name.trim().replaceFirstChar { c -> c.uppercase() },
                    quantity = entry.quantity,
                    description = EquipmentCatalog.description(entry),
                    categories = listOf(
                        when {
                            entry.armor?.type == ArmorType.SHIELD -> SHIELD_CATEGORY
                            entry.armor != null -> ARMOR_CATEGORY
                            entry.weapon != null -> WEAPON_CATEGORY
                            else -> EQUIPMENT_CATEGORY
                        },
                    ),
                    sortOrder = baseOrder + index,
                ).normalized()
            }
    }

    // endregion

    // region КД

    /**
     * Составляющие КД нового персонажа.
     *
     * Основы: доспехи из стартового снаряжения (включён лучший, остальные выключены),
     * защита без доспехов варвара и монаха. Прибавки: щит, черты и боевой стиль с «+1 к КД».
     * Монах не может носить щит с защитой без доспехов, а доспех и защита без доспехов
     * взаимоисключаются — это учитывает правило «действует наибольшая основа».
     */
    private fun armorClassParts(
        draft: CharacterDraft,
        context: WizardContext,
        equipment: List<EquipmentEntry>,
        feats: List<Feat>,
    ): List<StatPart> {
        val scores = WizardEngine.finalScores(draft, context)
        fun mod(ability: AbilityType) = abilityModifier(scores[ability] ?: 10)
        val parts = mutableListOf<StatPart>()

        val armors = equipment.mapNotNull { it.armor }.filter { it.type != ArmorType.SHIELD }.distinctBy { it.id }
        val unarmored = when (context.characterClass?.type) {
            CharacterClass.BARBARIAN -> "10 + [dex] + [con]"
            CharacterClass.MONK -> "10 + [dex] + [wis]"
            else -> null
        }
        // Оценка для выбора лучшей основы; итог потом считает StatFormula по персонажу.
        fun armorValue(armor: ArmorInfo) = armor.baseAc + when (armor.dexCap) {
            null -> mod(AbilityType.DEXTERITY)
            0 -> 0
            else -> minOf(mod(AbilityType.DEXTERITY), armor.dexCap)
        }
        val unarmoredValue = when (context.characterClass?.type) {
            CharacterClass.BARBARIAN -> 10 + mod(AbilityType.DEXTERITY) + mod(AbilityType.CONSTITUTION)
            CharacterClass.MONK -> 10 + mod(AbilityType.DEXTERITY) + mod(AbilityType.WISDOM)
            else -> Int.MIN_VALUE
        }
        val bestArmor = armors.maxByOrNull(::armorValue)
        val armorWins = bestArmor != null && armorValue(bestArmor) >= unarmoredValue
        armors.forEach { armor ->
            parts += StatPart(
                name = armor.name,
                formula = armor.acFormula(),
                kind = StatPartKind.BASE,
                enabled = armorWins && armor.id == bestArmor?.id,
                description = armor.notes().joinToString(", "),
            )
        }
        if (unarmored != null) {
            parts += StatPart(
                name = "Защита без доспехов",
                formula = unarmored,
                kind = StatPartKind.BASE,
                enabled = !armorWins,
            )
        }
        equipment.mapNotNull { it.armor }.firstOrNull { it.type == ArmorType.SHIELD }?.let { shield ->
            // Монах теряет защиту без доспехов со щитом — щит добавляем выключенным.
            val monkUnarmored = context.characterClass?.type == CharacterClass.MONK && !armorWins
            parts += StatPart(
                name = shield.name,
                formula = shield.acFormula(),
                kind = StatPartKind.BONUS,
                enabled = !monkUnarmored,
            )
        }
        feats.forEach { feat ->
            ArmorClassRules.bonusPart(feat.name, feat.description, "feat:${feat.id}")?.let(parts::add)
        }
        // Выбранные варианты умений с прибавкой к КД (боевой стиль «Оборона»).
        context.choices.filter { it.kind == ChoiceKind.OPTION }.forEach { choice ->
            draft.selections[choice.id].orEmpty().mapNotNull { id -> choice.options.firstOrNull { it.id == id } }
                .forEach { option -> ArmorClassRules.bonusPart(option.title, option.text)?.let(parts::add) }
        }
        return parts
    }


    // endregion
}
