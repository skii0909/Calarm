package com.smartworkalarm.preview

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object PreviewDeliveryWindow {
    fun isCurrent(targetDate: LocalDate, now: Instant, zoneId: ZoneId): Boolean {
        val localNow = now.atZone(zoneId)
        return localNow.toLocalDate() == targetDate.minusDays(1) &&
            !localNow.toLocalTime().isBefore(LocalTime.of(22, 0))
    }
}
