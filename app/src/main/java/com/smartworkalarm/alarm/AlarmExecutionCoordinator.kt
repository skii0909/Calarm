package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class ExecutionOutcome {
    Ringing,
    RingingWithCalendarWarning,
    SkippedForVacation,
    SkippedForShift,
    Ignored,
}

class AlarmExecutionCoordinator internal constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val decisionProvider: suspend (Alarm, java.time.LocalDate, ZoneId) -> AlarmExecutionDecision,
    private val ringer: AlarmRinger,
    private val zoneId: ZoneId,
) {
    @Inject
    constructor(
        repository: AlarmRepository,
        scheduler: AlarmScheduler,
        calculator: CalculateNextOccurrenceUseCase,
        shouldRingAlarm: ShouldRingAlarmUseCase,
        ringer: AlarmRinger,
        zoneId: ZoneId,
    ) : this(
        repository,
        scheduler,
        calculator,
        shouldRingAlarm::execute,
        ringer,
        zoneId,
    )

    suspend fun execute(alarmId: Long, isSnooze: Boolean, now: Instant): ExecutionOutcome {
        val alarm = repository.getById(alarmId) ?: return ExecutionOutcome.Ignored
        if (!alarm.enabled) return ExecutionOutcome.Ignored

        if (!isSnooze) {
            calculator.execute(alarm, now, zoneId)?.let { scheduler.schedule(alarm.id, it) }
        }

        return when (decisionProvider(alarm, now.atZone(zoneId).toLocalDate(), zoneId)) {
            AlarmExecutionDecision.SKIP_FOR_VACATION -> ExecutionOutcome.SkippedForVacation
            AlarmExecutionDecision.DO_NOT_RING -> ExecutionOutcome.Ignored
            AlarmExecutionDecision.RING,
            AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING,
            -> {
                ringer.start(alarm.id, alarm.name, alarm.ringtoneUri, alarm.gradualVolume, alarm.snoozeMinutes)
                ExecutionOutcome.Ringing
            }
        }
    }
}
