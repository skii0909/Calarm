package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTerminalResultStoreTest {
    @Test
    fun `matching Activity recreation consumes a service result exactly once`() {
        val key = key(7L)
        val store = AlarmTerminalResultStore()
        val result = AlarmTerminalServiceResult(
            requestId = "request-1",
            sessionKey = key,
            result = TerminalActionResult.SUCCESS,
            message = null,
        )

        store.publish(result)

        assertEquals(result, store.take("request-1"))
        assertNull(store.take("request-1"))
    }

    @Test
    fun `stale Activity cannot consume the replacement session result`() {
        val first = key(7L)
        val second = key(8L)
        val store = AlarmTerminalResultStore()
        val result = AlarmTerminalServiceResult(
            requestId = "request-2",
            sessionKey = second,
            result = TerminalActionResult.FAILED,
            message = "예약 실패",
        )
        store.publish(result)

        assertNull(store.take("request-1"))
        assertEquals(result, store.take("request-2"))
    }

    @Test
    fun `starting a new ringing session clears an obsolete pending result`() {
        val key = key(7L)
        val store = AlarmTerminalResultStore()
        store.publish(
            AlarmTerminalServiceResult(
                "request-1",
                key,
                TerminalActionResult.REJECTED,
                "거절",
            ),
        )

        store.clear()

        assertNull(store.take("request-1"))
    }

    private fun key(id: Long) = RingingSessionKey.create(
        isShiftAlarm = false,
        alarmId = id,
        attemptToken = "attempt-$id",
    )
}
