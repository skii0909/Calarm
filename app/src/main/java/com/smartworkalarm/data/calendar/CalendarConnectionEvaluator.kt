package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarSelectionRepository

object CalendarConnectionEvaluator {
    fun evaluate(
        permissionGranted: Boolean,
        calendars: List<ConnectedCalendar>,
        calendarQuerySucceeded: Boolean = true,
    ): CalendarConnectionState = CalendarConnectionState(
        permissionGranted = permissionGranted,
        calendars = calendars,
        calendarQuerySucceeded = calendarQuerySucceeded,
    )
}

internal object CalendarConnectionStateResolver {
    fun resolve(
        permissionGranted: Boolean,
        calendars: Result<List<ConnectedCalendar>>,
        selectionRepository: CalendarSelectionRepository,
    ): CalendarConnectionState {
        val availableCalendars = calendars.getOrElse {
            return CalendarConnectionEvaluator.evaluate(
                permissionGranted = permissionGranted,
                calendars = emptyList(),
                calendarQuerySucceeded = false,
            )
        }
        val selectedIds = selectionRepository.reconcile(
            availableCalendars.mapTo(mutableSetOf(), ConnectedCalendar::id),
        ).getOrThrow()
        return CalendarConnectionEvaluator.evaluate(
            permissionGranted = permissionGranted,
            calendars = availableCalendars.map { it.copy(selected = it.id in selectedIds) },
        )
    }
}
