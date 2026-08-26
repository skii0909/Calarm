package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.AlarmExecutionDecision
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCase
import com.smartworkalarm.prealarm.AlarmKind
import com.smartworkalarm.prealarm.AlarmOccurrenceKey
import com.smartworkalarm.prealarm.OccurrenceSkipStore
import com.smartworkalarm.prealarm.PreAlarmNotificationCanceller
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class ExecutionOutcome {
    Ringing,
    RingingWithCalendarWarning,
    SkippedForVacation,
    SkippedForShift,
    SkippedByUser,
    Ignored,
}

class AlarmExecutionCoordinator internal constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val decisionProvider: suspend (Alarm, java.time.LocalDate, ZoneId) -> AlarmExecutionDecision,
    private val ringer: AlarmRinger,
    private val zoneId: ZoneId,
    private val skipStore: OccurrenceSkipStore,
    private val preAlarmNotifications: PreAlarmNotificationCanceller,
) {
    @Inject
    constructor(
        repository: AlarmRepository,
        scheduler: AlarmScheduler,
        calculator: CalculateNextOccurrenceUseCase,
        shouldRingAlarm: ShouldRingAlarmUseCase,
        ringer: AlarmRinger,
        zoneId: ZoneId,
        skipStore: OccurrenceSkipStore,
        preAlarmNotifications: PreAlarmNotificationCanceller,
    ) : this(
        repository,
        scheduler,
        calculator,
        shouldRingAlarm::execute,
        ringer,
        zoneId,
        skipStore,
        preAlarmNotifications,
    )

    suspend fun execute(
        alarmId: Long,
        isSnooze: Boolean,
        now: Instant,
        scheduledOccurrenceAt: Instant = now,
    ): ExecutionOutcome {
        if (!isSnooze) {
            runCatching { preAlarmNotifications.cancel(AlarmKind.REGULAR, alarmId) }
        }
        val alarm = repository.getById(alarmId) ?: return ExecutionOutcome.Ignored
        if (!alarm.enabled) return ExecutionOutcome.Ignored

        if (!isSnooze) {
            calculator.execute(alarm, now, zoneId)?.let { scheduler.schedule(alarm.id, it) }
            if (skipStore.consume(AlarmOccurrenceKey(AlarmKind.REGULAR, alarm.id, scheduledOccurrenceAt))) {
                return ExecutionOutcome.SkippedByUser
            }
        }

        return when (decisionProvider(alarm, now.atZone(zoneId).toLocalDate(), zoneId)) {
            AlarmExecutionDecision.SKIP_FOR_VACATION -> ExecutionOutcome.SkippedForVacation
            AlarmExecutionDecision.DO_NOT_RING -> ExecutionOutcome.Ignored
            AlarmExecutionDecision.RING,
            AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING,
            -> {
                ringer.startAt(
                    alarm.id,
                    alarm.name,
                    alarm.ringtoneUri,
                    alarm.gradualVolume,
                    alarm.snoozeEnabled,
                    alarm.snoozeMinutes,
                    scheduledOccurrenceAt,
                )
                ExecutionOutcome.Ringing
            }
        }
    }
}
