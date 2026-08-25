package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.spellbook.data.model.NoteBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM note_blocks WHERE characterId = :characterId ORDER BY sortOrder ASC, createdAt DESC")
    fun observeBlocks(characterId: String): Flow<List<NoteBlock>>

    @Upsert
    suspend fun upsertBlock(block: NoteBlock)

    @Query("DELETE FROM note_blocks WHERE id = :blockId")
    suspend fun deleteBlock(blockId: String)

    @Query("UPDATE note_blocks SET sortOrder = :position WHERE id = :blockId")
    suspend fun updateSortOrder(blockId: String, position: Long)

    /** Записывает новый порядок одной транзакцией, чтобы Flow не отдавал промежуточные состояния. */
    @Transaction
    suspend fun reorderBlocks(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index.toLong()) }
    }
}
