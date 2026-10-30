package com.smartworkalarm.presentation

import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import com.smartworkalarm.MainActivity
import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRefreshLifecycleTest {
    @Test fun openingAndReturningToAppBothRefreshCalendars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var revision = 0L
            val firstDeadline = SystemClock.elapsedRealtime() + 30_000
            while (revision == 0L && SystemClock.elapsedRealtime() < firstDeadline) {
                scenario.onActivity { revision = it.calendarRefresh.state.value.revision }
                SystemClock.sleep(100)
            }
            assertTrue("Opening should finish an automatic refresh", revision > 0)
            val first = revision
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            val nextDeadline = SystemClock.elapsedRealtime() + 30_000
            while (revision <= first && SystemClock.elapsedRealtime() < nextDeadline) {
                scenario.onActivity { revision = it.calendarRefresh.state.value.revision }
                SystemClock.sleep(100)
            }
            assertTrue("Returning should refresh again", revision > first)
        }
    }
}
