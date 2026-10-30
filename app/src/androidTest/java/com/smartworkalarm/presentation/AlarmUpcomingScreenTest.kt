package com.smartworkalarm.presentation

import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartworkalarm.presentation.alarmlist.AlarmListScaffold
import com.smartworkalarm.presentation.alarmlist.NextAlarmCardItem
import com.smartworkalarm.presentation.alarmlist.UpcomingAlarms
import java.time.Instant
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmUpcomingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsHeroAndThreeFollowingAlarmsWithUncertainty() {
        content()
        compose.onNodeWithText("NEXT ALARM").assertIsDisplayed()
        compose.onNodeWithText("알람 0").assertIsDisplayed()
        saveScreen("upcoming-hero.png")
        compose.onNodeWithTag("following-alarm-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("알람 3").assertIsDisplayed()
        compose.onNodeWithTag("following-alarm-3").assertDoesNotExist()
        compose.onNodeWithText("캘린더 확인 필요", substring = false).assertExists()
        compose.onNodeWithTag("alarm-list").performScrollToIndex(2)
        saveScreen("upcoming-following.png")
    }

    @Test fun downwardSwipeRequestsRefreshFromTheList() {
        var requests = 0
        content { requests++ }
        compose.onNodeWithTag("alarm-list").performTouchInput { swipeDown(durationMillis = 700) }
        compose.waitUntil(5_000) { requests > 0 }
        assertTrue(requests > 0)
    }

    private fun content(onRefresh: () -> Unit = {}) {
        compose.setContent {
            WorkAlarmTheme {
                AlarmListScaffold(emptyList(), emptyList(), UpcomingAlarms((0..3).map {
                    NextAlarmCardItem(it.toLong(), "알람 $it", Instant.parse("2026-10-06T22:00:00Z").plusSeconds(it * 86400L), false, it == 3)
                }, true), false, "마지막 확인 19:00:00", onRefresh,
                    {}, {}, {}, {}, {}, { _, _ -> })
            }
        }
    }

    private fun saveScreen(name: String) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(directory).mkdirs()
        java.io.File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
