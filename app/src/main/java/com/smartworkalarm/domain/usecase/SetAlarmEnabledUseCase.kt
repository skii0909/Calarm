package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class SetAlarmEnabledUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    suspend fun execute(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        if (!enabled) {
            scheduler.cancel(id)
            return
        }
        val alarm = repository.getById(id) ?: return
        calculator.execute(alarm, clock.instant(), zoneId)?.let { scheduler.schedule(id, it) }
    }
}
