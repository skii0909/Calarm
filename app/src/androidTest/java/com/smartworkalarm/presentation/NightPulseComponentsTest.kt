package com.smartworkalarm.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.smartworkalarm.presentation.components.DayToggle
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

    @Test fun dayToggleExposesUnmistakableSelectedState() {
        compose.setContent {
            WorkAlarmTheme {
                var selected by remember { mutableStateOf(false) }
                DayToggle("월", selected) { selected = !selected }
            }
        }
        compose.onNodeWithText("월").assertIsNotSelected().performClick().assertIsSelected()
    }
}
