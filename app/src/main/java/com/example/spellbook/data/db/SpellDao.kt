package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.flow.Flow

/** Доступ к общей библиотеке заклинаний и связям заклинаний с персонажами. */
@Dao
interface SpellDao {

    @Query("SELECT * FROM spells ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Spell>>

    /** Заклинания конкретного персонажа (по таблице связи). */
    @Query(
        """
        SELECT s.* FROM spells s
        INNER JOIN character_spells cs ON cs.spellId = s.id
        WHERE cs.characterId = :characterId
        ORDER BY cs.addedAt DESC
        """
    )
    fun observeForCharacter(characterId: String): Flow<List<Spell>>

    @Query("SELECT * FROM spells WHERE id = :spellId LIMIT 1")
    suspend fun getById(spellId: String): Spell?

    /**
     * Ищет заклинание по названию (без учёта регистра для латиницы; для кириллицы —
     * точное совпадение). Используется для проверки уникальности при добавлении.
     */
    @Query("SELECT * FROM spells WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Spell?

    @Upsert
    suspend fun upsert(spell: Spell)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(spells: List<Spell>)

    @Query("DELETE FROM spells WHERE id = :spellId")
    suspend fun deleteById(spellId: String)

    @Query("SELECT COUNT(*) FROM spells")
    suspend fun count(): Int

    // region Связь с персонажами

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSpellToCharacter(ref: CharacterSpellCrossRef)

    @Query("DELETE FROM character_spells WHERE characterId = :characterId AND spellId = :spellId")
    suspend fun removeSpellFromCharacter(characterId: String, spellId: String)

    @Query("SELECT spellId FROM character_spells WHERE characterId = :characterId")
    fun observeSpellIdsForCharacter(characterId: String): Flow<List<String>>

    // endregion
}
