package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.*
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase
import com.smartworkalarm.prealarm.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RegularAlarmContinuityTest {
    @Test fun `weekday alarm survives substitute holiday keyword leave and following public holiday`() = runTest {
        val zone = ZoneId.of("Asia/Seoul")
        var saved = Alarm(1, "출근", 7, 0, 31, true, true, skipOnPublicHoliday = true)
        val repository = object : AlarmRepository {
            override fun observeAll() = flowOf(listOf(saved))
            override suspend fun getById(id: Long) = saved
            override suspend fun getEnabled() = listOf(saved)
            override suspend fun upsert(alarm: Alarm): Long { saved = alarm; return alarm.id }
            override suspend fun delete(id: Long) = Unit
            override suspend fun setEnabled(id: Long, enabled: Boolean) { saved = saved.copy(enabled = enabled) }
        }
        val calculator = CalculateNextOccurrenceUseCase()
        var next = calculator.execute(saved, Instant.parse("2026-10-01T15:00:00Z"), zone)!!
        val scheduler = object : AlarmScheduler {
            override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult { next = triggerAt; return ScheduleResult.Scheduled }
            override fun cancel(alarmId: Long) = Unit
            override fun scheduleSnooze(alarmId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
        }
        val source = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) = Result.success(
                if (startInclusive.atZone(zone).toLocalDate() == LocalDate.of(2026, 10, 7))
                    listOf(CalendarEvent("휴가", startInclusive, endExclusive, true)) else emptyList())
        }
        var rings = 0
        val ringer = object : AlarmRinger {
            override fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean,
                snoozeEnabled: Boolean, snoozeMinutes: Int) { rings++ }
        }
        val skips = object : OccurrenceSkipStore {
            override fun mark(key: AlarmOccurrenceKey) = Unit
            override fun consume(key: AlarmOccurrenceKey) = false
            override fun prune(now: Instant) = Unit
        }
        val coordinator = AlarmExecutionCoordinator(repository, scheduler, calculator,
            ShouldRingAlarmUseCase(source), ringer, zone, skips, PreAlarmNotificationCanceller { _, _ -> })
        val outcomes = mutableListOf<Pair<String, ExecutionOutcome>>()
        repeat(7) {
            val due = next
            outcomes += due.atZone(zone).toLocalDate().toString() to coordinator.execute(1, false, due, due)
            assertTrue("An exclusion must never disable the repeating alarm", saved.enabled)
            assertTrue(next > due)
        }
        assertEquals(listOf(
            "2026-10-02" to ExecutionOutcome.Ringing,
            "2026-10-05" to ExecutionOutcome.SkippedForVacation,
            "2026-10-06" to ExecutionOutcome.Ringing,
            "2026-10-07" to ExecutionOutcome.SkippedForVacation,
            "2026-10-08" to ExecutionOutcome.Ringing,
            "2026-10-09" to ExecutionOutcome.SkippedForVacation,
            "2026-10-12" to ExecutionOutcome.Ringing,
        ), outcomes)
        assertEquals(4, rings)
        assertEquals(LocalDate.of(2026, 10, 13), next.atZone(zone).toLocalDate())
    }
}
