package com.smartworkalarm.prealarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmOccurrenceKeyTest {
    @Test
    fun `encoding separates regular and shift alarms with the same id and occurrence`() {
        val scheduledAt = Instant.parse("2026-08-26T22:00:00Z")

        val regular = AlarmOccurrenceKey(AlarmKind.REGULAR, 7L, scheduledAt)
        val shift = AlarmOccurrenceKey(AlarmKind.SHIFT, 7L, scheduledAt)

        assertNotEquals(regular.encode(), shift.encode())
    }

    @Test
    fun `decoding an encoded occurrence restores all identity fields`() {
        val key = AlarmOccurrenceKey(
            kind = AlarmKind.SHIFT,
            id = 42L,
            scheduledAt = Instant.parse("2026-08-26T22:00:00.123Z"),
        )

        assertEquals(key, AlarmOccurrenceKey.decode(key.encode()))
    }

    @Test
    fun `decoding malformed occurrence returns null`() {
        listOf("", "v1:REGULAR:1", "v2:REGULAR:1:0", "v1:UNKNOWN:1:0", "v1:REGULAR:id:0", "v1:REGULAR:1:time", "v1:REGULAR:1:0:extra")
            .forEach { encoded ->
                assertNull(AlarmOccurrenceKey.decode(encoded))
            }
    }

    @Test
    fun `different scheduled times identify different occurrences of the same alarm`() {
        val first = AlarmOccurrenceKey(AlarmKind.REGULAR, 7L, Instant.parse("2026-08-26T22:00:00Z"))
        val next = AlarmOccurrenceKey(AlarmKind.REGULAR, 7L, Instant.parse("2026-08-27T22:00:00Z"))

        assertNotEquals(first, next)
        assertNotEquals(first.encode(), next.encode())
    }
}
