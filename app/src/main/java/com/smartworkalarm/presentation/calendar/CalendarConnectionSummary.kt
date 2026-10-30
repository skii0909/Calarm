package com.smartworkalarm.presentation.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState

internal fun calendarConnectionSummary(connection: CalendarConnectionState): String {
    if (!connection.permissionGranted && !connection.outlookConnected) return "연결된 캘린더 없음"
    val selectedCount = (if (connection.permissionGranted) connection.calendars.count { it.selected } else 0) +
        (if (connection.outlookConnected) connection.outlook?.selectedIds?.size ?: 0 else 0)
    return if (selectedCount == 0) {
        "연결된 캘린더 없음"
    } else {
        "연결된 캘린더 ${selectedCount}개"
    }
}
