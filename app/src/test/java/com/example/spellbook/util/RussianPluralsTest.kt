package com.example.spellbook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты склонения единиц времени и дистанции по числу. */
class RussianPluralsTest {

    @Test
    fun `single form is used for one`() {
        assertEquals("минута", RussianPlurals.forCount("minute", 1))
        assertEquals("час", RussianPlurals.forCount("hour", 1))
        assertEquals("день", RussianPlurals.forCount("day", 1))
    }

    @Test
    fun `few form is used for two to four`() {
        assertEquals("минуты", RussianPlurals.forCount("minute", 2))
        assertEquals("часа", RussianPlurals.forCount("hour", 3))
        assertEquals("дня", RussianPlurals.forCount("day", 4))
    }

    @Test
    fun `many form is used for five and more`() {
        assertEquals("минут", RussianPlurals.forCount("minute", 5))
        assertEquals("часов", RussianPlurals.forCount("hour", 10))
        assertEquals("дней", RussianPlurals.forCount("day", 100))
    }

    @Test
    fun `teens always use many form`() {
        // Классическая ловушка: 11–14 требуют формы множественного числа.
        listOf(11, 12, 13, 14).forEach { count ->
            assertEquals("Wrong form for $count", "минут", RussianPlurals.forCount("minute", count))
        }
    }

    @Test
    fun `compound numbers follow last digit`() {
        assertEquals("минута", RussianPlurals.forCount("minute", 21))
        assertEquals("минуты", RussianPlurals.forCount("minute", 22))
        assertEquals("минут", RussianPlurals.forCount("minute", 25))
        assertEquals("минута", RussianPlurals.forCount("minute", 101))
    }

    @Test
    fun `distance units are declined too`() {
        assertEquals("фут", RussianPlurals.forCount("ft", 1))
        assertEquals("фута", RussianPlurals.forCount("ft", 3))
        assertEquals("футов", RussianPlurals.forCount("ft", 30))
        assertEquals("миля", RussianPlurals.forCount("mi", 1))
        assertEquals("мили", RussianPlurals.forCount("mi", 2))
    }

    @Test
    fun `activation types are declined`() {
        assertEquals("действие", RussianPlurals.forCount("action", 1))
        assertEquals("действия", RussianPlurals.forCount("action", 2))
        assertEquals("бонусное действие", RussianPlurals.forCount("bonus", 1))
        assertEquals("реакция", RussianPlurals.forCount("reaction", 1))
    }

    @Test
    fun `unknown code has no forms`() {
        // Для «Мгновенной» и «Касания» склонение не нужно — подпись берётся из справочника.
        assertNull(RussianPlurals.forCount("inst", 1))
        assertNull(RussianPlurals.forCount("touch", 1))
        assertFalse(RussianPlurals.hasForms("spec"))
        assertTrue(RussianPlurals.hasForms("minute"))
    }
}
