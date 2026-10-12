package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spellbook.data.model.MagicItem
import kotlinx.coroutines.flow.Flow

/** Доступ к библиотеке магических предметов. */
@Dao
interface MagicItemDao {

    /** Все предметы по алфавиту. */
    @Query("SELECT * FROM magic_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MagicItem>>

    @Query("SELECT * FROM magic_items WHERE id = :itemId LIMIT 1")
    suspend fun getById(itemId: String): MagicItem?

    /** Поиск ранее загруженного предмета по ссылке — чтобы не плодить дубликаты. */
    @Query("SELECT * FROM magic_items WHERE source = :source LIMIT 1")
    suspend fun findBySource(source: String): MagicItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MagicItem)

    @Query("DELETE FROM magic_items WHERE id = :itemId")
    suspend fun delete(itemId: String)

    /**
     * Удаляет предметы из встроенного набора, которых нет в текущем файле.
     *
     * Записи без ссылки не трогаем: они добавлены пользователем вручную.
     *
     * @param keepSources ссылки предметов из актуального набора.
     * @return сколько записей удалено.
     */
    @Query("DELETE FROM magic_items WHERE source <> '' AND source NOT IN (:keepSources)")
    suspend fun deleteMissingFromBundle(keepSources: List<String>): Int
}
