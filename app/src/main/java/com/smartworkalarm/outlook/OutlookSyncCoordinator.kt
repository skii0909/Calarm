package com.smartworkalarm.outlook

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

interface OutlookTokenProvider {
    suspend fun account(): OutlookAccount?
    suspend fun token(forceRefresh: Boolean = false): String
}

interface OutlookGraphGateway {
    suspend fun calendars(token: String): List<OutlookCalendar>
    suspend fun events(token: String, calendarId: String, start: Instant, end: Instant): List<OutlookEvent>
}

enum class OutlookSyncResult { SUCCESS, RETRY, ACTION_REQUIRED, SUPERSEDED, NOT_CONNECTED, BUSY }

class OutlookSyncCoordinator(
    private val store: OutlookStore,
    private val auth: OutlookTokenProvider,
    private val graph: OutlookGraphGateway,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {
    private val mutex = Mutex()

    suspend fun sync(): OutlookSyncResult {
        if (!mutex.tryLock()) return OutlookSyncResult.BUSY
        try {
            val original = store.awaitLoaded()
            val account = original.account ?: return OutlookSyncResult.NOT_CONNECTED
            if (original.retryAt?.isAfter(clock.instant()) == true) return OutlookSyncResult.RETRY
            try {
                if (auth.account()?.id != account.id) throw OutlookException(OutlookIssue.REAUTH_REQUIRED)
                var token = auth.token()
                suspend fun <T> authenticated(block: suspend (String) -> T): T = try {
                    block(token)
                } catch (error: GraphHttpException) {
                    if (error.status != 401) throw error
                    token = auth.token(forceRefresh = true)
                    block(token)
                }
                val catalog = authenticated { graph.calendars(it) }
                val owner = catalog.firstOrNull { it.isDefault }?.ownerAddress ?: account.username
                val calendars = catalog.filter { it.ownerAddress == null || it.ownerAddress.equals(owner, ignoreCase = true) }
                val available = calendars.map { it.id }.toSet()
                val selected = if (original.catalogLoaded) original.selectedIds intersect available
                    else calendars.filter { it.isDefault }.map { it.id }.toSet()
                val localZone = zone()
                val today = clock.instant().atZone(localZone).toLocalDate()
                val start = today.minusDays(1).atStartOfDay(localZone).toInstant()
                val end = today.plusDays(32).atStartOfDay(localZone).toInstant()
                val events = selected.sorted().flatMap { id ->
                    // Extra day on either side covers all-day date normalization across distant timezones.
                    authenticated { graph.events(it, id, start.minusSeconds(86400), end.plusSeconds(86400)) }
                }
                var applied = false
                store.update { current ->
                    if (current.generation != original.generation || current.account?.id != account.id) current
                    else {
                        applied = true
                        current.copy(calendars = calendars, catalogLoaded = true, selectedIds = selected, events = events,
                            coverageStart = start, coverageEnd = end, syncedAt = clock.instant(), issue = null, retryAt = null)
                    }
                }
                return if (applied) OutlookSyncResult.SUCCESS else OutlookSyncResult.SUPERSEDED
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val issue = (error as? OutlookException)?.issue ?: OutlookIssue.NETWORK
                var applied = false
                store.update { current ->
                    if (current.generation != original.generation) current else {
                        applied = true
                        val blockingIssues = setOf(OutlookIssue.REAUTH_REQUIRED, OutlookIssue.ACCESS_DENIED, OutlookIssue.SETUP_REQUIRED)
                        val effectiveIssue = current.issue?.takeIf { it in blockingIssues } ?: issue
                        current.copy(issue = effectiveIssue,
                            syncedAt = if (effectiveIssue in blockingIssues) null else current.syncedAt,
                            retryAt = (error as? OutlookException)?.retryAt)
                    }
                }
                if (!applied) return OutlookSyncResult.SUPERSEDED
                return if (issue in setOf(OutlookIssue.NETWORK, OutlookIssue.THROTTLED)) OutlookSyncResult.RETRY
                    else OutlookSyncResult.ACTION_REQUIRED
            }
        } finally { mutex.unlock() }
    }
}
