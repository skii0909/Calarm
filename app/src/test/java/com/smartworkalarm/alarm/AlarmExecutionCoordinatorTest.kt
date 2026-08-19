package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmExecutionCoordinatorTest {
    private val now = Instant.parse("2026-08-19T20:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `next repeating occurrence is scheduled before vacation occurrence is skipped`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            decision = AlarmExecutionDecision.SKIP_FOR_VACATION,
            onSchedule = { events += "schedule" },
            onRing = { events += "ring" },
        )

        assertEquals(ExecutionOutcome.SkippedForVacation, coordinator.execute(7, false, now))
        assertEquals(listOf("schedule"), events)
    }

    @Test
    fun `normal decision starts ringing after scheduling next occurrence`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            decision = AlarmExecutionDecision.RING,
            onSchedule = { events += "schedule" },
            onRing = { events += "ring" },
        )

        assertEquals(ExecutionOutcome.Ringing, coordinator.execute(7, false, now))
        assertEquals(listOf("schedule", "ring"), events)
    }

    @Test
    fun `snooze delivery does not schedule another repeating occurrence`() = runTest {
        var schedules = 0
        val coordinator = coordinator(
            decision = AlarmExecutionDecision.RING,
            onSchedule = { schedules++ },
            onRing = {},
        )

        coordinator.execute(7, true, now)

        assertEquals(0, schedules)
    }

    @Test
    fun `missing alarm is ignored`() = runTest {
        val coordinator = coordinator(alarm = null)

        assertEquals(ExecutionOutcome.Ignored, coordinator.execute(7, false, now))
    }

    private fun coordinator(
        alarm: Alarm? = Alarm(7, "출근", 7, 0, 31, true, true),
        decision: AlarmExecutionDecision = AlarmExecutionDecision.RING,
        onSchedule: () -> Unit = {},
        onRing: () -> Unit = {},
    ) = AlarmExecutionCoordinator(
        repository = Repository(alarm),
        scheduler = Scheduler(onSchedule),
        calculator = CalculateNextOccurrenceUseCase(),
        decisionProvider = { _, _, _ -> decision },
        ringer = object : AlarmRinger {
            override fun start(alarmId: Long, alarmName: String) = onRing()
        },
        zoneId = zone,
    )

    private class Repository(private val alarm: Alarm?) : AlarmRepository {
        override fun observeAll(): Flow<List<Alarm>> = flowOf(listOfNotNull(alarm))
        override suspend fun getById(id: Long): Alarm? = alarm
        override suspend fun getEnabled(): List<Alarm> = listOfNotNull(alarm)
        override suspend fun upsert(alarm: Alarm): Long = alarm.id
        override suspend fun delete(id: Long) = Unit
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
    }

    private class Scheduler(private val onSchedule: () -> Unit) : AlarmScheduler {
        override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult {
            onSchedule()
            return ScheduleResult.Scheduled
        }
        override fun cancel(alarmId: Long) = Unit
        override fun scheduleSnooze(alarmId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
    }
}
