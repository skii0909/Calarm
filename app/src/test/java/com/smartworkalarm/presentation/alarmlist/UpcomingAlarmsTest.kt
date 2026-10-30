package com.smartworkalarm.presentation.alarmlist

import com.smartworkalarm.domain.model.*
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.prealarm.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpcomingAlarmsTest {
    @Test fun `fresh previous day Outlook snapshot still excludes known vacation when foreground sync is offline`() = runTest {
        val start = at("2026-10-06", 0)
        val state = com.smartworkalarm.outlook.OutlookState(
            account = com.smartworkalarm.outlook.OutlookAccount("account", "test@example.com"),
            selectedIds = setOf("calendar"), syncedAt = start.minusSeconds(300),
            coverageStart = at("2026-10-04", 0), coverageEnd = at("2026-11-06", 0),
            issue = com.smartworkalarm.outlook.OutlookIssue.NETWORK,
            events = listOf(com.smartworkalarm.outlook.OutlookEvent("calendar", "vacation", "휴가", start, at("2026-10-07", 0))),
        )
        val source = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) =
                com.smartworkalarm.outlook.OutlookSnapshotPolicy.events(state, startInclusive, endExclusive, start, zone)
        }
        val result = useCase(source).execute(listOf(work), emptyList(), start, zone)
        assertFalse(result.calendarReadFailed)
        assertEquals(at("2026-10-07", 7), result.items.first().triggerAt)
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = at("2026-10-05", 0)
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()
    private val work = Alarm(1, "출근", 7, 0, 31, true, true, skipOnPublicHoliday = true)
    private val skips = object : OccurrenceSkipStore {
        var marked: AlarmOccurrenceKey? = null
        override fun mark(key: AlarmOccurrenceKey) { marked = key }
        override fun contains(key: AlarmOccurrenceKey) = marked == key
        override fun consume(key: AlarmOccurrenceKey): Boolean = error("Forecast must never consume a skip")
        override fun prune(now: Instant) = Unit
    }
    private fun useCase(source: CalendarEventSource) = UpcomingAlarmsUseCase(source,
        KoreanPublicHolidayProvider(), EvaluateShiftGroupDayUseCase(ShiftDayEvaluator(), KoreanPublicHolidayProvider()), skips)
    private fun source(events: List<CalendarEvent> = emptyList()) = object : CalendarEventSource {
        override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) = Result.success(events)
    }

    @Test fun `holiday and vacation are excluded while following four occurrences stay ordered`() = runTest {
        val vacation = CalendarEvent("휴가", at("2026-10-07", 0), at("2026-10-08", 0), true)
        val result = useCase(source(listOf(vacation))).execute(listOf(work), emptyList(), now, zone)
        assertEquals(listOf("2026-10-06", "2026-10-08", "2026-10-12", "2026-10-13"),
            result.items.map { it.triggerAt.atZone(zone).toLocalDate().toString() })
        assertFalse(result.calendarReadFailed)
    }

    @Test fun `one time alarm excluded today must not reappear on future days`() = runTest {
        val result = useCase(source()).execute(listOf(work.copy(repeatDaysMask = 0)), emptyList(), now, zone)
        assertTrue(result.items.isEmpty())
    }

    @Test fun `one time and repeating alarms merge chronologically without duplicate one time occurrence`() = runTest {
        val result = useCase(source()).execute(listOf(work.copy(skipOnPublicHoliday = false, skipOnCalendarMatch = false),
            work.copy(id = 2, hour = 6, repeatDaysMask = 0, skipOnPublicHoliday = false, skipOnCalendarMatch = false)), emptyList(), now, zone)
        assertEquals(listOf(2L, 1L, 1L, 1L), result.items.map { it.id })
        assertEquals(at("2026-10-05", 6), result.items.first().triggerAt)
    }

    @Test fun `slow calendar fails open with warning and bounded latency`() = runTest {
        val slow = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> {
                delay(60_000); return Result.success(emptyList())
            }
        }
        val result = useCase(slow).execute(listOf(work), emptyList(), now, zone)
        assertTrue(result.calendarReadFailed)
        assertTrue(result.items.first().calendarWarning)
        assertEquals(at("2026-10-06", 7), result.items.first().triggerAt)
        assertEquals(5_000, testScheduler.currentTime)
    }

    @Test fun `manual occurrence skip is read without consuming it`() = runTest {
        val key = AlarmOccurrenceKey(AlarmKind.REGULAR, 1, at("2026-10-06", 7))
        skips.mark(key)
        val result = useCase(source()).execute(listOf(work), emptyList(), now, zone)
        assertEquals(at("2026-10-07", 7), result.items.first().triggerAt)
        assertTrue(skips.contains(key))
    }

    @Test fun `regular and shift occurrences share one calendar query and one ordered list`() = runTest {
        var reads = 0
        val events = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> {
                reads++
                return Result.success(listOf(CalendarEvent("주간", at("2026-10-06", 0), at("2026-10-07", 0), true)))
            }
        }
        val type = ShiftTypeAlarm(10, 2, "주간", listOf("주간"), 6, 0, 31, true, null, false, true, 5, true)
        val group = ShiftAlarmRepository.GroupWithTypes(ShiftAlarmGroup(2, "교대", true, 10), listOf(type))
        val result = useCase(events).execute(listOf(work), listOf(group), now, zone)
        assertEquals(10L, result.items.first().id)
        assertTrue(result.items.first().isShift)
        assertEquals(1L, result.items[1].id)
        assertEquals(1, reads)
    }
}
