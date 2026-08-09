package com.example.spellbook.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spellbook.data.db.SpellBookDatabase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.Spell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Тесты бизнес-правил репозитория поверх in-memory базы. */
@RunWith(AndroidJUnit4::class)
class SpellBookRepositoryTest {

    private companion object {
        const val CHARACTER_ID = "char-1"
        const val SPELL_ID = "spell-1"
        const val CANTRIP_ID = "cantrip-1"
        const val LEGACY_FILE_NAME = "spells.json"
        const val NO_LIMIT = 0
    }

    private lateinit var context: Context
    private lateinit var db: SpellBookDatabase
    private lateinit var repository: SpellBookRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SpellBookDatabase::class.java).build()
        repository = SpellBookRepository(context, db)
        repository.upsertCharacter(Character(id = CHARACTER_ID, name = "Гэндальф"))
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, LEGACY_FILE_NAME).delete()
    }

    private fun spell(id: String, name: String, level: Int = 1) = Spell(id = id, name = name, level = level)

    // region Добавление заклинаний персонажу

    @Test
    fun addingSpellReturnsAddedResult() = runBlocking {
        repository.upsertSpell(spell(SPELL_ID, "Щит"))

        val result = repository.tryAddSpellToCharacter(CHARACTER_ID, SPELL_ID, spellLevel = 1, maxCantrips = NO_LIMIT)

        assertEquals(AddSpellResult.ADDED, result)
        assertEquals(listOf(SPELL_ID), repository.observeSpellIdsForCharacter(CHARACTER_ID).first())
    }

    @Test
    fun repeatedAddingReportsAlreadyAdded() = runBlocking {
        repository.upsertSpell(spell(SPELL_ID, "Щит"))
        repository.tryAddSpellToCharacter(CHARACTER_ID, SPELL_ID, spellLevel = 1, maxCantrips = NO_LIMIT)

        val result = repository.tryAddSpellToCharacter(CHARACTER_ID, SPELL_ID, spellLevel = 1, maxCantrips = NO_LIMIT)

        assertEquals(AddSpellResult.ALREADY_ADDED, result)
    }

    @Test
    fun cantripLimitBlocksAdding() = runBlocking {
        repository.upsertSpell(spell(CANTRIP_ID, "Луч холода", level = 0))
        repository.upsertSpell(spell("cantrip-2", "Брызги кислоты", level = 0))
        repository.tryAddSpellToCharacter(CHARACTER_ID, CANTRIP_ID, spellLevel = 0, maxCantrips = 1)

        val result = repository.tryAddSpellToCharacter(CHARACTER_ID, "cantrip-2", spellLevel = 0, maxCantrips = 1)

        assertEquals(AddSpellResult.CANTRIP_LIMIT_REACHED, result)
        assertEquals(listOf(CANTRIP_ID), repository.observeSpellIdsForCharacter(CHARACTER_ID).first())
    }

    @Test
    fun zeroCantripLimitMeansNoLimit() = runBlocking {
        repository.upsertSpell(spell(CANTRIP_ID, "Луч холода", level = 0))
        repository.upsertSpell(spell("cantrip-2", "Брызги кислоты", level = 0))
        repository.tryAddSpellToCharacter(CHARACTER_ID, CANTRIP_ID, spellLevel = 0, maxCantrips = NO_LIMIT)

        val result = repository.tryAddSpellToCharacter(CHARACTER_ID, "cantrip-2", spellLevel = 0, maxCantrips = NO_LIMIT)

        assertEquals(AddSpellResult.ADDED, result)
    }

    @Test
    fun cantripLimitDoesNotBlockLeveledSpell() = runBlocking {
        repository.upsertSpell(spell(CANTRIP_ID, "Луч холода", level = 0))
        repository.upsertSpell(spell(SPELL_ID, "Щит", level = 1))
        repository.tryAddSpellToCharacter(CHARACTER_ID, CANTRIP_ID, spellLevel = 0, maxCantrips = 1)

        val result = repository.tryAddSpellToCharacter(CHARACTER_ID, SPELL_ID, spellLevel = 1, maxCantrips = 1)

        assertEquals(AddSpellResult.ADDED, result)
    }

    // endregion

    // region Подготовка заклинаний

    @Test
    fun preparingWithinLimitSucceeds() = runBlocking {
        repository.upsertSpell(spell(SPELL_ID, "Щит"))
        repository.addSpellToCharacter(CHARACTER_ID, SPELL_ID)

        val prepared = repository.setSpellPrepared(CHARACTER_ID, SPELL_ID, prepared = true, maxPrepared = 1)

        assertTrue(prepared)
        assertEquals(listOf(SPELL_ID), repository.observePreparedSpellIdsForCharacter(CHARACTER_ID).first())
    }

    @Test
    fun exceedingPreparedLimitChangesNothing() = runBlocking {
        repository.upsertSpell(spell(SPELL_ID, "Щит"))
        repository.upsertSpell(spell("spell-2", "Стрела"))
        repository.addSpellToCharacter(CHARACTER_ID, SPELL_ID)
        repository.addSpellToCharacter(CHARACTER_ID, "spell-2")
        repository.setSpellPrepared(CHARACTER_ID, SPELL_ID, prepared = true, maxPrepared = 1)

        val prepared = repository.setSpellPrepared(CHARACTER_ID, "spell-2", prepared = true, maxPrepared = 1)

        assertFalse(prepared)
        assertEquals(listOf(SPELL_ID), repository.observePreparedSpellIdsForCharacter(CHARACTER_ID).first())
    }

    @Test
    fun unpreparingWorksEvenAtLimit() = runBlocking {
        repository.upsertSpell(spell(SPELL_ID, "Щит"))
        repository.addSpellToCharacter(CHARACTER_ID, SPELL_ID)
        repository.setSpellPrepared(CHARACTER_ID, SPELL_ID, prepared = true, maxPrepared = 1)

        val changed = repository.setSpellPrepared(CHARACTER_ID, SPELL_ID, prepared = false, maxPrepared = 1)

        assertTrue(changed)
        assertTrue(repository.observePreparedSpellIdsForCharacter(CHARACTER_ID).first().isEmpty())
    }

    // endregion

    // region Комбинации и инвентарь

    @Test
    fun comboIsReturnedWithStepsInSavedOrder() = runBlocking {
        val steps = listOf("s1", "s2").map { ComboStep(id = it, characterId = CHARACTER_ID, name = it) }
        steps.forEach { repository.saveComboStep(it) }
        val combo = Combo(id = "combo-1", characterId = CHARACTER_ID, name = "Атака")

        repository.saveCombo(combo, listOf("s2", "s1"))

        val loaded = requireNotNull(repository.getComboWithSteps("combo-1"))
        assertEquals(combo, loaded.combo)
        assertEquals(listOf("s2", "s1"), loaded.steps.map { it.id })
    }

    @Test
    fun duplicatedComboStepsAreDropped() = runBlocking {
        repository.saveComboStep(ComboStep(id = "s1", characterId = CHARACTER_ID, name = "Шаг"))

        repository.saveCombo(Combo(id = "combo-1", characterId = CHARACTER_ID, name = "Атака"), listOf("s1", "s1"))

        assertEquals(1, repository.getComboWithSteps("combo-1")?.steps?.size)
    }

    @Test
    fun unknownComboReturnsNull() = runBlocking {
        assertNull(repository.getComboWithSteps("unknown"))
    }

    @Test
    fun savingItemNormalizesItsFields() = runBlocking {
        val item = InventoryItem(
            id = "item-1",
            characterId = CHARACTER_ID,
            name = "  Меч  ",
            quantity = 0,
            categories = listOf("Оружие", "Оружие"),
            isMagic = false,
            requiresAttunement = true,
            attuned = true,
        )

        repository.saveInventoryItem(item)

        val saved = requireNotNull(repository.getInventoryItem("item-1"))
        assertEquals("Меч", saved.name)
        assertEquals(1, saved.quantity)
        assertEquals(listOf("Оружие"), saved.categories)
        assertFalse(saved.requiresAttunement)
        assertFalse(saved.attuned)
    }

    // endregion

    // region Миграция из файлового хранилища

    @Test
    fun migrationMovesSpellsFromFileAndDeletesIt() = runBlocking {
        val legacy = File(context.filesDir, LEGACY_FILE_NAME)
        legacy.writeText(SpellLssCodec.encodeList(listOf(spell(SPELL_ID, "Щит"))))

        repository.migrateLegacyIfNeeded()

        assertNotNull(repository.getSpell(SPELL_ID))
        assertFalse("Legacy file must be deleted after migration", legacy.exists())
    }

    @Test
    fun migrationIsSkippedWhenLibraryIsNotEmpty() = runBlocking {
        repository.upsertSpell(spell("existing", "Существующее"))
        val legacy = File(context.filesDir, LEGACY_FILE_NAME)
        legacy.writeText(SpellLssCodec.encodeList(listOf(spell(SPELL_ID, "Щит"))))

        repository.migrateLegacyIfNeeded()

        assertNull(repository.getSpell(SPELL_ID))
        assertTrue("Legacy file must be left untouched", legacy.exists())
    }

    @Test
    fun migrationWithoutFileDoesNotFail() = runBlocking {
        File(context.filesDir, LEGACY_FILE_NAME).delete()

        repository.migrateLegacyIfNeeded()

        assertTrue(repository.observeAllSpells().first().isEmpty())
    }

    // endregion
}
