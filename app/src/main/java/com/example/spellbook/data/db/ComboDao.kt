package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboStepLink
import kotlinx.coroutines.flow.Flow

@Dao
interface ComboDao {
    @Query("SELECT * FROM combos WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun observeCombos(characterId: String): Flow<List<Combo>>

    @Query("SELECT * FROM combo_steps WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun observeSteps(characterId: String): Flow<List<ComboStep>>

    @Query("SELECT * FROM combos WHERE id = :comboId LIMIT 1")
    suspend fun getCombo(comboId: String): Combo?

    @Query("SELECT * FROM combo_steps WHERE id = :stepId LIMIT 1")
    suspend fun getStep(stepId: String): ComboStep?

    @Query(
        """
        SELECT stepId FROM combo_step_links
        WHERE comboId = :comboId ORDER BY position ASC
        """,
    )
    suspend fun getStepIds(comboId: String): List<String>

    @Upsert
    suspend fun upsertCombo(combo: Combo)

    @Upsert
    suspend fun upsertStep(step: ComboStep)

    @Query("DELETE FROM combos WHERE id = :comboId")
    suspend fun deleteCombo(comboId: String)

    @Query("DELETE FROM combo_steps WHERE id = :stepId")
    suspend fun deleteStep(stepId: String)

    @Query("DELETE FROM combo_step_links WHERE comboId = :comboId")
    suspend fun clearComboSteps(comboId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<ComboStepLink>)

    @Transaction
    suspend fun replaceComboSteps(comboId: String, stepIds: List<String>) {
        clearComboSteps(comboId)
        insertLinks(stepIds.mapIndexed { index, stepId -> ComboStepLink(comboId, stepId, index) })
    }
}
