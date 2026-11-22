package com.example.spellbook.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.wizard.ClassRules
import com.example.spellbook.data.wizard.ArmorType
import com.example.spellbook.data.wizard.EquipmentCatalog
import com.example.spellbook.data.wizard.EquipmentEntry
import com.example.spellbook.data.wizard.WeaponCategory
import com.example.spellbook.data.wizard.WeaponInfo
import com.example.spellbook.data.wizard.WeaponSlot
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import com.example.spellbook.data.wizard.HpMethod
import com.example.spellbook.data.wizard.MarkupText
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardStep

// region Класс

@Composable
fun ClassStep(ui: WizardUi) {
    val rules = ui.context.characterClass
    var browsing by rememberSaveable { mutableStateOf(rules == null) }
    if (browsing || rules == null) {
        ClassList(ui) { id ->
            ui.update { WizardEngine.selectClass(it, id) }
            browsing = false
        }
    } else {
        ClassDetails(ui, rules, onChange = { browsing = true })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassList(ui: WizardUi, onPick: (String) -> Unit) {
    val all = remember(ui.library.classes) {
        ui.library.classes.map(ui.controller.cache::characterClass).sortedBy { it.charClass.name }
    }
    // Неофициальные классы — только с переключателем; уже выбранный остаётся виден (как разновидности).
    val rules = all.filter { ui.draft.showUnofficial || !it.extended || it.charClass.id == ui.draft.classId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StepHeader(stringResource(R.string.wizard_class_title), stringResource(R.string.wizard_class_subtitle)) }
        if (rules.isEmpty()) item { HintRow(stringResource(R.string.wizard_library_empty)) }
        items(rules, key = { it.charClass.id }) { item ->
            SelectableCard(selected = item.charClass.id == ui.draft.classId, onClick = { onPick(item.charClass.id) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.charClass.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoPill(stringResource(R.string.wizard_hit_die, item.hitDie), highlighted = true)
                        if (item.savingThrows.isNotEmpty()) {
                            InfoPill(item.savingThrows.map { ability -> abilityShort(ability) }.joinToString(", "))
                        }
                        if (item.type.spellcaster != com.example.spellbook.data.model.SpellcasterType.NONE) {
                            InfoPill(stringResource(R.string.cform_spellcaster))
                        }
                        if (item.extended) InfoPill(stringResource(R.string.wizard_unofficial_badge), highlighted = true)
                    }
                    if (item.charClass.book.isNotBlank()) {
                        Text(item.charClass.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val features = item.levelFeatures[1].orEmpty()
                    if (features.isNotEmpty()) {
                        Text(
                            features.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun abilityShort(ability: com.example.spellbook.data.model.AbilityType) = stringResource(ability.shortLabelRes)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassDetails(ui: WizardUi, rules: ClassRules, onChange: () -> Unit) {
    val draft = ui.draft
    StepColumn {
        ChosenHeader(
            label = stringResource(R.string.wizard_step_class),
            title = rules.charClass.name,
            subtitle = stringResource(R.string.wizard_class_level, draft.level),
            onChange = onChange,
        )
        StepIssues(ui, WizardStep.Class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoPill(stringResource(R.string.wizard_hit_die, rules.hitDie), highlighted = true)
            if (rules.savingThrows.isNotEmpty()) {
                InfoPill(stringResource(R.string.wizard_saves, rules.savingThrows.map { abilityShort(it) }.joinToString(", ")))
            }
        }

        // Архетип.
        if (rules.subclasses.isNotEmpty()) {
            SubclassSection(ui, rules)
        }

        // Владения без выбора: спасброски, доспехи, оружие, инструменты и навыки из умений.
        ClassProficiencies(ui, rules)

        // Опциональные умения, заменяющие базовые: либо одно, либо другое.
        ui.context.swaps.forEach { swap -> OptionalSwapBlock(ui, swap) }

        // Выборы класса на этом шаге (навыки, инструменты, местность, избранный враг…).
        WizardEngine.choicesOn(WizardStep.Class, ui.context).forEach { choice -> ChoiceBlock(ui, choice) }

        // Умения по уровням — чтобы видеть, что даёт класс.
        HorizontalDivider()
        SectionTitle(stringResource(R.string.wizard_class_features))
        com.example.spellbook.data.wizard.FeatureRules
            .noteFeatures(rules.features, draft.level, draft.showOptional, rules)
            .forEach { feature ->
                ExpandableDescription(
                    title = feature.title,
                    text = feature.text,
                    badge = stringResource(R.string.wizard_level_short, feature.level),
                )
            }
    }
}

/**
 * Выбор между базовым умением и заменяющим его опциональным из Tasha's Cauldron of Everything.
 * По умолчанию действует базовое — как в «Книге игрока».
 */
@Composable
private fun OptionalSwapBlock(ui: WizardUi, swap: com.example.spellbook.data.wizard.OptionalSwap) {
    val useOptional = ui.draft.optionalSwaps[swap.id] == true
    fun choose(optional: Boolean) = ui.update { it.copy(optionalSwaps = it.optionalSwaps + (swap.id to optional)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.wizard_swap_title, swap.optional.title))
        HintRow(stringResource(R.string.wizard_swap_hint))
        SelectableCard(selected = !useOptional, onClick = { choose(false) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    swap.base.joinToString(" + ") { it.title },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.wizard_swap_base),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                swap.base.forEach { feature -> ExpandableDescription(feature.title, feature.text) }
            }
        }
        SelectableCard(selected = useOptional, onClick = { choose(true) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(swap.optional.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.wizard_swap_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                ExpandableDescription(swap.optional.title, swap.optional.text)
            }
        }
    }
}

@Composable
private fun SubclassSection(ui: WizardUi, rules: ClassRules) {
    val draft = ui.draft
    val available = draft.level >= rules.subclassLevel
    SectionTitle(rules.subclassGroupTitle)
    if (!available) {
        HintRow(stringResource(R.string.wizard_subclass_later, rules.subclassLevel))
        return
    }
    val visible = rules.subclasses.filter { draft.showUnofficial || !it.extended || it.id == draft.subclassId }
    visible.forEach { sub ->
        val selected = sub.id == draft.subclassId
        SelectableCard(selected = selected, onClick = { ui.update { WizardEngine.selectSubclass(it, sub.id) } }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (sub.extended) InfoPill(stringResource(R.string.wizard_unofficial_badge), highlighted = true)
                }
                val source = Regex("Источник:\\s*«?([^»*]+)").find(MarkupText.plain(sub.text))?.groupValues?.get(1)?.trim()
                if (!source.isNullOrBlank()) {
                    Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val features = sub.features.filter { it.level <= draft.level }
                if (features.isNotEmpty()) {
                    Text(
                        features.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpandOnSelect(selected) {
                    val subGrants = features.fold(com.example.spellbook.data.wizard.FeatureGrants()) { acc, feature ->
                        acc + com.example.spellbook.data.wizard.FeatureRules.grants(feature.text)
                    }
                    GrantedProficiencies(
                        skills = subGrants.skills,
                        rows = listOf(
                            stringResource(R.string.cform_armor) to subGrants.armor.sortedBy { it.ordinal }.map { stringResource(it.labelRes) },
                            stringResource(R.string.cform_weapons) to subGrants.weapons.sortedBy { it.ordinal }
                                .map { stringResource(it.labelRes) } + subGrants.otherWeapons,
                            stringResource(R.string.cform_tools) to subGrants.tools,
                            stringResource(R.string.cform_languages) to subGrants.languages,
                        ),
                    )
                    features.forEach { feature ->
                        ExpandableDescription(feature.title, feature.text, badge = stringResource(R.string.wizard_level_short, feature.level))
                    }
                }
            }
        }
    }
}

/**
 * Шаг стартового снаряжения класса: варианты «а) … или б) …» карточками с КД доспехов
 * и уроном оружия, а «любое простое оружие» — выбором из таблицы оружия.
 */
@Composable
fun EquipmentStep(ui: WizardUi) {
    val rules = ui.context.characterClass ?: return EmptyCentered(stringResource(R.string.wizard_issue_class))
    StepColumn {
        StepHeader(stringResource(R.string.wizard_equipment), stringResource(R.string.wizard_equipment_subtitle))
        StepIssues(ui, WizardStep.Equipment)
        EquipmentSection(ui, rules)
    }
}

@Composable
private fun EquipmentSection(ui: WizardUi, rules: ClassRules) {
    val draft = ui.draft
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !draft.takeClassGold,
            onClick = { ui.update { it.copy(takeClassGold = false) } },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.wizard_equipment_kit)) }
        SegmentedButton(
            selected = draft.takeClassGold,
            onClick = { ui.update { it.copy(takeClassGold = true) } },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            enabled = rules.startingGold.isNotBlank(),
        ) { Text(stringResource(R.string.wizard_equipment_gold)) }
    }
    if (draft.takeClassGold) {
        HintRow(stringResource(R.string.wizard_gold_hint, rules.startingGold))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { ui.controller.rollClassGold(rules.startingGold) }) {
                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.wizard_roll), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedTextField(
                value = draft.classGold?.toString().orEmpty(),
                onValueChange = { text -> ui.update { it.copy(classGold = text.filter(Char::isDigit).take(5).toIntOrNull()) } },
                label = { Text(stringResource(R.string.wizard_gold_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    if (rules.equipment.isEmpty()) {
        HintRow(stringResource(R.string.wizard_equipment_missing))
    }
    rules.equipment.forEachIndexed { index, group ->
        val chosen = (draft.equipmentChoices[index] ?: 0).coerceIn(0, (group.options.size - 1).coerceAtLeast(0))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (group.hasChoice) R.string.wizard_equipment_choose_one else R.string.wizard_equipment_included),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.options.forEachIndexed { optionIndex, option ->
                val parsed = remember(option.label) { EquipmentCatalog.parseOption(option.label) }
                val selected = chosen == optionIndex
                SelectableCard(
                    selected = selected || !group.hasChoice,
                    enabled = group.hasChoice,
                    onClick = { ui.update { it.copy(equipmentChoices = it.equipmentChoices + (index to optionIndex)) } },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.entries.forEach { entry -> EquipmentEntryRow(entry) }
                        // Оружие на выбор выбирается прямо в карточке выбранного варианта.
                        parsed.slots.forEachIndexed { slotIndex, slot ->
                            WeaponSlotField(
                                ui = ui,
                                slot = slot,
                                key = WizardEngine.weaponSlotKey(index, optionIndex, slotIndex),
                                active = selected || !group.hasChoice,
                            )
                        }
                    }
                }
            }
        }
    }
    HintRow(stringResource(R.string.wizard_equipment_hint))
}

/** Предмет варианта снаряжения: название, количество и характеристики доспеха или оружия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentEntryRow(entry: EquipmentEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (entry.quantity > 1) "${entry.name} ×${entry.quantity}" else entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.armor != null || entry.weapon != null) FontWeight.Bold else FontWeight.Normal,
        )
        entry.armor?.let { armor ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoPill(armor.acLabel(), highlighted = true)
                InfoPill(stringResource(armorTypeLabel(armor.type)))
                armor.notes().forEach { InfoPill(it) }
            }
        }
        entry.weapon?.let { weapon -> WeaponPills(weapon) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeaponPills(weapon: WeaponInfo) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (weapon.damage != WeaponInfo.NO_DAMAGE) InfoPill("${weapon.damage} ${weapon.damageType}", highlighted = true)
        weapon.properties.forEach { InfoPill(it) }
    }
}

private fun armorTypeLabel(type: ArmorType) = when (type) {
    ArmorType.LIGHT -> R.string.wizard_armor_light
    ArmorType.MEDIUM -> R.string.wizard_armor_medium
    ArmorType.HEAVY -> R.string.wizard_armor_heavy
    ArmorType.SHIELD -> R.string.wizard_armor_shield
}

/**
 * Слот «оружие на выбор»: выбранное оружие с уроном и кнопка, открывающая таблицу
 * подходящего оружия. «Два воинских оружия» — два выбора, можно одинаковых.
 */
@Composable
private fun WeaponSlotField(ui: WizardUi, slot: WeaponSlot, key: String, active: Boolean) {
    var picking by remember { mutableStateOf(false) }
    val picks = ui.draft.weaponPicks[key].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (active) CountBadge(picks.size, slot.count)
        }
        picks.forEachIndexed { pickIndex, id ->
            val weapon = EquipmentCatalog.weaponById(id) ?: return@forEachIndexed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(weapon.name, fontWeight = FontWeight.Bold)
                    WeaponPills(weapon)
                }
                IconButton(onClick = {
                    ui.update { d -> d.copy(weaponPicks = d.weaponPicks + (key to picks.filterIndexed { i, _ -> i != pickIndex })) }
                }) { Icon(Icons.Default.Close, stringResource(R.string.action_remove)) }
            }
        }
        if (active && picks.size < slot.count) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_choose_weapon))
            }
        }
    }
    if (picking) {
        WeaponPickerDialog(
            title = slot.label,
            weapons = EquipmentCatalog.weapons(slot.categories),
            onPick = { weapon ->
                ui.update { d -> d.copy(weaponPicks = d.weaponPicks + (key to (d.weaponPicks[key].orEmpty() + weapon.id))) }
                picking = false
            },
            onClose = { picking = false },
        )
    }
}

/** Таблица оружия для выбора: по категориям, с уроном и свойствами. */
@Composable
private fun WeaponPickerDialog(title: String, weapons: List<WeaponInfo>, onPick: (WeaponInfo) -> Unit, onClose: () -> Unit) {
    FullScreenPicker(title = title, onClose = onClose) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weapons.groupBy { it.category }.forEach { (category, items) ->
                item(key = "h:$category") {
                    Text(
                        stringResource(weaponCategoryLabel(category)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items, key = { it.id }) { weapon ->
                    SelectableCard(selected = false, onClick = { onPick(weapon) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(weapon.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            WeaponPills(weapon)
                        }
                    }
                }
            }
        }
    }
}

private fun weaponCategoryLabel(category: WeaponCategory) = when (category) {
    WeaponCategory.SIMPLE_MELEE -> R.string.wizard_weapon_simple_melee
    WeaponCategory.SIMPLE_RANGED -> R.string.wizard_weapon_simple_ranged
    WeaponCategory.MARTIAL_MELEE -> R.string.wizard_weapon_martial_melee
    WeaponCategory.MARTIAL_RANGED -> R.string.wizard_weapon_martial_ranged
}


/**
 * Владения класса и архетипа, которые даются без выбора, — явно, чтобы было видно,
 * что персонаж уже получил (например, «Бонусное владение» домена бури).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassProficiencies(ui: WizardUi, rules: ClassRules) {
    val grants = ui.context.classGrants
    val armor = (rules.armor + grants.armor).sortedBy { it.ordinal }.map { stringResource(it.labelRes) }
    val weapons = (rules.weapons + grants.weapons)
        .filter { it != com.example.spellbook.data.model.WeaponProficiency.OTHER }
        .sortedBy { it.ordinal }
        .map { stringResource(it.labelRes) } +
        listOf(rules.otherWeapons).filter { it.isNotBlank() } + grants.otherWeapons
    val tools = listOf(WizardEngine.fixedClassTools(rules)).filter { it.isNotBlank() } + grants.tools
    GrantedProficiencies(
        skills = grants.skills,
        rows = listOf(
            stringResource(R.string.cform_armor) to armor,
            stringResource(R.string.cform_weapons) to weapons,
            stringResource(R.string.cform_tools) to tools,
            stringResource(R.string.cform_languages) to grants.languages,
        ),
    )
}

@Composable
fun HitPointsSection(ui: WizardUi, rules: ClassRules) {
    val draft = ui.draft
    SectionTitle(stringResource(R.string.wizard_hp)) {
        WizardEngine.maxHp(draft, ui.context)?.let { hp ->
            InfoPill(stringResource(R.string.wizard_hp_value, hp), highlighted = true)
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        HpMethod.entries.forEachIndexed { index, method ->
            SegmentedButton(
                selected = draft.hpMethod == method,
                onClick = {
                    ui.update { it.copy(hpMethod = method) }
                    if (method == HpMethod.ROLL && draft.hpRolls.size != draft.level - 1) ui.controller.rollHitPoints(rules.hitDie)
                },
                shape = SegmentedButtonDefaults.itemShape(index, HpMethod.entries.size),
            ) {
                Text(
                    stringResource(
                        when (method) {
                            HpMethod.AVERAGE -> R.string.wizard_hp_average
                            HpMethod.ROLL -> R.string.wizard_hp_roll
                            HpMethod.MANUAL -> R.string.wizard_hp_manual
                        },
                    ),
                )
            }
        }
    }
    when (draft.hpMethod) {
        HpMethod.AVERAGE -> HintRow(
            stringResource(R.string.wizard_hp_average_hint, rules.hitDie, WizardEngine.averageHitDie(rules.hitDie)),
        )
        HpMethod.ROLL -> {
            if (draft.level == 1) {
                HintRow(stringResource(R.string.wizard_hp_first_level))
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    draft.hpRolls.forEachIndexed { index, roll ->
                        InfoPill(stringResource(R.string.wizard_hp_roll_item, index + 2, roll))
                    }
                }
                OutlinedButton(onClick = { ui.controller.rollHitPoints(rules.hitDie) }) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.wizard_reroll), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        HpMethod.MANUAL -> OutlinedTextField(
            value = draft.manualHp?.toString().orEmpty(),
            onValueChange = { text -> ui.update { it.copy(manualHp = text.filter(Char::isDigit).take(4).toIntOrNull()) } },
            label = { Text(stringResource(R.string.wizard_hp_max)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HintRow(stringResource(R.string.wizard_hp_con_hint))
}

// endregion

// region Заклинания

/**
 * Шаг заклинаний: вкладки «Заговоры», «Заклинания»/«Книга», «Подготовка»
 * и дополнительные выборы класса (арканум, книга теней, тайны магии).
 */
@Composable
fun SpellsStep(ui: WizardUi) {
    val plan = ui.context.spellcasting ?: return EmptyCentered(stringResource(R.string.wizard_no_spellcasting))
    val draft = ui.draft
    val tabs = buildList {
        if (plan.cantrips > 0) add(SpellTab.CANTRIPS)
        if ((plan.knownSpells ?: 0) > 0) add(SpellTab.KNOWN)
        if ((plan.spellbookSpells ?: 0) > 0) add(SpellTab.SPELLBOOK)
        if (plan.canPrepare) add(SpellTab.PREPARE)
        if (WizardEngine.choicesOn(WizardStep.Spells, ui.context).isNotEmpty()) add(SpellTab.EXTRA)
    }
    var tab by rememberSaveable { mutableStateOf(tabs.firstOrNull() ?: SpellTab.CANTRIPS) }
    if (tab !in tabs) tab = tabs.firstOrNull() ?: SpellTab.CANTRIPS

    val classFilter = com.example.spellbook.data.wizard.SpellFilter(classCodes = setOf(plan.classCode), schools = plan.schools)
    val alwaysPrepared = remember(ui.spells, plan.alwaysPrepared) {
        ui.spells.filter { spell -> plan.alwaysPrepared.any { MarkupText.matchesSpellName(it, spell.name) } }.map { it.id }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEach { item ->
                val (count, total) = when (item) {
                    SpellTab.CANTRIPS -> draft.cantripIds.size to plan.cantrips
                    SpellTab.KNOWN -> draft.spellIds.size to (plan.knownSpells ?: 0)
                    SpellTab.SPELLBOOK -> draft.spellIds.size to (plan.spellbookSpells ?: 0)
                    SpellTab.PREPARE -> draft.preparedIds.size to plan.preparedCount
                    SpellTab.EXTRA -> {
                        val extra = WizardEngine.choicesOn(WizardStep.Spells, ui.context)
                        extra.sumOf { ui.selection(it.id).size } to extra.sumOf { it.count }
                    }
                }
                androidx.compose.material3.Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text("${stringResource(item.labelRes)} $count/$total") },
                )
            }
        }
        val header: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                StepIssues(ui, WizardStep.Spells)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoPill(stringResource(R.string.wizard_spell_ability, stringResource(plan.ability.labelRes)), highlighted = true)
                    if (plan.maxSpellLevel > 0) InfoPill(stringResource(R.string.wizard_max_circle, plan.maxSpellLevel))
                    plan.slots.toSortedMap().forEach { (circle, count) -> InfoPill(stringResource(R.string.wizard_slot, circle, count)) }
                }
            }
        }
        when (tab) {
            SpellTab.CANTRIPS -> SpellSelectionList(
                // Заговоры мистических рыцаря и ловкача школой не ограничены.
                candidates = remember(ui.spells, classFilter) { filterSpells(ui.spells, classFilter.copy(maxLevel = 0, schools = emptySet())) },
                selected = draft.cantripIds,
                limit = plan.cantrips,
                onToggle = { id -> ui.update { it.copy(cantripIds = toggleSet(it.cantripIds, id, plan.cantrips)) } },
                header = { header(); HintRow(stringResource(R.string.wizard_cantrips_hint, plan.cantrips)) },
            )
            SpellTab.KNOWN, SpellTab.SPELLBOOK -> {
                val limit = if (tab == SpellTab.KNOWN) plan.knownSpells ?: 0 else plan.spellbookSpells ?: 0
                // Мистические рыцарь и ловкач: часть заклинаний может быть любой школы.
                val anySchool = plan.anySchoolSpells
                val offSchool = ui.context.offSchoolSpellIds
                val knownFilter = if (anySchool > 0) classFilter.copy(schools = emptySet()) else classFilter
                SpellSelectionList(
                    candidates = remember(ui.spells, knownFilter, plan.maxSpellLevel, plan.extraSpellNames) {
                        filterSpells(ui.spells, knownFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel), plan.extraSpellNames)
                    },
                    selected = draft.spellIds,
                    limit = limit,
                    onToggle = { id ->
                        ui.update {
                            // Свободные места заняты — заклинание другой школы больше не взять.
                            if (id !in it.spellIds && id in offSchool && it.spellIds.count { s -> s in offSchool } >= anySchool) {
                                return@update it
                            }
                            val spells = toggleSet(it.spellIds, id, limit)
                            // Волшебник готовит из книги: убранное из книги снимается с подготовки.
                            it.copy(spellIds = spells, preparedIds = if (tab == SpellTab.SPELLBOOK) it.preparedIds intersect spells else it.preparedIds)
                        }
                    },
                    header = {
                        header()
                        HintRow(
                            stringResource(
                                if (tab == SpellTab.KNOWN) R.string.wizard_known_hint else R.string.wizard_spellbook_hint,
                                limit,
                            ),
                        )
                        if (anySchool > 0) {
                            HintRow(
                                stringResource(
                                    R.string.wizard_any_school_hint,
                                    anySchool,
                                    draft.spellIds.count { it in offSchool },
                                ),
                            )
                        }
                    },
                )
            }
            SpellTab.PREPARE -> {
                // Волшебник готовит из книги, остальные — из всего списка класса.
                val candidates = remember(ui.spells, draft.spellIds, plan) {
                    if (plan.spellbookSpells != null) {
                        ui.spells.filter { it.id in draft.spellIds }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
                    } else {
                        filterSpells(ui.spells, classFilter.copy(minLevel = 1, maxLevel = plan.maxSpellLevel))
                    }
                }
                SpellSelectionList(
                    candidates = candidates,
                    selected = draft.preparedIds,
                    limit = plan.preparedCount,
                    locked = alwaysPrepared,
                    onToggle = { id -> ui.update { it.copy(preparedIds = toggleSet(it.preparedIds, id, plan.preparedCount)) } },
                    header = {
                        header()
                        HintRow(stringResource(R.string.wizard_prepare_hint, plan.preparedCount))
                        if (plan.preparesFromClassList) HintRow(stringResource(R.string.wizard_prepare_all_known))
                        if (plan.spellbookSpells != null && draft.spellIds.isEmpty()) HintRow(stringResource(R.string.wizard_prepare_book_first))
                    },
                )
            }
            SpellTab.EXTRA -> StepColumn {
                header()
                WizardEngine.choicesOn(WizardStep.Spells, ui.context).forEach { choice -> ChoiceBlock(ui, choice) }
            }
        }
    }
}

private enum class SpellTab(val labelRes: Int) {
    CANTRIPS(R.string.wizard_tab_cantrips),
    KNOWN(R.string.wizard_tab_known),
    SPELLBOOK(R.string.wizard_tab_spellbook),
    PREPARE(R.string.wizard_tab_prepare),
    EXTRA(R.string.wizard_tab_extra),
}

private fun toggleSet(set: Set<String>, id: String, limit: Int): Set<String> = when {
    id in set -> set - id
    set.size < limit -> set + id
    else -> set
}

// endregion

/** Чип со значением, по нажатию выбирающий его (используется в нескольких шагах). */
@Composable
fun ValueChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(selected = selected, enabled = enabled, onClick = onClick, label = { Text(text) })
}
