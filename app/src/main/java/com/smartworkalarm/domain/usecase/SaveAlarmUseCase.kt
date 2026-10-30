package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import com.smartworkalarm.presentation.alarmedit.AlarmSaver

class SaveAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : AlarmSaver {
    override suspend fun save(alarm: Alarm): Result<Long> = execute(alarm)

    suspend fun execute(alarm: Alarm): Result<Long> = runCatching {
        require(alarm.name.isNotBlank()) { "알람 이름을 입력하세요" }
        require(alarm.hour in 0..23 && alarm.minute in 0..59) { "올바른 시간을 입력하세요" }
        val previous = if (alarm.id != 0L) repository.getById(alarm.id) else null
        val id = repository.upsert(alarm.copy(name = alarm.name.trim()))
        val saved = alarm.copy(id = id, name = alarm.name.trim())
        try {
            val next = calculator.execute(saved, clock.instant(), zoneId)
            if (next != null) {
                check(scheduler.schedule(id, next) == ScheduleResult.Scheduled) {
                    "정확한 알람 권한이 없어 예약하지 못했습니다. 설정에서 권한을 허용해 주세요."
                }
            } else scheduler.cancel(id)
        } catch (error: Exception) {
            if (previous != null) repository.upsert(previous) else repository.setEnabled(id, false)
            throw error
        }
        // The new reservation is committed; optional cleanup must not roll back only its DB row.
        if (saved.enabled) runCatching { scheduler.cancelSnooze(id) }
        id
    }
}
