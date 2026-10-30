package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class CalendarContractEventSourceTest {
    @Test
    fun `cancellation returns before a blocking provider cooperates`() = runBlocking {
        val started = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val source = CalendarContractEventSource(
            calendarIds = { listOf(1L) },
            selectionRepository = selectionRepository(Result.success(setOf(1L))),
            eventQuery = { _, _, _ ->
                started.countDown()
                try {
                    // Simulate a provider that ignores thread interruption.
                    while (unblock.count > 0) try { unblock.await() } catch (_: InterruptedException) { }
                    emptyList()
                } finally { finished.countDown() }
            },
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

    @Test
    fun `blocked provider work is bounded and excess queries fail open`() = runBlocking {
        val started = CountDownLatch(2)
        val unblock = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val source = CalendarContractEventSource(
            calendarIds = { listOf(1L) },
            selectionRepository = selectionRepository(Result.success(setOf(1L))),
            eventQuery = { _, _, _ ->
                started.countDown()
                try { unblock.await(); emptyList() } finally { finished.countDown() }
            },
        )
        val pending = List(2) { async(Dispatchers.Default) { source.getEventsOverlapping(start, end) } }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val overflow = withTimeout(1_000) { source.getEventsOverlapping(start, end) }
            assertTrue(overflow.isFailure)
        } finally {
            unblock.countDown()
            pending.forEach { it.await() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `previous days all day holiday must not skip October 6 alarm in Korea`() = runTest {
        val previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
        try {
            val holiday = CalendarEvent("개천절 대체공휴일", Instant.parse("2026-10-05T00:00:00Z"),
                Instant.parse("2026-10-06T00:00:00Z"), true)
            val source = CalendarContractEventSource(
                calendarIds = { listOf(1L) },
                selectionRepository = selectionRepository(Result.success(setOf(1L))),
                eventQuery = { _, from, until -> listOf(holiday).filter { it.start < until && it.end > from } },
            )
            val alarm = com.smartworkalarm.domain.model.Alarm(
                id = 1, name = "아침", hour = 7, minute = 0, repeatDaysMask = 127,
                enabled = true, skipOnCalendarMatch = true, skipOnPublicHoliday = true,
            )
            val decision = com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase(source).execute(
                alarm, java.time.LocalDate.of(2026, 10, 6), java.time.ZoneId.of("Asia/Seoul"))
            assertEquals(com.smartworkalarm.domain.model.AlarmExecutionDecision.RING, decision)
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    private val start = Instant.parse("2026-08-19T00:00:00Z")
    private val end = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `all day dates stay on their calendar dates across timezones and DST`() = runTest {
        for (zoneName in listOf("Asia/Seoul", "America/Los_Angeles", "Pacific/Kiritimati")) {
            val zone = java.time.ZoneId.of(zoneName)
            val day = java.time.LocalDate.of(2026, 3, 8)
            val from = day.atStartOfDay(zone).toInstant()
            val until = day.plusDays(1).atStartOfDay(zone).toInstant()
            val events = listOf(
                CalendarEvent("어제 휴가", Instant.parse("2026-03-07T00:00:00Z"), Instant.parse("2026-03-08T00:00:00Z"), true),
                CalendarEvent("오늘 휴가", Instant.parse("2026-03-08T00:00:00Z"), Instant.parse("2026-03-09T00:00:00Z"), true),
                CalendarEvent("내일 휴가", Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-10T00:00:00Z"), true),
                CalendarEvent("여러 날 휴가", Instant.parse("2026-03-07T00:00:00Z"), Instant.parse("2026-03-10T00:00:00Z"), true),
                CalendarEvent("시간 지정", from.plusSeconds(3600), from.plusSeconds(7200), false),
                CalendarEvent("자정에 종료", from.minusSeconds(3600), from, false),
                CalendarEvent("다음 자정 시작", until, until.plusSeconds(3600), false),
            )
            val source = CalendarContractEventSource(
                calendarIds = { listOf(1L) },
                selectionRepository = selectionRepository(Result.success(setOf(1L))),
                eventQuery = { _, queryStart, queryEnd -> events.filter { it.start < queryEnd && it.end > queryStart } },
                zone = { zone },
            )
            val actual = source.getEventsOverlapping(from, until).getOrThrow()
            assertEquals(zoneName, listOf("오늘 휴가", "여러 날 휴가", "시간 지정"), actual.map { it.title })
            assertEquals(CalendarEvent("오늘 휴가", from, until, true), actual[0])
            assertEquals(events[4], actual[2])
            // A short query just after local midnight must also see today's UTC-encoded all-day event.
            assertEquals(listOf("오늘 휴가", "여러 날 휴가"),
                source.getEventsOverlapping(from, from.plusSeconds(60)).getOrThrow().map { it.title })
        }
    }

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
