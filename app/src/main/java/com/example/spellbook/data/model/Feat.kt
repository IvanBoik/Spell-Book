package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Черта из общей библиотеки. Как и заклинания, черты не привязаны к персонажу:
 * персонаж ссылается на них через [CharacterFeatCrossRef].
 */
@Entity(tableName = "feats")
data class Feat(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    /** Источник: ссылка на dnd.su, если черта загружена оттуда. */
    val source: String = "",
    /**
     * Книга, в которой появилась черта, например `Player's Handbook`.
     * По ней черты группируются в библиотеке; пустая строка — у созданных вручную.
     */
    val book: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Связь персонажа с чертой. Свёрнутость и порядок — свойства конкретного персонажа,
 * поэтому хранятся здесь, а не в самой черте.
 */
@Entity(
    tableName = "character_feats",
    primaryKeys = ["characterId", "featId"],
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Feat::class,
            parentColumns = ["id"],
            childColumns = ["featId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId"), Index("featId")],
)
data class CharacterFeatCrossRef(
    val characterId: String,
    val featId: String,
    /** Свёрнут ли блок; состояние сохраняется между запусками. */
    val collapsed: Boolean = false,
    /** Пользовательский порядок; по умолчанию новые черты оказываются сверху. */
    val sortOrder: Long = -System.currentTimeMillis(),
    /**
     * Название и описание только для этого персонажа; null — берётся из библиотеки.
     *
     * Позволяет править черту «под себя», не затрагивая общую библиотеку
     * и остальных персонажей, взявших ту же черту.
     */
    val nameOverride: String? = null,
    val descriptionOverride: String? = null,
)

/**
 * Черта персонажа: данные из библиотеки плюс персональные свёрнутость и порядок.
 *
 * [name] и [description] уже учитывают персональную правку, если она есть.
 */
data class CharacterFeat(
    val id: String,
    val name: String,
    val description: String,
    val source: String,
    val book: String,
    val createdAt: Long,
    val collapsed: Boolean,
    val sortOrder: Long,
    /** Правлена ли черта только для этого персонажа. */
    val hasPersonalEdit: Boolean = false,
) {
    /** Короткая выжимка для свёрнутого состояния. */
    val preview: String
        get() = description.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    /** Представление в виде библиотечной черты — для общих экранов списка и просмотра. */
    fun asFeat(): Feat = Feat(
        id = id,
        name = name,
        description = description,
        source = source,
        book = book,
        createdAt = createdAt,
    )
}
