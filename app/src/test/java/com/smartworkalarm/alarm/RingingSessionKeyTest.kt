package com.smartworkalarm.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingingSessionKeyTest {
    @Test
    fun `key is stable for kind alarm and scheduled occurrence`() {
        val occurrence = Instant.parse("2026-08-25T00:00:00Z")
        val first = RingingSessionKey.create(false, 7L, occurrence)
        val same = RingingSessionKey.create(false, 7L, occurrence)

        assertEquals(first, same)
        assertEquals(first, RingingSessionKey.decode(first.encode()))
    }

    @Test
    fun `kind id and occurrence each separate sessions`() {
        val occurrence = Instant.parse("2026-08-25T00:00:00Z")
        val base = RingingSessionKey.create(false, 7L, occurrence)

        assertNotEquals(base, RingingSessionKey.create(true, 7L, occurrence))
        assertNotEquals(base, RingingSessionKey.create(false, 8L, occurrence))
        assertNotEquals(base, RingingSessionKey.create(false, 7L, occurrence.plusSeconds(60)))
    }

    @Test
    fun `invalid serialized key fails closed`() {
        assertNull(RingingSessionKey.decode(null))
        assertNull(RingingSessionKey.decode("regular:not-a-number:123"))
        assertNull(RingingSessionKey.decode("unknown:7:123"))
        assertNull(RingingSessionKey.decode("regular:-1:123"))
    }
}
