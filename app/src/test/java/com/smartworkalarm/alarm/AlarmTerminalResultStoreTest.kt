package com.smartworkalarm.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTerminalResultStoreTest {
    @Test
    fun `matching Activity recreation consumes a service result exactly once`() {
        val key = key(7L)
        val store = AlarmTerminalResultStore()
        val result = AlarmTerminalServiceResult(
            sessionKey = key,
            result = TerminalActionResult.SUCCESS,
            message = null,
        )

        store.publish(result)

        assertEquals(result, store.take(key))
        assertNull(store.take(key))
    }

    @Test
    fun `stale Activity cannot consume the replacement session result`() {
        val first = key(7L)
        val second = key(8L)
        val store = AlarmTerminalResultStore()
        val result = AlarmTerminalServiceResult(
            sessionKey = second,
            result = TerminalActionResult.FAILED,
            message = "예약 실패",
        )
        store.publish(result)

        assertNull(store.take(first))
        assertEquals(result, store.take(second))
    }

    @Test
    fun `starting a new ringing session clears an obsolete pending result`() {
        val key = key(7L)
        val store = AlarmTerminalResultStore()
        store.publish(
            AlarmTerminalServiceResult(key, TerminalActionResult.REJECTED, "거절"),
        )

        store.clear()

        assertNull(store.take(key))
    }

    private fun key(id: Long) = RingingSessionKey.create(
        isShiftAlarm = false,
        alarmId = id,
        scheduledOccurrenceAt = Instant.parse("2026-08-25T00:00:00Z"),
    )
}
