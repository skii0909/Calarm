package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class CalculateNextOccurrenceUseCase @Inject constructor() {
    fun execute(alarm: Alarm, now: Instant, zoneId: ZoneId): Instant? {
        if (!alarm.enabled || alarm.repeatDaysMask == 0) return null

        val today = now.atZone(zoneId).toLocalDate()
        return (0L..7L)
            .asSequence()
            .map(today::plusDays)
            .filter { alarm.repeatsOn(it.dayOfWeek) }
            .map { ZonedDateTime.of(it, LocalTime.of(alarm.hour, alarm.minute), zoneId).toInstant() }
            .firstOrNull { it > now }
    }
}
