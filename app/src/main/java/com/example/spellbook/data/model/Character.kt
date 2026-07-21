package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Персонаж-владелец набора заклинаний. Заклинания хранятся в общей библиотеке,
 * а персонаж ссылается на них через таблицу связи [CharacterSpellCrossRef].
 *
 * [imageUri] — строковый URI выбранной из галереи картинки; если пусто,
 * показывается запасной аватар с инициалами.
 *
 * Подготовка заклинаний:
 * [canPrepareSpells] — умеет ли персонаж переподготавливать заклинания. Если да,
 * его известные заклинания делятся на «все известные» и «подготовленные» (флаг
 * [CharacterSpellCrossRef.prepared]), а [maxPreparedSpells] ограничивает их число.
 * Заговоры (уровень 0) подготовке не подлежат и ограничиваются отдельно [maxCantrips].
 *
 * Ячейки заклинаний:
 * [spellSlots] — доступно ячеек по уровням (1..9), [spellSlotsUsed] — потрачено.
 */
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val canPrepareSpells: Boolean = false,
    val maxPreparedSpells: Int = 0,
    /** Сколько заговоров (уровень 0) доступно. 0 — без ограничения. */
    val maxCantrips: Int = 0,
    val spellSlots: Map<Int, Int> = emptyMap(),
    val spellSlotsUsed: Map<Int, Int> = emptyMap(),
) {
    /** Количество доступных (не потраченных) ячеек указанного уровня. */
    fun availableSlots(level: Int): Int =
        (spellSlots[level] ?: 0) - (spellSlotsUsed[level] ?: 0)
}
