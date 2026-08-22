package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.ConnectedCalendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConnectionEvaluatorTest {
    @Test
    fun `one readable calendar is connected`() {
        val result = CalendarConnectionEvaluator.evaluate(
            permissionGranted = true,
            calendars = listOf(ConnectedCalendar(1, "내 캘린더")),
        )

        assertTrue(result.isConnected)
    }

    @Test
    fun `permission missing or empty calendars is disconnected`() {
        assertFalse(CalendarConnectionEvaluator.evaluate(false, emptyList()).isConnected)
        assertFalse(CalendarConnectionEvaluator.evaluate(true, emptyList()).isConnected)
    }
}
