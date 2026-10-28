package com.example.spellbook.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.wizard.CharacterDraft
import com.example.spellbook.data.wizard.WizardContext
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardLibrary
import com.example.spellbook.data.wizard.WizardStep
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/**
 * Всё, что нужно экранам шагов: черновик, разобранные правила и библиотеки.
 * Один объект вместо длинных списков параметров у каждого шага.
 */
class WizardUi(
    val controller: CharacterWizardController,
    val context: WizardContext,
    val library: WizardLibrary,
    val spells: List<Spell>,
    val feats: List<Feat>,
    val goTo: (WizardStep) -> Unit,
) {
    val draft: CharacterDraft get() = controller.draft
    fun update(transform: (CharacterDraft) -> CharacterDraft) = controller.update(transform)

    /** Значения выбора и запись нового значения. */
    fun selection(id: String): List<String> = draft.selections[id].orEmpty()
    fun select(id: String, values: List<String>) = update { it.copy(selections = it.selections + (id to values)) }
}

/**
 * Мастер пошагового создания персонажа.
 *
 * Сверху — лента шагов: между ними можно переходить свободно, незавершённые шаги
 * отмечены. Дополнительные шаги (воззвания, боевые приёмы, компетентность) появляются
 * сразу после своего источника, как только выбор становится нужен.
 */
@Composable
fun CharacterWizardScreen(
    controller: CharacterWizardController,
    library: WizardLibrary,
    spells: List<Spell>,
    feats: List<Feat>,
    onClose: () -> Unit,
    onCreate: () -> Unit,
) {
    val draft = controller.draft
    val context = remember(draft, library) { controller.context(library) }
    val steps = remember(draft, context) { WizardEngine.steps(draft, context) }
    // Шаг мог исчезнуть (например, сменили класс) — тогда возвращаемся к ближайшему основному.
    val currentIndex = steps.indexOfFirst { it.key == controller.stepKey }.takeIf { it >= 0 }
        ?: steps.indexOfFirst { it == WizardStep.Class }.coerceAtLeast(0)
    val step = steps[currentIndex]
    var confirmExit by rememberSaveable { mutableStateOf(false) }

    val ui = WizardUi(controller, context, library, spells, feats, controller::goTo)

    fun requestClose() {
        if (controller.isDirty) confirmExit = true else onClose()
    }

    BackHandler {
        if (currentIndex > 0) controller.goTo(steps[currentIndex - 1]) else requestClose()
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.wizard_exit_title)) },
            text = { Text(stringResource(R.string.wizard_exit_text)) },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onClose() }) {
                    Text(stringResource(R.string.wizard_exit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.wizard_exit_stay)) }
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                DndTopBar(
                    title = draft.name.ifBlank { stringResource(R.string.wizard_title) },
                    navigationIcon = {
                        IconButton(onClick = ::requestClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
                StepRibbon(
                    steps = steps,
                    currentIndex = currentIndex,
                    ui = ui,
                    onSelect = { controller.goTo(it) },
                )
            }
        },
        bottomBar = {
            WizardBottomBar(
                isFirst = currentIndex == 0,
                isLast = step == WizardStep.Summary,
                canCreate = WizardEngine.canCreate(draft, context),
                stepComplete = WizardEngine.issues(step, draft, context).isEmpty(),
                onBack = { if (currentIndex > 0) controller.goTo(steps[currentIndex - 1]) },
                onNext = { steps.getOrNull(currentIndex + 1)?.let(controller::goTo) },
                onCreate = onCreate,
            )
        },
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = steps.indexOf(targetState) >= steps.indexOf(initialState)
                val direction = if (forward) 1 else -1
                (slideInHorizontally { it / 4 * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 * direction } + fadeOut())
            },
            contentKey = { it.key },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "wizardStep",
        ) { target ->
            when (target) {
                WizardStep.Basics -> BasicsStep(ui)
                WizardStep.Race -> RaceStep(ui)
                WizardStep.Class -> ClassStep(ui)
                WizardStep.Equipment -> EquipmentStep(ui)
                is WizardStep.Choice -> ChoiceStep(ui, target.choiceId)
                WizardStep.Spells -> SpellsStep(ui)
                WizardStep.Background -> BackgroundStep(ui)
                WizardStep.Abilities -> AbilitiesStep(ui)
                WizardStep.Feats -> FeatsStep(ui)
                WizardStep.Summary -> SummaryStep(ui, onCreate)
            }
        }
    }
}

/** Подпись шага в ленте и в сводке. */
@Composable
fun stepLabel(step: WizardStep, ui: WizardUi): String = when (step) {
    WizardStep.Basics -> stringResource(R.string.wizard_step_basics)
    WizardStep.Race -> stringResource(R.string.wizard_step_race)
    WizardStep.Class -> stringResource(R.string.wizard_step_class)
    WizardStep.Equipment -> stringResource(R.string.wizard_equipment)
    is WizardStep.Choice -> ui.context.choices.firstOrNull { it.id == step.choiceId }?.title.orEmpty()
    WizardStep.Spells -> stringResource(R.string.wizard_step_spells)
    WizardStep.Background -> stringResource(R.string.wizard_step_background)
    WizardStep.Abilities -> stringResource(R.string.wizard_step_abilities)
    WizardStep.Feats -> stringResource(R.string.wizard_step_feats)
    WizardStep.Summary -> stringResource(R.string.wizard_step_summary)
}

/**
 * Лента шагов: номер, подпись и состояние. Дополнительные шаги отмечены отступом
 * и меньшим кружком — видно, к какому основному шагу они относятся.
 */
@Composable
private fun StepRibbon(
    steps: List<WizardStep>,
    currentIndex: Int,
    ui: WizardUi,
    onSelect: (WizardStep) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    val progress by animateFloatAsState((currentIndex + 1f) / steps.size, label = "wizardProgress")
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(steps, key = { _, step -> step.key }) { index, step ->
                    val current = index == currentIndex
                    val visited = step.key in ui.controller.visitedSteps
                    val complete = WizardEngine.issues(step, ui.draft, ui.context).isEmpty()
                    StepChip(
                        number = index + 1,
                        label = stepLabel(step, ui),
                        current = current,
                        complete = complete && visited,
                        warning = visited && !complete && !current,
                        secondary = step is WizardStep.Choice,
                        onClick = { onSelect(step) },
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun StepChip(
    number: Int,
    label: String,
    current: Boolean,
    complete: Boolean,
    warning: Boolean,
    secondary: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(50),
        color = if (current) primary else Color.Transparent,
        border = if (current) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val circleColor = when {
                current -> MaterialTheme.colorScheme.onPrimary
                complete -> primary
                warning -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Surface(shape = CircleShape, color = circleColor, modifier = Modifier.size(if (secondary) 20.dp else 24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (complete && !current) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            if (warning) "!" else number.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                current -> primary
                                warning -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            Text(
                label,
                style = if (secondary) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Длинные названия дополнительных шагов обрезаются, чтобы лента оставалась компактной.
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}



@Composable
private fun WizardBottomBar(
    isFirst: Boolean,
    isLast: Boolean,
    canCreate: Boolean,
    stepComplete: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, enabled = !isFirst, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wizard_back))
                }
                if (isLast) {
                    Button(onClick = onCreate, enabled = canCreate, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wizard_create))
                    }
                } else {
                    // Незавершённый шаг не блокирует переход: вернуться можно в любой момент,
                    // но кнопка менее заметна, чтобы подсказать, что здесь ещё есть выбор.
                    val content: @Composable () -> Unit = {
                        Text(stringResource(R.string.wizard_next))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    if (stepComplete) {
                        Button(onClick = onNext, modifier = Modifier.weight(1.4f)) { content() }
                    } else {
                        OutlinedButton(onClick = onNext, modifier = Modifier.weight(1.4f)) { content() }
                    }
                }
            }
        }
    }
}
