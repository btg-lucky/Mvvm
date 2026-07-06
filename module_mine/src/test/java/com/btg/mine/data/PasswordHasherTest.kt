package com.btg.mine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce same hash`() {
        assertEquals(
            PasswordHasher.hash("secret123", "abcd"),
            PasswordHasher.hash("secret123", "abcd")
        )
    }

    @Test
    fun `different salt produces different hash`() {
        assertNotEquals(
            PasswordHasher.hash("secret123", "salt1"),
            PasswordHasher.hash("secret123", "salt2")
        )
    }

    @Test
    fun `hash is 64-char lowercase hex`() {
        val hash = PasswordHasher.hash("secret123", "abcd")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `generateSalt returns 32-char hex and differs each call`() {
        val s1 = PasswordHasher.generateSalt()
        val s2 = PasswordHasher.generateSalt()
        assertEquals(32, s1.length)
        assertNotEquals(s1, s2)
    }

    @Test
    fun `verify matches correct password and rejects wrong one`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("secret123", salt)
        assertTrue(PasswordHasher.verify("secret123", salt, hash))
        assertFalse(PasswordHasher.verify("wrong", salt, hash))
    }
}
