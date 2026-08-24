package com.smartworkalarm.presentation.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarConnectionSummaryTest {
    @Test
    fun `connection summary counts only selected calendars`() {
        val connection = CalendarConnectionState(
            permissionGranted = true,
            calendars = listOf(
                ConnectedCalendar(1L, "근무표", selected = true),
                ConnectedCalendar(2L, "개인", selected = false),
            ),
        )

        assertEquals("연결된 캘린더 1개", calendarConnectionSummary(connection))
    }

    @Test
    fun `zero selected calendars are summarized as disconnected`() {
        val connection = CalendarConnectionState(
            permissionGranted = true,
            calendars = listOf(ConnectedCalendar(1L, "근무표", selected = false)),
        )

        assertEquals("연결된 캘린더 없음", calendarConnectionSummary(connection))
    }
}
