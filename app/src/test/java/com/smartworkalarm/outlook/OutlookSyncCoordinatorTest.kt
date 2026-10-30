package com.smartworkalarm.outlook

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OutlookSyncCoordinatorTest {
    private val now = Instant.parse("2026-09-19T00:00:00Z")
    private val account = OutlookAccount("a", "me@example.com")
    private val zone = ZoneId.of("Asia/Seoul")

    @Test fun `initial sync selects default only and saves complete bounded snapshot`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account))
        val coordinator = coordinator(store)
        assertEquals(OutlookSyncResult.SUCCESS, coordinator.sync())
        val state = store.state.value
        assertEquals(setOf("primary"), state.selectedIds)
        assertEquals(listOf("primary"), state.events.map { it.calendarId })
        assertEquals(Instant.parse("2026-09-17T15:00:00Z"), state.coverageStart)
        assertEquals(Instant.parse("2026-10-20T15:00:00Z"), state.coverageEnd)
        assertEquals(now, state.syncedAt)
    }

    @Test fun `response after logout cannot resurrect account or calendar data`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = MemoryOutlookStore(OutlookState(account = account))
        val job = async { coordinator(store, beforeEvents = { entered.complete(Unit); release.await() }).sync() }
        entered.await()
        store.update { OutlookState() }
        release.complete(Unit)
        assertEquals(OutlookSyncResult.SUPERSEDED, job.await())
        assertNull(store.state.value.account)
        assertTrue(store.state.value.events.isEmpty())
    }

    @Test fun `selection change invalidates in flight fetch`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = MemoryOutlookStore(OutlookState(account = account))
        val job = async { coordinator(store, beforeEvents = { entered.complete(Unit); release.await() }).sync() }
        entered.await()
        store.update { it.copy(generation = "new", catalogLoaded = true, selectedIds = setOf("other")) }
        release.complete(Unit)
        assertEquals(OutlookSyncResult.SUPERSEDED, job.await())
        assertEquals(setOf("other"), store.state.value.selectedIds)
        assertNull(store.state.value.syncedAt)
    }

    @Test fun `failed fetch preserves old snapshot and choices`() = runTest {
        val previous = OutlookEvent("primary", "old", "휴가", now, now.plusSeconds(3600))
        val store = MemoryOutlookStore(OutlookState(account = account, catalogLoaded = true,
            selectedIds = setOf("primary"), events = listOf(previous), syncedAt = now.minusSeconds(20)))
        assertEquals(OutlookSyncResult.RETRY, coordinator(store, beforeEvents = { throw OutlookException(OutlookIssue.NETWORK) }).sync())
        assertEquals(listOf(previous), store.state.value.events)
        assertEquals(now.minusSeconds(20), store.state.value.syncedAt)
        assertEquals(OutlookIssue.NETWORK, store.state.value.issue)
    }

    @Test fun `zero selected calendars remain zero on refresh`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account, catalogLoaded = true))
        assertEquals(OutlookSyncResult.SUCCESS, coordinator(store, beforeEvents = { error("not selected") }).sync())
        assertTrue(store.state.value.selectedIds.isEmpty())
    }

    @Test fun `account mismatch requires login and never fetches graph`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account))
        assertEquals(OutlookSyncResult.ACTION_REQUIRED, coordinator(store, currentAccount = null).sync())
        assertEquals(OutlookIssue.REAUTH_REQUIRED, store.state.value.issue)
    }

    @Test fun `server retry deadline prevents early requests`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account, retryAt = now.plusSeconds(3600)))
        assertEquals(OutlookSyncResult.RETRY, coordinator(store, beforeEvents = { error("too soon") }).sync())
        assertNull(store.state.value.syncedAt)
    }

    @Test fun `401 retries once with refreshed token and second denial invalidates cache`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account))
        val requests = mutableListOf<Boolean>()
        var fetches = 0
        val auth = object : OutlookTokenProvider {
            override suspend fun account() = account
            override suspend fun token(forceRefresh: Boolean): String { requests += forceRefresh; return "test" }
        }
        val graph = object : OutlookGraphGateway {
            override suspend fun calendars(token: String): List<OutlookCalendar> { fetches++; throw GraphHttpException(401) }
            override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant) = emptyList<OutlookEvent>()
        }
        assertEquals(OutlookSyncResult.ACTION_REQUIRED, OutlookSyncCoordinator(store, auth, graph, Clock.fixed(now, zone)) { zone }.sync())
        assertEquals(listOf(false, true), requests)
        assertEquals(2, fetches)
        assertEquals(OutlookIssue.REAUTH_REQUIRED, store.state.value.issue)
    }

    @Test fun `network failure after access revocation cannot reactivate old cached vacation`() = runTest {
        val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val end = start.plusSeconds(86400)
        val store = MemoryOutlookStore(OutlookState(account = account, catalogLoaded = true,
            selectedIds = setOf("primary"), coverageStart = start, coverageEnd = end, syncedAt = now,
            events = listOf(OutlookEvent("primary", "old", "휴가", start, end))))
        var deny = true
        val auth = object : OutlookTokenProvider {
            override suspend fun account() = account
            override suspend fun token(forceRefresh: Boolean) = "test"
        }
        val graph = object : OutlookGraphGateway {
            override suspend fun calendars(token: String): List<OutlookCalendar> {
                if (deny) throw GraphHttpException(403)
                throw OutlookException(OutlookIssue.NETWORK)
            }
            override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant) = emptyList<OutlookEvent>()
        }
        val coordinator = OutlookSyncCoordinator(store, auth, graph, Clock.fixed(now, zone)) { zone }
        coordinator.sync()
        assertTrue(OutlookSnapshotPolicy.events(store.state.value, start, end, now, zone).isFailure)
        deny = false
        coordinator.sync()
        assertTrue(OutlookSnapshotPolicy.events(store.state.value, start, end, now, zone).isFailure)
    }

    @Test fun `shared calendars are excluded from selectable catalog`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account))
        val graph = object : OutlookGraphGateway {
            override suspend fun calendars(token: String) = listOf(
                OutlookCalendar("primary", "Calendar", true, "mail-alias@example.com"),
                OutlookCalendar("shared", "Someone else", false, "other@example.com"),
                OutlookCalendar("owned", "Mine", false, "MAIL-ALIAS@example.com"),
            )
            override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant) = emptyList<OutlookEvent>()
        }
        val auth = object : OutlookTokenProvider {
            override suspend fun account() = account
            override suspend fun token(forceRefresh: Boolean) = "test"
        }
        OutlookSyncCoordinator(store, auth, graph, Clock.fixed(now, zone)) { zone }.sync()
        assertEquals(listOf("primary", "owned"), store.state.value.calendars.map { it.id })
    }

    private fun coordinator(
        store: OutlookStore,
        currentAccount: OutlookAccount? = account,
        beforeEvents: suspend () -> Unit = {},
    ) = OutlookSyncCoordinator(store, object : OutlookTokenProvider {
        override suspend fun account() = currentAccount
        override suspend fun token(forceRefresh: Boolean) = "test-token"
    }, object : OutlookGraphGateway {
        override suspend fun calendars(token: String) = listOf(OutlookCalendar("primary", "Calendar", true), OutlookCalendar("other", "Other"))
        override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant): List<OutlookEvent> {
            beforeEvents()
            return listOf(OutlookEvent(calendarId, "event", "휴가", now, now.plusSeconds(3600)))
        }
    }, Clock.fixed(now, zone), { zone })
}

internal class MemoryOutlookStore(initial: OutlookState = OutlookState()) : OutlookStore {
    override val state = MutableStateFlow(initial)
    override suspend fun awaitLoaded() = state.value
    override suspend fun update(transform: (OutlookState) -> OutlookState): OutlookState = transform(state.value).also { state.value = it }
}
