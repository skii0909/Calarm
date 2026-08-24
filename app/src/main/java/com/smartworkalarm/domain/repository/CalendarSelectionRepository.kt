package com.smartworkalarm.domain.repository

interface CalendarSelectionRepository {
    fun getSelectedCalendarIds(): Result<Set<Long>>

    fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>>

    fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit>

    fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit>
}
