package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RingingSessionStateMachineTest {
    @Test
    fun `duplicate successful snooze schedules exactly once`() {
        val stateMachine = RingingSessionStateMachine().apply { start(session()) }
        var schedules = 0

        val first = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.Scheduled
        }
        val duplicate = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(SnoozeRequestOutcome.Scheduled, first)
        assertEquals(SnoozeRequestOutcome.Rejected, duplicate)
        assertEquals(1, schedules)
    }

    @Test
    fun `cold snooze action is rejected without invoking scheduler`() {
        val stateMachine = RingingSessionStateMachine()
        var schedules = 0

        val outcome = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(SnoozeRequestOutcome.Rejected, outcome)
        assertEquals(0, schedules)
    }

    @Test
    fun `permission failure leaves session active for retry`() {
        val stateMachine = RingingSessionStateMachine().apply { start(session()) }
        var schedules = 0

        val failure = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.ExactAlarmPermissionRequired
        }
        val retry = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(SnoozeRequestOutcome.Failed, failure)
        assertEquals(SnoozeRequestOutcome.Scheduled, retry)
        assertEquals(2, schedules)
    }

    @Test
    fun `scheduler exception leaves session active for retry`() {
        val stateMachine = RingingSessionStateMachine().apply { start(session()) }
        var schedules = 0

        val failure = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            error("scheduler failure")
        }
        val retry = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(SnoozeRequestOutcome.Failed, failure)
        assertEquals(SnoozeRequestOutcome.Scheduled, retry)
        assertEquals(2, schedules)
    }

    @Test
    fun `dismiss consumes active session`() {
        val stateMachine = RingingSessionStateMachine().apply { start(session()) }
        stateMachine.dismiss()

        val outcome = stateMachine.requestSnooze(AlarmRingingService.ACTION_SNOOZE) {
            ScheduleResult.Scheduled
        }

        assertEquals(SnoozeRequestOutcome.Rejected, outcome)
    }

    private fun session() = ActiveRingingSession(
        alarmId = 7L,
        isShiftAlarm = true,
        snoozeEnabled = true,
        snoozeMinutes = 11,
        scheduledOccurrenceAt = Instant.parse("2026-08-25T00:00:00Z"),
    )
}
