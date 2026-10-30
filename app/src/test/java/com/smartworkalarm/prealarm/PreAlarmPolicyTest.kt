package com.smartworkalarm.prealarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreAlarmPolicyTest {
    private val now = Instant.parse("2026-08-26T00:00:00Z")

    @Test
    fun `occurrence more than thirty minutes away triggers thirty minutes before`() {
        assertEquals(
            Instant.parse("2026-08-26T00:15:00Z"),
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:45:00Z")),
        )
    }

    @Test
    fun `occurrence exactly thirty minutes away triggers now`() {
        assertEquals(
            now,
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:30:00Z")),
        )
    }

    @Test
    fun `occurrence less than thirty minutes away triggers immediately`() {
        assertEquals(
            now,
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:29:59Z")),
        )
    }

    @Test
    fun `occurrence that already passed has no pre-alarm`() {
        assertNull(preAlarmTriggerAt(now, Instant.parse("2026-08-25T23:59:59Z")))
    }
}
