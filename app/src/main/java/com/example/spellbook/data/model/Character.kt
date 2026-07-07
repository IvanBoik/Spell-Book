package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Персонаж-владелец набора заклинаний. Заклинания хранятся в общей библиотеке,
 * а персонаж ссылается на них через таблицу связи [CharacterSpellCrossRef].
 *
 * [imageUri] — строковый URI выбранной из галереи картинки; если пусто,
 * показывается запасной аватар с инициалами.
 */
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
