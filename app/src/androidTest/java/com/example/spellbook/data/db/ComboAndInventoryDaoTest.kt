package com.example.spellbook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.InventoryItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Тесты хранения комбинаций и инвентаря на in-memory базе. */
@RunWith(AndroidJUnit4::class)
class ComboAndInventoryDaoTest {

    private companion object {
        const val CHARACTER_ID = "char-1"
        const val COMBO_ID = "combo-1"
    }

    private lateinit var db: SpellBookDatabase
    private lateinit var comboDao: ComboDao
    private lateinit var inventoryDao: InventoryDao
    private lateinit var characterDao: CharacterDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpellBookDatabase::class.java,
        ).build()
        comboDao = db.comboDao()
        inventoryDao = db.inventoryDao()
        characterDao = db.characterDao()
        characterDao.upsert(Character(id = CHARACTER_ID, name = "Гэндальф"))
    }

    @After
    fun tearDown() = db.close()

    private fun step(id: String, name: String = "Шаг", createdAt: Long = 0) =
        ComboStep(id = id, characterId = CHARACTER_ID, name = name, createdAt = createdAt)

    private fun item(id: String, name: String, sortOrder: Long = 0, createdAt: Long = 0) =
        InventoryItem(
            id = id,
            characterId = CHARACTER_ID,
            name = name,
            sortOrder = sortOrder,
            createdAt = createdAt,
        )

    // region Комбинации

    @Test
    fun comboStepsAreStoredInGivenOrder() = runBlocking {
        comboDao.upsertCombo(Combo(id = COMBO_ID, characterId = CHARACTER_ID, name = "Атака"))
        listOf("s1", "s2", "s3").forEach { comboDao.upsertStep(step(it)) }

        comboDao.replaceComboSteps(COMBO_ID, listOf("s3", "s1", "s2"))

        assertEquals(listOf("s3", "s1", "s2"), comboDao.getStepIds(COMBO_ID))
    }

    @Test
    fun replaceComboStepsFullyReplacesPreviousSet() = runBlocking {
        comboDao.upsertCombo(Combo(id = COMBO_ID, characterId = CHARACTER_ID, name = "Атака"))
        listOf("s1", "s2").forEach { comboDao.upsertStep(step(it)) }
        comboDao.replaceComboSteps(COMBO_ID, listOf("s1", "s2"))

        comboDao.replaceComboSteps(COMBO_ID, listOf("s2"))

        assertEquals(listOf("s2"), comboDao.getStepIds(COMBO_ID))
    }

    @Test
    fun deletingStepRemovesItFromCombo() = runBlocking {
        comboDao.upsertCombo(Combo(id = COMBO_ID, characterId = CHARACTER_ID, name = "Атака"))
        listOf("s1", "s2").forEach { comboDao.upsertStep(step(it)) }
        comboDao.replaceComboSteps(COMBO_ID, listOf("s1", "s2"))

        comboDao.deleteStep("s1")

        assertEquals(listOf("s2"), comboDao.getStepIds(COMBO_ID))
        assertNull(comboDao.getStep("s1"))
    }

    @Test
    fun deletingComboKeepsStepsInLibrary() = runBlocking {
        comboDao.upsertCombo(Combo(id = COMBO_ID, characterId = CHARACTER_ID, name = "Атака"))
        comboDao.upsertStep(step("s1"))
        comboDao.replaceComboSteps(COMBO_ID, listOf("s1"))

        comboDao.deleteCombo(COMBO_ID)

        assertNull(comboDao.getCombo(COMBO_ID))
        assertEquals(1, comboDao.observeSteps(CHARACTER_ID).first().size)
    }

    @Test
    fun combosAndStepsAreReturnedNewestFirst() = runBlocking {
        comboDao.upsertCombo(Combo(id = "c-old", characterId = CHARACTER_ID, name = "Старая", createdAt = 100))
        comboDao.upsertCombo(Combo(id = "c-new", characterId = CHARACTER_ID, name = "Новая", createdAt = 200))
        comboDao.upsertStep(step("s-old", name = "Старый", createdAt = 100))
        comboDao.upsertStep(step("s-new", name = "Новый", createdAt = 200))

        assertEquals(listOf("Новая", "Старая"), comboDao.observeCombos(CHARACTER_ID).first().map { it.name })
        assertEquals(listOf("Новый", "Старый"), comboDao.observeSteps(CHARACTER_ID).first().map { it.name })
    }

    @Test
    fun deletingCharacterCascadesCombos() = runBlocking {
        comboDao.upsertCombo(Combo(id = COMBO_ID, characterId = CHARACTER_ID, name = "Атака"))
        comboDao.upsertStep(step("s1"))

        characterDao.deleteById(CHARACTER_ID)

        assertNull(comboDao.getCombo(COMBO_ID))
        assertNull(comboDao.getStep("s1"))
    }

    // endregion

    // region Инвентарь

    @Test
    fun itemsAreReturnedInUserDefinedOrder() = runBlocking {
        inventoryDao.upsertItem(item("i1", "Меч", sortOrder = 2))
        inventoryDao.upsertItem(item("i2", "Щит", sortOrder = 1))

        assertEquals(listOf("Щит", "Меч"), inventoryDao.observeItems(CHARACTER_ID).first().map { it.name })
    }

    @Test
    fun reorderItemsPersistsNewOrder() = runBlocking {
        inventoryDao.upsertItem(item("i1", "Меч", sortOrder = 0))
        inventoryDao.upsertItem(item("i2", "Щит", sortOrder = 1))
        inventoryDao.upsertItem(item("i3", "Зелье", sortOrder = 2))

        inventoryDao.reorderItems(listOf("i3", "i1", "i2"))

        assertEquals(
            listOf("Зелье", "Меч", "Щит"),
            inventoryDao.observeItems(CHARACTER_ID).first().map { it.name },
        )
    }

    @Test
    fun newerItemsComeFirstOnEqualSortOrder() = runBlocking {
        inventoryDao.upsertItem(item("i1", "Старый", sortOrder = 0, createdAt = 100))
        inventoryDao.upsertItem(item("i2", "Новый", sortOrder = 0, createdAt = 200))

        assertEquals(listOf("Новый", "Старый"), inventoryDao.observeItems(CHARACTER_ID).first().map { it.name })
    }

    @Test
    fun deletingItemRemovesItFromList() = runBlocking {
        inventoryDao.upsertItem(item("i1", "Меч"))

        inventoryDao.deleteItem("i1")

        assertNull(inventoryDao.getItem("i1"))
        assertTrue(inventoryDao.observeItems(CHARACTER_ID).first().isEmpty())
    }

    @Test
    fun deletingCharacterCascadesInventory() = runBlocking {
        inventoryDao.upsertItem(item("i1", "Меч"))

        characterDao.deleteById(CHARACTER_ID)

        assertNull(inventoryDao.getItem("i1"))
    }

    // endregion
}
