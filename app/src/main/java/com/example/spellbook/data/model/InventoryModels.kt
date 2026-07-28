package com.example.spellbook.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Стандартные категории; предмет также может хранить произвольные категории. */
val INVENTORY_CATEGORIES = listOf(
    "Оружие", "Доспех", "Щит", "Инструмент", "Снаряжение",
    "Расходник", "Зелье", "Боеприпасы", "Сокровище", "Контейнер", "Квестовый",
)

val ITEM_RARITIES = listOf(
    "Без редкости", "Обычный", "Необычный", "Редкий",
    "Очень редкий", "Легендарный", "Артефакт",
)

enum class CoinType(val code: String, val label: String, val shortLabel: String) {
    PLATINUM("pp", "Платина", "ПМ"),
    GOLD("gp", "Золото", "ЗМ"),
    ELECTRUM("ep", "Электрум", "ЭМ"),
    SILVER("sp", "Серебро", "СМ"),
    COPPER("cp", "Медь", "ММ"),
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
    val rarity: String = "Без редкости",
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
        rarity = rarity.ifBlank { "Без редкости" },
        requiresAttunement = isMagic && requiresAttunement,
        attuned = isMagic && requiresAttunement && attuned,
    )
}
