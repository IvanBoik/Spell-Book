package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spellbook.data.model.Background
import kotlinx.coroutines.flow.Flow

/** Доступ к библиотеке предысторий. */
@Dao
interface BackgroundDao {

    /** Все предыстории по алфавиту. */
    @Query("SELECT * FROM backgrounds ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Background>>

    @Query("SELECT * FROM backgrounds WHERE id = :backgroundId LIMIT 1")
    suspend fun getById(backgroundId: String): Background?

    /** Поиск ранее загруженной предыстории по ссылке — чтобы не плодить дубликаты. */
    @Query("SELECT * FROM backgrounds WHERE source = :source LIMIT 1")
    suspend fun findBySource(source: String): Background?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(background: Background)

    @Query("DELETE FROM backgrounds WHERE id = :backgroundId")
    suspend fun delete(backgroundId: String)

    /**
     * Удаляет предыстории из встроенного набора, которых нет в текущем файле.
     *
     * Записи без ссылки не трогаем: они добавлены пользователем вручную.
     *
     * @param keepSources ссылки предысторий из актуального набора.
     * @return сколько записей удалено.
     */
    @Query("DELETE FROM backgrounds WHERE source <> '' AND source NOT IN (:keepSources)")
    suspend fun deleteMissingFromBundle(keepSources: List<String>): Int
}
