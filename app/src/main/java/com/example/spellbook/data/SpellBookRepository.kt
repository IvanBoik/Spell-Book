package com.example.spellbook.data

import android.content.Context
import com.example.spellbook.data.db.CharacterSpellCount
import com.example.spellbook.data.db.SpellBookDatabase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.flow.Flow
import java.io.File

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

    suspend fun removeSpellFromCharacter(characterId: String, spellId: String) =
        spellDao.removeSpellFromCharacter(characterId, spellId)

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
