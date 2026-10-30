package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ShouldRingAlarmUseCase @Inject constructor(
    private val calendarEventSource: CalendarEventSource,
    private val holidayProvider: KoreanPublicHolidayProvider = KoreanPublicHolidayProvider(),
) {
    suspend fun execute(
        alarm: Alarm,
        date: LocalDate,
        zoneId: ZoneId,
    ): AlarmExecutionDecision {
        if (!alarm.enabled) return AlarmExecutionDecision.DO_NOT_RING
        if (alarm.skipOnPublicHoliday && holidayProvider.find(date) != null) {
            return AlarmExecutionDecision.SKIP_FOR_VACATION
        }
        val needsKeywordCheck = alarm.skipOnCalendarMatch && alarm.calendarKeywords.isNotEmpty()
        val needsHolidayCheck = alarm.skipOnPublicHoliday
        if (!needsKeywordCheck && !needsHolidayCheck) return AlarmExecutionDecision.RING

        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        return calendarEventSource.getEventsOverlapping(start, end).fold(
            onSuccess = { events ->
                val hasKeywordMatch = needsKeywordCheck && events.any {
                    CalendarKeywordMatcher.matches(it.title, alarm.calendarKeywords, alarm.calendarKeywordExactMatch)
                }
                val hasCalendarHoliday = needsHolidayCheck && events.any {
                    CalendarHolidayMatcher.matches(it)
                }
                if (hasKeywordMatch || hasCalendarHoliday) {
                    AlarmExecutionDecision.SKIP_FOR_VACATION
                } else {
                    AlarmExecutionDecision.RING
                }
            },
            onFailure = { AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING },
        )
    }
}
