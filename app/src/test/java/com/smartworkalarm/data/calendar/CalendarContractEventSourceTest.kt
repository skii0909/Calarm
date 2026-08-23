package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarEvent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarContractEventSourceTest {
    private val start = Instant.parse("2026-08-19T00:00:00Z")
    private val end = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `no eligible Google calendars is a calendar read failure`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { emptyList() },
            eventQuery = { _, _, _ -> error("event query must not run without a calendar") },
        )

        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test
    fun `eligible calendar with no events remains a successful empty result`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { listOf(42L) },
            eventQuery = { _, _, _ -> emptyList<CalendarEvent>() },
        )

        assertEquals(emptyList<CalendarEvent>(), source.getEventsOverlapping(start, end).getOrThrow())
    }
}
