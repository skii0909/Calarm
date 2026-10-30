package com.smartworkalarm.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import com.smartworkalarm.presentation.alarmlist.*
import com.smartworkalarm.domain.model.Alarm
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class HomeDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeShowsNextAlarmAndAnchoredAddAction() {
        content()
        compose.onNodeWithText("NEXT ALARM").assertIsDisplayed()
        compose.onNodeWithText("새 알람").assertIsDisplayed()
        screenshot("home-overview.png")
        compose.onNodeWithTag("following-alarm-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("새 알람").assertIsDisplayed()
        screenshot("home-following.png")
    }

    @Test fun largeTypeKeepsAdditionAndRefreshAccessible() {
        content(1.5f)
        compose.onNodeWithText("새 알람").assertIsDisplayed()
        compose.onNodeWithTag("alarm-list").performScrollToNode(hasText("상세"))
        compose.onNodeWithText("상세").performClick()
        compose.onNodeWithText("지금 새로고침").performScrollTo().assertIsDisplayed()
        screenshot("home-large-font.png")
    }

    @Test fun emptyHomeExplainsHowToStart() {
        content(empty = true)
        compose.onNodeWithText("나에게 맞는 아침을 시작하세요").assertIsDisplayed()
        compose.onNodeWithText("새 알람").assertIsDisplayed()
        screenshot("home-empty.png")
    }

    private fun content(scale: Float = 1f, empty: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                WorkAlarmTheme {
                    val alarm = Alarm(1, "출근 알람", 6, 40, 31, true, true, skipOnPublicHoliday = true)
                    val upcoming = UpcomingAlarms(if (empty) emptyList() else (0..3).map {
                        NextAlarmCardItem(1, "출근 알람", LocalDate.of(2026, 10, 12).plusDays(it.toLong()).atTime(6, 40).atZone(ZoneId.systemDefault()).toInstant(), false)
                    }, false)
                    AlarmListScaffold(if (empty) emptyList() else listOf(alarm), emptyList(), upcoming, false,
                        "마지막 확인 21:04:12\nGoogle 동기화 요청 · 기기 일정으로 확인", {}, {}, {}, {}, {}, {}, { _, _ -> })
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(directory).mkdirs()
        java.io.File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
