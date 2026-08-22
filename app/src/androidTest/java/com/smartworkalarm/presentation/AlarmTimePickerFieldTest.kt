package com.smartworkalarm.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smartworkalarm.presentation.alarmedit.AlarmTimePickerField
import org.junit.Rule
import org.junit.Test

class AlarmTimePickerFieldTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun fiveAmIsDisplayedWithoutNumericKeyboardAndOpensPicker() {
        composeRule.setContent {
            AlarmTimePickerField(hour = 5, minute = 0, onTimeSelected = { _, _ -> })
        }

        composeRule.onNodeWithText("05:00").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("시간 선택").assertIsDisplayed()
    }
}
