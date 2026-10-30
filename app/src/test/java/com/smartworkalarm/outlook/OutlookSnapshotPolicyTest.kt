package com.smartworkalarm.outlook

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class OutlookSnapshotPolicyTest {
    private val start = Instant.parse("2026-09-18T15:00:00Z")
    private val end = Instant.parse("2026-09-19T15:00:00Z")
    private val synced = Instant.parse("2026-09-18T14:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val event = OutlookEvent("primary", "event", "휴가", start, end)
    private val state = OutlookState(
        account = OutlookAccount("account", "person@example.com"),
        calendars = listOf(OutlookCalendar("primary", "Calendar", true)),
        selectedIds = setOf("primary"), events = listOf(event),
        coverageStart = start, coverageEnd = end, syncedAt = synced,
    )

    @Test fun `fresh selected events are available without network`() {
        val events = read(state).getOrThrow()
        assertEquals(listOf("휴가"), events.map { it.title })
    }

    @Test fun `expired future missing and incomplete snapshots are errors rather than empty days`() {
        listOf(
            state.copy(syncedAt = synced.minusSeconds(21601)),
            state.copy(syncedAt = synced.plusSeconds(1)),
            state.copy(syncedAt = null),
            state.copy(coverageStart = start.plusSeconds(1)),
            state.copy(coverageEnd = end.minusSeconds(1)),
            state.copy(loaded = false),
        ).forEach { assertTrue(read(it).isFailure) }
    }

    @Test fun `six hour boundary is inclusive but older snapshot fails`() {
        assertTrue(OutlookSnapshotPolicy.events(state, start, end, synced.plusSeconds(21600), zone).isSuccess)
        assertTrue(OutlookSnapshotPolicy.events(state, start, end, synced.plusSeconds(21601), zone).isFailure)
    }

    @Test fun `revoked and disconnected accounts cannot use even fresh cache`() {
        assertTrue(read(state.copy(issue = OutlookIssue.REAUTH_REQUIRED)).isFailure)
        assertTrue(read(state.copy(issue = OutlookIssue.ACCESS_DENIED)).isFailure)
        assertTrue(read(state.copy(account = null)).isFailure)
        assertTrue(read(state.copy(selectedIds = emptySet())).isFailure)
    }

    @Test fun `transient failure keeps fresh snapshot and selected calendars only`() {
        val other = event.copy(calendarId = "unselected", title = "야간")
        assertEquals(listOf("휴가"), read(state.copy(events = listOf(event, other), issue = OutlookIssue.NETWORK)).getOrThrow().map { it.title })
    }

    @Test fun `all day dates map to device day without UTC date shifting`() {
        val allDay = event.copy(
            start = Instant.parse("2026-09-19T07:00:00Z"), end = Instant.parse("2026-09-20T07:00:00Z"),
            allDayStart = LocalDate.parse("2026-09-19"), allDayEnd = LocalDate.parse("2026-09-20"),
        )
        val mapped = read(state.copy(events = listOf(allDay))).getOrThrow().single()
        assertEquals(start, mapped.start)
        assertEquals(end, mapped.end)
        assertTrue(mapped.allDay)
    }

    @Test fun `events touching but not overlapping range are excluded`() {
        val before = event.copy(start = start.minusSeconds(3600), end = start)
        val after = event.copy(start = end, end = end.plusSeconds(3600))
        assertTrue(read(state.copy(events = listOf(before, after))).getOrThrow().isEmpty())
    }

    private fun read(value: OutlookState) = OutlookSnapshotPolicy.events(value, start, end, synced, zone)
}
