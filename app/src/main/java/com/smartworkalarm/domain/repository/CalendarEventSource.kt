package com.smartworkalarm.domain.repository

import com.smartworkalarm.domain.model.CalendarEvent
import java.time.Instant

interface CalendarEventSource {
    suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>>
}
