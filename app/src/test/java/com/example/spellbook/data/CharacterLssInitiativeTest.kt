package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Character
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Перенос бонуса инициативы через лист персонажа LSS.
 *
 * Формат хранит готовое число, а не формулу, поэтому проверяется главное:
 * после выгрузки и загрузки итоговый бонус остаётся прежним.
 */
class CharacterLssInitiativeTest {

    /** Ловкость 16 даёт модификатор +3. */
    private val base = Character(
        name = "Тест",
        level = 5,
        abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 16),
    )

    private fun roundTrip(character: Character): Character =
        CharacterLssCodec.decode(CharacterLssCodec.encode(character), existing = character)

    @Test
    fun `custom bonus survives the round trip`() {
        val alert = base.copy(initiativeFormula = "[dex] + 5")

        assertEquals(8, roundTrip(alert).initiativeBonus)
    }

    @Test
    fun `default bonus stays default`() {
        val restored = roundTrip(base)

        assertEquals(3, restored.initiativeBonus)
        // Ровно модификатор Ловкости записывается как поведение по умолчанию.
        assertEquals("", restored.initiativeFormula)
    }

    @Test
    fun `penalty survives the round trip`() {
        val penalised = base.copy(initiativeFormula = "[dex] - 2")

        assertEquals(1, roundTrip(penalised).initiativeBonus)
    }

    @Test
    fun `restored formula still follows dexterity`() {
        // Бонус восстанавливается относительно Ловкости, поэтому её рост учитывается.
        val restored = roundTrip(base.copy(initiativeFormula = "[dex] + 5"))
        val improved = restored.copy(
            abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 20),
        )

        assertEquals(10, improved.initiativeBonus)
    }
}
