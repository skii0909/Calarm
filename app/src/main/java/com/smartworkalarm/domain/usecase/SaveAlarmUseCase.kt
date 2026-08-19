package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class SaveAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    suspend fun execute(alarm: Alarm): Result<Long> = runCatching {
        require(alarm.name.isNotBlank()) { "알람 이름을 입력하세요" }
        require(alarm.hour in 0..23 && alarm.minute in 0..59) { "올바른 시간을 입력하세요" }
        require(alarm.repeatDaysMask != 0) { "반복 요일을 하나 이상 선택하세요" }

        if (alarm.id != 0L) scheduler.cancel(alarm.id)
        val id = repository.upsert(alarm.copy(name = alarm.name.trim()))
        val saved = alarm.copy(id = id, name = alarm.name.trim())
        calculator.execute(saved, clock.instant(), zoneId)?.let { scheduler.schedule(id, it) }
        id
    }
}
