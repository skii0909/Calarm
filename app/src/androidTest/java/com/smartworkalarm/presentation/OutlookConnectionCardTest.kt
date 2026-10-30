package com.smartworkalarm.presentation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smartworkalarm.outlook.*
import com.smartworkalarm.presentation.settings.OutlookConnectionCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OutlookConnectionCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingAppRegistrationDisablesLoginAndExplainsAvailability() {
        content(OutlookUiState(OutlookState(), configured = false))
        compose.onNodeWithText("Microsoft 로그인").assertIsNotEnabled()
        compose.onNodeWithText("Microsoft 로그인 설정이 아직 완료되지 않았습니다.", substring = true).assertIsDisplayed()
    }

    @Test fun configuredLoginStartsAuthenticationFromCalarm() {
        var requested = false
        content(OutlookUiState(OutlookState(), configured = true), onConnect = { requested = true })
        compose.onNodeWithText("Microsoft 로그인").performClick()
        compose.runOnIdle { assertEquals(true, requested) }
    }

    @Test fun calendarSelectionKeepsAccountAndOpaqueCalendarIdentity() {
        var selected: Pair<OutlookCalendarKey, Boolean>? = null
        val state = OutlookState(account = OutlookAccount("account", "me@example.com"),
            calendars = listOf(OutlookCalendar("opaque/id==", "업무", true)), catalogLoaded = true)
        content(OutlookUiState(state, configured = true), onSelect = { key, value -> selected = key to value })
        compose.onNodeWithContentDescription("Outlook 업무 선택").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(OutlookCalendarKey("account", "opaque/id==") to true, selected) }
    }

    @Test fun revokedAccessShowsReauthenticationAction() {
        content(OutlookUiState(OutlookState(account = OutlookAccount("a", "me@example.com"), issue = OutlookIssue.REAUTH_REQUIRED), configured = true))
        compose.onNodeWithText("Microsoft 다시 로그인").assertIsDisplayed()
        compose.onNodeWithText("지금 새로고침").performScrollTo().assertIsNotEnabled()
    }

    private fun content(state: OutlookUiState, onConnect: () -> Unit = {}, onSelect: (OutlookCalendarKey, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlookConnectionCard(state, onConnect, {}, {}, onSelect)
                }
            }
        }
    }
}
