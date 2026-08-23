package com.smartworkalarm.worker

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class RescheduleAlarmsUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val zoneId: ZoneId,
    private val shiftRepository: ShiftAlarmRepository,
    private val shiftScheduler: ShiftAlarmScheduler,
) {
    suspend fun execute(now: Instant) {
        repository.getEnabled().forEach { alarm ->
            calculator.execute(alarm, now, zoneId)?.let { scheduler.schedule(alarm.id, it) }
        }
        shiftRepository.getEnabledShiftTypes().forEach { shiftType ->
            calculator.execute(
                Alarm(
                    shiftType.id, shiftType.name, shiftType.hour, shiftType.minute, shiftType.repeatDaysMask,
                    shiftType.enabled, false, ringtoneUri = shiftType.ringtoneUri,
                    skipOnPublicHoliday = shiftType.skipOnPublicHoliday,
                    gradualVolume = shiftType.gradualVolume, snoozeMinutes = shiftType.snoozeMinutes,
                ),
                now,
                zoneId,
            )?.let { shiftScheduler.scheduleShift(shiftType.id, it) }
        }
    }
}
