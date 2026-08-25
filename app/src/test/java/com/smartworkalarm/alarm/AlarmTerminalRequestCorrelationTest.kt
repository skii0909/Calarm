package com.smartworkalarm.alarm

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTerminalRequestCorrelationTest {
    @Test
    fun `terminal request ids are unique UUIDs`() {
        val first = newTerminalRequestId()
        val second = newTerminalRequestId()

        UUID.fromString(first)
        UUID.fromString(second)
        assertFalse(first == second)
    }

    @Test
    fun `Activity recreation preserves in-flight request and prevents duplicate submission`() {
        val key = key("attempt-a")
        val original = AlarmTerminalActionController(key)
        assertTrue(original.tryStartRequest(key, "request-1"))

        val recreated = AlarmTerminalActionController(original.state)

        assertEquals("request-1", recreated.state.inFlightRequestId)
        assertFalse(recreated.state.actionsEnabled)
        assertFalse(recreated.tryStartRequest(key, "request-2"))
    }

    @Test
    fun `saved in-flight request restores only for the same ringing attempt`() {
        val first = key("attempt-a")
        val replacement = key("attempt-b")

        val restored = restoreAlarmTerminalUiState(
            initialSessionKey = first,
            savedSessionKey = first.encode(),
            savedInFlightRequestId = "request-1",
            savedPersistentError = null,
        )
        val rejectedStaleState = restoreAlarmTerminalUiState(
            initialSessionKey = replacement,
            savedSessionKey = first.encode(),
            savedInFlightRequestId = "request-1",
            savedPersistentError = "stale",
        )

        assertEquals("request-1", restored.inFlightRequestId)
        assertFalse(restored.actionsEnabled)
        assertNull(rejectedStaleState.inFlightRequestId)
        assertNull(rejectedStaleState.persistentError)
        assertTrue(rejectedStaleState.actionsEnabled)
    }

    @Test
    fun `result store preserves results by request id without overwrite`() {
        val key = key("attempt-a")
        val store = AlarmTerminalResultStore()
        val success = result("request-1", key, TerminalActionResult.SUCCESS)
        val rejection = result("request-2", key, TerminalActionResult.REJECTED)

        store.publish(success)
        store.publish(rejection)

        assertEquals(success, store.take("request-1"))
        assertEquals(rejection, store.take("request-2"))
    }

    @Test
    fun `duplicate result for one request cannot overwrite its first success`() {
        val key = key("attempt-a")
        val store = AlarmTerminalResultStore()
        val success = result("request-1", key, TerminalActionResult.SUCCESS)

        store.publish(success)
        store.publish(result("request-1", key, TerminalActionResult.REJECTED))

        assertEquals(success, store.take("request-1"))
    }

    @Test
    fun `success broadcast finishes its request even when another rejection is pending`() {
        val key = key("attempt-a")
        val controller = AlarmTerminalActionController(key)
        val store = AlarmTerminalResultStore()
        val router = AlarmTerminalResultRouter(controller, store)
        val success = result("request-1", key, TerminalActionResult.SUCCESS)
        val duplicateRejection = result("request-2", key, TerminalActionResult.REJECTED)
        assertTrue(controller.tryStartRequest(key, "request-1"))
        store.publish(success)
        store.publish(duplicateRejection)

        assertEquals(AlarmTerminalUiEffect.FINISH, router.acceptBroadcast(success))
        assertEquals(duplicateRejection, store.take("request-2"))
        assertNull(controller.state.inFlightRequestId)
        assertEquals(
            AlarmTerminalUiEffect.IGNORE,
            router.acceptBroadcast(result("request-1", key, TerminalActionResult.REJECTED)),
        )
    }

    @Test
    fun `Activity receiver does not consume another request broadcast result`() {
        val key = key("attempt-a")
        val controller = AlarmTerminalActionController(key)
        val store = AlarmTerminalResultStore()
        val router = AlarmTerminalResultRouter(controller, store)
        val other = result("request-2", key, TerminalActionResult.REJECTED)
        assertTrue(controller.tryStartRequest(key, "request-1"))
        store.publish(other)

        assertEquals(AlarmTerminalUiEffect.IGNORE, router.acceptBroadcast(other))
        assertEquals(other, store.take("request-2"))
    }

    @Test
    fun `recreated Activity consumes only its stored in-flight request result`() {
        val key = key("attempt-a")
        val controller = AlarmTerminalActionController(
            AlarmTerminalUiState(sessionKey = key, inFlightRequestId = "request-1"),
        )
        val store = AlarmTerminalResultStore()
        val router = AlarmTerminalResultRouter(controller, store)
        store.publish(result("request-2", key, TerminalActionResult.REJECTED))
        store.publish(result("request-1", key, TerminalActionResult.SUCCESS))

        assertEquals(AlarmTerminalUiEffect.FINISH, router.acceptStoredResult())
        assertEquals(
            TerminalActionResult.REJECTED,
            store.take("request-2")?.result,
        )
    }

    private fun key(token: String) = RingingSessionKey.create(
        isShiftAlarm = false,
        alarmId = 7L,
        attemptToken = token,
    )

    private fun result(
        requestId: String,
        key: RingingSessionKey,
        result: TerminalActionResult,
    ) = AlarmTerminalServiceResult(
        requestId = requestId,
        sessionKey = key,
        result = result,
        message = null,
    )
}
