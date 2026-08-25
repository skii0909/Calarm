package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingingSessionKeyTest {
    @Test
    fun `key is stable for kind alarm and attempt token`() {
        val first = RingingSessionKey.create(false, 7L, "attempt-a")
        val same = RingingSessionKey.create(false, 7L, "attempt-a")

        assertEquals(first, same)
        assertEquals(first, RingingSessionKey.decode(first.encode()))
    }

    @Test
    fun `kind id and attempt token each separate sessions`() {
        val base = RingingSessionKey.create(false, 7L, "attempt-a")

        assertNotEquals(base, RingingSessionKey.create(true, 7L, "attempt-a"))
        assertNotEquals(base, RingingSessionKey.create(false, 8L, "attempt-a"))
        assertNotEquals(base, RingingSessionKey.create(false, 7L, "attempt-b"))
    }

    @Test
    fun `invalid serialized key fails closed`() {
        assertNull(RingingSessionKey.decode(null))
        assertNull(RingingSessionKey.decode("shift:7:1787586900000"))
        assertNull(RingingSessionKey.decode("v2:regular:not-a-number:attempt"))
        assertNull(RingingSessionKey.decode("v2:unknown:7:attempt"))
        assertNull(RingingSessionKey.decode("v2:regular:-1:attempt"))
        assertNull(RingingSessionKey.decode("v2:regular:7:"))
    }
}
