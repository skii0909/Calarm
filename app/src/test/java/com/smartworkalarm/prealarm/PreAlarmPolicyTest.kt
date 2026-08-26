package com.smartworkalarm.prealarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreAlarmPolicyTest {
    private val now = Instant.parse("2026-08-26T00:00:00Z")

    @Test
    fun `occurrence more than ten minutes away triggers ten minutes before`() {
        assertEquals(
            Instant.parse("2026-08-26T00:05:00Z"),
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:15:00Z")),
        )
    }

    @Test
    fun `occurrence exactly ten minutes away triggers now`() {
        assertEquals(
            now,
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:10:00Z")),
        )
    }

    @Test
    fun `occurrence less than ten minutes away triggers immediately`() {
        assertEquals(
            now,
            preAlarmTriggerAt(now, Instant.parse("2026-08-26T00:09:59Z")),
        )
    }

    @Test
    fun `occurrence that already passed has no pre-alarm`() {
        assertNull(preAlarmTriggerAt(now, Instant.parse("2026-08-25T23:59:59Z")))
    }
}
