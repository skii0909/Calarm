package com.smartworkalarm.domain.model

data class ConnectedCalendar(
    val id: Long,
    val displayName: String,
)

data class CalendarConnectionState(
    val permissionGranted: Boolean,
    val calendars: List<ConnectedCalendar>,
) {
    val isConnected: Boolean = permissionGranted && calendars.isNotEmpty()
}
