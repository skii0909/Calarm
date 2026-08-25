package com.smartworkalarm.alarm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmRingingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun enabledSnoozeShowsConfiguredThreeSecondHoldArea() {
        setRingingContent(snoozeEnabled = true, snoozeMinutes = 17)

        composeRule.onNodeWithText("17분 후 다시 울림").assertIsDisplayed()
        composeRule.onNodeWithText("3초간 길게 누르기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "17분 후 다시 울림, 3초간 길게 누르기",
        ).assertIsDisplayed()
    }

    @Test
    fun disabledSnoozeHidesHoldArea() {
        setRingingContent(snoozeEnabled = false)

        composeRule.onNodeWithText("3초간 길게 누르기").assertDoesNotExist()
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).assertDoesNotExist()
    }

    @Test
    fun dragPastThresholdDismissesInAnyDirection() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(280f, -280f), durationMillis = 500L)
        }

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun dragBelowThresholdKeepsAlarmRinging() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(24f, 24f), durationMillis = 200L)
        }

        composeRule.runOnIdle { assertEquals(0, dismissCount) }
    }

    @Test
    fun releasingSnoozeBeforeThreeSecondsCancelsRequest() {
        var snoozeCount = 0
        setRingingContent(onSnooze = { snoozeCount += 1 })
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(SNOOZE_HOLD_MILLIS - 100L)
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { assertEquals(0, snoozeCount) }
    }

    @Test
    fun holdingSnoozeForThreeSecondsCompletesRequest() {
        var snoozeCount = 0
        setRingingContent(onSnooze = { snoozeCount += 1 })
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(SNOOZE_HOLD_MILLIS + 100L)

        composeRule.runOnIdle { assertEquals(1, snoozeCount) }
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { up() }
    }

    @Test
    fun longTitleIsLimitedToTwoLinesAndEllipsized() {
        setRingingContent(alarmName = "아주 긴 출근 준비 알람 ".repeat(20))
        val textLayouts = mutableListOf<TextLayoutResult>()

        composeRule.onNodeWithTag(ALARM_TITLE_TAG).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { action -> action(textLayouts) }

        assertEquals(2, textLayouts.single().lineCount)
        assertTrue(textLayouts.single().hasVisualOverflow)
    }

    @Test
    fun accessibilityClickOnDismissHandleDismissesAlarm() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })

        composeRule.onNodeWithContentDescription("알람 해제").performClick()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    private fun setRingingContent(
        snoozeEnabled: Boolean = true,
        snoozeMinutes: Int = 17,
        alarmName: String = "주간 출근",
        onSnooze: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            AlarmRingingScreen(
                presentation = AlarmPresentation(
                    alarmName = alarmName,
                    isShiftAlarm = true,
                    snoozeEnabled = snoozeEnabled,
                    snoozeMinutes = snoozeMinutes,
                ),
                onSnooze = onSnooze,
                onDismiss = onDismiss,
            )
        }
    }
}
