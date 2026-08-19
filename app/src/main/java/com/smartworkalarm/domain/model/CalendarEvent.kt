package com.smartworkalarm.domain.model

import java.time.Instant

data class CalendarEvent(
    val title: String?,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
)
