package com.smartworkalarm.preview

import com.smartworkalarm.data.local.AlarmPreviewDao
import com.smartworkalarm.data.local.AlarmPreviewEntity
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.PreviewChangeType
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.usecase.CompareAlarmPreviewStateUseCase
import java.time.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PreviewReliabilityTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-10-04T13:00:00Z"), zone)
    private val states = mutableMapOf<Long, AlarmPreviewEntity>()
    private val dao = object : AlarmPreviewDao {
        override suspend fun get(alarmId: Long) = states[alarmId]
        override suspend fun upsert(entity: AlarmPreviewEntity) { states[entity.alarmId] = entity }
        override suspend fun delete(alarmId: Long) { states.remove(alarmId) }
    }
    private fun alarm(id: Long = 1) = Alarm(id = id, name = "출근", hour = 7, minute = 0,
        repeatDaysMask = 127, enabled = true, skipOnPublicHoliday = true, skipOnCalendarMatch = false)
    private fun evaluator(vararg alarms: Alarm, skips: ActualAlarmSkipStore = NoActualAlarmSkipStore): EvaluateNextDayAlarmsUseCase {
        val repo = object : AlarmRepository {
            override fun observeAll() = flowOf(alarms.toList())
            override suspend fun getById(id: Long) = alarms.find { it.id == id }
            override suspend fun getEnabled() = alarms.toList()
            override suspend fun upsert(alarm: Alarm) = alarm.id
            override suspend fun delete(id: Long) = Unit
            override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
        }
        val events = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> =
                Result.failure(IllegalStateException("Calendar unavailable"))
        }
        return EvaluateNextDayAlarmsUseCase(repo, events, dao, CompareAlarmPreviewStateUseCase(), clock, actualSkips = skips)
    }
    @Test fun `actual holiday skip resumes once even when evening holiday preview was missed`() = runTest {
        states[1] = AlarmPreviewEntity(1, "RING", "2026-10-05", 0)
        val skips = object : ActualAlarmSkipStore {
            override fun recordSkipped(alarmId: Long, date: LocalDate) = Unit
            override fun latestSkippedDate(alarmId: Long) = LocalDate.of(2026, 10, 5)
            override fun delete(alarmId: Long) = Unit
        }
        val evaluator = evaluator(alarm().copy(skipOnPublicHoliday = false), skips = skips)
        val date = LocalDate.of(2026, 10, 6)
        val result = evaluator.executeWithStatus(date, zone)
        assertEquals(PreviewChangeType.RESUMED, result.changes.single().type)
        evaluator.acknowledge(result)
        assertTrue(evaluator.executeWithStatus(date, zone).changes.isEmpty())
    }
    @Test fun `actual skip does not consume an undelivered holiday start notice`() = runTest {
        states[1] = AlarmPreviewEntity(1, "RING", "2026-10-03", 0)
        val skips = object : ActualAlarmSkipStore {
            override fun recordSkipped(alarmId: Long, date: LocalDate) = Unit
            override fun latestSkippedDate(alarmId: Long) = LocalDate.of(2026, 10, 3)
            override fun delete(alarmId: Long) = Unit
        }
        val result = evaluator(alarm(), skips = skips).executeWithStatus(LocalDate.of(2026, 10, 5), zone)
        assertEquals(PreviewChangeType.SKIPPED, result.changes.single().type)
    }
    @Test fun `known holiday still reports skip with unavailable calendar`() = runTest {
        val result = evaluator(alarm()).executeWithStatus(LocalDate.of(2026, 10, 5), zone)
        assertEquals(PreviewChangeType.SKIPPED, result.changes.single().type)
        assertFalse(result.calendarReadFailed)
    }
    @Test fun `unaffected alarm resumes despite another alarm needing failed calendar`() = runTest {
        states[2] = AlarmPreviewEntity(2, "SKIP", "2026-10-05", 0)
        val result = evaluator(alarm(), alarm(2).copy(skipOnPublicHoliday = false))
            .executeWithStatus(LocalDate.of(2026, 10, 6), zone)
        assertTrue(result.calendarReadFailed)
        assertEquals(2L, result.changes.single().alarm.id)
        assertEquals(PreviewChangeType.RESUMED, result.changes.single().type)
    }
    @Test fun `evaluating without delivery does not consume change`() = runTest {
        val evaluator = evaluator(alarm())
        val date = LocalDate.of(2026, 10, 5)
        val first = evaluator.executeWithStatus(date, zone)
        assertEquals(1, first.changes.size)
        assertEquals(first.changes, evaluator.executeWithStatus(date, zone).changes)
        assertNull(states[1])
    }
}
