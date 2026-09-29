package com.example.spellbook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spellbook.data.model.CharClass
import kotlinx.coroutines.flow.Flow

/** Доступ к библиотеке классов. */
@Dao
interface CharClassDao {

    /** Все классы по алфавиту. */
    @Query("SELECT * FROM char_classes ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CharClass>>

    @Query("SELECT * FROM char_classes WHERE id = :classId LIMIT 1")
    suspend fun getById(classId: String): CharClass?

    /** Поиск ранее загруженного класса по ссылке — чтобы не плодить дубликаты. */
    @Query("SELECT * FROM char_classes WHERE source = :source LIMIT 1")
    suspend fun findBySource(source: String): CharClass?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(charClass: CharClass)

    @Query("DELETE FROM char_classes WHERE id = :classId")
    suspend fun delete(classId: String)

    /**
     * Удаляет классы из встроенного набора, которых нет в текущем файле.
     *
     * Записи без ссылки не трогаем: они добавлены пользователем вручную.
     *
     * @param keepSources ссылки классов из актуального набора.
     * @return сколько записей удалено.
     */
    @Query("DELETE FROM char_classes WHERE source <> '' AND source NOT IN (:keepSources)")
    suspend fun deleteMissingFromBundle(keepSources: List<String>): Int
}
