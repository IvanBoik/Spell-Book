package com.example.spellbook.data.wizard

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.FeatLibraryCodec
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.SpellLssCodec
import com.example.spellbook.data.StatFormula
import com.example.spellbook.data.effectiveArmorClass
import com.example.spellbook.data.effectiveSpeed
import com.example.spellbook.data.initiativeBonus
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.ui.wizard.filterSpells
import org.junit.Test
import java.io.File

/**
 * Перебор комбинаций мастера создания персонажа на настоящих библиотеках.
 *
 * Для каждой комбинации (раса × разновидность, класс × архетип × уровень, предыстория,
 * переключатели Tasha и UA) черновик заполняется автоматически — как если бы пользователь
 * выбирал первые доступные варианты, — затем собирается персонаж и проверяются инварианты:
 * нет дублей особенностей, заметок и ресурсов, опциональные умения не просачиваются без
 * переключателя, у каждого выбора хватает вариантов, числа листа разумны.
 *
 * Нарушения не валят тест по одному: они собираются в отчёт `build/wizard-sweep.txt`
 * (сгруппированные по виду), а тест падает, если найдено хоть одно.
 */
class WizardSweepTest {

    private val races by lazy {
        RaceLibraryCodec.decodeAll(File("src/main/assets/races-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "r$index") }
    }
    private val classes by lazy {
        ClassLibraryCodec.decodeAll(File("src/main/assets/classes-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "c$index") }
    }
    private val backgrounds by lazy {
        BackgroundLibraryCodec.decodeAll(File("src/main/assets/backgrounds-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "b$index") }
    }
    private val feats by lazy {
        FeatLibraryCodec.decodeList(File("src/main/assets/feats-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "f$index") }
    }
    private val spells: List<Spell> by lazy {
        SpellLssCodec.decodeList(File("src/main/assets/spellbook-library.json").readText())
            .mapIndexed { index, item -> item.copy(id = "s$index") }
    }
    private val library by lazy { WizardLibrary(races, classes, backgrounds, feats, spells) }
    private val cache = WizardRulesCache()

    /** Нарушение: вид (для группировки) и подробности комбинации. */
    private data class Finding(val kind: String, val where: String, val details: String)

    private val findings = mutableListOf<Finding>()

    private fun report(kind: String, where: String, details: String) {
        findings += Finding(kind, where, details)
    }

    // region Автозаполнение

    /** Значения для выбора: первые подходящие варианты, как сделал бы пользователь. */
    private fun fill(choice: ChoiceSpec, draft: CharacterDraft, context: WizardContext): List<String> {
        val count = choice.count
        return when (choice.kind) {
            ChoiceKind.CANTRIP, ChoiceKind.SPELL -> {
                val filter = choice.spellFilter ?: SpellFilter(maxLevel = if (choice.kind == ChoiceKind.CANTRIP) 0 else 9)
                val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
                filterSpells(spells, effective).map { it.id }.take(count)
            }
            ChoiceKind.SKILL, ChoiceKind.EXPERTISE -> {
                val owned = WizardEngine.proficientSkills(draft, context)
                val pool = when {
                    choice.kind == ChoiceKind.EXPERTISE -> owned.map { it.name }
                    choice.options.isNotEmpty() -> choice.options.map { it.id }
                    else -> com.example.spellbook.data.model.SkillType.entries.map { it.name }
                }
                // Как пользователь: сначала то, чего ещё нет.
                val fresh = pool.filter { name -> choice.kind == ChoiceKind.EXPERTISE || owned.none { it.name == name } }
                (fresh + pool).distinct().take(count)
            }
            ChoiceKind.ABILITY -> choice.options.map { it.id }.ifEmpty { AbilityType.entries.map { it.name } }.take(count)
            else -> {
                val unmetFree = choice.options.filter { OptionRequirements.unmet(it, draft, context, spells).isEmpty() }
                unmetFree.map { it.id }.take(count)
            }
        }
    }

    /** Заполняет все выборы контекста за несколько проходов (выборы зависят друг от друга). */
    private fun fillChoices(start: CharacterDraft): CharacterDraft {
        var draft = start
        // Архетип не выбран, а уровень его требует — берём первый официальный.
        val initial = WizardEngine.context(draft, library, cache)
        if (initial.subclass == null && WizardEngine.needsSubclass(draft, initial)) {
            initial.characterClass?.subclasses?.firstOrNull { !it.extended }?.let { sub ->
                draft = WizardEngine.selectSubclass(draft, sub.id)
            }
        }
        repeat(FILL_PASSES) {
            val context = WizardEngine.context(draft, library, cache)
            val selections = draft.selections.toMutableMap()
            context.choices.forEach { choice ->
                if (WizardEngine.selectedCount(draft, choice) < choice.count) {
                    selections[choice.id] = fill(choice, draft.copy(selections = selections), context)
                }
            }
            draft = draft.copy(selections = selections)
        }
        return draft
    }

    /** Заполняет характеристики, бонусы расы, увеличения, черты и заклинания. */
    private fun fillRest(start: CharacterDraft): CharacterDraft {
        var draft = start.copy(
            abilityMethod = com.example.spellbook.data.wizard.AbilityMethod.STANDARD_ARRAY,
            poolAssignment = AbilityType.entries.mapIndexed { index, ability -> ability to index }.toMap(),
        )
        var context = WizardEngine.context(draft, library, cache)

        // Бонусы расы: по порядку характеристик, пропуская запрещённые (фиксированные).
        WizardEngine.bonusRule(context)?.let { rule ->
            val values = WizardEngine.bonusValues(draft, rule)
            val blocked = if (rule.excludeFixed) rule.fixed.map { it.ability }.toSet() else emptySet()
            val targets = AbilityType.entries.filter { it !in blocked }.take(values.size)
            draft = draft.copy(bonusTargets = targets)
        }

        // Увеличения характеристик: первое — черта (проверяем черты), остальные — +2 к Силе.
        context = WizardEngine.context(draft, library, cache)
        val asi = WizardEngine.asiLevels(draft, context).mapIndexed { index, level ->
            level to if (index == 0) AsiChoice(level, AsiKind.FEAT) else AsiChoice(level, AsiKind.PLUS_TWO, listOf(AbilityType.STRENGTH))
        }.toMap()
        draft = draft.copy(asiChoices = asi)

        // Черты: первая подходящая, ещё не взятая.
        context = WizardEngine.context(draft, library, cache)
        val taken = mutableSetOf<String>()
        val slots = WizardEngine.featSlots(draft, context).associate { slot ->
            val feat = WizardEngine.featsForSlot(slot, feats).first { it.id !in taken }
            taken += feat.id
            slot.id to feat.id
        }
        draft = fillChoices(draft.copy(featSlots = slots))

        // Заклинания класса — как на шаге заклинаний.
        context = WizardEngine.context(draft, library, cache)
        context.spellcasting?.let { plan ->
            val classFilter = SpellFilter(classCodes = setOf(plan.classCode), schools = plan.schools)
            // Заговоры школой не ограничены — как на шаге заклинаний.
            val cantrips = filterSpells(spells, classFilter.copy(maxLevel = 0, schools = emptySet())).map { it.id }.take(plan.cantrips).toSet()
            val target = plan.knownSpells ?: plan.spellbookSpells ?: 0
            val known = if (plan.maxSpellLevel > 0) {
                filterSpells(spells, classFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel), plan.extraSpellNames)
                    .map { it.id }.take(target).toSet()
            } else emptySet()
            val prepared = if (plan.canPrepare) {
                val pool = if (plan.spellbookSpells != null) known.toList() else
                    filterSpells(spells, classFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel)).map { it.id }
                pool.take(plan.preparedCount).toSet()
            } else emptySet()
            draft = draft.copy(cantripIds = cantrips, spellIds = known, preparedIds = prepared)
        }

        // Оружие на выбор в снаряжении — первое подходящее.
        context = WizardEngine.context(draft, library, cache)
        val picks = WizardEngine.chosenEquipment(draft, context).flatMap { (group, option, parsed) ->
            parsed.slots.mapIndexed { slotIndex, slot ->
                val pool = EquipmentCatalog.weapons(slot.categories)
                WizardEngine.weaponSlotKey(group, option, slotIndex) to List(slot.count) { pool.getOrNull(it % pool.size.coerceAtLeast(1))?.id }.filterNotNull()
            }
        }.toMap()
        return draft.copy(weaponPicks = picks)
    }

    // endregion

    // region Проверки

    private fun check(where: String, draft: CharacterDraft) {
        val context = WizardEngine.context(draft, library, cache)

        // Самая строгая проверка: после автозаполнения не остаётся незавершённых шагов.
        // Если остаётся — выбор невыполним (вариантов меньше, чем нужно, все закрыты требованиями
        // и т. п.): пользователь столкнётся с тем же.
        WizardEngine.allIssues(draft, context).forEach { (step, issues) ->
            issues.forEach { issue ->
                report("unsatisfiable-${issue.kind}", where, "${step.key}: ${issue.label} missing=${issue.missing}")
            }
        }

        // Обязательная разновидность должна быть доступна без переключателя UA.
        context.race?.takeIf { it.subraceRequired && !it.extended }?.let { race ->
            if (race.subraces.none { !it.extended }) report("subrace-only-unofficial", where, race.race.name)
        }

        // Заряды опциональных умений без переключателя (как было у следопыта).
        if (!draft.showOptional) {
            val parsed = draft.classId?.let { id -> classes.firstOrNull { it.id == id } }?.let(cache::characterClass)
            val optionalTitles = parsed?.features.orEmpty().filter { it.extended }.map { it.title.lowercase() }
            context.resources.filter { resource ->
                optionalTitles.any { resource.name.lowercase().startsWith(it) }
            }.forEach { report("optional-resource-leak", where, it.name) }
        }

        // Слоты оружия в снаряжении: есть из чего выбирать.
        WizardEngine.chosenEquipment(draft, context).forEach { (_, _, parsed) ->
            parsed.slots.forEach { slot ->
                if (EquipmentCatalog.weapons(slot.categories).isEmpty()) report("weapon-slot-empty", where, slot.label)
            }
        }

        // Выборы: хватает вариантов, нет пустых, нет дублей id.
        context.choices.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            report("choice-duplicate-id", where, it)
        }
        context.choices.forEach { choice ->
            val needsOptions = choice.kind == ChoiceKind.OPTION || choice.kind == ChoiceKind.LANGUAGE ||
                choice.kind == ChoiceKind.TOOL || choice.kind == ChoiceKind.WEAPON || choice.kind == ChoiceKind.PROFICIENCY
            if (choice.count <= 0) report("choice-zero-count", where, "${choice.id} «${choice.title}»")
            if (needsOptions && choice.options.isEmpty()) {
                report("choice-no-options", where, "${choice.id} «${choice.title}» kind=${choice.kind}")
            } else if (needsOptions && choice.options.size < choice.count) {
                report("choice-too-few-options", where, "${choice.id} «${choice.title}» ${choice.options.size} < ${choice.count}")
            }
            if (choice.kind == ChoiceKind.CANTRIP || choice.kind == ChoiceKind.SPELL) {
                val filter = choice.spellFilter ?: SpellFilter(maxLevel = 9)
                val effective = if (choice.kind == ChoiceKind.CANTRIP) filter.copy(minLevel = 0, maxLevel = 0) else filter
                val candidates = filterSpells(spells, effective).size
                if (candidates < choice.count) {
                    report("choice-too-few-spells", where, "${choice.id} «${choice.title}» $candidates < ${choice.count} filter=$effective")
                }
            }
            if (choice.options.map { it.id }.distinct().size != choice.options.size) {
                report("choice-duplicate-option", where, "${choice.id} «${choice.title}»")
            }
            // Опциональные варианты без переключателя.
            if (!draft.showOptional && choice.options.any { it.extended && !it.id.startsWith("ua.") }) {
                report("optional-option-leak", where, "${choice.id} «${choice.title}»")
            }
        }

        // Опциональные умения без переключателя.
        if (!draft.showOptional) {
            val optional = context.characterClass?.features.orEmpty().filter { it.extended } +
                context.subclass?.features.orEmpty().filter { it.extended }
            val leaked = FeatureRules.noteFeatures(optional, draft.level, showOptional = false, rules = context.characterClass)
            leaked.forEach { report("optional-feature-leak", where, it.title) }
        }
        // Взаимоисключающие умения: никогда оба сразу.
        context.swaps.forEach { swap ->
            val titles = context.characterClass?.features.orEmpty().map { it.title }
            if (swap.optional.title in titles && swap.base.any { it.title in titles }) {
                report("swap-both", where, swap.optional.title)
            }
        }

        // Неофициальный архетип/разновидность без переключателя (если выбраны вручную, не проверяем).

        // Шаги: без дублей, сводка последняя.
        val steps = WizardEngine.steps(draft, context)
        steps.groupBy { it.key }.filterValues { it.size > 1 }.keys.forEach { report("step-duplicate", where, it) }
        if (steps.last() != WizardStep.Summary) report("step-order", where, "last=${steps.last().key}")
        // Каждый выбор где-то показывается.
        val stepKeys = steps.map { it.key }.toSet()
        context.choices.forEach { choice ->
            val key = WizardEngine.choiceStepKey(choice, context)
            if (key !in stepKeys) report("choice-without-step", where, "${choice.id} → $key")
        }
        // Шаг заклинаний не показывается пустым.
        if (WizardStep.Spells in steps) {
            val plan = context.spellcasting
            val anything = plan != null && (plan.cantrips > 0 || (plan.knownSpells ?: 0) > 0 ||
                (plan.spellbookSpells ?: 0) > 0 || plan.canPrepare) || WizardEngine.choicesOn(WizardStep.Spells, context).isNotEmpty()
            if (!anything) report("spell-step-empty", where, "")
        }

        // Сборка.
        val creation = runCatching { CharacterAssembler.assemble(draft, context, spells, feats) }
            .getOrElse { error ->
                report("assemble-crash", where, "${error::class.simpleName}: ${error.message}")
                return
            }
        val character = creation.character

        // Дубли заметок, абзацев и ресурсов.
        creation.notes.groupBy { it.title }.filterValues { it.size > 1 }.keys.forEach {
            report("note-duplicate-block", where, it)
        }
        creation.notes.forEach { block ->
            val heads = block.paragraphs.map { it.text.lineSequence().first().trim() }
            heads.groupBy { it }.filterValues { it.size > 1 }.keys.forEach {
                report("note-duplicate-paragraph", where, "${block.title} → $it")
            }
            if (block.paragraphs.isEmpty()) report("note-empty-block", where, block.title)
        }
        character.resources.groupBy { it.name.lowercase().substringBefore(" (") }.filterValues { it.size > 1 }.keys.forEach {
            report("resource-duplicate", where, it)
        }
        character.resources.forEach { resource ->
            if (resource.maximum <= 0) report("resource-zero-max", where, resource.name)
            if (resource.maximumFormula.isNotBlank() && !StatFormula.isValid(resource.maximumFormula)) {
                report("resource-bad-formula", where, "${resource.name}: ${resource.maximumFormula}")
            }
            // Максимум должен совпадать с формулой: иначе при первом пересчёте заряды «прыгнут».
            if (resource.maximumFormula.isNotBlank()) {
                val evaluated = StatFormula.evaluateValue(resource.maximumFormula, character)
                if (evaluated != null && evaluated.coerceAtLeast(1) != resource.maximum) {
                    report("resource-max-mismatch", where, "${resource.name}: max=${resource.maximum} formula=${resource.maximumFormula}=$evaluated")
                }
            }
        }
        // Одна и та же способность в двух разных блоках заметок (класс и архетип, раса и черта).
        // Сравниваем и начало текста: у разных способностей бывают одинаковые названия
        // («Перевёртыш» чейнджлинга и школы Преобразования).
        creation.notes
            .flatMap { block ->
                block.paragraphs.map { paragraph ->
                    val lines = paragraph.text.lines()
                    val key = lines.first().trim().substringBefore(" (") + "|" + lines.drop(1).joinToString(" ").take(NOTE_COMPARE_CHARS)
                    key to block.title
                }
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.distinct().size > 1 }
            .forEach { (key, blocks) ->
                report("note-duplicate-across-blocks", where, "${key.substringBefore('|')} in ${blocks.distinct()}")
            }
        // Умения выше уровня персонажа не попадают в заметки (заголовок «… (N ур.)»).
        creation.notes.flatMap { block -> block.paragraphs.map { block.title to it.text.lineSequence().first() } }
            .forEach { (block, head) ->
                Regex("\\((\\d+) ур\\.\\)").find(head)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it > draft.level }
                    ?.let { report("note-above-level", where, "$block → $head") }
            }
        // Выборы с одинаковым названием и содержимым — одно и то же правило разобрано дважды.
        // Разные источники (язык от расы и от предыстории) дают разные выборы — сравниваем внутри одного.
        context.choices
            .groupBy { listOf(it.source, it.title, it.kind, it.options.map { option -> option.id }) }
            .filterValues { it.size > 1 }
            .forEach { (key, same) -> report("choice-duplicate-content", where, "${key[1]} ${same.map { it.id }}") }
        // Выбор заклинания должен содержать то, что правило требует (перечисленные имена есть в библиотеке).
        context.choices.filter { it.spellFilter?.names?.isNotEmpty() == true }.forEach { choice ->
            val missing = choice.spellFilter!!.names.filter { name -> spells.none { MarkupText.matchesSpellName(name, it.name) } }
            if (missing.isNotEmpty()) report("choice-spell-name-missing", where, "${choice.title}: $missing")
        }
        // Заклинания расы не дублируют заклинания старой версии особенности (тифлинги).
        val raceSpellSources = (context.race?.grants?.spells.orEmpty() + context.subrace?.grants?.spells.orEmpty())
            .filter { it.minLevel <= draft.level }
            .map { it.source }.filter { it.isNotBlank() }
        raceSpellSources.groupBy { it }.filterValues { it.size > MAX_SPELLS_PER_TRAIT }.keys.forEach {
            report("race-spells-many-per-trait", where, it)
        }
        // Лимит подготовки не превышен.
        if (character.canPrepareSpells && creation.preparedIds.size > character.maxPreparedSpells) {
            report("prepared-over-limit", where, "${creation.preparedIds.size} > ${character.maxPreparedSpells}")
        }
        // Служебные умения в заметках.
        creation.notes.flatMap { block -> block.paragraphs.map { block.title to it.text.lineSequence().first() } }
            .filter { (_, head) -> META_TITLES.any { head.lowercase().startsWith(it) } }
            .forEach { (block, head) -> report("note-meta", where, "$block → $head") }

        // Характеристики и числа листа.
        AbilityType.entries.forEach { ability ->
            val score = character.abilityScores[ability.ordinal] ?: 0
            if (score !in 3..MAX_SCORE) report("ability-out-of-range", where, "$ability=$score")
        }
        // Ни одна официальная раса не даёт больше +2 к одной характеристике; больше — значит бонусы
        // разновидности сложились с бонусами расы, хотя должны были их заменить.
        val racial = WizardEngine.racialBonuses(draft, context)
        val bonusTotal = racial.values.sum()
        if (bonusTotal > MAX_RACIAL_BONUS || racial.values.any { it > 2 }) {
            report("racial-bonus-too-high", where, "sum=$bonusTotal $racial")
        }
        // Одноимённые особенности расы и разновидности — версия разновидности должна заменять базовую.
        val raceTitles = context.race?.traits.orEmpty().map { it.title.lowercase() }.toSet()
        context.subrace?.traits.orEmpty().filter { it.title.lowercase() in raceTitles }.forEach {
            report("trait-duplicate-race-subrace", where, it.title)
        }
        if (character.maxHp <= 0) report("hp-nonpositive", where, "${character.maxHp}")
        val ac = character.effectiveArmorClass
        if (ac !in 8..MAX_AC) report("ac-out-of-range", where, "$ac parts=${character.armorClassParts.map { it.name + ":" + it.formula }}")
        val speed = character.effectiveSpeed
        if (speed !in 10..MAX_SPEED) report("speed-out-of-range", where, "$speed parts=${character.speedParts.map { it.name + ":" + it.formula }}")
        if (character.initiativeBonus !in -5..MAX_INITIATIVE) report("initiative-out-of-range", where, "${character.initiativeBonus}")
        (character.armorClassParts + character.speedParts + character.initiativeParts).forEach { part ->
            if (!StatFormula.isValid(part.formula)) report("stat-part-bad-formula", where, "${part.name}: ${part.formula}")
        }
        character.speedParts.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { report("speed-part-duplicate", where, it) }
        character.initiativeParts.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { report("initiative-part-duplicate", where, it) }
        character.armorClassParts.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { report("ac-part-duplicate", where, it) }

        // Заклинания: подготовленные — подмножество известных, лимит заговоров.
        if (!creation.spellIds.containsAll(creation.preparedIds)) report("prepared-not-known", where, "")
        val byId = spells.associateBy { it.id }
        val missingSpells = creation.spellIds.filter { it !in byId }
        if (missingSpells.isNotEmpty()) report("spell-unknown-id", where, missingSpells.joinToString())
        if (character.canPrepareSpells && character.maxPreparedSpells <= 0) report("prepare-zero-limit", where, "")
        if (context.spellcasting == null && character.spellSlots.isNotEmpty()) report("slots-without-caster", where, "${character.spellSlots}")

        // Расовые и классовые заклинания из текста должны находиться в библиотеке.
        val textSpells = context.featureSpells + context.race?.grants?.spells.orEmpty().filter { it.minLevel <= draft.level }.map { it.name } +
            context.subrace?.grants?.spells.orEmpty().filter { it.minLevel <= draft.level }.map { it.name }
        textSpells.filter { name -> spells.none { MarkupText.matchesSpellName(name, it.name) } }
            .forEach { report("text-spell-not-in-library", where, it) }

        // Языки и навыки без мусора.
        character.languages.split(',').map { it.trim() }.filter { it.isNotEmpty() }.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { report("language-duplicate", where, it) }
        if (character.languages.lines().size > 1 || character.languages.length > MAX_LANGUAGES_LENGTH) {
            report("language-garbage", where, character.languages.take(200))
        }
        if (character.toolProficiencies.length > MAX_TOOLS_LENGTH) report("tools-garbage", where, character.toolProficiencies.take(200))
    }

    // endregion

    @Test
    fun sweep() {
        val classRules = classes.map(cache::characterClass)
        val raceRules = races.map(cache::race)
        val officialBackgrounds = backgrounds.map(cache::background)
            .filter { it.skills.isNotEmpty() || it.choices.isNotEmpty() || it.equipment.isNotEmpty() }
        var combinations = 0

        // 1. Каждый класс × архетип × уровень × опции Tasha (раса и предыстория фиксированы).
        val human = raceRules.first { it.race.name == "Человек" && it.race.category == "Основные расы" }
        val soldier = officialBackgrounds.first { it.background.name == "Солдат" }
        classRules.forEach { rules ->
            val subclasses = rules.subclasses.ifEmpty { listOf(null) }
            subclasses.forEach { sub ->
                LEVELS.forEach { level ->
                    listOf(false, true).forEach { optional ->
                        // Неофициальные архетипы проверяем только с переключателем UA.
                        val unofficial = sub?.extended == true
                        val baseDraft = CharacterDraft(
                            name = "Тест", level = level,
                            raceId = human.race.id, backgroundId = soldier.background.id,
                            showOptional = optional, showUnofficial = unofficial,
                        )
                        var draft = WizardEngine.selectClass(baseDraft, rules.charClass.id)
                        if (sub != null && level >= rules.subclassLevel) draft = WizardEngine.selectSubclass(draft, sub.id)
                        val swapVariants = if (optional) {
                            val swaps = WizardEngine.context(draft, library, cache).swaps
                            listOf(emptyMap(), swaps.associate { it.id to true })
                        } else listOf(emptyMap())
                        swapVariants.forEach { swaps ->
                            val where = "class=${rules.charClass.name} sub=${sub?.title} lvl=$level tasha=$optional swaps=${swaps.size}"
                            runCatching {
                                check(where, fillRest(fillChoices(draft.copy(optionalSwaps = swaps))))
                            }.onFailure { report("sweep-crash", where, "${it::class.simpleName}: ${it.message}") }
                            combinations++
                        }
                    }
                }
            }
        }

        // 2. Каждая раса × разновидность (класс и предыстория фиксированы: волшебник 5 и солдат).
        val wizard = classRules.first { it.charClass.name == "Волшебник" }
        raceRules.forEach { race ->
            val variants = listOf<SubraceRules?>(null) + race.subraces
            variants.forEach { sub ->
                if (sub == null && race.subraceRequired) return@forEach
                listOf(false, true).forEach { unofficial ->
                    if (!unofficial && (race.extended || sub?.extended == true)) return@forEach
                    var draft = CharacterDraft(
                        name = "Тест", level = RACE_LEVEL, backgroundId = soldier.background.id,
                        showUnofficial = unofficial,
                    )
                    draft = WizardEngine.selectRace(draft, race.race.id)
                    if (sub != null) draft = WizardEngine.selectSubrace(draft, sub.id)
                    draft = WizardEngine.selectClass(draft, wizard.charClass.id)
                    val where = "race=${race.race.name} [${race.race.category}] sub=${sub?.title} ua=$unofficial"
                    runCatching { check(where, fillRest(fillChoices(draft))) }
                        .onFailure { report("sweep-crash", where, "${it::class.simpleName}: ${it.message}") }
                    combinations++
                }
            }
        }

        // 3. Каждая предыстория (человек-воин 1-го уровня).
        val fighter = classRules.first { it.charClass.name == "Воин" }
        officialBackgrounds.forEach { bg ->
            var draft = CharacterDraft(name = "Тест", level = 1, raceId = human.race.id)
            draft = WizardEngine.selectClass(draft, fighter.charClass.id)
            draft = WizardEngine.selectBackground(draft, bg.background.id)
            val where = "background=${bg.background.name} [${bg.background.book}]"
            runCatching { check(where, fillRest(fillChoices(draft))) }
                .onFailure { report("sweep-crash", where, "${it::class.simpleName}: ${it.message}") }
            combinations++
        }

        // 4. Каждая черта в слоте увеличения (воин 4-го уровня).
        feats.forEach { feat ->
            var draft = CharacterDraft(name = "Тест", level = 4, raceId = human.race.id, backgroundId = soldier.background.id)
            draft = WizardEngine.selectClass(draft, fighter.charClass.id)
            draft = draft.copy(asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)), featSlots = mapOf("asi.4" to feat.id))
            val where = "feat=${feat.name} [${feat.book}]"
            runCatching {
                val filled = fillChoices(draft)
                val rest = fillRest(filled).copy(featSlots = mapOf("asi.4" to feat.id), asiChoices = mapOf(4 to AsiChoice(4, AsiKind.FEAT)))
                check(where, fillChoices(rest))
            }.onFailure { report("sweep-crash", where, "${it::class.simpleName}: ${it.message}") }
            combinations++
        }

        // 5. Случайные полные сочетания: раса × разновидность × класс × архетип × предыстория × уровень ×
        // переключатели. Ловят ошибки на стыке правил (повторные владения, заклинания расы у заклинателя
        // и т. п.). Зерно фиксировано — прогон воспроизводим.
        val random = kotlin.random.Random(RANDOM_SEED)
        val officialRaces = raceRules.filter { !it.extended }
        repeat(RANDOM_COMBINATIONS) {
            val race = officialRaces.random(random)
            val sub = race.subraces.filter { !it.extended }.let { subs ->
                if (subs.isEmpty() || (!race.subraceRequired && random.nextBoolean())) null else subs.random(random)
            }
            val rules = classRules.random(random)
            val level = random.nextInt(1, 21)
            val subclass = rules.subclasses.filter { !it.extended }.randomOrNull(random)
            val bg = officialBackgrounds.random(random)
            val optional = random.nextBoolean()
            var draft = CharacterDraft(name = "Тест", level = level, showOptional = optional)
            draft = WizardEngine.selectRace(draft, race.race.id)
            if (sub != null) draft = WizardEngine.selectSubrace(draft, sub.id)
            draft = WizardEngine.selectClass(draft, rules.charClass.id)
            if (subclass != null && level >= rules.subclassLevel) draft = WizardEngine.selectSubclass(draft, subclass.id)
            draft = WizardEngine.selectBackground(draft, bg.background.id)
            if (optional) {
                val swaps = WizardEngine.context(draft, library, cache).swaps
                draft = draft.copy(optionalSwaps = swaps.associate { it.id to random.nextBoolean() })
            }
            val where = "random: race=${race.race.name} sub=${sub?.title} class=${rules.charClass.name} " +
                "sub=${subclass?.title} lvl=$level bg=${bg.background.name} tasha=$optional"
            runCatching { check(where, fillRest(fillChoices(draft))) }
                .onFailure { report("sweep-crash", where, "${it::class.simpleName}: ${it.message}") }
            combinations++
        }

        writeReport(combinations)
        // Тест — инструмент поиска: отчёт важнее красного статуса, но регресс должен быть виден.
        check(findings.isEmpty()) { "Найдено нарушений: ${findings.size}, см. build/wizard-sweep.txt" }
    }

    private fun writeReport(combinations: Int) {
        val out = StringBuilder()
        out.appendLine("Комбинаций: $combinations, нарушений: ${findings.size}")
        findings.groupBy { it.kind }.toSortedMap().forEach { (kind, items) ->
            out.appendLine()
            out.appendLine("=== $kind (${items.size})")
            // Одинаковые подробности сводим в одну строку с числом повторов и примером места.
            items.groupBy { it.details }.entries.sortedByDescending { it.value.size }.forEach { (details, same) ->
                out.appendLine("  [${same.size}] $details  ← ${same.first().where}")
            }
        }
        File("build/wizard-sweep.txt").writeText(out.toString())
    }

    private companion object {
        val LEVELS = listOf(1, 2, 3, 5, 11, 20)
        const val RACE_LEVEL = 5
        const val FILL_PASSES = 4
        const val MAX_SCORE = 30
        const val MAX_RACIAL_BONUS = 6
        const val MAX_AC = 26
        const val MAX_SPEED = 120
        const val MAX_INITIATIVE = 20
        const val MAX_LANGUAGES_LENGTH = 200
        const val MAX_TOOLS_LENGTH = 300
        const val MAX_SPELLS_PER_TRAIT = 3
        const val RANDOM_SEED = 20240611L
        /**
         * Случайных полных сочетаний за прогон. Разово проверено 3000 без нарушений; в обычном
         * наборе меньше, чтобы тесты шли быстро. Для глубокой проверки после правок разбора — увеличить.
         */
        const val RANDOM_COMBINATIONS = 400
        const val NOTE_COMPARE_CHARS = 80
        val META_TITLES = listOf(
            "использование заклинаний", "увеличение характеристик", "дополнительные заклинания",
            "расширенный список заклинаний", "магия договора",
        )
    }
}
