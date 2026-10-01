package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Character
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты бонуса инициативы.
 *
 * Бонус хранится формулой, поэтому проверяется и поведение по умолчанию
 * (модификатор Ловкости), и пересчёт при изменении характеристик.
 */
class InitiativeBonusTest {

    /** Ловкость 16 даёт модификатор +3. */
    private val dexterous = Character(
        level = 5,
        abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 16),
    )

    @Test
    fun `empty formula falls back to the dexterity modifier`() {
        assertEquals(3, dexterous.initiativeBonus)
    }

    @Test
    fun `default formula matches the implicit behaviour`() {
        val explicit = dexterous.copy(initiativeFormula = DEFAULT_INITIATIVE_FORMULA)

        assertEquals(dexterous.initiativeBonus, explicit.initiativeBonus)
    }

    @Test
    fun `custom formula adds a flat bonus`() {
        val alert = dexterous.copy(initiativeFormula = "[dex] + 5")

        assertEquals(8, alert.initiativeBonus)
    }

    @Test
    fun `formula may use other variables`() {
        // Бонус мастерства на 5 уровне равен 3, поэтому 3 + 3 = 6.
        val swashbuckler = dexterous.copy(initiativeFormula = "[dex] + [pb]")

        assertEquals(6, swashbuckler.initiativeBonus)
    }

    @Test
    fun `bonus follows the ability it depends on`() {
        // Ради этого бонус и хранится формулой: рост Ловкости пересчитывает его сам.
        val improved = dexterous.copy(
            initiativeFormula = "[dex] + 5",
            abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 20),
        )

        assertEquals(10, improved.initiativeBonus)
    }

    @Test
    fun `negative modifier is supported`() {
        val clumsy = Character(abilityScores = mapOf(AbilityType.DEXTERITY.ordinal to 6))

        assertEquals(-2, clumsy.initiativeBonus)
    }

    @Test
    fun `broken formula falls back to the default`() {
        // Экран характеристик должен работать, даже если выражение испортили вручную.
        val broken = dexterous.copy(initiativeFormula = "[dex] +")

        assertEquals(3, broken.initiativeBonus)
    }

    @Test
    fun `plain number overrides the ability entirely`() {
        val fixed = dexterous.copy(initiativeFormula = "2")

        assertEquals(2, fixed.initiativeBonus)
    }
}
