package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.spellbook.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE characterId = :characterId ORDER BY sortOrder ASC, createdAt DESC")
    fun observeItems(characterId: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :itemId LIMIT 1")
    suspend fun getItem(itemId: String): InventoryItem?

    @Upsert
    suspend fun upsertItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("UPDATE inventory_items SET sortOrder = :position WHERE id = :itemId")
    suspend fun updateSortOrder(itemId: String, position: Long)

    /** Записывает новый порядок одной транзакцией, чтобы Flow не отдавал промежуточные состояния. */
    @Transaction
    suspend fun reorderItems(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index.toLong()) }
    }
}
