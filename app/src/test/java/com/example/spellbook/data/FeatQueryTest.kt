package com.example.spellbook.data

import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Feat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты поиска и фильтра библиотеки черт. */
class FeatQueryTest {

    private fun feat(name: String, description: String) =
        Feat(name = name, description = description)

    @Test
    fun `single ability is detected`() {
        val result = FeatAbilities.increasedBy(
            feat("Атлет", "Увеличьте значение Силы на 1 при максимуме 20."),
        )

        assertEquals(setOf(AbilityType.STRENGTH), result)
    }

    @Test
    fun `possessive wording is detected`() {
        // На dnd.su встречаются обе формы: «значение Силы» и «значение вашей Ловкости».
        val result = FeatAbilities.increasedBy(
            feat("Ловкач", "Увеличьте значение вашей Ловкости на 1, при максимуме 20."),
        )

        assertEquals(setOf(AbilityType.DEXTERITY), result)
    }

    @Test
    fun `several abilities in one sentence are detected`() {
        val result = FeatAbilities.increasedBy(
            feat("Боец", "Увеличьте значение Силы, Телосложения или Харизмы на 1 при максимуме 20."),
        )

        assertEquals(
            setOf(AbilityType.STRENGTH, AbilityType.CONSTITUTION, AbilityType.CHARISMA),
            result,
        )
    }

    @Test
    fun `free choice counts as every ability`() {
        val result = FeatAbilities.increasedBy(
            feat("Агент", "Увеличьте значение характеристики по вашему выбору на 1 при максимуме 20."),
        )

        assertEquals(AbilityType.entries.toSet(), result)
    }

    @Test
    fun `feat without increase yields nothing`() {
        val result = FeatAbilities.increasedBy(
            feat("Бдительный", "Вы получаете бонус +5 к проверкам инициативы."),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ability mentioned outside increase sentence is ignored`() {
        // Иначе в фильтр «Сила» попадали бы черты, где характеристика лишь в условии применения.
        val result = FeatAbilities.increasedBy(
            feat("Мастер щита", "Вы можете использовать проверку Силы, чтобы оттолкнуть существо."),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter keeps only feats increasing the selected ability`() {
        val feats = listOf(
            feat("Атлет", "Увеличьте значение Силы на 1 при максимуме 20."),
            feat("Ловкач", "Увеличьте значение Ловкости на 1 при максимуме 20."),
            feat("Бдительный", "Вы получаете бонус +5 к проверкам инициативы."),
        )

        val result = feats.filterSearch("", FeatFilters(abilities = setOf(AbilityType.STRENGTH)))

        assertEquals(listOf("Атлет"), result.map { it.name })
    }

    @Test
    fun `several selected abilities are combined by OR`() {
        val feats = listOf(
            feat("Атлет", "Увеличьте значение Силы на 1 при максимуме 20."),
            feat("Ловкач", "Увеличьте значение Ловкости на 1 при максимуме 20."),
            feat("Умник", "Увеличьте значение Интеллекта на 1 при максимуме 20."),
        )

        val result = feats.filterSearch(
            "",
            FeatFilters(abilities = setOf(AbilityType.STRENGTH, AbilityType.DEXTERITY)),
        )

        assertEquals(listOf("Атлет", "Ловкач"), result.map { it.name })
    }

    @Test
    fun `search and filter are combined by AND`() {
        val feats = listOf(
            feat("Атлетичный", "Увеличьте значение Силы на 1 при максимуме 20."),
            feat("Атлетичный бегун", "Увеличьте значение Ловкости на 1 при максимуме 20."),
        )

        val result = feats.filterSearch(
            "атлетичный",
            FeatFilters(abilities = setOf(AbilityType.STRENGTH)),
        )

        assertEquals(listOf("Атлетичный"), result.map { it.name })
    }

    @Test
    fun `empty query and filters keep everything`() {
        val feats = listOf(feat("Атлет", "текст"), feat("Ловкач", "текст"))

        assertEquals(feats, feats.filterSearch("", FeatFilters()))
    }

    @Test
    fun `search ignores case and surrounding spaces`() {
        val feats = listOf(feat("Бдительный", "текст"))

        assertEquals(1, feats.filterSearch("  бдиТЕЛЬ  ", FeatFilters()).size)
    }
}
