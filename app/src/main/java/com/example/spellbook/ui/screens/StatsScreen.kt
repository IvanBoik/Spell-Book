package com.example.spellbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spellbook.data.model.AbilityType
import androidx.compose.ui.res.stringResource
import com.example.spellbook.R
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.D20RollResult
import com.example.spellbook.data.model.ProficiencyLevel
import com.example.spellbook.data.model.RollKind
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.formatModifier
import com.example.spellbook.ui.components.DndTopBar
import kotlin.math.cos
import kotlin.math.sin

/** Через сколько всплывающая плашка результата скрывается сама. */
private const val ROLL_TOAST_DURATION_MS = 4_000L
private val STATS_CARD_SHAPE = RoundedCornerShape(12.dp)
private val STATS_CHIP_SHAPE = RoundedCornerShape(8.dp)


/** Область нажатия и размер самого значка владения. */
private val PROFICIENCY_TOGGLE_SIZE = 40.dp
private val PROFICIENCY_MARK_SIZE = 26.dp
private const val EXPERTISE_RAY_COUNT = 8

/**
 * Экран характеристик персонажа: хиты и защита сверху, ниже характеристики,
 * спасброски и навыки. Нажатие на любую строку бросает d20 с её бонусом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    character: Character,
    lastRoll: D20RollResult?,
    onChangeHp: (Int) -> Unit,
    onSetHpValues: (tempHp: Int, maxHp: Int) -> Unit,
    onSetArmorClass: (Int) -> Unit,
    onSetSpeed: (Int) -> Unit,
    onSetAbilityScore: (AbilityType, Int) -> Unit,
    onToggleSave: (AbilityType) -> Unit,
    onCycleSkill: (SkillType) -> Unit,
    onRoll: (title: String, kind: RollKind, bonus: Int) -> Unit,
    onDismissRoll: () -> Unit,
    onBack: () -> Unit,
    sectionsBar: @Composable () -> Unit = {},
) {
    var showHpDialog by remember { mutableStateOf(false) }
    var editingArmor by remember { mutableStateOf(false) }
    var editingSpeed by remember { mutableStateOf(false) }
    var editingAbility by remember { mutableStateOf<AbilityType?>(null) }

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(R.string.stats_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Панель вне списка: так её отступы не зависят от интервалов между карточками
                // и одинаковы со всеми остальными экранами персонажа.
                sectionsBar()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "vitals") {
                        VitalsCard(
                            character = character,
                            onHpClick = { showHpDialog = true },
                            onArmorClick = { editingArmor = true },
                            onSpeedClick = { editingSpeed = true },
                        )
                    }

                    // Характеристика — заголовок блока, внутри — её спасбросок и связанные навыки.
                    items(AbilityType.entries, key = { "ability_${it.ordinal}" }) { ability ->
                        AbilityBlock(
                            character = character,
                            ability = ability,
                            onRoll = onRoll,
                            onEditScore = { editingAbility = ability },
                            onToggleSave = { onToggleSave(ability) },
                            onCycleSkill = onCycleSkill,
                        )
                    }
                }
            }

            // Небольшая плашка результата слева внизу — не перекрывает экран.
            RollToast(
                roll = lastRoll,
                onDismiss = onDismissRoll,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }
    }

    if (showHpDialog) {
        HpDialog(
            character = character,
            onDismiss = { showHpDialog = false },
            onChangeHp = onChangeHp,
            onSetHpValues = onSetHpValues,
        )
    }
    if (editingArmor) {
        NumberDialog(
            title = stringResource(R.string.stats_armor_class),
            initial = character.armorClass,
            onDismiss = { editingArmor = false },
            onSave = { onSetArmorClass(it); editingArmor = false },
        )
    }
    if (editingSpeed) {
        NumberDialog(
            title = stringResource(R.string.stats_speed_dialog),
            initial = character.speed,
            onDismiss = { editingSpeed = false },
            onSave = { onSetSpeed(it); editingSpeed = false },
        )
    }
    editingAbility?.let { ability ->
        NumberDialog(
            title = stringResource(ability.labelRes),
            initial = character.abilityScore(ability),
            onDismiss = { editingAbility = null },
            onSave = { onSetAbilityScore(ability, it); editingAbility = null },
        )
    }
}

/** Хиты, класс защиты и скорость. Хиты открывают расширенный диалог. */
@Composable
private fun VitalsCard(
    character: Character,
    onHpClick: () -> Unit,
    onArmorClick: () -> Unit,
    onSpeedClick: () -> Unit,
) {
    Card(
        shape = STATS_CARD_SHAPE,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHpClick)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.stats_hp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${character.currentHp} / ${character.maxHp}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (character.tempHp > 0) {
                        Text(
                            text = " +${character.tempHp}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (character.tempHp > 0) {
                        stringResource(R.string.stats_temp_hp_value, character.tempHp)
                    } else {
                        stringResource(R.string.stats_no_temp_hp)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                VitalValue(
                    label = stringResource(R.string.stats_defense),
                    value = character.armorClass.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = onArmorClick,
                )
                VitalValue(
                    label = stringResource(R.string.stats_speed),
                    value = stringResource(R.string.stats_speed_value, character.speed),
                    modifier = Modifier.weight(1f),
                    onClick = onSpeedClick,
                )
                VitalValue(
                    label = stringResource(R.string.stats_proficiency),
                    value = formatModifier(character.proficiencyBonus),
                    modifier = Modifier.weight(1f),
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun VitalValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Блок одной характеристики: сама характеристика как заголовок, рядом спасбросок,
 * ниже связанные навыки по два в строке.
 */
@Composable
private fun AbilityBlock(
    character: Character,
    ability: AbilityType,
    onRoll: (String, RollKind, Int) -> Unit,
    onEditScore: () -> Unit,
    onToggleSave: () -> Unit,
    onCycleSkill: (SkillType) -> Unit,
) {
    val abilityModifier = character.abilityModifierOf(ability)
    val saveBonus = character.saveBonus(ability)
    val saveProficient = character.saveProficiency(ability) != ProficiencyLevel.NONE
    val skills = SkillType.entries.filter { it.ability == ability }
    val saveTitle = stringResource(R.string.action_type_save)
    val abilityLabel = stringResource(ability.labelRes)

    Card(
        shape = STATS_CARD_SHAPE,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Заголовок: тап — проверка характеристики, кнопка — изменение значения.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRoll(abilityLabel, RollKind.ABILITY, abilityModifier) },
                ) {
                    Text(abilityLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.stats_ability_check,
                            character.abilityScore(ability),
                            formatModifier(abilityModifier),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEditScore) { Text(stringResource(R.string.stats_edit_value)) }
            }

            // Спасбросок той же характеристики.
            StatChip(
                title = saveTitle,
                bonus = saveBonus,
                proficiencyMark = if (saveProficient) ProficiencyLevel.PROFICIENT else ProficiencyLevel.NONE,
                onClick = { onRoll("$saveTitle · $abilityLabel", RollKind.SAVE, saveBonus) },
                onToggle = onToggleSave,
                modifier = Modifier.fillMaxWidth(),
            )

            // По одному навыку в строке: длинные названия помещаются без переноса.
            skills.forEach { skill ->
                val bonus = character.skillBonus(skill)
                val skillLabel = stringResource(skill.labelRes)
                StatChip(
                    title = skillLabel,
                    bonus = bonus,
                    proficiencyMark = character.skillProficiency(skill),
                    onClick = { onRoll(skillLabel, RollKind.SKILL, bonus) },
                    onToggle = { onCycleSkill(skill) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Компактная ячейка спасброска или навыка: тап по ячейке — бросок d20,
 * тап по кружку справа — переключение владения.
 */
@Composable
private fun StatChip(
    title: String,
    bonus: Int,
    proficiencyMark: ProficiencyLevel,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proficient = proficiencyMark != ProficiencyLevel.NONE
    Row(
        modifier = modifier
            .clip(STATS_CHIP_SHAPE)
            .background(
                if (proficient) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (proficient) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
        Text(
            text = formatModifier(bonus),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        ProficiencyToggle(
            level = proficiencyMark,
            contentColor = contentColor,
            onToggle = onToggle,
        )
    }
}

/**
 * Кружок-переключатель владения: пустой — владения нет, залитый — владение,
 * залитый с лучами и внешним кольцом — экспертиза.
 */
@Composable
private fun ProficiencyToggle(
    level: ProficiencyLevel,
    contentColor: Color,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PROFICIENCY_TOGGLE_SIZE)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(PROFICIENCY_MARK_SIZE)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = radius * 0.22f
            // Базовый контур есть всегда; заливка появляется с владением.
            val innerRadius = radius * 0.62f
            drawCircle(
                color = contentColor.copy(alpha = 0.75f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
            if (level != ProficiencyLevel.NONE) {
                drawCircle(color = contentColor, radius = innerRadius - strokeWidth / 2f, center = center)
            }
            if (level == ProficiencyLevel.EXPERTISE) {
                // Узор экспертизы: внешнее кольцо и лучи по кругу.
                drawCircle(
                    color = contentColor.copy(alpha = 0.9f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth * 0.7f),
                )
                repeat(EXPERTISE_RAY_COUNT) { index ->
                    val angle = (2.0 * Math.PI / EXPERTISE_RAY_COUNT) * index
                    val startRadius = innerRadius + strokeWidth * 0.6f
                    val endRadius = radius - strokeWidth * 0.6f
                    drawLine(
                        color = contentColor,
                        start = Offset(
                            center.x + (cos(angle) * startRadius).toFloat(),
                            center.y + (sin(angle) * startRadius).toFloat(),
                        ),
                        end = Offset(
                            center.x + (cos(angle) * endRadius).toFloat(),
                            center.y + (sin(angle) * endRadius).toFloat(),
                        ),
                        strokeWidth = strokeWidth * 0.7f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Строка характеристики, спасброска или навыка. Обычное нажатие бросает d20,
 * кнопка справа меняет владение или значение.
 */
@Composable
private fun StatRow(
    title: String,
    subtitle: String,
    bonus: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actionLabel: String,
    highlighted: Boolean = false,
) {
    Card(
        shape = STATS_CARD_SHAPE,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatModifier(bonus),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(56.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLongClick) { Text(actionLabel) }
        }
    }
}

/** Всплывающая плашка результата броска: компактная, слева внизу. */
@Composable
private fun RollToast(
    roll: D20RollResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Плашка сама исчезает, чтобы не мешать дальнейшим броскам.
    LaunchedEffect(roll?.id) {
        if (roll != null) {
            kotlinx.coroutines.delay(ROLL_TOAST_DURATION_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = roll != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        roll?.let { result ->
            Card(
                shape = STATS_CARD_SHAPE,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "${result.total}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
            text = stringResource(R.string.stats_roll_result, result.roll, formatModifier(result.bonus)) + when {
                result.isCriticalSuccess -> stringResource(R.string.stats_crit_success)
                result.isCriticalFailure -> stringResource(R.string.stats_crit_failure)
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.stats_close_result),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** Диалог хитов: урон, лечение, временные хиты и максимум. */
@Composable
private fun HpDialog(
    character: Character,
    onDismiss: () -> Unit,
    onChangeHp: (Int) -> Unit,
    onSetHpValues: (tempHp: Int, maxHp: Int) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var tempHp by remember { mutableStateOf(character.tempHp.takeIf { it > 0 }?.toString() ?: "") }
    var maxHp by remember { mutableStateOf(character.maxHp.toString()) }
    val parsedAmount = amount.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.stats_hp)) },
        text = {
            // При открытой клавиатуре диалог сжимается, поэтому содержимое прокручивается.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                stringResource(R.string.stats_current_hp, character.currentHp, character.maxHp) +
                    if (character.tempHp > 0) {
                        stringResource(R.string.stats_temp_suffix, character.tempHp)
                    } else {
                        ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> amount = new.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.stats_amount)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChangeHp(-parsedAmount); amount = "" },
                        enabled = parsedAmount > 0,
                        modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.stats_damage)) }
                    Button(
                        onClick = { onChangeHp(parsedAmount); amount = "" },
                        enabled = parsedAmount > 0,
                        modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.stats_heal)) }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = tempHp,
                    onValueChange = { new -> tempHp = new.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.stats_temp_hp)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxHp,
                    onValueChange = { new -> maxHp = new.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.stats_max_hp)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSetHpValues(tempHp.toIntOrNull() ?: 0, maxHp.toIntOrNull() ?: character.maxHp)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
