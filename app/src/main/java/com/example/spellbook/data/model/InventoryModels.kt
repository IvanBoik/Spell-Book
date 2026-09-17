package com.example.spellbook.data.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.spellbook.R
import java.util.UUID

/**
 * Стандартные категории; предмет также может хранить произвольные категории.
 *
 * Значения хранятся в базе строками и принадлежат пользователю, поэтому,
 * как и остальной пользовательский контент, при смене языка не переводятся.
 */
val INVENTORY_CATEGORIES = listOf(
    "Оружие", "Доспех", "Щит", "Инструмент", "Снаряжение",
    "Расходник", "Зелье", "Боеприпасы", "Сокровище", "Контейнер", "Квестовый",
)

/** Редкости предметов; также хранятся строками в карточке предмета. */
val ITEM_RARITIES = listOf(
    DEFAULT_ITEM_RARITY, "Обычный", "Необычный", "Редкий",
    "Очень редкий", "Легендарный", "Артефакт",
)

/** Редкость по умолчанию для новых и незаполненных предметов. */
const val DEFAULT_ITEM_RARITY = "Без редкости"

enum class CoinType(
    val code: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
) {
    PLATINUM("pp", R.string.coin_platinum, R.string.coin_platinum_short),
    GOLD("gp", R.string.coin_gold, R.string.coin_gold_short),
    ELECTRUM("ep", R.string.coin_electrum, R.string.coin_electrum_short),
    SILVER("sp", R.string.coin_silver, R.string.coin_silver_short),
    COPPER("cp", R.string.coin_copper, R.string.coin_copper_short),
}

@Entity(
    tableName = "inventory_items",
    foreignKeys = [ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class InventoryItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val name: String,
    val quantity: Int = 1,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val isMagic: Boolean = false,
    val rarity: String = DEFAULT_ITEM_RARITY,
    val requiresAttunement: Boolean = false,
    val attuned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Сохраняемая позиция предмета в списке; меньшее значение отображается выше. */
    val sortOrder: Long = -System.currentTimeMillis(),
) {
    fun normalized(): InventoryItem = copy(
        name = name.trim(),
        quantity = quantity.coerceAtLeast(1),
        description = description.trim(),
        categories = categories.map(String::trim).filter(String::isNotBlank).distinct(),
        rarity = rarity.ifBlank { DEFAULT_ITEM_RARITY },
        requiresAttunement = isMagic && requiresAttunement,
        attuned = isMagic && requiresAttunement && attuned,
    )
}
