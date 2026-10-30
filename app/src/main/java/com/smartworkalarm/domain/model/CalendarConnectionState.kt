package com.smartworkalarm.domain.model

import com.smartworkalarm.outlook.OutlookState
import com.smartworkalarm.outlook.OutlookSnapshotPolicy
import java.time.Instant

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
    val outlook: OutlookState? = null,
) {
    val outlookConnected: Boolean get() = outlook?.let { OutlookSnapshotPolicy.isFresh(it, Instant.now()) } == true
    val isConnected: Boolean get() = (permissionGranted && calendars.any { it.selected }) || outlookConnected
}
