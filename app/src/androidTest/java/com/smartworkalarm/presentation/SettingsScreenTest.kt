package com.smartworkalarm.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.smartworkalarm.presentation.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun helpExplainsShiftTitleMatchingFallbackAndChangeOnlyNotifications() {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(state = null, onBack = {})
            }
        }

        compose.onNodeWithText("교대 알람 그룹은 캘린더 제목 전체가 등록한 별칭과 정확히 같을 때만 선택됩니다.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("교대 그룹 안에서는 제목 별칭 일부가 겹치는 근무 형태를 저장할 수 없습니다.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("내일 캘린더에 등록한 근무 형태가 없으면 해당 교대 그룹 알람은 울리지 않으며", substring = true).assertIsDisplayed()
        compose.onNodeWithText("같은 날짜에 서로 다른 근무 형태가 함께 있으면 충돌로 판단해", substring = true).assertIsDisplayed()
        compose.onNodeWithText("교대 근무를 조회하지 못하면 지정한 비상 기본 근무 형태 알람만 울립니다.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("교대 근무가 바뀌거나 근무 없음·충돌 상태가 새로 생긴 경우에만 22시에 안내합니다.", substring = true).assertIsDisplayed()
    }
}
