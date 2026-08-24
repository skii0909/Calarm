package com.smartworkalarm.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smartworkalarm.permissions.RequiredPermission
import com.smartworkalarm.presentation.settings.AccountSelection
import com.smartworkalarm.presentation.settings.CalendarAccountUiState
import com.smartworkalarm.presentation.settings.CalendarCatalogStatus
import com.smartworkalarm.presentation.settings.PermissionUiState
import com.smartworkalarm.presentation.settings.SettingsCalendarUiState
import com.smartworkalarm.presentation.settings.SettingsScreen
import com.smartworkalarm.presentation.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsNightPulseCardsPermissionStatusAndSelectedCount() {
        setSettingsContent()

        compose.onNodeWithText("연결 및 권한").assertIsDisplayed()
        compose.onNodeWithText("캘린더 읽기").assertIsDisplayed()
        compose.onNodeWithText("허용됨").assertIsDisplayed()
        compose.onNodeWithText("필요", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("사용 중인 캘린더").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("me@example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1/2 선택").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Outlook — 준비 중").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expandsAccountAndShowsIndividuallySelectableCalendars() {
        setSettingsContent()

        compose.onNodeWithText("개인").assertDoesNotExist()
        compose.onNodeWithContentDescription("me@example.com 펼치기").performScrollTo().performClick()

        compose.onNodeWithText("개인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("업무").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("개인 선택됨").assertIsDisplayed()
        compose.onNodeWithContentDescription("업무 선택 안 됨").assertIsDisplayed()
    }

    @Test
    fun helpCardsExplainSameScreenSnoozeAndSelectedCalendarFiltering() {
        setSettingsContent()

        compose.onNodeWithText("일반 알람").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("설정한 5~30분 뒤 같은 알람 화면으로 다시 울립니다.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("캘린더 자동 건너뛰기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("선택한 캘린더의 일정만 읽습니다.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("교대근무").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("22시 확인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("공휴일·알람음·다시 울림").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("안전 동작").performScrollTo().assertIsDisplayed()
    }

    private fun setSettingsContent() {
        val account = CalendarAccountUiState(
            key = "me@example.com",
            displayName = "me@example.com",
            calendars = listOf(
                SettingsCalendarUiState(1, "개인", selected = true),
                SettingsCalendarUiState(2, "업무", selected = false),
            ),
        )
        check(account.selection == AccountSelection.PARTIAL)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(CalendarCatalogStatus.READY, listOf(account)),
                    permissions = listOf(
                        PermissionUiState(RequiredPermission.CALENDAR, granted = true),
                        PermissionUiState(RequiredPermission.NOTIFICATIONS, granted = false),
                    ),
                    onPermissionAction = {},
                    onSetCalendarSelected = { _, _ -> },
                    onSetAccountSelected = { _, _ -> },
                    onBack = {},
                )
            }
        }
    }
}
