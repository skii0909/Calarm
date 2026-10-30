package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarEventSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShouldRingAlarmUseCaseTest {
    @Test
    fun `same calendar title gives different decisions per alarm match mode`() = runTest {
        val event = CalendarEvent("오전 휴가", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), true)
        val evaluator = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
        assertEquals(AlarmExecutionDecision.RING,
            evaluator.execute(alarm().copy(calendarKeywordExactMatch = true), date, zone))
        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION,
            evaluator.execute(alarm().copy(calendarKeywordExactMatch = false), date, zone))
    }
    private val date = LocalDate.of(2026, 8, 20)
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `vacation event skips opted in alarm`() = runTest {
        val event = CalendarEvent("여름휴가", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), true)
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
            .execute(alarm(), date, zone)

        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION, decision)
    }

    @Test
    fun `calendar failure rings with warning`() = runTest {
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.failure(IllegalStateException())))
            .execute(alarm(), date, zone)

        assertEquals(AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING, decision)
    }

    @Test
    fun `alarm not configured for vacation does not query calendar`() = runTest {
        val source = FakeEventSource(Result.failure(IllegalStateException()))
        val decision = ShouldRingAlarmUseCase(source).execute(alarm().copy(skipOnCalendarMatch = false), date, zone)

        assertEquals(AlarmExecutionDecision.RING, decision)
        assertEquals(0, source.queryCount)
    }

    @Test
    fun `alarm with empty keywords does not query calendar`() = runTest {
        val source = FakeEventSource(Result.failure(IllegalStateException()))
        val decision = ShouldRingAlarmUseCase(source).execute(alarm().copy(calendarKeywords = emptyList()), date, zone)

        assertEquals(AlarmExecutionDecision.RING, decision)
        assertEquals(0, source.queryCount)
    }

    @Test
    fun `only this alarms configured keyword controls its decision`() = runTest {
        val event = CalendarEvent("해외 출장", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false)
        val source = FakeEventSource(Result.success(listOf(event)))

        val tripAlarm = ShouldRingAlarmUseCase(source).execute(alarm().copy(calendarKeywords = listOf("출장")), date, zone)
        val vacationAlarm = ShouldRingAlarmUseCase(source).execute(alarm().copy(calendarKeywords = listOf("휴가")), date, zone)

        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION, tripAlarm)
        assertEquals(AlarmExecutionDecision.RING, vacationAlarm)
    }

    @Test
    fun `disabled alarm never rings`() = runTest {
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(emptyList())))
            .execute(alarm().copy(enabled = false), date, zone)

        assertEquals(AlarmExecutionDecision.DO_NOT_RING, decision)
    }

    @Test
    fun `non vacation event rings`() = runTest {
        val event = CalendarEvent("회의", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false)
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
            .execute(alarm(), date, zone)

        assertEquals(AlarmExecutionDecision.RING, decision)
    }

    @Test
    fun `public holiday skips only opted in alarm without calendar`() = runTest {
        val source = FakeEventSource(Result.failure(IllegalStateException()))
        val holiday = LocalDate.of(2026, 8, 15)

        val optedIn = ShouldRingAlarmUseCase(source)
            .execute(alarm().copy(skipOnPublicHoliday = true), holiday, zone)
        val optedOut = ShouldRingAlarmUseCase(source)
            .execute(alarm().copy(skipOnCalendarMatch = false, skipOnPublicHoliday = false), holiday, zone)

        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION, optedIn)
        assertEquals(AlarmExecutionDecision.RING, optedOut)
    }

    @Test
    fun `connected calendar temporary holiday supplements embedded data`() = runTest {
        val event = CalendarEvent("대한민국 임시공휴일", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), true)
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
            .execute(
                alarm().copy(skipOnCalendarMatch = false, skipOnPublicHoliday = true),
                LocalDate.of(2026, 8, 20),
                zone,
            )

        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION, decision)
    }

    @Test
    fun `holiday mentions and timed holiday titles cannot suppress a working day`() = runTest {
        listOf("한글날 행사 준비" to true, "공휴일 근무 회의" to false, "대체공휴일" to false).forEach { (title, allDay) ->
            val event = CalendarEvent(title, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant(), allDay)
            val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
                .execute(alarm().copy(skipOnCalendarMatch = false, skipOnPublicHoliday = true), date, zone)
            assertEquals(title, AlarmExecutionDecision.RING, decision)
        }
    }

    @Test
    fun `explicit keyword still matches ordinary holiday related event`() = runTest {
        val event = CalendarEvent("한글날 행사 준비", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false)
        val decision = ShouldRingAlarmUseCase(FakeEventSource(Result.success(listOf(event))))
            .execute(alarm().copy(calendarKeywords = listOf("행사"), skipOnPublicHoliday = true), date, zone)
        assertEquals(AlarmExecutionDecision.SKIP_FOR_VACATION, decision)
    }

    private fun alarm() = Alarm(1, "출근", 7, 0, 31, true, true)

    private class FakeEventSource(
        private val result: Result<List<CalendarEvent>>,
    ) : CalendarEventSource {
        var queryCount = 0

        override suspend fun getEventsOverlapping(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Result<List<CalendarEvent>> {
            queryCount++
            return result
        }
    }
}
