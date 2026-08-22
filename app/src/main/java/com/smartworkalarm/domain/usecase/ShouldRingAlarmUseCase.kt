package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.CalendarEventSource
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ShouldRingAlarmUseCase @Inject constructor(
    private val calendarEventSource: CalendarEventSource,
) {
    suspend fun execute(
        alarm: Alarm,
        date: LocalDate,
        zoneId: ZoneId,
    ): AlarmExecutionDecision {
        if (!alarm.enabled) return AlarmExecutionDecision.DO_NOT_RING
        if (!alarm.skipOnCalendarMatch) return AlarmExecutionDecision.RING

        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        return calendarEventSource.getEventsOverlapping(start, end).fold(
            onSuccess = { events ->
                if (events.any { VacationMatcher.isVacationTitle(it.title) }) {
                    AlarmExecutionDecision.SKIP_FOR_VACATION
                } else {
                    AlarmExecutionDecision.RING
                }
            },
            onFailure = { AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING },
        )
    }
}
