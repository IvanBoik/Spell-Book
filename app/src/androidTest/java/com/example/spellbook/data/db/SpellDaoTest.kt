package com.example.spellbook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Тесты доступа к заклинаниям и связям «персонаж — заклинание» на in-memory базе. */
@RunWith(AndroidJUnit4::class)
class SpellDaoTest {

    private companion object {
        const val CHARACTER_ID = "char-1"
        const val CANTRIP_ID = "cantrip-1"
        const val SPELL_ID = "spell-1"
    }

    private lateinit var db: SpellBookDatabase
    private lateinit var spellDao: SpellDao
    private lateinit var characterDao: CharacterDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpellBookDatabase::class.java,
        ).build()
        spellDao = db.spellDao()
        characterDao = db.characterDao()
        characterDao.upsert(Character(id = CHARACTER_ID, name = "Гэндальф"))
    }

    @After
    fun tearDown() = db.close()

    private fun spell(id: String, name: String, level: Int = 1, createdAt: Long = 0) =
        Spell(id = id, name = name, level = level, createdAt = createdAt)

    // region Библиотека заклинаний

    @Test
    fun upsertInsertsAndUpdatesSpell() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        assertEquals("Щит", spellDao.getById(SPELL_ID)?.name)

        spellDao.upsert(spell(SPELL_ID, "Щит веры"))

        assertEquals("Щит веры", spellDao.getById(SPELL_ID)?.name)
        assertEquals(1, spellDao.count())
    }

    @Test
    fun observeAllReturnsNewestSpellsFirst() = runBlocking {
        spellDao.upsert(spell("old", "Старое", createdAt = 100))
        spellDao.upsert(spell("new", "Новое", createdAt = 200))

        assertEquals(listOf("Новое", "Старое"), spellDao.observeAll().first().map { it.name })
    }

    @Test
    fun findByNameIsCaseInsensitiveForLatin() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Fireball"))

        assertNotNull(spellDao.findByName("fireball"))
        assertNull(spellDao.findByName("Ice bolt"))
    }

    @Test
    fun insertAllIgnoresDuplicatesById() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))

        spellDao.insertAll(listOf(spell(SPELL_ID, "Другое"), spell("spell-2", "Стрела")))

        assertEquals(2, spellDao.count())
        assertEquals("Щит", spellDao.getById(SPELL_ID)?.name)
    }

    @Test
    fun deleteByIdRemovesSpell() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))

        spellDao.deleteById(SPELL_ID)

        assertEquals(0, spellDao.count())
    }

    // endregion

    // region Связи с персонажем

    @Test
    fun characterSpellsAreVisibleThroughCrossRef() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.upsert(spell("spell-2", "Стрела"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        val spells = spellDao.observeForCharacter(CHARACTER_ID).first()

        assertEquals(listOf("Щит"), spells.map { it.name })
        assertEquals(listOf(SPELL_ID), spellDao.observeSpellIdsForCharacter(CHARACTER_ID).first())
    }

    @Test
    fun repeatedLinkInsertDoesNotCreateDuplicate() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))

        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        assertEquals(1, spellDao.countLink(CHARACTER_ID, SPELL_ID))
    }

    @Test
    fun removingLinkKeepsSpellInLibrary() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        spellDao.removeSpellFromCharacter(CHARACTER_ID, SPELL_ID)

        assertEquals(0, spellDao.countLink(CHARACTER_ID, SPELL_ID))
        assertNotNull(spellDao.getById(SPELL_ID))
    }

    @Test
    fun countCantripsCountsOnlyLevelZeroSpells() = runBlocking {
        spellDao.upsert(spell(CANTRIP_ID, "Луч холода", level = 0))
        spellDao.upsert(spell(SPELL_ID, "Щит", level = 1))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, CANTRIP_ID))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        assertEquals(1, spellDao.countCantrips(CHARACTER_ID))
    }

    @Test
    fun preparedFlagIsToggledAndCounted() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        spellDao.setPrepared(CHARACTER_ID, SPELL_ID, true)

        assertEquals(1, spellDao.countPrepared(CHARACTER_ID))
        assertEquals(listOf(SPELL_ID), spellDao.observePreparedSpellIdsForCharacter(CHARACTER_ID).first())

        spellDao.setPrepared(CHARACTER_ID, SPELL_ID, false)

        assertEquals(0, spellDao.countPrepared(CHARACTER_ID))
    }

    @Test
    fun deletingCharacterCascadesLinks() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        characterDao.deleteById(CHARACTER_ID)

        assertEquals(0, spellDao.countLink(CHARACTER_ID, SPELL_ID))
        assertNotNull("Spell must remain in the library", spellDao.getById(SPELL_ID))
    }

    @Test
    fun deletingSpellCascadesLinks() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))

        spellDao.deleteById(SPELL_ID)

        assertTrue(spellDao.observeSpellIdsForCharacter(CHARACTER_ID).first().isEmpty())
    }

    @Test
    fun observeSpellCountsCountsCharacterSpells() = runBlocking {
        spellDao.upsert(spell(SPELL_ID, "Щит"))
        spellDao.upsert(spell("spell-2", "Стрела"))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, SPELL_ID))
        spellDao.addSpellToCharacter(CharacterSpellCrossRef(CHARACTER_ID, "spell-2"))

        val counts = characterDao.observeSpellCounts().first()

        assertEquals(1, counts.size)
        assertEquals(CHARACTER_ID, counts.single().characterId)
        assertEquals(2, counts.single().count)
    }

    // endregion
}
