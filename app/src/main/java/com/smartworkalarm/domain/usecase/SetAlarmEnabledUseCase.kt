package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
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
        try {
            calculator.execute(alarm, clock.instant(), zoneId)?.let {
                check(scheduler.schedule(id, it) == ScheduleResult.Scheduled) {
                    "정확한 알람 권한이 없어 알람을 켜지 못했습니다. 설정에서 권한을 허용해 주세요."
                }
            }
        } catch (error: Exception) {
            repository.setEnabled(id, false)
            throw error
        }
    }
}
