package com.example.spellbook.data.auth

import com.example.spellbook.ui.auth.minutesCeil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Клиентские проверки полей — по правилам из описания API. */
class AuthValidationTest {

    @Test
    fun `email is trimmed and checked`() {
        assertTrue(AuthValidation.isValidEmail("  user@example.com "))
        assertFalse(AuthValidation.isValidEmail("user@"))
        assertFalse(AuthValidation.isValidEmail("user example.com"))
    }

    @Test
    fun `short password is rejected`() {
        assertEquals(PasswordProblem.TOO_SHORT, AuthValidation.newPasswordProblem("1234567"))
        assertNull(AuthValidation.newPasswordProblem("12345678"))
    }

    @Test
    fun `password over 72 characters is rejected`() {
        assertNull(AuthValidation.newPasswordProblem("a".repeat(72)))
        assertEquals(PasswordProblem.TOO_LONG, AuthValidation.newPasswordProblem("a".repeat(73)))
    }

    @Test
    fun `cyrillic password is limited by bytes`() {
        // Кириллица занимает 2 байта: 36 букв — ровно 72 байта, 37 — уже больше.
        assertNull(AuthValidation.newPasswordProblem("я".repeat(36)))
        assertEquals(PasswordProblem.TOO_LONG, AuthValidation.newPasswordProblem("я".repeat(37)))
    }

    @Test
    fun `code keeps only digits up to six`() {
        assertEquals("123456", AuthValidation.sanitizeCode("12 34-56789"))
        assertTrue(AuthValidation.isValidCode("123456"))
        assertFalse(AuthValidation.isValidCode("12345"))
    }

    @Test
    fun `lock minutes round up`() {
        assertEquals(1, minutesCeil(30))
        assertEquals(15, minutesCeil(900))
        assertEquals(16, minutesCeil(901))
    }

    @Test
    fun `device name avoids duplicating the manufacturer`() {
        assertEquals("Google Pixel 8", AuthModule.deviceName("google", "Pixel 8"))
        assertEquals("OnePlus 12", AuthModule.deviceName("OnePlus", "OnePlus 12"))
        assertEquals("Pixel 8", AuthModule.deviceName("", "Pixel 8"))
    }
}
