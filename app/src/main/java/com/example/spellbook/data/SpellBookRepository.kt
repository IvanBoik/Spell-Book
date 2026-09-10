package com.example.spellbook.data

import android.content.Context
import com.example.spellbook.data.db.CharacterSpellCount
import com.example.spellbook.data.db.SpellBookDatabase
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterSpellCrossRef
import com.example.spellbook.data.model.Combo
import com.example.spellbook.data.model.ComboStep
import com.example.spellbook.data.model.ComboWithSteps
import com.example.spellbook.data.model.CharacterFeat
import com.example.spellbook.data.model.CharacterFeatCrossRef
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.InventoryItem
import com.example.spellbook.data.model.NoteBlock
import com.example.spellbook.data.model.Spell
import com.example.spellbook.data.model.SpellOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Результат попытки добавить заклинание персонажу. */
enum class AddSpellResult { ADDED, ALREADY_ADDED, CANTRIP_LIMIT_REACHED }

/**
 * Единая точка доступа к данным приложения (Room). Инкапсулирует библиотеку
 * заклинаний, персонажей и связи между ними, а также разовую миграцию данных
 * из старого файлового хранилища [LEGACY_FILE_NAME].
 */
class SpellBookRepository(
    private val context: Context,
    /** Позволяет подменить БД (например, in-memory в тестах). */
    db: SpellBookDatabase = SpellBookDatabase.get(context),
) {

    private val spellDao = db.spellDao()
    private val characterDao = db.characterDao()
    private val comboDao = db.comboDao()
    private val inventoryDao = db.inventoryDao()
    private val noteDao = db.noteDao()
    private val featDao = db.featDao()

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

    /**
     * Сохраняет официальное заклинание, не создавая дублей по названию.
     *
     * Правила разрешения конфликтов:
     * - записи, созданной пользователем вручную, никогда не касаемся;
     * - ранее загруженную запись обновляем, сохраняя `id`, чтобы не потерять
     *   привязки к персонажам и отметки о подготовке.
     *
     * @return была ли запись добавлена или обновлена.
     */
    suspend fun saveOfficialSpell(spell: Spell): Boolean {
        val official = spell.copy(origin = SpellOrigin.OFFICIAL.name)
        val existing = findSpellByName(spell.name)
            ?: run {
                spellDao.upsert(official)
                return true
            }
        if (!existing.isReplaceableByOfficial) return false
        spellDao.upsert(official.copy(id = existing.id, createdAt = existing.createdAt))
        return true
    }

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

    // region Комбинации и библиотека шагов

    fun observeCombos(characterId: String): Flow<List<Combo>> = comboDao.observeCombos(characterId)

    fun observeComboSteps(characterId: String): Flow<List<ComboStep>> = comboDao.observeSteps(characterId)

    suspend fun getCombo(comboId: String): Combo? = comboDao.getCombo(comboId)

    suspend fun getComboStep(stepId: String): ComboStep? = comboDao.getStep(stepId)

    suspend fun getComboWithSteps(comboId: String): ComboWithSteps? {
        val combo = comboDao.getCombo(comboId) ?: return null
        val steps = comboDao.getStepIds(comboId).mapNotNull { comboDao.getStep(it) }
        return ComboWithSteps(combo, steps)
    }

    suspend fun saveCombo(combo: Combo, stepIds: List<String>) {
        comboDao.upsertCombo(combo)
        comboDao.replaceComboSteps(combo.id, stepIds.distinct())
    }

    suspend fun saveComboStep(step: ComboStep) = comboDao.upsertStep(step)

    suspend fun deleteCombo(comboId: String) = comboDao.deleteCombo(comboId)

    suspend fun reorderCombos(orderedIds: List<String>) = comboDao.reorderCombos(orderedIds)

    suspend fun deleteComboStep(stepId: String) = comboDao.deleteStep(stepId)

    // endregion

    // region Инвентарь

    fun observeInventoryItems(characterId: String): Flow<List<InventoryItem>> =
        inventoryDao.observeItems(characterId)

    suspend fun getInventoryItem(itemId: String): InventoryItem? = inventoryDao.getItem(itemId)

    suspend fun saveInventoryItem(item: InventoryItem) = inventoryDao.upsertItem(item.normalized())

    suspend fun deleteInventoryItem(itemId: String) = inventoryDao.deleteItem(itemId)

    suspend fun reorderInventoryItems(orderedIds: List<String>) = inventoryDao.reorderItems(orderedIds)

    // endregion

    // region Заметки

    fun observeNoteBlocks(characterId: String): Flow<List<NoteBlock>> = noteDao.observeBlocks(characterId)

    suspend fun saveNoteBlock(block: NoteBlock) = noteDao.upsertBlock(block)

    suspend fun deleteNoteBlock(blockId: String) = noteDao.deleteBlock(blockId)

    suspend fun reorderNoteBlocks(orderedIds: List<String>) = noteDao.reorderBlocks(orderedIds)

    // endregion

    // region Черты

    fun observeAllFeats(): Flow<List<Feat>> = featDao.observeAllFeats()

    fun observeFeatsForCharacter(characterId: String): Flow<List<CharacterFeat>> =
        featDao.observeFeatsForCharacter(characterId)

    fun observeFeatIdsForCharacter(characterId: String): Flow<List<String>> =
        featDao.observeFeatIdsForCharacter(characterId)

    suspend fun findFeatByName(name: String): Feat? = featDao.findByName(name.trim())

    suspend fun saveFeat(feat: Feat) = featDao.upsertFeat(feat)

    suspend fun deleteFeat(featId: String) = featDao.deleteFeat(featId)

    suspend fun addFeatToCharacter(characterId: String, featId: String) =
        featDao.addFeatToCharacter(CharacterFeatCrossRef(characterId = characterId, featId = featId))

    suspend fun removeFeatFromCharacter(characterId: String, featId: String) =
        featDao.removeFeatFromCharacter(characterId, featId)

    suspend fun setFeatCollapsed(characterId: String, featId: String, collapsed: Boolean) =
        featDao.updateCollapsed(characterId, featId, collapsed)

    suspend fun reorderFeats(characterId: String, orderedIds: List<String>) =
        featDao.reorderFeats(characterId, orderedIds)

    // endregion

    /**
     * Разовая миграция: если библиотека пуста
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

    /**
     * Импортирует набор заклинаний, вложенный в приложение как файл в `assets`.
     *
     * Выполняется в фоне: файл большой, а разбор JSON недёшев. Действуют те же
     * правила, что и при загрузке с сайта: заклинания, созданные пользователем
     * вручную, не затираются, а ранее загруженные обновляются на месте.
     *
     * @return сколько записей добавлено или обновлено; 0 — если файла нет.
     */
    suspend fun importBundledLibrary(): Int = withContext(Dispatchers.IO) {
        val json = runCatching {
            context.assets.open(BUNDLED_LIBRARY_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext 0

        val spells = runCatching { SpellLssCodec.decodeList(json) }.getOrNull().orEmpty()
        var saved = 0
        spells.forEach { spell ->
            if (spell.name.isNotBlank() && saveOfficialSpell(spell)) saved++
        }
        saved
    }

    private companion object {
        const val LEGACY_FILE_NAME = "spells.json"

        /** Файл со встроенной библиотекой; лежит в `app/src/main/assets`. */
        const val BUNDLED_LIBRARY_ASSET = "spellbook-library.json"
    }
}
