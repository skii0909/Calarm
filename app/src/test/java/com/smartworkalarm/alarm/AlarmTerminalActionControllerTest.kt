package com.smartworkalarm.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTerminalActionControllerTest {
    @Test
    fun `failed service result keeps screen open shows error and enables retry`() {
        val key = key(7L)
        val controller = AlarmTerminalActionController(key)
        assertTrue(controller.tryStartRequest(key))
        assertFalse(controller.state.actionsEnabled)

        val effect = controller.acceptServiceResult(
            key,
            TerminalActionResult.FAILED,
            "다시 울림 예약 실패",
        )

        assertEquals(AlarmTerminalUiEffect.STAY_OPEN, effect)
        assertTrue(controller.state.actionsEnabled)
        assertEquals("다시 울림 예약 실패", controller.state.persistentError)
        assertTrue(controller.tryStartRequest(key))
    }

    @Test
    fun `rejected result and synchronous dispatch failure both release request gate`() {
        val key = key(7L)
        val controller = AlarmTerminalActionController(key)
        assertTrue(controller.tryStartRequest(key))

        controller.acceptServiceResult(key, TerminalActionResult.REJECTED, "요청 거부")
        assertTrue(controller.tryStartRequest(key))

        controller.acceptDispatchFailure(key, "서비스 요청 실패")
        assertTrue(controller.state.actionsEnabled)
        assertEquals("서비스 요청 실패", controller.state.persistentError)
        assertTrue(controller.tryStartRequest(key))
    }

    @Test
    fun `only matching success asks Activity to finish`() {
        val first = key(7L)
        val second = key(8L)
        val controller = AlarmTerminalActionController(first)
        assertTrue(controller.tryStartRequest(first))

        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            controller.acceptServiceResult(second, TerminalActionResult.SUCCESS, null),
        )
        assertEquals(
            AlarmTerminalUiEffect.FINISH,
            controller.acceptServiceResult(first, TerminalActionResult.SUCCESS, null),
        )
    }

    @Test
    fun `replacing session resets interaction state and rejects old requests and results`() {
        val first = key(7L)
        val second = key(8L)
        val controller = AlarmTerminalActionController(first)
        assertTrue(controller.tryStartRequest(first))

        controller.replaceSession(second)

        assertEquals(second, controller.state.sessionKey)
        assertTrue(controller.state.actionsEnabled)
        assertNull(controller.state.persistentError)
        assertFalse(controller.tryStartRequest(first))
        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            controller.acceptServiceResult(first, TerminalActionResult.FAILED, "stale"),
        )
        assertTrue(controller.tryStartRequest(second))
    }

    private fun key(id: Long) = RingingSessionKey.create(
        isShiftAlarm = false,
        alarmId = id,
        scheduledOccurrenceAt = Instant.parse("2026-08-25T00:00:00Z"),
    )
}
