package com.smartworkalarm.alarm

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal const val SNOOZE_HOLD_MILLIS = 3_000L

internal fun shouldDismiss(distancePx: Float, thresholdPx: Float): Boolean =
    distancePx >= thresholdPx

internal fun dismissProgress(distancePx: Float, thresholdPx: Float): Float =
    (distancePx / thresholdPx).coerceIn(0f, 1f)

internal fun alarmTitleFontSize(titleLength: Int): TextUnit = when {
    titleLength <= 10 -> 48.sp
    titleLength <= 24 -> 40.sp
    else -> 34.sp
}
