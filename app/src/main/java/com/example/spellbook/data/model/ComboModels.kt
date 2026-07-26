package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

val COMBO_DICE_SIDES = listOf(4, 6, 8, 10, 12, 20, 100)

enum class ComboStepType(val label: String) {
    DICE("Кубики"),
    CONSTANT("Число"),
}

enum class ComboRollMode(val label: String) {
    NORMAL("Обычный"),
    MAXIMUM("Максимум"),
    CRITICAL("Критический"),
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
    val effect: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val stepType: ComboStepType get() = runCatching { ComboStepType.valueOf(type) }.getOrDefault(ComboStepType.DICE)
    val formula: String get() = when (stepType) {
        ComboStepType.DICE -> buildString {
            append(diceCount.coerceAtLeast(1)).append('к').append(diceSides)
            if (modifier > 0) append(" + ").append(modifier)
            if (modifier < 0) append(" - ").append(-modifier)
        }
        ComboStepType.CONSTANT -> if (flatValue >= 0) "+$flatValue" else flatValue.toString()
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

data class ComboStepRollResult(
    val step: ComboStep,
    val rolls: List<Int>,
    val total: Int,
)

data class ComboRollResult(
    val combo: Combo,
    val mode: ComboRollMode,
    val steps: List<ComboStepRollResult>,
) {
    val total: Int get() = steps.sumOf { it.total }
    val effects: List<String> get() = steps.mapNotNull { it.step.effect.takeIf(String::isNotBlank) }
}
