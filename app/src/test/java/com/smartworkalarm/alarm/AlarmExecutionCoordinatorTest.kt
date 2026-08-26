package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.prealarm.AlarmKind
import com.smartworkalarm.prealarm.AlarmOccurrenceKey
import com.smartworkalarm.prealarm.OccurrenceSkipStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            onRing = { _, _ -> events += "ring" },
        )

        assertEquals(ExecutionOutcome.SkippedForVacation, coordinator.execute(7, false, now))
        assertEquals(listOf("schedule"), events)
    }

    @Test
    fun `normal decision starts ringing after scheduling next occurrence`() = runTest {
        val events = mutableListOf<String>()
        var ringingUri: String? = null
        val coordinator = coordinator(
            alarm = Alarm(7, "출근", 7, 0, 31, true, true, ringtoneUri = "content://alarm/custom"),
            decision = AlarmExecutionDecision.RING,
            onSchedule = { events += "schedule" },
            onRing = { uri, _ ->
                events += "ring"
                ringingUri = uri
            },
        )

        assertEquals(ExecutionOutcome.Ringing, coordinator.execute(7, false, now))
        assertEquals(listOf("schedule", "ring"), events)
        assertEquals("content://alarm/custom", ringingUri)
    }

    @Test
    fun `snooze delivery does not schedule another repeating occurrence`() = runTest {
        var schedules = 0
        val coordinator = coordinator(
            decision = AlarmExecutionDecision.RING,
            onSchedule = { schedules++ },
            onRing = { _, _ -> },
        )

        coordinator.execute(7, true, now)

        assertEquals(0, schedules)
    }

    @Test
    fun `coordinator carries disabled snooze to ringer`() = runTest {
        var startedSnoozeEnabled = true
        val coordinator = coordinator(
            alarm = Alarm(7, "출근", 7, 0, 31, true, true).copy(snoozeEnabled = false),
            onRing = { _, snoozeEnabled -> startedSnoozeEnabled = snoozeEnabled },
        )

        coordinator.execute(7, false, now)

        assertFalse(startedSnoozeEnabled)
    }

    @Test
    fun `regular ringing carries scheduled occurrence into session start`() = runTest {
        var startedOccurrence: Instant? = null
        val coordinator = coordinator(onRingAt = { startedOccurrence = it })
        val scheduledOccurrence = now.minusSeconds(37L)

        coordinator.execute(7, false, now, scheduledOccurrence)

        assertEquals(scheduledOccurrence, startedOccurrence)
    }

    @Test
    fun `marked regular occurrence is consumed after scheduling its repeat and the following occurrence rings`() = runTest {
        val events = mutableListOf<String>()
        val skips = SkipStore()
        val scheduledOccurrence = now.minusSeconds(37L)
        val followingOccurrence = scheduledOccurrence.plusSeconds(24 * 60 * 60L)
        val key = AlarmOccurrenceKey(AlarmKind.REGULAR, 7, scheduledOccurrence)
        skips.mark(key)
        val coordinator = coordinator(
            skipStore = skips,
            onSchedule = { events += "schedule" },
            onDecision = { events += "evaluate" },
            onRing = { _, _ -> events += "ring" },
        )

        assertEquals(
            ExecutionOutcome.SkippedByUser,
            coordinator.execute(7, false, now, scheduledOccurrence),
        )
        assertEquals(listOf("schedule"), events)
        assertFalse(skips.consume(key))
        assertEquals(
            ExecutionOutcome.Ringing,
            coordinator.execute(7, false, now.plusSeconds(60L), followingOccurrence),
        )
        assertEquals(listOf("schedule", "schedule", "evaluate", "ring"), events)
    }

    @Test
    fun `snooze delivery does not consume a marked regular occurrence`() = runTest {
        val skips = SkipStore()
        val scheduledOccurrence = now.minusSeconds(37L)
        val key = AlarmOccurrenceKey(AlarmKind.REGULAR, 7, scheduledOccurrence)
        skips.mark(key)
        val coordinator = coordinator(skipStore = skips)

        assertEquals(ExecutionOutcome.Ringing, coordinator.execute(7, true, now, scheduledOccurrence))
        assertEquals(ExecutionOutcome.SkippedByUser, coordinator.execute(7, false, now, scheduledOccurrence))
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
        onDecision: () -> Unit = {},
        onRing: (String?, Boolean) -> Unit = { _, _ -> },
        onRingAt: (Instant) -> Unit = {},
        skipStore: OccurrenceSkipStore = SkipStore(),
    ) = AlarmExecutionCoordinator(
        repository = Repository(alarm),
        scheduler = Scheduler(onSchedule),
        calculator = CalculateNextOccurrenceUseCase(),
        decisionProvider = { _, _, _ ->
            onDecision()
            decision
        },
        ringer = object : AlarmRinger {
            override fun start(
                alarmId: Long,
                alarmName: String,
                ringtoneUri: String?,
                gradualVolume: Boolean,
                snoozeEnabled: Boolean,
                snoozeMinutes: Int,
            ) = onRing(ringtoneUri, snoozeEnabled)

            override fun startAt(
                alarmId: Long,
                alarmName: String,
                ringtoneUri: String?,
                gradualVolume: Boolean,
                snoozeEnabled: Boolean,
                snoozeMinutes: Int,
                scheduledOccurrenceAt: Instant,
            ) {
                onRing(ringtoneUri, snoozeEnabled)
                onRingAt(scheduledOccurrenceAt)
            }
        },
        zoneId = zone,
        skipStore = skipStore,
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

    private class SkipStore : OccurrenceSkipStore {
        private val keys = mutableSetOf<AlarmOccurrenceKey>()

        override fun mark(key: AlarmOccurrenceKey) {
            keys += key
        }

        override fun consume(key: AlarmOccurrenceKey): Boolean = keys.remove(key)

        override fun prune(now: Instant) = Unit
    }
}
