package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingingAttemptIdentityTest {
    @Test
    fun `attempt token separates ringing attempts for the same shift occurrence`() {
        val first = key("attempt-a")
        val snoozed = key("attempt-b")

        assertNotEquals(first, snoozed)
        assertEquals(first, RingingSessionKey.decode(first.encode()))
        assertEquals(snoozed, RingingSessionKey.decode(snoozed.encode()))
    }

    @Test
    fun `legacy key cannot be reconstructed by an action but service start assigns a token`() {
        assertNull(RingingSessionKey.decode("shift:7:1787586900000"))

        val resolved = resolveStartSessionKey(
            suppliedKey = null,
            isShiftAlarm = true,
            alarmId = 7L,
            nextAttemptToken = { "service-generated" },
        )

        assertEquals(key("service-generated"), resolved)
    }

    @Test
    fun `ringer attempt token factory creates UUID tokens for separate starts`() {
        val factory = RingingAttemptTokenFactory()
        val first = factory.next()
        val second = factory.next()

        UUID.fromString(first)
        UUID.fromString(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `delayed action from consumed shift attempt cannot consume snoozed attempt`() {
        val originalOccurrence = Instant.parse("2026-08-24T14:55:00Z")
        val first = session(key("attempt-a"), originalOccurrence)
        val snoozed = session(key("attempt-b"), originalOccurrence)
        val state = RingingSessionStateMachine().apply { start(first) }

        assertEquals(
            TerminalActionResult.SUCCESS,
            state.requestSnooze(first.key) { ScheduleResult.Scheduled },
        )
        state.start(snoozed)

        assertEquals(TerminalActionResult.REJECTED, state.dismiss(first.key))
        assertEquals(TerminalActionResult.SUCCESS, state.dismiss(snoozed.key))
        assertEquals(originalOccurrence, snoozed.originalScheduledOccurrenceAt)
    }

    private fun key(attemptToken: String) = RingingSessionKey.create(
        isShiftAlarm = true,
        alarmId = 7L,
        attemptToken = attemptToken,
    )

    private fun session(
        key: RingingSessionKey,
        originalOccurrence: Instant,
    ) = ActiveRingingSession(
        key = key,
        snoozeEnabled = true,
        snoozeMinutes = 5,
        originalScheduledOccurrenceAt = originalOccurrence,
    )
}
