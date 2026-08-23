package com.smartworkalarm.alarm

object GradualVolume {
    fun atSecond(second: Int): Float =
        (0.05f + 0.95f * (second.coerceIn(0, 60) / 60f)).coerceIn(0.05f, 1f)
}
