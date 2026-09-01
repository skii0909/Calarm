package com.smartworkalarm.alarm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

internal const val SNOOZE_HOLD_MILLIS = 3_000L
internal const val DISMISS_THRESHOLD_FRACTION = 0.28f
// Preserve proportional effort across safe screen sizes without becoming unreachable on tablets.
internal const val MIN_DISMISS_THRESHOLD_DP = 88f
internal const val MAX_DISMISS_THRESHOLD_DP = 160f

internal fun shouldDismiss(distancePx: Float, thresholdPx: Float): Boolean =
    distancePx >= thresholdPx

internal fun dismissProgress(distancePx: Float, thresholdPx: Float): Float =
    (distancePx / thresholdPx).coerceIn(0f, 1f)

internal fun dragDisplacement(downPosition: Offset, currentPosition: Offset): Offset =
    currentPosition - downPosition

internal data class ResolvedDragSample(
    val offset: Offset,
    val dismiss: Boolean,
)

internal fun resolveDragSample(
    downPosition: Offset,
    currentPosition: Offset,
    thresholdPx: Float,
    cancelled: Boolean,
): ResolvedDragSample {
    if (cancelled) return ResolvedDragSample(Offset.Zero, dismiss = false)
    val offset = dragDisplacement(downPosition, currentPosition)
    return ResolvedDragSample(offset, shouldDismiss(offset.getDistance(), thresholdPx))
}

internal fun dismissReleaseTarget(
    sample: ResolvedDragSample,
    exitDistancePx: Float,
): Offset {
    if (!sample.dismiss) return Offset.Zero
    val distance = sample.offset.getDistance()
    if (distance == 0f) return Offset.Zero
    val targetDistance = max(distance, exitDistancePx)
    val scale = targetDistance / distance
    return Offset(sample.offset.x * scale, sample.offset.y * scale)
}

internal fun snoozeHoldProgress(elapsedMillis: Long): Float =
    (elapsedMillis.coerceAtLeast(0L).toFloat() / SNOOZE_HOLD_MILLIS).coerceIn(0f, 1f)

internal fun dismissThresholdPx(
    safeWidthPx: Float,
    safeHeightPx: Float,
    density: Float,
): Float {
    val minPx = MIN_DISMISS_THRESHOLD_DP * density
    val maxPx = MAX_DISMISS_THRESHOLD_DP * density
    return (min(safeWidthPx, safeHeightPx) * DISMISS_THRESHOLD_FRACTION)
        .coerceIn(minPx, maxPx)
}

internal fun alarmTitleFontSize(titleLength: Int): TextUnit = when {
    titleLength <= 10 -> 48.sp
    titleLength <= 24 -> 40.sp
    else -> 34.sp
}
