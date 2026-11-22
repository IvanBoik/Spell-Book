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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.SkillType
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.wizard.ChoiceOption
import com.example.spellbook.data.wizard.ChoiceSource
import com.example.spellbook.data.wizard.LevelUpContext
import com.example.spellbook.data.wizard.LevelUpDraft
import com.example.spellbook.data.wizard.LevelUpIssue
import com.example.spellbook.data.wizard.LevelUpIssueKind
import com.example.spellbook.data.wizard.LevelUpEngine
import com.example.spellbook.data.wizard.LevelUpStep
import com.example.spellbook.data.wizard.LevelUpWarning
import com.example.spellbook.data.wizard.LevelUpWarningKind
import com.example.spellbook.data.wizard.MulticlassRules
import com.example.spellbook.data.wizard.OptionRequirements
import com.example.spellbook.data.wizard.ReplacementSpec
import com.example.spellbook.data.wizard.WizardLibrary
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets

/**
 * Всё, что нужно шагам окна повышения уровня: черновик, вычисленный контекст и библиотеки.
 * Аналог [WizardUi] мастера создания.
 */
class LevelUpUi(
    val controller: LevelUpController,
    val context: LevelUpContext,
    val library: WizardLibrary,
    val spells: List<Spell>,
    val feats: List<Feat>,
    val steps: List<LevelUpStep>,
    val goTo: (LevelUpStep) -> Unit,
) {
    val draft: LevelUpDraft get() = controller.draft
    val base get() = controller.base
    val spellsById: Map<String, Spell> by lazy { spells.associateBy { it.id } }

    fun update(transform: (LevelUpDraft) -> LevelUpDraft) = controller.update(transform)

    fun selection(id: String): List<String> = draft.selections[id].orEmpty()
    fun select(id: String, values: List<String>) = update { it.copy(selections = it.selections + (id to values)) }

    /**
     * Почему вариант выбора недоступен. Требования проверяются по черновику мастера создания
     * на новом уровне класса, а известные персонажу заклинания (мистический заряд для воззваний)
     * добавляются к нему — в черновике класса их нет.
     */
    fun unmet(option: ChoiceOption): List<OptionRequirements.Unmet> {
        val wizardDraft = context.newWizardDraft ?: return emptyList()
        val wizardContext = context.newWizardContext ?: return emptyList()
        val withKnown = wizardDraft.copy(
            cantripIds = wizardDraft.cantripIds + base.knownSpellIds + draft.cantripIds + draft.spellIds,
        )
        return OptionRequirements.unmet(option, withKnown, wizardContext, spells)
    }
}

/**
 * Окно пошагового повышения уровня персонажа на +1.
 *
 * Устроено как мастер создания: лента шагов сверху, свободный переход между шагами,
 * незавершённости отмечены; итоговая кнопка «Повысить уровень» активна, когда нет
 * блокирующих незавершённостей ([LevelUpEngine.canApply]).
 */
@Composable
fun LevelUpScreen(
    controller: LevelUpController,
    library: WizardLibrary,
    spells: List<Spell>,
    feats: List<Feat>,
    onClose: () -> Unit,
    onApply: () -> Unit,
) {
    val draft = controller.draft
    val context = remember(draft, library) { controller.context(library) }
    val steps = remember(context) { LevelUpEngine.steps(context) }
    val currentIndex = steps.indexOfFirst { it.key == controller.stepKey }.takeIf { it >= 0 } ?: 0
    val step = steps[currentIndex]
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val ui = LevelUpUi(controller, context, library, spells, feats, steps, controller::goTo)

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
            title = { Text(stringResource(R.string.levelup_exit_title)) },
            text = { Text(stringResource(R.string.levelup_exit_text)) },
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
                    title = stringResource(
                        R.string.levelup_top_title,
                        controller.base.character.name.ifBlank { stringResource(R.string.character_unnamed) },
                    ),
                    navigationIcon = {
                        IconButton(onClick = ::requestClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
                LevelUpRibbon(steps, currentIndex, ui) { controller.goTo(it) }
            }
        },
        bottomBar = {
            WizardBottomBar(
                isFirst = currentIndex == 0,
                isLast = step == LevelUpStep.Summary,
                canCreate = LevelUpEngine.canApply(context),
                stepComplete = LevelUpEngine.issues(step, context).isEmpty(),
                onBack = { if (currentIndex > 0) controller.goTo(steps[currentIndex - 1]) },
                onNext = { steps.getOrNull(currentIndex + 1)?.let(controller::goTo) },
                onCreate = onApply,
                finishLabel = stringResource(R.string.levelup_action),
                finishIcon = Icons.Default.Upgrade,
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
            label = "levelUpStep",
        ) { target ->
            when (target) {
                LevelUpStep.Class -> LevelUpClassStep(ui)
                is LevelUpStep.Choice -> LevelUpChoiceStep(ui, target.choiceId)
                LevelUpStep.Abilities -> LevelUpAbilitiesStep(ui)
                LevelUpStep.HitPoints -> LevelUpHitPointsStep(ui)
                LevelUpStep.Spells -> LevelUpSpellsStep(ui)
                LevelUpStep.Replacements -> LevelUpReplacementsStep(ui)
                LevelUpStep.Prepare -> LevelUpPrepareStep(ui)
                LevelUpStep.Summary -> LevelUpSummaryStep(ui)
                else -> Unit
            }
        }
    }
}

/** Подпись шага в ленте и в итоге. */
@Composable
fun levelUpStepLabel(step: LevelUpStep, context: LevelUpContext): String = when (step) {
    LevelUpStep.Class -> stringResource(R.string.wizard_step_class)
    is LevelUpStep.Choice -> context.choices.firstOrNull { it.id == step.choiceId }?.title.orEmpty()
    LevelUpStep.Abilities -> stringResource(R.string.wizard_asi_title)
    LevelUpStep.HitPoints -> stringResource(R.string.wizard_hp)
    LevelUpStep.Spells -> stringResource(R.string.wizard_step_spells)
    LevelUpStep.Replacements -> stringResource(R.string.levelup_step_replacements)
    LevelUpStep.Prepare -> stringResource(R.string.levelup_step_prepare)
    LevelUpStep.Summary -> stringResource(R.string.wizard_step_summary)
    else -> step.key
}

@Composable
private fun LevelUpRibbon(
    steps: List<LevelUpStep>,
    currentIndex: Int,
    ui: LevelUpUi,
    onSelect: (LevelUpStep) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    val progress by animateFloatAsState((currentIndex + 1f) / steps.size, label = "levelUpProgress")
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
                    val complete = LevelUpEngine.issues(step, ui.context).isEmpty()
                    StepChip(
                        number = index + 1,
                        label = levelUpStepLabel(step, ui.context),
                        current = current,
                        complete = complete && visited,
                        warning = visited && !complete && !current,
                        secondary = step is LevelUpStep.Choice,
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

/**
 * Незавершённости шага. Блокирующие (класс, требования мультикласса, хиты) видны сразу,
 * остальные — после первого посещения шага, как в мастере создания.
 */
@Composable
fun LevelUpIssues(ui: LevelUpUi, step: LevelUpStep) {
    val visited = step.key in ui.controller.visitedSteps
    val issues = LevelUpEngine.issues(step, ui.context).filter { visited || it.blocking }
    TextIssuesBanner(issues.map { levelUpIssueText(it) })
}

/** Плашка с предупреждениями — в том же стиле, что [IssuesBanner] мастера создания. */
@Composable
fun TextIssuesBanner(texts: List<String>, modifier: Modifier = Modifier) {
    if (texts.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                texts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Текст незавершённости окна повышения уровня. */
@Composable
fun levelUpIssueText(issue: LevelUpIssue): String = when (issue.kind) {
    LevelUpIssueKind.MAX_LEVEL -> stringResource(R.string.levelup_max_level)
    LevelUpIssueKind.CLASS_MISSING -> stringResource(R.string.wizard_issue_class)
    LevelUpIssueKind.MULTICLASS_REQUIREMENTS -> stringResource(R.string.levelup_issue_multiclass, issue.label)
    LevelUpIssueKind.SUBCLASS_MISSING -> stringResource(R.string.wizard_issue_subclass, issue.label)
    LevelUpIssueKind.CHOICE_INCOMPLETE ->
        pluralStringResource(R.plurals.wizard_issue_choice, issue.missing, issue.label, issue.missing)
    LevelUpIssueKind.ASI_INCOMPLETE -> stringResource(R.string.wizard_issue_asi, issue.label)
    LevelUpIssueKind.FEAT_MISSING -> stringResource(R.string.wizard_issue_feat)
    LevelUpIssueKind.HP_MISSING -> stringResource(R.string.levelup_issue_hp)
    LevelUpIssueKind.CANTRIPS_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_cantrips, issue.missing, issue.missing)
    LevelUpIssueKind.SPELLS_INCOMPLETE -> pluralStringResource(R.plurals.wizard_issue_spells, issue.missing, issue.missing)
    LevelUpIssueKind.REPLACEMENT_INCOMPLETE -> stringResource(R.string.levelup_issue_replacement, issue.label)
    LevelUpIssueKind.REPLACEMENT_CONFLICT -> stringResource(R.string.levelup_issue_conflict, issue.label)
    LevelUpIssueKind.SPELLS_SCHOOL_LIMIT -> stringResource(R.string.wizard_issue_school_limit, issue.missing)
    LevelUpIssueKind.RESOURCE_DECISION -> stringResource(R.string.levelup_issue_resource, issue.label)
    LevelUpIssueKind.PREPARED_FREE -> stringResource(R.string.levelup_issue_prepared_free, issue.missing)
    LevelUpIssueKind.PREPARED_OVER -> stringResource(R.string.levelup_issue_prepared_over, issue.missing)
    LevelUpIssueKind.DUPLICATE_PICK -> stringResource(R.string.levelup_issue_duplicate, issue.label)
    else -> issue.label
}

/** Предупреждение о том, что не нашлось в библиотеке. */
@Composable
fun levelUpWarningText(warning: LevelUpWarning): String = when (warning.kind) {
    LevelUpWarningKind.RACE_NOT_FOUND -> stringResource(R.string.levelup_warning_race, warning.label)
    LevelUpWarningKind.BACKGROUND_NOT_FOUND -> stringResource(R.string.levelup_warning_background, warning.label)
    LevelUpWarningKind.CLASS_NOT_FOUND -> stringResource(R.string.levelup_warning_class, warning.label)
    LevelUpWarningKind.SUBCLASS_NOT_FOUND -> stringResource(R.string.levelup_warning_subclass, warning.label)
    else -> warning.label
}

/** Подпись источника нового умения. */
@Composable
fun levelUpSourceLabel(source: ChoiceSource): String = stringResource(
    when (source) {
        ChoiceSource.RACE -> R.string.levelup_source_race
        ChoiceSource.SUBRACE -> R.string.levelup_source_subrace
        ChoiceSource.CLASS -> R.string.levelup_source_class
        ChoiceSource.SUBCLASS -> R.string.levelup_source_subclass
        ChoiceSource.BACKGROUND -> R.string.levelup_source_background
        ChoiceSource.FEAT -> R.string.levelup_source_feat
    },
)

/** Требования мультикласса текстом: «СИЛ или ЛОВ 13 и ХАР 13». */
@Composable
fun multiclassRequirementText(rules: MulticlassRules, groups: List<Set<AbilityType>> = rules.requirements): String {
    val or = stringResource(R.string.levelup_or)
    val and = stringResource(R.string.levelup_and)
    return groups.map { group ->
        group.sortedBy { it.ordinal }.map { stringResource(it.shortLabelRes) }.joinToString(or) + " ${rules.minScore}"
    }.joinToString(and)
}

/** Название варианта замены: заклинание, вариант выбора или навык. */
@Composable
fun replacementItemTitle(ui: LevelUpUi, spec: ReplacementSpec, id: String?): String? {
    if (id == null) return null
    ui.spellsById[id]?.let { return it.name }
    (spec.removableOptions + spec.candidateOptions).firstOrNull { it.id == id }?.let { return it.title }
    runCatching { SkillType.valueOf(id) }.getOrNull()?.let { return stringResource(it.labelRes) }
    return id
}
