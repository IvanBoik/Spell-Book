package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.CharacterFeatCrossRef
import com.example.spellbook.data.model.Feat
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatDao {

    // region Библиотека

    @Query("SELECT * FROM feats ORDER BY createdAt DESC")
    fun observeAllFeats(): Flow<List<Feat>>

    /** Черта с таким названием в библиотеке — для защиты от дублей при загрузке. */
    @Query("SELECT * FROM feats WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Feat?

    @Query("SELECT * FROM feats WHERE id = :featId LIMIT 1")
    suspend fun getById(featId: String): Feat?

    @Upsert
    suspend fun upsertFeat(feat: Feat)

    @Query("DELETE FROM feats WHERE id = :featId")
    suspend fun deleteFeat(featId: String)

    // endregion

    // region Черты персонажа

    /**
     * Черты персонажа. Персональная правка перекрывает текст библиотеки,
     * поэтому подмена делается сразу в запросе — UI не знает о двойном хранении.
     */
    @Query(
        """
        SELECT f.id AS id,
               COALESCE(cf.nameOverride, f.name) AS name,
               COALESCE(cf.descriptionOverride, f.description) AS description,
               f.source AS source, f.book AS book, f.createdAt AS createdAt,
               cf.collapsed AS collapsed, cf.sortOrder AS sortOrder,
               (cf.nameOverride IS NOT NULL OR cf.descriptionOverride IS NOT NULL) AS hasPersonalEdit
        FROM feats f
        INNER JOIN character_feats cf ON cf.featId = f.id
        WHERE cf.characterId = :characterId
        ORDER BY cf.sortOrder ASC, f.createdAt DESC
        """,
    )
    fun observeFeatsForCharacter(characterId: String): Flow<List<CharacterFeat>>

    /**
     * Сохраняет персональную правку черты. null в обоих полях возвращает
     * черту к тексту из библиотеки.
     */
    @Query(
        """
        UPDATE character_feats
        SET nameOverride = :name, descriptionOverride = :description
        WHERE characterId = :characterId AND featId = :featId
        """,
    )
    suspend fun updateOverrides(
        characterId: String,
        featId: String,
        name: String?,
        description: String?,
    )

    @Query("SELECT featId FROM character_feats WHERE characterId = :characterId")
    fun observeFeatIdsForCharacter(characterId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFeatToCharacter(ref: CharacterFeatCrossRef)

    @Query("DELETE FROM character_feats WHERE characterId = :characterId AND featId = :featId")
    suspend fun removeFeatFromCharacter(characterId: String, featId: String)

    @Query("UPDATE character_feats SET collapsed = :collapsed WHERE characterId = :characterId AND featId = :featId")
    suspend fun updateCollapsed(characterId: String, featId: String, collapsed: Boolean)

    @Query("UPDATE character_feats SET sortOrder = :position WHERE characterId = :characterId AND featId = :featId")
    suspend fun updateSortOrder(characterId: String, featId: String, position: Long)

    /** Записывает новый порядок одной транзакцией, чтобы Flow не отдавал промежуточные состояния. */
    @Transaction
    suspend fun reorderFeats(characterId: String, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(characterId, id, index.toLong()) }
    }

    // endregion
}
