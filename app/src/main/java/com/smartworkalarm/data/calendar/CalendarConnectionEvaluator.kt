package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar

object CalendarConnectionEvaluator {
    fun evaluate(
        permissionGranted: Boolean,
        calendars: List<ConnectedCalendar>,
    ): CalendarConnectionState = CalendarConnectionState(permissionGranted, calendars)
}
