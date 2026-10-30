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
import kotlinx.coroutines.CancellationException
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.alarm.AlarmScheduleAuditStore

class RescheduleAlarmsUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val zoneId: ZoneId,
    private val shiftRepository: ShiftAlarmRepository,
    private val shiftScheduler: ShiftAlarmScheduler,
    private val audit: AlarmScheduleAuditStore? = null,
) {
    suspend fun execute(now: Instant) {
        var firstFailure: Exception? = null
        fun attempt(block: () -> Unit) {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (firstFailure == null) firstFailure = error }
        }
        repository.getEnabled().forEach { alarm ->
            attempt {
                val pending = runCatching { audit?.nextTriggerFor("regular:${alarm.id}") }.getOrNull()
                val next = recentPendingOccurrence(pending, now) ?: calculator.execute(alarm, now, zoneId)
                next?.let {
                    check(scheduler.schedule(alarm.id, it) == ScheduleResult.Scheduled) { "정확한 알람 권한이 필요합니다." }
                }
            }
        }
        shiftRepository.getEnabledShiftTypes().forEach { shiftType ->
            attempt {
            val pending = runCatching { audit?.nextTriggerFor("shift:${shiftType.id}") }.getOrNull()
            val next = recentPendingOccurrence(pending, now) ?: calculator.execute(
                Alarm(
                    shiftType.id, shiftType.name, shiftType.hour, shiftType.minute, shiftType.repeatDaysMask,
                    shiftType.enabled, false, ringtoneUri = shiftType.ringtoneUri,
                    skipOnPublicHoliday = shiftType.skipOnPublicHoliday,
                    gradualVolume = shiftType.gradualVolume,
                    snoozeEnabled = shiftType.snoozeEnabled,
                    snoozeMinutes = shiftType.snoozeMinutes,
                ),
                now,
                zoneId,
            )
            next?.let { check(shiftScheduler.scheduleShift(shiftType.id, it) == ScheduleResult.Scheduled) { "정확한 알람 권한이 필요합니다." } }
            }
        }
        firstFailure?.let { throw it }
    }
}

// Recovery near an occurrence must not replace a just-due alarm with tomorrow's.
internal fun recentPendingOccurrence(pending: Instant?, now: Instant): Instant? =
    pending?.takeIf { !it.isAfter(now) && !it.isBefore(now.minusSeconds(120)) }
