package com.smartworkalarm.alarm

import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Test

class RingingSessionStateMachineTest {
    @Test
    fun `duplicate successful snooze schedules exactly once`() {
        val active = session(key(7L))
        val stateMachine = RingingSessionStateMachine().apply { start(active) }
        var schedules = 0

        val first = stateMachine.requestSnooze(active.key) {
            schedules++
            ScheduleResult.Scheduled
        }
        val duplicate = stateMachine.requestSnooze(active.key) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(TerminalActionResult.SUCCESS, first)
        assertEquals(TerminalActionResult.REJECTED, duplicate)
        assertEquals(1, schedules)
    }

    @Test
    fun `cold snooze and dismiss are rejected`() {
        val stateMachine = RingingSessionStateMachine()
        var schedules = 0

        val snooze = stateMachine.requestSnooze(key(7L)) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(TerminalActionResult.REJECTED, snooze)
        assertEquals(TerminalActionResult.REJECTED, stateMachine.dismiss(key(7L)))
        assertEquals(0, schedules)
    }

    @Test
    fun `permission failure leaves matching session active for retry`() {
        val active = session(key(7L))
        val stateMachine = RingingSessionStateMachine().apply { start(active) }
        var schedules = 0

        val failure = stateMachine.requestSnooze(active.key) {
            schedules++
            ScheduleResult.ExactAlarmPermissionRequired
        }
        val retry = stateMachine.requestSnooze(active.key) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(TerminalActionResult.FAILED, failure)
        assertEquals(TerminalActionResult.SUCCESS, retry)
        assertEquals(2, schedules)
    }

    @Test
    fun `scheduler exception leaves matching session active for retry`() {
        val active = session(key(7L))
        val stateMachine = RingingSessionStateMachine().apply { start(active) }
        var schedules = 0

        val failure = stateMachine.requestSnooze(active.key) {
            schedules++
            error("scheduler failure")
        }
        val retry = stateMachine.requestSnooze(active.key) {
            schedules++
            ScheduleResult.Scheduled
        }

        assertEquals(TerminalActionResult.FAILED, failure)
        assertEquals(TerminalActionResult.SUCCESS, retry)
        assertEquals(2, schedules)
    }

    @Test
    fun `stale actions for A cannot affect replacement session B`() {
        val first = session(key(7L))
        val second = session(key(8L))
        val stateMachine = RingingSessionStateMachine().apply {
            start(first)
            start(second)
        }
        var schedules = 0

        val staleSnooze = stateMachine.requestSnooze(first.key) {
            schedules++
            ScheduleResult.Scheduled
        }
        val staleDismiss = stateMachine.dismiss(first.key)
        val matchingDismiss = stateMachine.dismiss(second.key)

        assertEquals(TerminalActionResult.REJECTED, staleSnooze)
        assertEquals(TerminalActionResult.REJECTED, staleDismiss)
        assertEquals(TerminalActionResult.SUCCESS, matchingDismiss)
        assertEquals(0, schedules)
    }

    @Test
    fun `matching dismiss is consumed once`() {
        val active = session(key(7L))
        val stateMachine = RingingSessionStateMachine().apply { start(active) }

        assertEquals(TerminalActionResult.SUCCESS, stateMachine.dismiss(active.key))
        assertEquals(TerminalActionResult.REJECTED, stateMachine.dismiss(active.key))
    }

    @Test
    fun `concurrent duplicate dismiss accepts exactly one request`() {
        val active = session(key(7L))
        val stateMachine = RingingSessionStateMachine().apply { start(active) }
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<TerminalActionResult>())
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) {
            executor.submit {
                start.await()
                results += stateMachine.dismiss(active.key)
            }
        }
        start.countDown()
        executor.shutdown()
        while (!executor.isTerminated) Thread.yield()

        assertEquals(1, results.count { it == TerminalActionResult.SUCCESS })
        assertEquals(7, results.count { it == TerminalActionResult.REJECTED })
    }

    private fun key(alarmId: Long) = RingingSessionKey.create(
        isShiftAlarm = true,
        alarmId = alarmId,
        attemptToken = "attempt-$alarmId",
    )

    private fun session(key: RingingSessionKey) = ActiveRingingSession(
        key = key,
        snoozeEnabled = true,
        snoozeMinutes = 11,
        originalScheduledOccurrenceAt = Instant.parse("2026-08-25T00:00:00Z"),
    )
}
