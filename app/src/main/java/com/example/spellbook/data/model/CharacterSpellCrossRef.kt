package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Связь «многие-ко-многим» между персонажами и заклинаниями.
 * Удаление персонажа или заклинания каскадно убирает соответствующие ссылки,
 * при этом сами заклинания остаются в библиотеке.
 */
@Entity(
    tableName = "character_spells",
    primaryKeys = ["characterId", "spellId"],
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Spell::class,
            parentColumns = ["id"],
            childColumns = ["spellId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId"), Index("spellId")],
)
data class CharacterSpellCrossRef(
    val characterId: String,
    val spellId: String,
    /** Момент добавления заклинания персонажу — для сортировки «по дате добавления». */
    val addedAt: Long = System.currentTimeMillis(),
)
