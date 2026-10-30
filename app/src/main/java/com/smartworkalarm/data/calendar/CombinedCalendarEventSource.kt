package com.smartworkalarm.data.calendar

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.outlook.OutlookSnapshotPolicy
import com.smartworkalarm.outlook.OutlookStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CombinedCalendarEventSource(
    private val google: CalendarEventSource,
    private val googleConnection: () -> CalendarConnectionState,
    private val selectedGoogle: () -> Result<Set<Long>>,
    private val outlook: OutlookStore,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : CalendarEventSource {
    override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> =
        withContext(Dispatchers.IO) {
            try {
                val cloud = outlook.awaitLoaded()
                val useGoogle = awaitCalendarProvider {
                    val local = googleConnection()
                    local.calendars.any { it.selected } || selectedGoogle().getOrThrow().isNotEmpty()
                }
                val useOutlook = cloud.hasSelection
                check(useGoogle || useOutlook) { "No selected calendars" }
                val result = mutableListOf<CalendarEvent>()
                if (useGoogle) result += google.getEventsOverlapping(startInclusive, endExclusive).getOrThrow()
                if (useOutlook) result += OutlookSnapshotPolicy.events(cloud, startInclusive, endExclusive, clock.instant(), zone()).getOrThrow()
                Result.success(result)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }
}

class CombinedCalendarConnectionRepository(
    private val google: CalendarConnectionRepository,
    private val outlook: OutlookStore,
) : CalendarConnectionRepository {
    override fun getState(): CalendarConnectionState = google.getState().copy(outlook = outlook.state.value)
}
