package com.smartworkalarm.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AlarmRingingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun alarmScreen_showsAlarmNameAndConfiguredSnoozeMinutes() {
        composeRule.setContent {
            AlarmRingingScreen(
                alarmName = "주간 출근",
                timeText = "07:00",
                snoozeMinutes = 17,
                isShiftAlarm = true,
                calendarWarning = false,
                onSnooze = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("07:00").assertIsDisplayed()
        composeRule.onNodeWithText("주간 출근").assertIsDisplayed()
        composeRule.onNodeWithText("17분 후 다시 울림").assertIsDisplayed()
        composeRule.onNodeWithText("알람 해제").assertIsDisplayed()
    }
}
