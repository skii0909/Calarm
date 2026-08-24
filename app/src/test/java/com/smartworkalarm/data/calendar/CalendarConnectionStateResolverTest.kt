package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConnectionStateResolverTest {
    @Test
    fun `null provider cursor is a failed catalog and preserves selected ids`() {
        val selectionRepository = RecordingSelectionRepository(selectedIds = setOf(7L, 9L))
        val queryResult = calendarProviderRows<Any>(
            query = { null },
            readRows = { error("rows must not be read from a null cursor") },
        )

        val result = CalendarConnectionStateResolver.resolve(
            permissionGranted = true,
            calendars = queryResult,
            selectionRepository = selectionRepository,
        )

        assertFalse(result.calendarQuerySucceeded)
        assertTrue(selectionRepository.reconcileCalls.isEmpty())
        assertEquals(setOf(7L, 9L), selectionRepository.getSelectedCalendarIds().getOrThrow())
    }

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

private class RecordingSelectionRepository(
    private var selectedIds: Set<Long> = emptySet(),
) : CalendarSelectionRepository {
    val reconcileCalls = mutableListOf<Set<Long>>()

    override fun getSelectedCalendarIds(): Result<Set<Long>> = Result.success(selectedIds)

    override fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>> {
        reconcileCalls += availableCalendarIds
        selectedIds = availableCalendarIds
        return Result.success(selectedIds)
    }

    override fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit> = Result.success(Unit)

    override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit> = Result.success(Unit)
}
