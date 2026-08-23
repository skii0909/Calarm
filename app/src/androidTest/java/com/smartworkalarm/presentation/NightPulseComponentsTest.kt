package com.smartworkalarm.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.smartworkalarm.presentation.components.NightPulseSection
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import org.junit.Rule
import org.junit.Test

class NightPulseComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingSectionShowsTitleSubtitleAndContent() {
        compose.setContent {
            WorkAlarmTheme {
                NightPulseSection("자동 건너뛰기", "쉬는 날은 알아서 조용하게") { Text("공휴일 자동 끄기") }
            }
        }
        compose.onNodeWithText("자동 건너뛰기").assertIsDisplayed()
        compose.onNodeWithText("쉬는 날은 알아서 조용하게").assertIsDisplayed()
        compose.onNodeWithText("공휴일 자동 끄기").assertIsDisplayed()
    }
}
