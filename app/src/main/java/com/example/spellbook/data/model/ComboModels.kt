package com.example.spellbook.data.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.spellbook.R
import java.util.UUID

val COMBO_DICE_SIDES = listOf(4, 6, 8, 10, 12, 20, 100)

enum class ComboStepType(@param:StringRes val labelRes: Int) {
    DICE(R.string.combo_step_dice),
    CONSTANT(R.string.combo_step_constant),
}

enum class ComboRollMode(@param:StringRes val labelRes: Int) {
    NORMAL(R.string.combo_mode_normal),
    MAXIMUM(R.string.combo_mode_maximum),
    CRITICAL_CLASSIC(R.string.combo_mode_crit_classic),
    CRITICAL_HOMEBREW(R.string.combo_mode_crit_homebrew),
}

@Entity(
    tableName = "combo_steps",
    foreignKeys = [ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class ComboStep(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val name: String,
    /** [ComboStepType.name]. */
    val type: String = ComboStepType.DICE.name,
    val diceCount: Int = 1,
    val diceSides: Int = 8,
    val modifier: Int = 0,
    /** Значение плоского шага; для DICE не используется. */
    val flatValue: Int = 0,
    /**
     * Универсальное выражение шага, например `6d8 + [int]` или `[pb] * 2`.
     * Задаёт и кости, и модификаторы, поэтому заменяет отдельные числовые поля.
     */
    val expression: String = "",
    /** Код типа урона из SpellOptions.damageTypes; пусто — без типа. */
    val damageType: String = "",
    val effect: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val stepType: ComboStepType get() = runCatching { ComboStepType.valueOf(type) }.getOrDefault(ComboStepType.DICE)

    /**
     * Читаемая запись шага. Для старых шагов без [expression]
     * собирается из сохранённых числовых полей.
     */
    val formula: String
        get() = expression.ifBlank {
            when (stepType) {
                ComboStepType.DICE -> buildString {
                    append(diceCount.coerceAtLeast(1)).append('d').append(diceSides)
                    if (modifier > 0) append(" + ").append(modifier)
                    if (modifier < 0) append(" - ").append(-modifier)
                }

                ComboStepType.CONSTANT -> if (flatValue >= 0) "+$flatValue" else flatValue.toString()
            }
        }
}

@Entity(
    tableName = "combos",
    foreignKeys = [ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class Combo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Пользовательский порядок; по умолчанию новые комбинации оказываются сверху. */
    val sortOrder: Long = -createdAt,
)

@Entity(
    tableName = "combo_step_links",
    primaryKeys = ["comboId", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = Combo::class,
            parentColumns = ["id"],
            childColumns = ["comboId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ComboStep::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comboId"), Index("stepId")],
)
data class ComboStepLink(
    val comboId: String,
    val stepId: String,
    val position: Int,
)

data class ComboWithSteps(
    val combo: Combo,
    val steps: List<ComboStep>,
)

/**
 * Результат одного шага.
 *
 * @param breakdown слагаемые с подставленными значениями, например `6 + 2 + 5`.
 */
data class ComboStepRollResult(
    val step: ComboStep,
    val rolls: List<Int>,
    val total: Int,
    val breakdown: String = "",
)

data class ComboRollResult(
    val combo: Combo,
    val mode: ComboRollMode,
    val steps: List<ComboStepRollResult>,
) {
    val total: Int get() = steps.sumOf { it.total }
    val effects: List<String> get() = steps.mapNotNull { it.step.effect.takeIf(String::isNotBlank) }
}
