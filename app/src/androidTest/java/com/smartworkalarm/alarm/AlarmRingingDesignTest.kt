package com.smartworkalarm.alarm

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Reviewable screenshots of the actual ringing composable, with decorative motion disabled. */
class AlarmRingingDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun normalRingingShowsDistinctSnoozeAndDismissControls() {
        content()

        compose.onNodeWithText("주간 출근").assertIsDisplayed()
        compose.onNodeWithText("10분 후 다시 울림").assertIsDisplayed()
        compose.onNodeWithText("원을 원하는 방향으로 밀어 해제").assertIsDisplayed()
        assertSeparatedControls()
        saveScreen("ringing-normal.png")
    }

    @Test
    fun largeFontKeepsPersistentErrorAndControlsVisible() {
        content(fontScale = 1.5f, persistentError = "다시 울림 예약 실패 · 다시 시도해 주세요")

        compose.onNodeWithTag(ALARM_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ALARM_ACTION_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithText("3초간 길게 누르기").assertIsDisplayed()
        assertSeparatedControls()
        saveScreen("ringing-large-font-error.png")
    }

    private fun content(fontScale: Float = 1f, persistentError: String? = null) {
        compose.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                WorkAlarmTheme {
                    AlarmRingingScreen(
                        presentation = AlarmPresentation(
                            alarmName = "주간 출근",
                            isShiftAlarm = true,
                            snoozeEnabled = true,
                            snoozeMinutes = 10,
                        ),
                        persistentError = persistentError,
                        animationsDisabledOverride = true,
                        onDismiss = {},
                        onSnooze = {},
                    )
                }
            }
        }
    }

    private fun assertSeparatedControls() {
        val snooze = compose.onNodeWithTag(SNOOZE_HOLD_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dismiss = compose.onNodeWithTag(DISMISS_HANDLE_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Snooze and dismiss targets must not overlap", snooze.bottom <= dismiss.top)
    }

    private fun saveScreen(name: String) {
        val directory = InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory).mkdirs()
        File(directory, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
