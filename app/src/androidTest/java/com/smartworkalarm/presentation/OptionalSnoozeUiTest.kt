package com.smartworkalarm.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.presentation.alarmedit.AlarmEditScreenForTest
import com.smartworkalarm.presentation.alarmlist.AlarmRow
import com.smartworkalarm.presentation.shift.ShiftTypeCard
import com.smartworkalarm.presentation.shift.ShiftTypeEditScreen
import com.smartworkalarm.presentation.shift.ShiftTypeEditState
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import org.junit.Rule
import org.junit.Test

class OptionalSnoozeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun alarmSnoozeControlsOnlyAppearWhenEnabled() {
        compose.setContent {
            WorkAlarmTheme {
                AlarmEditScreenForTest(snoozeEnabled = false, snoozeMinutes = 17)
            }
        }

        compose.onNodeWithText("17분").assertDoesNotExist()
        compose.onNodeWithText("다시 울림 사용").assertIsDisplayed()
    }

    @Test
    fun shiftSnoozeControlsOnlyAppearWhenEnabled() {
        compose.setContent {
            WorkAlarmTheme {
                ShiftTypeEditScreen(ShiftTypeEditState(snoozeEnabled = false, snoozeMinutes = 17))
            }
        }

        compose.onNodeWithText("17분", substring = true).assertDoesNotExist()
        compose.onNodeWithText("다시 울림 사용").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun disabledAlarmDoesNotShowSnoozePill() {
        compose.setContent {
            WorkAlarmTheme {
                AlarmRow(alarm(snoozeEnabled = false), onEdit = {}, onEnabled = { _, _ -> })
            }
        }

        compose.onNodeWithText("17분 후").assertDoesNotExist()
    }

    @Test
    fun disabledShiftTypeDoesNotShowSnoozePill() {
        compose.setContent {
            WorkAlarmTheme {
                ShiftTypeCard(shiftType(snoozeEnabled = false), onClick = {})
            }
        }

        compose.onNodeWithText("17분 후").assertDoesNotExist()
    }

    private fun alarm(snoozeEnabled: Boolean) = Alarm(
        id = 1,
        name = "출근",
        hour = 7,
        minute = 0,
        repeatDaysMask = 1,
        enabled = true,
        skipOnCalendarMatch = false,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = 17,
    )

    private fun shiftType(snoozeEnabled: Boolean) = ShiftTypeAlarm(
        id = 1,
        groupId = 1,
        name = "주간",
        exactTitles = listOf("주간"),
        hour = 7,
        minute = 0,
        repeatDaysMask = 1,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = 17,
        skipOnPublicHoliday = false,
    )
}
