package com.example.spellbook.ui.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.spellbook.data.wizard.HpMethod
import com.example.spellbook.data.wizard.LevelUpBase
import com.example.spellbook.data.wizard.LevelUpContext
import com.example.spellbook.data.wizard.LevelUpDraft
import com.example.spellbook.data.wizard.LevelUpEngine
import com.example.spellbook.data.wizard.LevelUpStep
import com.example.spellbook.data.wizard.WizardLibrary
import com.example.spellbook.data.wizard.WizardRulesCache
import kotlin.random.Random

/**
 * Состояние окна повышения уровня.
 *
 * Живёт во ViewModel, чтобы переживать поворот экрана; создаётся при каждом открытии окна
 * по уже восстановленному персонажу ([base]). Черновик неизменяемый — любое действие
 * заменяет его целиком, как в мастере создания.
 */
class LevelUpController(
    val base: LevelUpBase,
    val cache: WizardRulesCache,
    private val random: Random = Random.Default,
) {
    private val initial = LevelUpEngine.initialDraft(base)

    var draft by mutableStateOf(initial)
        private set

    /** Ключ текущего шага ([LevelUpStep.key]); список шагов зависит от черновика. */
    var stepKey by mutableStateOf(LevelUpStep.Class.key)
        private set

    /** Шаги, которые пользователь уже открывал: на них показываются незавершённости. */
    var visitedSteps by mutableStateOf(setOf(LevelUpStep.Class.key))
        private set

    val characterId: String get() = base.character.id

    /** Изменился ли черновик — от этого зависит предупреждение о потере выбора. */
    val isDirty: Boolean get() = draft != initial

    fun update(transform: (LevelUpDraft) -> LevelUpDraft) {
        draft = transform(draft)
    }

    fun context(library: WizardLibrary): LevelUpContext = LevelUpEngine.context(base, draft, library, cache)

    fun goTo(step: LevelUpStep) {
        stepKey = step.key
        visitedSteps = visitedSteps + step.key
    }

    /** Бросок кости хитов за новый уровень (без модификатора Телосложения). */
    fun rollHitDie(hitDie: Int) = update {
        it.copy(hpMethod = HpMethod.ROLL, hpRoll = random.nextInt(1, hitDie.coerceAtLeast(1) + 1))
    }
}
