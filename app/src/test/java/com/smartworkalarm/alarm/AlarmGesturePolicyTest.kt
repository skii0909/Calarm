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
    fun `drag displacement uses current absolute position from original down`() {
        val down = Offset(120f, 80f)

        assertEquals(Offset(90f, -50f), dragDisplacement(down, Offset(210f, 30f)))
        assertEquals(Offset(100f, 0f), dragDisplacement(down, Offset(220f, 80f)))
    }

    @Test
    fun `final pointer up sample can cross the dismiss threshold`() {
        val sample = resolveDragSample(
            downPosition = Offset(20f, 40f),
            currentPosition = Offset(121f, 40f),
            thresholdPx = 100f,
            cancelled = false,
        )

        assertEquals(Offset(101f, 0f), sample.offset)
        assertTrue(sample.dismiss)
    }

    @Test
    fun `cancelled drag resolves to center without dismissal`() {
        val sample = resolveDragSample(
            downPosition = Offset(20f, 40f),
            currentPosition = Offset(220f, 40f),
            thresholdPx = 100f,
            cancelled = true,
        )

        assertEquals(Offset.Zero, sample.offset)
        assertFalse(sample.dismiss)
    }

    @Test
    fun `hold progress follows monotonic elapsed time independent of animation scale`() {
        assertEquals(0f, snoozeHoldProgress(elapsedMillis = 0L))
        assertEquals(1f / 3f, snoozeHoldProgress(elapsedMillis = 1_000L), 0.0001f)
        assertTrue(snoozeHoldProgress(elapsedMillis = 2_999L) < 1f)
        assertEquals(1f, snoozeHoldProgress(elapsedMillis = 3_000L))
        assertEquals(1f, snoozeHoldProgress(elapsedMillis = 4_000L))
    }

    @Test
    fun `dismiss threshold scales from phone to tablet within ergonomic bounds`() {
        val density = 3f
        val compactPhone = dismissThresholdPx(320f * density, 700f * density, density) / density
        val phone = dismissThresholdPx(400f * density, 850f * density, density) / density
        val tablet = dismissThresholdPx(800f * density, 1_100f * density, density) / density

        assertEquals(89.6f, compactPhone, 0.01f)
        assertEquals(112f, phone, 0.01f)
        assertEquals(160f, tablet, 0.01f)
        assertTrue(compactPhone in 88f..160f)
        assertTrue(tablet in 88f..160f)
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
