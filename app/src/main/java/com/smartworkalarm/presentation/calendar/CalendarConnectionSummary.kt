package com.smartworkalarm.presentation.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState

internal fun calendarConnectionSummary(connection: CalendarConnectionState): String {
    if (!connection.permissionGranted) return "캘린더 권한 없음"
    val selectedCount = connection.calendars.count { it.selected }
    return if (selectedCount == 0) {
        "연결된 캘린더 없음"
    } else {
        "연결된 캘린더 ${selectedCount}개"
    }
}
