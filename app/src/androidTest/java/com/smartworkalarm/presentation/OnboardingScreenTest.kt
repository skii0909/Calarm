package com.smartworkalarm.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smartworkalarm.presentation.onboarding.OnboardingScreen
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun outlookSelectionExplainsThatItIsNotAvailableInV1() {
        composeRule.setContent { OnboardingScreen(onComplete = {}) }

        composeRule.onNodeWithText("Outlook Calendar (준비 중)").performClick()

        composeRule.onNodeWithText("Outlook Calendar는 준비 중입니다.").assertIsDisplayed()
    }
}
