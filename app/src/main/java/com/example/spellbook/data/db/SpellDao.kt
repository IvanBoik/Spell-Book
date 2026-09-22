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

    /**
     * Заклинания конкретного персонажа вместе с его персональными правками.
     *
     * Заклинание берётся целиком (`s.*`), а не поколоночно: иначе каждое новое
     * поле модели пришлось бы не забыть добавить и сюда.
     */
    @Query(
        """
        SELECT s.*, cs.nameOverride AS nameOverride, cs.descriptionOverride AS descriptionOverride
        FROM spells s
        INNER JOIN character_spells cs ON cs.spellId = s.id
        WHERE cs.characterId = :characterId
        ORDER BY cs.addedAt DESC
        """
    )
    fun observeForCharacterRaw(characterId: String): Flow<List<SpellWithOverrides>>

    /**
     * Сохраняет персональную правку заклинания. null в обоих полях
     * возвращает текст из библиотеки.
     */
    @Query(
        """
        UPDATE character_spells
        SET nameOverride = :name, descriptionOverride = :description
        WHERE characterId = :characterId AND spellId = :spellId
        """
    )
    suspend fun updateOverrides(
        characterId: String,
        spellId: String,
        name: String?,
        description: String?,
    )

    /** Есть ли у персонажа персональная правка этого заклинания. */
    @Query(
        """
        SELECT COUNT(*) FROM character_spells
        WHERE characterId = :characterId AND spellId = :spellId
          AND (nameOverride IS NOT NULL OR descriptionOverride IS NOT NULL)
        """
    )
    suspend fun countOverrides(characterId: String, spellId: String): Int

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

    /** id подготовленных заклинаний персонажа. */
    @Query("SELECT spellId FROM character_spells WHERE characterId = :characterId AND prepared = 1")
    fun observePreparedSpellIdsForCharacter(characterId: String): Flow<List<String>>

    /** Меняет флаг подготовки конкретного заклинания персонажа. */
    @Query("UPDATE character_spells SET prepared = :prepared WHERE characterId = :characterId AND spellId = :spellId")
    suspend fun setPrepared(characterId: String, spellId: String, prepared: Boolean)

    /** Количество подготовленных заклинаний персонажа. */
    @Query("SELECT COUNT(*) FROM character_spells WHERE characterId = :characterId AND prepared = 1")
    suspend fun countPrepared(characterId: String): Int

    /** Количество заговоров (уровень 0) в наборе персонажа. */
    @Query(
        """
        SELECT COUNT(*) FROM character_spells cs
        INNER JOIN spells s ON s.id = cs.spellId
        WHERE cs.characterId = :characterId AND s.level = 0
        """
    )
    suspend fun countCantrips(characterId: String): Int

    /** Есть ли уже это заклинание у персонажа. */
    @Query("SELECT COUNT(*) FROM character_spells WHERE characterId = :characterId AND spellId = :spellId")
    suspend fun countLink(characterId: String, spellId: String): Int

    // endregion
}
