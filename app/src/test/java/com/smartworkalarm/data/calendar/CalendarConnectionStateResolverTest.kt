package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConnectionStateResolverTest {
    @Test
    fun `provider failure is distinct from an empty successful catalog and does not reconcile`() {
        val selectionRepository = RecordingSelectionRepository()

        val result = CalendarConnectionStateResolver.resolve(
            permissionGranted = true,
            calendars = Result.failure(SecurityException("Calendar Provider unavailable")),
            selectionRepository = selectionRepository,
        )

        assertFalse(result.calendarQuerySucceeded)
        assertTrue(result.calendars.isEmpty())
        assertTrue(selectionRepository.reconcileCalls.isEmpty())
    }

    @Test
    fun `empty successful catalog reconciles removed calendars`() {
        val selectionRepository = RecordingSelectionRepository()

        val result = CalendarConnectionStateResolver.resolve(
            permissionGranted = true,
            calendars = Result.success(emptyList()),
            selectionRepository = selectionRepository,
        )

        assertTrue(result.calendarQuerySucceeded)
        assertEquals(listOf(emptySet<Long>()), selectionRepository.reconcileCalls)
    }
}

private class RecordingSelectionRepository : CalendarSelectionRepository {
    val reconcileCalls = mutableListOf<Set<Long>>()

    override fun getSelectedCalendarIds(): Result<Set<Long>> = Result.success(emptySet())

    override fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>> {
        reconcileCalls += availableCalendarIds
        return Result.success(availableCalendarIds)
    }

    override fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit> = Result.success(Unit)

    override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit> = Result.success(Unit)
}
