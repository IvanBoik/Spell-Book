package com.example.spellbook.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты правил доменных моделей: ячейки, ресурсы, предметы и шаги комбинаций. */
class ModelsTest {

    private companion object {
        const val CHARACTER_ID = "char-1"
    }

    // region Character

    @Test
    fun `availableSlots subtracts used slots`() {
        val character = Character(
            spellSlots = mapOf(1 to 4, 2 to 3),
            spellSlotsUsed = mapOf(1 to 1),
        )

        assertEquals(3, character.availableSlots(1))
        assertEquals(3, character.availableSlots(2))
    }

    @Test
    fun `availableSlots returns zero for unknown level`() {
        assertEquals(0, Character().availableSlots(9))
    }

    @Test
    fun `availableSlots may be negative on inconsistent data`() {
        val character = Character(spellSlots = mapOf(1 to 1), spellSlotsUsed = mapOf(1 to 3))

        assertEquals(-2, character.availableSlots(1))
    }

    // endregion

    // region CharacterResource

    @Test
    fun `resource normalized caps current value by maximum`() {
        val resource = CharacterResource(name = "Очки", current = 99, maximum = 5).normalized()

        assertEquals(5, resource.current)
    }

    @Test
    fun `resource normalized raises negative current value to zero`() {
        val resource = CharacterResource(name = "Очки", current = -4, maximum = 5).normalized()

        assertEquals(0, resource.current)
    }

    @Test
    fun `resource normalized keeps maximum at least one`() {
        val resource = CharacterResource(name = "Очки", current = 0, maximum = 0).normalized()

        assertEquals(1, resource.maximum)
    }

    @Test
    fun `resource normalized trims name and description`() {
        val resource = CharacterResource(
            name = "  Кость превосходства  ",
            description = "  Тратится на приём  ",
            current = 1,
            maximum = 1,
        ).normalized()

        assertEquals("Кость превосходства", resource.name)
        assertEquals("Тратится на приём", resource.description)
    }

    // endregion

    // region InventoryItem

    private fun item(
        name: String = "Меч",
        quantity: Int = 1,
        categories: List<String> = emptyList(),
        isMagic: Boolean = false,
        rarity: String = "Без редкости",
        requiresAttunement: Boolean = false,
        attuned: Boolean = false,
    ) = InventoryItem(
        id = "item-1",
        characterId = CHARACTER_ID,
        name = name,
        quantity = quantity,
        categories = categories,
        isMagic = isMagic,
        rarity = rarity,
        requiresAttunement = requiresAttunement,
        attuned = attuned,
    )

    @Test
    fun `item normalized raises quantity to one`() {
        assertEquals(1, item(quantity = 0).normalized().quantity)
        assertEquals(1, item(quantity = -5).normalized().quantity)
    }

    @Test
    fun `item normalized cleans blank and duplicated categories`() {
        val normalized = item(categories = listOf(" Оружие ", "Оружие", "  ", "Сокровище")).normalized()

        assertEquals(listOf("Оружие", "Сокровище"), normalized.categories)
    }

    @Test
    fun `item normalized falls back to default rarity`() {
        assertEquals("Без редкости", item(rarity = "  ").normalized().rarity)
    }

    @Test
    fun `non magic item cannot require attunement`() {
        val normalized = item(isMagic = false, requiresAttunement = true, attuned = true).normalized()

        assertFalse(normalized.requiresAttunement)
        assertFalse(normalized.attuned)
    }

    @Test
    fun `magic item without attunement requirement cannot be attuned`() {
        val normalized = item(isMagic = true, requiresAttunement = false, attuned = true).normalized()

        assertFalse(normalized.attuned)
    }

    @Test
    fun `attunement is kept for magic item that requires it`() {
        val normalized = item(isMagic = true, requiresAttunement = true, attuned = true).normalized()

        assertTrue(normalized.requiresAttunement)
        assertTrue(normalized.attuned)
    }

    @Test
    fun `item normalized trims name and description`() {
        val normalized = item(name = "  Посох  ").copy(description = "  Магия  ").normalized()

        assertEquals("Посох", normalized.name)
        assertEquals("Магия", normalized.description)
    }

    // endregion

    // region ComboStep

    private fun step(
        type: String = ComboStepType.DICE.name,
        count: Int = 2,
        sides: Int = 8,
        modifier: Int = 0,
        flatValue: Int = 0,
    ) = ComboStep(
        characterId = CHARACTER_ID,
        name = "Шаг",
        type = type,
        diceCount = count,
        diceSides = sides,
        modifier = modifier,
        flatValue = flatValue,
    )

    @Test
    fun `dice formula includes modifier sign`() {
        assertEquals("2к8", step().formula)
        assertEquals("2к8 + 3", step(modifier = 3).formula)
        assertEquals("2к8 - 3", step(modifier = -3).formula)
    }

    @Test
    fun `dice formula raises dice count to one`() {
        assertEquals("1к8", step(count = 0).formula)
    }

    @Test
    fun `constant step formula shows value sign`() {
        assertEquals("+5", step(type = ComboStepType.CONSTANT.name, flatValue = 5).formula)
        assertEquals("-5", step(type = ComboStepType.CONSTANT.name, flatValue = -5).formula)
        assertEquals("+0", step(type = ComboStepType.CONSTANT.name, flatValue = 0).formula)
    }

    @Test
    fun `unknown step type falls back to dice`() {
        assertEquals(ComboStepType.DICE, step(type = "UNKNOWN").stepType)
        assertEquals(ComboStepType.CONSTANT, step(type = ComboStepType.CONSTANT.name).stepType)
    }

    // endregion

    @Test
    fun `inventory dictionaries are not empty and have no duplicates`() {
        assertEquals(INVENTORY_CATEGORIES.distinct(), INVENTORY_CATEGORIES)
        assertEquals(ITEM_RARITIES.distinct(), ITEM_RARITIES)
        assertEquals(COMBO_DICE_SIDES.distinct(), COMBO_DICE_SIDES)
        assertTrue(ITEM_RARITIES.first() == "Без редкости")
    }

    @Test
    fun `coin codes are unique`() {
        assertEquals(CoinType.entries.size, CoinType.entries.map { it.code }.distinct().size)
    }
}
