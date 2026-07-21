package com.example.spellbook.data

import android.content.Context
import com.example.spellbook.data.db.CharacterSpellCount
import com.example.spellbook.data.db.SpellBookDatabase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Результат попытки добавить заклинание персонажу. */
enum class AddSpellResult { ADDED, ALREADY_ADDED, CANTRIP_LIMIT_REACHED }

/**
 * Единая точка доступа к данным приложения (Room). Инкапсулирует библиотеку
 * заклинаний, персонажей и связи между ними, а также разовую миграцию данных
 * из старого файлового хранилища [LEGACY_FILE_NAME].
 */
class SpellBookRepository(private val context: Context) {

    private val db = SpellBookDatabase.get(context)
    private val spellDao = db.spellDao()
    private val characterDao = db.characterDao()

    // region Заклинания

    fun observeAllSpells(): Flow<List<Spell>> = spellDao.observeAll()

    fun observeSpellsForCharacter(characterId: String): Flow<List<Spell>> =
        spellDao.observeForCharacter(characterId)

    fun observeSpellIdsForCharacter(characterId: String): Flow<List<String>> =
        spellDao.observeSpellIdsForCharacter(characterId)

    fun observePreparedSpellIdsForCharacter(characterId: String): Flow<List<String>> =
        spellDao.observePreparedSpellIdsForCharacter(characterId)

    suspend fun getSpell(spellId: String): Spell? = spellDao.getById(spellId)

    /** Заклинание с указанным названием (без учёта регистра) или null. */
    suspend fun findSpellByName(name: String): Spell? = spellDao.findByName(name.trim())

    suspend fun upsertSpell(spell: Spell) = spellDao.upsert(spell)

    suspend fun deleteSpell(spellId: String) = spellDao.deleteById(spellId)

    // endregion

    // region Персонажи

    fun observeCharacters(): Flow<List<Character>> = characterDao.observeAll()

    fun observeSpellCounts(): Flow<List<CharacterSpellCount>> = characterDao.observeSpellCounts()

    suspend fun getCharacter(characterId: String): Character? = characterDao.getById(characterId)

    suspend fun upsertCharacter(character: Character) = characterDao.upsert(character)

    suspend fun deleteCharacter(characterId: String) = characterDao.deleteById(characterId)

    // endregion

    // region Связь персонаж — заклинание

    suspend fun addSpellToCharacter(characterId: String, spellId: String) =
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(characterId, spellId))

    /**
     * Добавляет заклинание персонажу с проверкой лимита заговоров.
     * Возвращает [AddSpellResult]: успех, «уже есть» или превышение лимита заговоров.
     * [maxCantrips] <= 0 означает отсутствие ограничения.
     */
    suspend fun tryAddSpellToCharacter(
        characterId: String,
        spellId: String,
        spellLevel: Int,
        maxCantrips: Int,
    ): AddSpellResult {
        if (spellDao.countLink(characterId, spellId) > 0) return AddSpellResult.ALREADY_ADDED
        if (spellLevel == 0 && maxCantrips > 0 && spellDao.countCantrips(characterId) >= maxCantrips) {
            return AddSpellResult.CANTRIP_LIMIT_REACHED
        }
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(characterId, spellId))
        return AddSpellResult.ADDED
    }

    suspend fun removeSpellFromCharacter(characterId: String, spellId: String) =
        spellDao.removeSpellFromCharacter(characterId, spellId)

    /**
     * Меняет подготовку заклинания. При попытке подготовить сверх лимита возвращает false
     * и ничего не меняет. [maxPrepared] <= 0 означает отсутствие лимита.
     */
    suspend fun setSpellPrepared(
        characterId: String,
        spellId: String,
        prepared: Boolean,
        maxPrepared: Int,
    ): Boolean {
        if (prepared && maxPrepared > 0 && spellDao.countPrepared(characterId) >= maxPrepared) {
            return false
        }
        spellDao.setPrepared(characterId, spellId, prepared)
        return true
    }

    // endregion

    /**
     * Разовая миграция: если библиотека пуста и существует старый JSON-файл,
     * переносим заклинания в БД и удаляем файл.
     */
    suspend fun migrateLegacyIfNeeded() {
        if (spellDao.count() > 0) return
        val legacy = File(context.filesDir, LEGACY_FILE_NAME)
        if (!legacy.exists()) return
        runCatching {
            val spells = SpellLssCodec.decodeList(legacy.readText())
            if (spells.isNotEmpty()) spellDao.insertAll(spells)
        }
        legacy.delete()
    }

    private companion object {
        const val LEGACY_FILE_NAME = "spells.json"
    }
}
