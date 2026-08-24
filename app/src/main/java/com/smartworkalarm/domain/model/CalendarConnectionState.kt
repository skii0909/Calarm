package com.smartworkalarm.domain.model

data class ConnectedCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String = "",
    val accountType: String = "",
    val selected: Boolean = true,
)

data class CalendarConnectionState(
    val permissionGranted: Boolean,
    val calendars: List<ConnectedCalendar>,
    val calendarQuerySucceeded: Boolean = true,
) {
    val isConnected: Boolean = permissionGranted && calendars.any { it.selected }
}
