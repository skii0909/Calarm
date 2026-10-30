package com.smartworkalarm.outlook

import com.smartworkalarm.domain.model.CalendarEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object OutlookSnapshotPolicy {
    fun isFresh(state: OutlookState, now: Instant): Boolean {
        val syncedAt = state.syncedAt ?: return false
        return state.loaded && state.hasSelection &&
            state.issue !in setOf(OutlookIssue.REAUTH_REQUIRED, OutlookIssue.ACCESS_DENIED, OutlookIssue.STORAGE, OutlookIssue.SETUP_REQUIRED) &&
            !now.isBefore(syncedAt) && Duration.between(syncedAt, now) <= Duration.ofHours(6)
    }

    fun events(
        state: OutlookState,
        start: Instant,
        end: Instant,
        now: Instant,
        zone: ZoneId,
    ): Result<List<CalendarEvent>> = runCatching {
        check(start < end) { "Invalid calendar range" }
        check(isFresh(state, now)) { "Outlook snapshot unavailable or expired" }
        check(state.coverageStart?.let { it <= start } == true && state.coverageEnd?.let { it >= end } == true) {
            "Outlook snapshot does not cover requested range"
        }
        state.events.asSequence()
            .filter { it.calendarId in state.selectedIds }
            .map { event ->
                val allDay = event.allDayStart != null && event.allDayEnd != null
                CalendarEvent(
                    event.title,
                    if (allDay) event.allDayStart.atStartOfDay(zone).toInstant() else event.start,
                    if (allDay) event.allDayEnd.atStartOfDay(zone).toInstant() else event.end,
                    allDay,
                )
            }
            .filter { it.start < end && it.end > start }
            .toList()
    }
}
