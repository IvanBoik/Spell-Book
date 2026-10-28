package com.example.spellbook.ui.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.wizard.AbilityMethod
import com.example.spellbook.data.wizard.CharacterDraft
import com.example.spellbook.data.wizard.RolledScore
import com.example.spellbook.data.wizard.WizardContext
import com.example.spellbook.data.wizard.WizardEngine
import com.example.spellbook.data.wizard.WizardLibrary
import com.example.spellbook.data.wizard.WizardRulesCache
import com.example.spellbook.data.wizard.WizardStep
import kotlin.random.Random

/**
 * Состояние мастера создания персонажа.
 *
 * Живёт во ViewModel, чтобы переживать поворот экрана; создаётся заново при каждом
 * открытии мастера. Черновик неизменяемый — любое действие заменяет его целиком.
 */
class CharacterWizardController(private val random: Random = Random.Default) {

    val cache = WizardRulesCache()

    var draft by mutableStateOf(CharacterDraft())
        private set

    /** Ключ текущего шага ([WizardStep.key]); список шагов зависит от черновика. */
    var stepKey by mutableStateOf(WizardStep.Basics.key)
        private set

    /** Шаги, которые пользователь уже открывал: на них показываются незавершённости. */
    var visitedSteps by mutableStateOf(setOf(WizardStep.Basics.key))
        private set

    /** Изменился ли черновик — от этого зависит предупреждение о потере данных. */
    val isDirty: Boolean get() = draft != CharacterDraft()

    fun update(transform: (CharacterDraft) -> CharacterDraft) {
        draft = transform(draft)
    }

    fun context(library: WizardLibrary): WizardContext = WizardEngine.context(draft, library, cache)

    fun goTo(step: WizardStep) {
        stepKey = step.key
        visitedSteps = visitedSteps + step.key
    }

    // region Броски

    fun rollAbilityScores() = update { draft ->
        draft.copy(
            abilityMethod = AbilityMethod.ROLL,
            rolledScores = List(AbilityType.entries.size) { RolledScore(List(4) { random.nextInt(1, 7) }) },
            poolAssignment = emptyMap(),
        )
    }

    /** Броски кости хитов за уровни после первого. */
    fun rollHitPoints(hitDie: Int) = update { draft ->
        draft.copy(hpRolls = List((draft.level - 1).coerceAtLeast(0)) { random.nextInt(1, hitDie + 1) })
    }

    /** Бросок стартового золота по формуле класса вида `5к4×10`. */
    fun rollClassGold(formula: String) = update { draft ->
        val match = Regex("(\\d+)к(\\d+)(?:×(\\d+))?").find(formula)
        val gold = match?.let {
            val count = it.groupValues[1].toInt()
            val sides = it.groupValues[2].toInt()
            val multiplier = it.groupValues[3].toIntOrNull() ?: 1
            (1..count).sumOf { random.nextInt(1, sides + 1) } * multiplier
        }
        draft.copy(classGold = gold)
    }

    // endregion
}
