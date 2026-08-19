package com.smartworkalarm.worker

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class RescheduleAlarmsUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val zoneId: ZoneId,
) {
    suspend fun execute(now: Instant) {
        repository.getEnabled().forEach { alarm ->
            calculator.execute(alarm, now, zoneId)?.let { scheduler.schedule(alarm.id, it) }
        }
    }
}
