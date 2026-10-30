package com.smartworkalarm.outlook

import android.app.Activity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OutlookControllerTest {
    private val account = OutlookAccount("a", "me@example.com")
    private val clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC)
    private val graph = object : OutlookGraphGateway {
        override suspend fun calendars(token: String) = listOf(OutlookCalendar("one", "Calendar", true))
        override suspend fun events(token: String, calendarId: String, start: Instant, end: Instant) = emptyList<OutlookEvent>()
    }

    @Test fun `cancelled sign in retains connected account and selections`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account, selectedIds = setOf("one")))
        val auth = FakeAuth(account, signInResult = { null })
        val controller = controller(store, auth)
        assertFalse(controller.connect(Activity()))
        assertEquals(account, store.state.value.account)
        assertEquals(setOf("one"), store.state.value.selectedIds)
    }

    @Test fun `logout during sign in invalidates callback and finishes with cleared MSAL account`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = MemoryOutlookStore()
        val auth = FakeAuth(null, signInResult = { entered.complete(Unit); release.await(); account })
        val controller = controller(store, auth)
        val connecting = async { controller.connect(Activity()) }
        entered.await()
        val disconnecting = async { controller.disconnect() }
        testScheduler.runCurrent()
        assertNull(store.state.value.account)
        release.complete(Unit)
        connecting.await(); disconnecting.await()
        assertNull(auth.current)
        assertNull(store.state.value.account)
        assertTrue(store.state.value.events.isEmpty())
    }

    @Test fun `selecting calendar invalidates cache before refresh`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account,
            calendars = listOf(OutlookCalendar("one", "Calendar", true)), catalogLoaded = true,
            selectedIds = setOf("one"), syncedAt = clock.instant()))
        val controller = controller(store, FakeAuth(account))
        controller.setSelected(OutlookCalendarKey("a", "one"), false)
        assertTrue(store.state.value.selectedIds.isEmpty())
        assertNull(store.state.value.syncedAt)
    }

    @Test fun `calendar key from former account cannot alter current account`() = runTest {
        val store = MemoryOutlookStore(OutlookState(account = account,
            calendars = listOf(OutlookCalendar("one", "Calendar")), selectedIds = setOf("one")))
        controller(store, FakeAuth(account)).setSelected(OutlookCalendarKey("other", "one"), false)
        assertEquals(setOf("one"), store.state.value.selectedIds)
    }

    private fun controller(store: OutlookStore, auth: OutlookAuth) = OutlookController(store, auth,
        OutlookSyncCoordinator(store, auth, graph, clock) { ZoneOffset.UTC }, object : OutlookSyncScheduler {
            override fun ensureScheduled() = Unit
            override fun requestSync() = Unit
            override fun cancel() = Unit
        }, onChanged = {})

    private class FakeAuth(var current: OutlookAccount?, val signInResult: suspend () -> OutlookAccount? = { current }) : OutlookAuth {
        override suspend fun account() = current
        override suspend fun token(forceRefresh: Boolean) = "test"
        override suspend fun signIn(activity: Activity): OutlookAccount? = signInResult().also { if (it != null) current = it }
        override suspend fun signOut() { current = null }
    }
}
