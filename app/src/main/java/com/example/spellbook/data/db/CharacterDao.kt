package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.spellbook.data.model.Character
import kotlinx.coroutines.flow.Flow

/** Доступ к персонажам и агрегатам по их заклинаниям. */
@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Character>>

    @Query("SELECT * FROM characters WHERE id = :characterId LIMIT 1")
    suspend fun getById(characterId: String): Character?

    /** Количество заклинаний у каждого персонажа: (characterId -> count). */
    @Query(
        """
        SELECT characterId AS characterId, COUNT(spellId) AS count
        FROM character_spells GROUP BY characterId
        """
    )
    fun observeSpellCounts(): Flow<List<CharacterSpellCount>>

    @Upsert
    suspend fun upsert(character: Character)

    @Query("DELETE FROM characters WHERE id = :characterId")
    suspend fun deleteById(characterId: String)
}

/** Проекция «сколько заклинаний у персонажа» для отображения на карточке. */
data class CharacterSpellCount(
    val characterId: String,
    val count: Int,
)
