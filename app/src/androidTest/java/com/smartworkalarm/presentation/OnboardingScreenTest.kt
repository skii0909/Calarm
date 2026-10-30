package com.smartworkalarm.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smartworkalarm.presentation.onboarding.OnboardingScreen
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun onboardingIdentifiesCalarmAndItsCalendarAlarmDescriptor() {
        setOnboardingContent()
        saveScreen("onboarding-overview.png")

        composeRule.onNodeWithText("Calarm").assertIsDisplayed()
        composeRule.onNodeWithText("캘람 · 캘린더와 알람의 조합").assertIsDisplayed()
    }

    @Test
    fun outlookSelectionOpensConnectionSettings() {
        var openedSettings = false
        setOnboardingContent { openedSettings = true }
        composeRule.onNodeWithText("Outlook 연결").performScrollTo().performClick()
        composeRule.runOnIdle { check(openedSettings) }
    }

    @Test
    fun googleCalendarActionRoutesToTheUnifiedSettingsFlow() {
        var openedSettings = false
        setOnboardingContent { openedSettings = true }

        composeRule.onNodeWithText("Google Calendar 연결").performScrollTo().performClick()

        composeRule.runOnIdle { check(openedSettings) }
    }

    private fun setOnboardingContent(onOpenCalendarSettings: () -> Unit = {}) {
        composeRule.setContent {
            WorkAlarmTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OnboardingScreen(onComplete = {}, onOpenCalendarSettings = onOpenCalendarSettings)
                }
            }
        }
    }

    private fun saveScreen(name: String) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir") ?: return
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(directory).mkdirs()
        java.io.File(directory, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
