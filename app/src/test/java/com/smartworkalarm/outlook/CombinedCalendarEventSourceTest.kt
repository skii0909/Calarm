package com.smartworkalarm.outlook

import com.smartworkalarm.data.calendar.CombinedCalendarEventSource
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarEventSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class CombinedCalendarEventSourceTest {
    @Test fun `blocking Google connection preflight cannot hold alarm cancellation`() = runBlocking {
        val started = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val source = CombinedCalendarEventSource(
            object : CalendarEventSource {
                override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> = error("cancelled before events")
            },
            {
                started.countDown()
                try {
                    while (unblock.count > 0) try { unblock.await() } catch (_: InterruptedException) { }
                    CalendarConnectionState(false, emptyList())
                } finally { finished.countDown() }
            },
            { Result.success(emptySet()) }, MemoryOutlookStore(ready), Clock.fixed(start, ZoneOffset.UTC), { ZoneOffset.UTC },
        )
        val pending = async(Dispatchers.Default) { source.getEventsOverlapping(start, end) }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            withTimeout(1_000) { pending.cancelAndJoin() }
            assertEquals(1L, finished.count)
        } finally {
            unblock.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
        }
    }

    private val start = Instant.parse("2026-09-19T00:00:00Z")
    private val end = start.plusSeconds(86400)
    private val ready = OutlookState(account = OutlookAccount("a", "me@example.com"),
        selectedIds = setOf("opaque-id"), syncedAt = start, coverageStart = start, coverageEnd = end,
        events = listOf(OutlookEvent("opaque-id", "event", "휴가", start, end)))

    @Test fun `Outlook alone works without Google permission and never calls Google source`() = runTest {
        val source = source(CalendarConnectionState(false, emptyList()), ready, emptySet()) { error("Google is not selected") }
        assertEquals(listOf("휴가"), source.getEventsOverlapping(start, end).getOrThrow().map { it.title })
    }

    @Test fun `Google and Outlook selections both contribute to rules`() = runTest {
        val source = source(CalendarConnectionState(true, listOf(ConnectedCalendar(1, "Google"))), ready, setOf(1)) {
            Result.success(listOf(CalendarEvent("야간", start, end, false)))
        }
        assertEquals(setOf("야간", "휴가"), source.getEventsOverlapping(start, end).getOrThrow().map { it.title }.toSet())
    }

    @Test fun `expired selected Outlook snapshot does not look like successful Google-only data`() = runTest {
        val source = source(CalendarConnectionState(true, listOf(ConnectedCalendar(1, "Google"))), ready.copy(syncedAt = start.minusSeconds(21601)), setOf(1)) {
            Result.success(emptyList())
        }
        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test fun `previously selected Google provider failure is not ignored when Outlook succeeds`() = runTest {
        val source = source(CalendarConnectionState(false, emptyList()), ready, setOf(1)) { Result.failure(SecurityException()) }
        assertTrue(source.getEventsOverlapping(start, end).isFailure)
    }

    @Test fun `unselected Outlook source does not interfere with Google`() = runTest {
        val source = source(CalendarConnectionState(true, listOf(ConnectedCalendar(1, "Google"))), ready.copy(selectedIds = emptySet(), issue = OutlookIssue.REAUTH_REQUIRED), setOf(1)) {
            Result.success(listOf(CalendarEvent("Google", start, end, false)))
        }
        assertEquals("Google", source.getEventsOverlapping(start, end).getOrThrow().single().title)
    }

    private fun source(googleState: CalendarConnectionState, outlookState: OutlookState, selected: Set<Long>, query: suspend () -> Result<List<CalendarEvent>>) =
        CombinedCalendarEventSource(object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) = query()
        }, { googleState }, { Result.success(selected) }, MemoryOutlookStore(outlookState), Clock.fixed(start, ZoneOffset.UTC), { ZoneOffset.UTC })
}
