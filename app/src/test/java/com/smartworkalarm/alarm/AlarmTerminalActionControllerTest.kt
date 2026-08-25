package com.smartworkalarm.alarm

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
        assertTrue(controller.tryStartRequest(key, "request-1"))
        assertFalse(controller.state.actionsEnabled)

        val effect = controller.acceptServiceResult(
            result("request-1", key, TerminalActionResult.FAILED, "다시 울림 예약 실패"),
        )

        assertEquals(AlarmTerminalUiEffect.STAY_OPEN, effect)
        assertTrue(controller.state.actionsEnabled)
        assertEquals("다시 울림 예약 실패", controller.state.persistentError)
        assertTrue(controller.tryStartRequest(key, "request-2"))
    }

    @Test
    fun `rejected result and synchronous dispatch failure release only matching request`() {
        val key = key(7L)
        val controller = AlarmTerminalActionController(key)
        assertTrue(controller.tryStartRequest(key, "request-1"))

        controller.acceptServiceResult(
            result("request-1", key, TerminalActionResult.REJECTED, "요청 거부"),
        )
        assertTrue(controller.tryStartRequest(key, "request-2"))

        controller.acceptDispatchFailure(key, "request-2", "서비스 요청 실패")
        assertTrue(controller.state.actionsEnabled)
        assertEquals("서비스 요청 실패", controller.state.persistentError)
        assertTrue(controller.tryStartRequest(key, "request-3"))
    }

    @Test
    fun `only matching session and request success asks Activity to finish`() {
        val first = key(7L)
        val second = key(8L)
        val controller = AlarmTerminalActionController(first)
        assertTrue(controller.tryStartRequest(first, "request-1"))

        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            controller.acceptServiceResult(
                result("request-1", second, TerminalActionResult.SUCCESS),
            ),
        )
        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            controller.acceptServiceResult(
                result("request-other", first, TerminalActionResult.SUCCESS),
            ),
        )
        assertEquals(
            AlarmTerminalUiEffect.FINISH,
            controller.acceptServiceResult(
                result("request-1", first, TerminalActionResult.SUCCESS),
            ),
        )
    }

    @Test
    fun `replacing session resets interaction state and rejects old requests and results`() {
        val first = key(7L)
        val second = key(8L)
        val controller = AlarmTerminalActionController(first)
        assertTrue(controller.tryStartRequest(first, "request-1"))

        controller.replaceSession(second)

        assertEquals(second, controller.state.sessionKey)
        assertTrue(controller.state.actionsEnabled)
        assertNull(controller.state.persistentError)
        assertFalse(controller.tryStartRequest(first, "request-2"))
        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            controller.acceptServiceResult(
                result("request-1", first, TerminalActionResult.FAILED, "stale"),
            ),
        )
        assertTrue(controller.tryStartRequest(second, "request-3"))
    }

    private fun key(id: Long) = RingingSessionKey.create(
        isShiftAlarm = false,
        alarmId = id,
        attemptToken = "attempt-$id",
    )

    private fun result(
        requestId: String,
        key: RingingSessionKey,
        result: TerminalActionResult,
        message: String? = null,
    ) = AlarmTerminalServiceResult(requestId, key, result, message)
}
