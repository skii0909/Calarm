package com.smartworkalarm.alarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ShiftAlarmExecutionCoordinator internal constructor(
    private val repository: ShiftAlarmRepository,
    private val scheduler: ShiftAlarmScheduler,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val decisionProvider: suspend (
        ShiftAlarmRepository.GroupWithTypes,
        ShiftTypeAlarm,
        LocalDate,
    ) -> ShiftExecutionDecision,
    private val ringer: AlarmRinger,
    private val zoneId: ZoneId,
) {
    @Inject
    constructor(
        repository: ShiftAlarmRepository,
        scheduler: ShiftAlarmScheduler,
        calculator: CalculateNextOccurrenceUseCase,
        events: CalendarEventSource,
        effectiveEvaluator: EvaluateShiftGroupDayUseCase,
        ringer: AlarmRinger,
        zoneId: ZoneId,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        calculator = calculator,
        decisionProvider = { group, _, date ->
            val dayEvents = events.getEventsOverlapping(
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )
            ShiftExecutionDecision.Effective(
                effectiveEvaluator.execute(group, date, dayEvents),
            )
        },
        ringer = ringer,
        zoneId = zoneId,
    )

    suspend fun execute(
        shiftTypeId: Long,
        isSnooze: Boolean,
        now: Instant,
        scheduledOccurrenceAt: Instant = now,
    ): ExecutionOutcome {
        val shiftType = repository.getShiftType(shiftTypeId) ?: return ExecutionOutcome.Ignored
        val group = repository.getGroupWithTypes(shiftType.groupId) ?: return ExecutionOutcome.Ignored
        val occurrenceDate = scheduledOccurrenceAt.atZone(zoneId).toLocalDate()
        if (!group.group.enabled || !shiftType.enabled || !shiftType.repeatsOn(occurrenceDate.dayOfWeek)) {
            return ExecutionOutcome.Ignored
        }

        if (!isSnooze) {
            calculator.execute(shiftType.toAlarm(), now, zoneId)?.let { scheduler.scheduleShift(shiftType.id, it) }
        }

        return when (val decision = decisionProvider(group, shiftType, occurrenceDate)) {
            ShiftExecutionDecision.SkipForVacation -> ExecutionOutcome.SkippedForVacation
            is ShiftExecutionDecision.Effective -> outcomeFor(decision.value, shiftType, scheduledOccurrenceAt)
            is ShiftExecutionDecision.Evaluated -> when (val evaluation = decision.value) {
                is ShiftDayEvaluation.Matched -> {
                    if (evaluation.shiftTypeId != shiftType.id) ExecutionOutcome.SkippedForShift
                    else ring(shiftType, warning = false, scheduledOccurrenceAt)
                }
                ShiftDayEvaluation.ReadFailure -> {
                    if (group.group.emergencyShiftTypeId != shiftType.id) ExecutionOutcome.SkippedForShift
                    else ring(shiftType, warning = true, scheduledOccurrenceAt)
                }
                ShiftDayEvaluation.None,
                is ShiftDayEvaluation.Conflict,
                -> ExecutionOutcome.SkippedForShift
            }
        }
    }

    private fun outcomeFor(
        status: ShiftGroupDayStatus,
        shiftType: ShiftTypeAlarm,
        scheduledOccurrenceAt: Instant,
    ): ExecutionOutcome = when (status) {
        ShiftGroupDayStatus.Inactive -> ExecutionOutcome.Ignored
        ShiftGroupDayStatus.Vacation,
        ShiftGroupDayStatus.PublicHoliday,
        -> ExecutionOutcome.SkippedForVacation
        is ShiftGroupDayStatus.Matched -> {
            if (status.shiftType.id != shiftType.id) ExecutionOutcome.SkippedForShift
            else ring(shiftType, warning = false, scheduledOccurrenceAt)
        }
        ShiftGroupDayStatus.None,
        is ShiftGroupDayStatus.Conflict,
        -> ExecutionOutcome.SkippedForShift
        is ShiftGroupDayStatus.ReadFailure -> {
            if (status.emergencyType?.id != shiftType.id) ExecutionOutcome.SkippedForShift
            else ring(shiftType, warning = true, scheduledOccurrenceAt)
        }
    }

    private fun ring(
        shiftType: ShiftTypeAlarm,
        warning: Boolean,
        scheduledOccurrenceAt: Instant,
    ): ExecutionOutcome {
        ringer.startShift(
            shiftType.id,
            shiftType.name,
            shiftType.ringtoneUri,
            shiftType.gradualVolume,
            shiftType.snoozeEnabled,
            shiftType.snoozeMinutes,
            warning,
            scheduledOccurrenceAt,
        )
        return if (warning) ExecutionOutcome.RingingWithCalendarWarning else ExecutionOutcome.Ringing
    }
}

sealed interface ShiftExecutionDecision {
    data object SkipForVacation : ShiftExecutionDecision
    data class Evaluated(val value: ShiftDayEvaluation) : ShiftExecutionDecision
    data class Effective(val value: ShiftGroupDayStatus) : ShiftExecutionDecision
}

private fun ShiftTypeAlarm.toAlarm() = Alarm(
    id = id,
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = false,
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
)

private fun ShiftTypeAlarm.repeatsOn(dayOfWeek: DayOfWeek): Boolean =
    repeatDaysMask and (1 shl (dayOfWeek.value - 1)) != 0
