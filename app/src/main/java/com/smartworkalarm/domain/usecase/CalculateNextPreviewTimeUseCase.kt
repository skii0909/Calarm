package com.smartworkalarm.domain.usecase

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class CalculateNextPreviewTimeUseCase @Inject constructor() {
    fun execute(now: Instant, zoneId: ZoneId): Instant {
        val localNow = now.atZone(zoneId)
        var target = localNow.toLocalDate().atTime(LocalTime.of(22, 0)).atZone(zoneId)
        if (!target.toInstant().isAfter(now)) target = target.plusDays(1)
        return target.toInstant()
    }
}
