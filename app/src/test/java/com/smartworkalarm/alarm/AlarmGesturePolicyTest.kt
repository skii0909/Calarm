package com.smartworkalarm.alarm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmGesturePolicyTest {
    @Test
    fun `dismiss threshold is direction independent`() {
        val threshold = 100f
        val completedDrags = listOf(
            Offset(101f, 0f),
            Offset(-101f, 0f),
            Offset(0f, 101f),
            Offset(0f, -101f),
            Offset(72f, 72f),
        )

        completedDrags.forEach { offset ->
            assertTrue(shouldDismiss(offset.getDistance(), threshold))
        }
    }

    @Test
    fun `drag below threshold keeps alarm ringing`() {
        assertFalse(shouldDismiss(distancePx = 99f, thresholdPx = 100f))
    }

    @Test
    fun `drag at threshold dismisses alarm`() {
        assertTrue(shouldDismiss(distancePx = 100f, thresholdPx = 100f))
    }

    @Test
    fun `dismiss progress is clamped between zero and one`() {
        assertEquals(0f, dismissProgress(distancePx = -20f, thresholdPx = 100f))
        assertEquals(0.5f, dismissProgress(distancePx = 50f, thresholdPx = 100f))
        assertEquals(1f, dismissProgress(distancePx = 120f, thresholdPx = 100f))
    }

    @Test
    fun `title size steps down at readable length boundaries`() {
        assertEquals(48.sp, alarmTitleFontSize(titleLength = 8))
        assertEquals(48.sp, alarmTitleFontSize(titleLength = 10))
        assertEquals(40.sp, alarmTitleFontSize(titleLength = 18))
        assertEquals(40.sp, alarmTitleFontSize(titleLength = 24))
        assertEquals(34.sp, alarmTitleFontSize(titleLength = 40))
    }
}
