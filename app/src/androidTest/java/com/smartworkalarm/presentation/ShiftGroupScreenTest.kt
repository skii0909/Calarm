package com.smartworkalarm.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.presentation.shift.CalendarQueryStatus
import com.smartworkalarm.presentation.shift.ShiftGroupEditState
import com.smartworkalarm.presentation.shift.ShiftGroupScreen
import com.smartworkalarm.presentation.shift.ShiftTypeEditScreen
import com.smartworkalarm.presentation.shift.ShiftTypeEditState
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ShiftGroupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun groupScreenShowsCoreSetupAndTodayStatus() {
        compose.setContent {
            MaterialTheme {
                ShiftGroupScreen(
                    state = ShiftGroupEditState(
                        id = 1,
                        name = "병원 근무",
                        shiftTypes = listOf(type()),
                        todayStatus = ShiftGroupDayStatus.Matched(type()),
                        tomorrowStatus = ShiftGroupDayStatus.None,
                        calendarConnection = CalendarConnectionState(true, listOf(ConnectedCalendar(1, "근무표"))),
                        lastCalendarQuery = CalendarQueryStatus(Instant.parse("2026-08-23T03:00:00Z"), true),
                    ),
                )
            }
        }

        compose.onNodeWithText("교대 알람 그룹").assertIsDisplayed()
        compose.onNodeWithText("오늘 근무").assertIsDisplayed()
        compose.onNodeWithText("내일 근무").assertIsDisplayed()
        compose.onNodeWithText("연결된 캘린더 1개").assertIsDisplayed()
        compose.onNodeWithText("최근 캘린더 조회 · 성공", substring = true).assertIsDisplayed()
        compose.onNodeWithText("비상 기본 알람").assertIsDisplayed()
    }

    @Test
    fun shiftTypeScreenExplainsExactTitleAliases() {
        compose.setContent {
            MaterialTheme {
                ShiftTypeEditScreen(state = ShiftTypeEditState(groupId = 1))
            }
        }

        compose.onNodeWithText("정확히 일치할 캘린더 제목").assertIsDisplayed()
        compose.onNodeWithText("제목 전체가 일치할 때만 적용됩니다").assertIsDisplayed()
    }

    private fun type() = ShiftTypeAlarm(
        id = 1, groupId = 1, name = "주간", exactTitles = listOf("주간"), hour = 7, minute = 0,
        repeatDaysMask = 127, enabled = true, ringtoneUri = null, gradualVolume = false,
        snoozeMinutes = 5, skipOnPublicHoliday = false,
    )
}
