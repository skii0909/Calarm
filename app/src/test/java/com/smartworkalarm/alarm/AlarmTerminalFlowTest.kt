package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTerminalFlowTest {
    @Test
    fun `permission failure returns to retry UI then one successful snooze finishes`() {
        val session = session()
        val serviceState = RingingSessionStateMachine().apply { start(session) }
        val activityState = AlarmTerminalActionController(session.key)
        assertTrue(activityState.tryStartRequest(session.key))

        val failed = serviceState.requestSnooze(session.key) {
            ScheduleResult.ExactAlarmPermissionRequired
        }
        assertEquals(
            AlarmTerminalUiEffect.STAY_OPEN,
            activityState.acceptServiceResult(session.key, failed, "권한을 확인해 주세요"),
        )
        assertTrue(activityState.tryStartRequest(session.key))

        val success = serviceState.requestSnooze(session.key) { ScheduleResult.Scheduled }
        assertEquals(
            AlarmTerminalUiEffect.FINISH,
            activityState.acceptServiceResult(session.key, success, null),
        )
    }

    @Test
    fun `scheduler exception returns to retry UI and dismiss can still finish`() {
        val session = session()
        val serviceState = RingingSessionStateMachine().apply { start(session) }
        val activityState = AlarmTerminalActionController(session.key)
        assertTrue(activityState.tryStartRequest(session.key))

        val failed = serviceState.requestSnooze(session.key) { error("scheduler") }
        assertEquals(
            AlarmTerminalUiEffect.STAY_OPEN,
            activityState.acceptServiceResult(session.key, failed, "예약하지 못했습니다"),
        )
        assertTrue(activityState.tryStartRequest(session.key))

        val success = serviceState.dismiss(session.key)
        assertEquals(
            AlarmTerminalUiEffect.FINISH,
            activityState.acceptServiceResult(session.key, success, null),
        )
    }

    private fun session(): ActiveRingingSession = ActiveRingingSession(
        key = RingingSessionKey.create(
            isShiftAlarm = false,
            alarmId = 7L,
            scheduledOccurrenceAt = Instant.parse("2026-08-25T00:00:00Z"),
        ),
        snoozeEnabled = true,
        snoozeMinutes = 11,
    )
}
