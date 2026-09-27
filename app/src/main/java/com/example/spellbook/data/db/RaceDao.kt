package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spellbook.data.model.Race
import kotlinx.coroutines.flow.Flow

/** Доступ к библиотеке рас. */
@Dao
interface RaceDao {

    /** Все расы по алфавиту: внутри книги список удобнее читать отсортированным. */
    @Query("SELECT * FROM races ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Race>>

    @Query("SELECT * FROM races WHERE id = :raceId LIMIT 1")
    suspend fun getById(raceId: String): Race?

    /** Поиск ранее загруженной расы по ссылке — чтобы не плодить дубликаты. */
    @Query("SELECT * FROM races WHERE source = :source LIMIT 1")
    suspend fun findBySource(source: String): Race?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(race: Race)

    @Query("DELETE FROM races WHERE id = :raceId")
    suspend fun delete(raceId: String)

    /**
     * Удаляет расы, загруженные из встроенного набора, но отсутствующие в текущем файле.
     *
     * Записи без ссылки не трогаем: они созданы пользователем вручную.
     *
     * @param keepSources ссылки рас из актуального набора.
     * @return сколько записей удалено.
     */
    @Query("DELETE FROM races WHERE source <> '' AND source NOT IN (:keepSources)")
    suspend fun deleteMissingFromBundle(keepSources: List<String>): Int
}
