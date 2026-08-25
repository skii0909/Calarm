package com.smartworkalarm.alarm

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import java.time.Instant
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

    @Test
    fun thresholdCrossingContainedOnlyInPointerUpDismisses() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            down(center)
            updatePointerTo(pointerId = 0, position = center + Offset(320f, 0f))
            up()
        }

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun pointerCancellationReturnsHandleToCenterWithoutDismissal() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })
        val originalCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(120f, 0f))
            cancel()
        }

        composeRule.waitForIdle()
        val restoredCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center
        assertEquals(0, dismissCount)
        assertEquals(originalCenter.x, restoredCenter.x, 1f)
        assertEquals(originalCenter.y, restoredCenter.y, 1f)
    }

    @Test
    fun secondPointerCancelsDragAndReturnsHandleToCenter() {
        var dismissCount = 0
        setRingingContent(onDismiss = { dismissCount += 1 })
        val originalCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            down(pointerId = 0, position = center)
            moveTo(pointerId = 0, position = center + Offset(120f, 0f))
            down(pointerId = 1, position = center + Offset(20f, 20f))
            up(pointerId = 1)
            up(pointerId = 0)
        }

        composeRule.waitForIdle()
        val restoredCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center
        assertEquals(0, dismissCount)
        assertEquals(originalCenter.x, restoredCenter.x, 1f)
        assertEquals(originalCenter.y, restoredCenter.y, 1f)
    }

    @Test
    fun reducedMotionHoldProgressTracksElapsedTimeAndDoesNotCompleteEarly() {
        var snoozeCount = 0
        setRingingContent(
            animationsDisabledOverride = true,
            onSnooze = { snoozeCount += 1 },
        )
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, snoozeCount) }
        composeRule.mainClock.advanceTimeBy(1_899L)
        composeRule.runOnIdle { assertEquals(0, snoozeCount) }
        composeRule.mainClock.advanceTimeBy(101L)
        composeRule.runOnIdle { assertEquals(1, snoozeCount) }
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { up() }
    }

    @Test
    fun changingSessionMidHoldRequiresANewCompleteHold() {
        var snoozeCount = 0
        var presentation by androidx.compose.runtime.mutableStateOf(presentation(7L))
        composeRule.setContent {
            AlarmRingingScreen(
                presentation = presentation,
                onSnooze = { snoozeCount += 1 },
                onDismiss = {},
            )
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(SNOOZE_HOLD_MILLIS - 100L)
        composeRule.runOnIdle { presentation = presentation(8L) }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.runOnIdle { assertEquals(0, snoozeCount) }
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { cancel() }

        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(SNOOZE_HOLD_MILLIS + 100L)
        composeRule.runOnIdle { assertEquals(1, snoozeCount) }
        composeRule.onNodeWithTag(SNOOZE_HOLD_TAG).performTouchInput { up() }
    }

    @Test
    fun changingSessionMidDragReturnsNewHandleToCenterAndRequiresFreshDrag() {
        var dismissCount = 0
        var presentation by androidx.compose.runtime.mutableStateOf(presentation(7L))
        composeRule.setContent {
            AlarmRingingScreen(
                presentation = presentation,
                onSnooze = {},
                onDismiss = { dismissCount += 1 },
            )
        }
        val originalCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(120f, 0f))
        }
        composeRule.runOnIdle { presentation = presentation(8L) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput { cancel() }

        val replacementCenter = composeRule.onNodeWithTag(DISMISS_HANDLE_TAG)
            .fetchSemanticsNode().boundsInRoot.center
        assertEquals(0, dismissCount)
        assertEquals(originalCenter.x, replacementCenter.x, 1f)
        assertEquals(originalCenter.y, replacementCenter.y, 1f)

        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(320f, 0f), durationMillis = 300L)
        }
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun sameRootDragDistanceUsesSameThresholdWithOrWithoutSnooze() {
        var withSnoozeDismisses = 0
        var withoutSnoozeDismisses = 0
        var snoozeEnabled by androidx.compose.runtime.mutableStateOf(true)
        composeRule.setContent {
            AlarmRingingScreen(
                presentation = presentation(7L).copy(snoozeEnabled = snoozeEnabled),
                onSnooze = {},
                onDismiss = {
                    if (snoozeEnabled) withSnoozeDismisses += 1
                    else withoutSnoozeDismisses += 1
                },
            )
        }
        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(320f, 0f), durationMillis = 300L)
        }

        composeRule.runOnIdle { snoozeEnabled = false }
        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(320f, 0f), durationMillis = 300L)
        }

        composeRule.runOnIdle {
            assertEquals(1, withSnoozeDismisses)
            assertEquals(1, withoutSnoozeDismisses)
        }
    }

    @Test
    fun persistentFailureStaysVisibleAndDisabledControlsIgnoreInput() {
        var dismissCount = 0
        setRingingContent(
            actionsEnabled = false,
            persistentError = "다시 울림 예약 실패",
            onDismiss = { dismissCount += 1 },
        )

        composeRule.onNodeWithText("다시 울림 예약 실패").assertIsDisplayed()
        composeRule.onNodeWithTag(DISMISS_HANDLE_TAG).performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(0, dismissCount) }
    }

    private fun setRingingContent(
        snoozeEnabled: Boolean = true,
        snoozeMinutes: Int = 17,
        alarmName: String = "주간 출근",
        actionsEnabled: Boolean = true,
        persistentError: String? = null,
        animationsDisabledOverride: Boolean? = null,
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
                actionsEnabled = actionsEnabled,
                persistentError = persistentError,
                animationsDisabledOverride = animationsDisabledOverride,
                onSnooze = onSnooze,
                onDismiss = onDismiss,
            )
        }
    }

    private fun presentation(id: Long): AlarmPresentation = AlarmPresentation(
        sessionKey = RingingSessionKey.create(
            isShiftAlarm = false,
            alarmId = id,
            scheduledOccurrenceAt = Instant.ofEpochMilli(1_000L + id),
        ),
        alarmId = id,
        alarmName = "알람 $id",
        snoozeEnabled = true,
        snoozeMinutes = 17,
        scheduledOccurrenceAt = 1_000L + id,
    )
}
