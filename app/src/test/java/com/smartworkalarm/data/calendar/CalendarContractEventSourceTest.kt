package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarContractEventSourceTest {
    private val start = Instant.parse("2026-08-19T00:00:00Z")
    private val end = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `selected subset is forwarded to the event query in deterministic order`() = runTest {
        var queriedIds: List<Long>? = null
        val source = CalendarContractEventSource(
            calendarIds = { listOf(30L, 10L, 20L) },
            selectionRepository = selectionRepository(Result.success(setOf(30L, 10L))),
            eventQuery = { ids, _, _ ->
                queriedIds = ids
                emptyList()
            },
        )

        source.getEventsOverlapping(start, end).getOrThrow()

        assertEquals(listOf(10L, 30L), queriedIds)
    }

    @Test
    fun `unselected readable IDs are excluded from the event query`() = runTest {
        var queriedIds: List<Long>? = null
        val source = CalendarContractEventSource(
            calendarIds = { listOf(1L, 2L, 3L) },
            selectionRepository = selectionRepository(Result.success(setOf(1L, 3L))),
            eventQuery = { ids, _, _ ->
                queriedIds = ids
                emptyList()
            },
        )

        source.getEventsOverlapping(start, end).getOrThrow()

        assertEquals(listOf(1L, 3L), queriedIds)
    }

    @Test
    fun `no readable Google calendars is a calendar read failure without an event query`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { emptyList() },
            selectionRepository = selectionRepository(Result.success(setOf(42L))),
            eventQuery = { _, _, _ -> error("event query must not run without a calendar") },
        )

        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test
    fun `no selected calendars is a calendar read failure without an event query`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { listOf(42L) },
            selectionRepository = selectionRepository(Result.success(emptySet())),
            eventQuery = { _, _, _ -> error("event query must not run without a selection") },
        )

        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test
    fun `selection reconciliation failure is a calendar read failure without an event query`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { listOf(42L) },
            selectionRepository = selectionRepository(Result.failure(IllegalStateException("selection unavailable"))),
            eventQuery = { _, _, _ -> error("event query must not run after reconciliation failure") },
        )

        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test
    fun `provider failure does not reconcile or query events`() = runTest {
        var reconcileCalls = 0
        val source = CalendarContractEventSource(
            calendarIds = { error("provider unavailable") },
            selectionRepository = selectionRepository(Result.success(setOf(42L))) { reconcileCalls++ },
            eventQuery = { _, _, _ -> error("event query must not run after provider failure") },
        )

        assertTrue(source.getEventsOverlapping(start, end).isFailure)
        assertEquals(0, reconcileCalls)
    }

    @Test
    fun `selected calendar with no events remains a successful empty result`() = runTest {
        val source = CalendarContractEventSource(
            calendarIds = { listOf(42L) },
            selectionRepository = selectionRepository(Result.success(setOf(42L))),
            eventQuery = { _, _, _ -> emptyList<CalendarEvent>() },
        )

        assertEquals(emptyList<CalendarEvent>(), source.getEventsOverlapping(start, end).getOrThrow())
    }

    private fun selectionRepository(
        reconcileResult: Result<Set<Long>>,
        onReconcile: () -> Unit = {},
    ) = object : CalendarSelectionRepository {
        override fun getSelectedCalendarIds(): Result<Set<Long>> = error("not used by event source")

        override fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>> {
            onReconcile()
            return reconcileResult
        }

        override fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit> =
            error("not used by event source")

        override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit> =
            error("not used by event source")
    }
}
