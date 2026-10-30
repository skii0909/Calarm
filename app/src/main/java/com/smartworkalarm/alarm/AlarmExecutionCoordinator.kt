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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.smartworkalarm.preview.ActualAlarmSkipStore
import com.smartworkalarm.preview.NoActualAlarmSkipStore

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
    private val actualSkips: ActualAlarmSkipStore = NoActualAlarmSkipStore,
    private val audit: AlarmScheduleAuditStore? = null,
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
        actualSkips: ActualAlarmSkipStore = NoActualAlarmSkipStore,
        audit: AlarmScheduleAuditStore? = null,
    ) : this(
        repository,
        scheduler,
        calculator,
        shouldRingAlarm::execute,
        ringer,
        zoneId,
        skipStore,
        preAlarmNotifications,
        actualSkips,
        audit,
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
        val isOneTime = alarm.repeatDaysMask == 0
        if (!alarm.enabled && !(isSnooze && isOneTime)) return ExecutionOutcome.Ignored
        val alarmForExecution = if (isSnooze && isOneTime) alarm.copy(enabled = true) else alarm

        if (!isSnooze) {
            preserveCurrentAlarm { audit?.recordHandled("regular:$alarmId", scheduledOccurrenceAt) }
            if (isOneTime) {
                preserveCurrentAlarm { repository.setEnabled(alarm.id, false) }
            } else {
                preserveCurrentAlarm { calculator.execute(alarm, now, zoneId)?.let { scheduler.schedule(alarm.id, it) } }
            }
            if (preserveCurrentAlarm { skipStore.consume(AlarmOccurrenceKey(AlarmKind.REGULAR, alarm.id, scheduledOccurrenceAt)) } == true) {
                return ExecutionOutcome.SkippedByUser
            }
        }

        val occurrenceDate = scheduledOccurrenceAt.atZone(zoneId).toLocalDate()
        val decision = preserveCurrentAlarm {
            withTimeoutOrNull(5_000) { decisionProvider(alarmForExecution, occurrenceDate, zoneId) }
        } ?: AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING
        return when (decision) {
            AlarmExecutionDecision.SKIP_FOR_VACATION -> {
                preserveCurrentAlarm { actualSkips.recordSkipped(alarm.id, occurrenceDate) }
                ExecutionOutcome.SkippedForVacation
            }
            AlarmExecutionDecision.DO_NOT_RING -> ExecutionOutcome.Ignored
            AlarmExecutionDecision.RING,
            AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING,
            -> {
                ringer.startAt(
                    alarmForExecution.id,
                    alarmForExecution.name,
                    alarmForExecution.ringtoneUri,
                    alarmForExecution.gradualVolume,
                    alarmForExecution.snoozeEnabled,
                    alarmForExecution.snoozeMinutes,
                    scheduledOccurrenceAt,
                )
                if (decision == AlarmExecutionDecision.RING_WITH_CALENDAR_WARNING)
                    ExecutionOutcome.RingingWithCalendarWarning else ExecutionOutcome.Ringing
            }
        }
    }
}

// Bookkeeping failures must not prevent an already due alarm from ringing.
internal suspend fun <T> preserveCurrentAlarm(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
