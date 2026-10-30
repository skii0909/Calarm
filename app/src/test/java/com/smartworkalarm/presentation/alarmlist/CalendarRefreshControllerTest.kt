package com.smartworkalarm.presentation.alarmlist

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalendarRefreshControllerTest {
    @Test fun `refresh stays busy until sync ends and overlapping gestures share one refresh`() = runTest {
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val controller = CalendarRefreshController({ calls++; finish.await(); "Google 동기화 요청" }, { null }, emptyFlow())
        val first = launch { controller.refresh() }
        runCurrent()
        assertTrue(controller.state.value.refreshing)
        controller.refresh()
        assertEquals(1, calls)
        assertEquals(0L, controller.state.value.revision)
        finish.complete(Unit)
        first.join()
        assertFalse(controller.state.value.refreshing)
        assertEquals(1L, controller.state.value.revision)
    }

    @Test fun `failure in one calendar does not block the other and ends progress`() = runTest {
        var outlookCalls = 0
        val controller = CalendarRefreshController({ error("offline") }, { outlookCalls++; "Outlook 동기화 완료" }, emptyFlow())
        controller.refresh()
        assertEquals(1, outlookCalls)
        assertFalse(controller.state.value.refreshing)
        assertTrue(controller.state.value.message.contains("Google"))
        assertTrue(controller.state.value.message.contains("실패"))
        assertTrue(controller.state.value.message.contains("Outlook 동기화 완료"))
    }

    @Test fun `hung synchronization times out and can be retried`() = runTest {
        val controller = CalendarRefreshController({ CompletableDeferred<Unit>().await(); null }, { null }, emptyFlow())
        controller.refresh()
        assertFalse(controller.state.value.refreshing)
        assertTrue(controller.state.value.message.contains("시간 초과"))
        assertEquals(5_000L, testScheduler.currentTime)
    }
}
