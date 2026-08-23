package com.smartworkalarm.alarm

import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.CalendarHolidayMatcher
import com.smartworkalarm.domain.usecase.CalendarKeywordMatcher
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
        evaluator: ShiftDayEvaluator,
        holidays: KoreanPublicHolidayProvider,
        ringer: AlarmRinger,
        zoneId: ZoneId,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        calculator = calculator,
        decisionProvider = { group, type, date ->
            when {
                type.skipOnPublicHoliday && holidays.find(date) != null -> ShiftExecutionDecision.SkipForVacation
                else -> events.getEventsOverlapping(
                    date.atStartOfDay(zoneId).toInstant(),
                    date.plusDays(1).atStartOfDay(zoneId).toInstant(),
                ).fold(
                    onSuccess = { dayEvents ->
                        if (dayEvents.hasVacation() || (type.skipOnPublicHoliday && dayEvents.any { CalendarHolidayMatcher.matches(it.title) })) {
                            ShiftExecutionDecision.SkipForVacation
                        } else {
                            ShiftExecutionDecision.Evaluated(evaluator.evaluate(group.shiftTypes, Result.success(dayEvents)))
                        }
                    },
                    onFailure = {
                        ShiftExecutionDecision.Evaluated(ShiftDayEvaluation.ReadFailure)
                    },
                )
            }
        },
        ringer = ringer,
        zoneId = zoneId,
    )

    suspend fun execute(shiftTypeId: Long, isSnooze: Boolean, now: Instant): ExecutionOutcome {
        val shiftType = repository.getShiftType(shiftTypeId) ?: return ExecutionOutcome.Ignored
        val group = repository.getGroupWithTypes(shiftType.groupId) ?: return ExecutionOutcome.Ignored
        if (!group.group.enabled || !shiftType.enabled || !shiftType.repeatsOn(now.atZone(zoneId).dayOfWeek)) {
            return ExecutionOutcome.Ignored
        }

        if (!isSnooze) {
            calculator.execute(shiftType.toAlarm(), now, zoneId)?.let { scheduler.scheduleShift(shiftType.id, it) }
        }

        return when (val decision = decisionProvider(group, shiftType, now.atZone(zoneId).toLocalDate())) {
            ShiftExecutionDecision.SkipForVacation -> ExecutionOutcome.SkippedForVacation
            is ShiftExecutionDecision.Evaluated -> when (val evaluation = decision.value) {
                is ShiftDayEvaluation.Matched -> {
                    if (evaluation.shiftTypeId != shiftType.id) ExecutionOutcome.SkippedForShift
                    else ring(shiftType, warning = false)
                }
                ShiftDayEvaluation.ReadFailure -> {
                    if (group.group.emergencyShiftTypeId != shiftType.id) ExecutionOutcome.SkippedForShift
                    else ring(shiftType, warning = true)
                }
                ShiftDayEvaluation.None,
                is ShiftDayEvaluation.Conflict,
                -> ExecutionOutcome.SkippedForShift
            }
        }
    }

    private fun ring(shiftType: ShiftTypeAlarm, warning: Boolean): ExecutionOutcome {
        ringer.startShift(
            shiftType.id,
            shiftType.name,
            shiftType.ringtoneUri,
            shiftType.gradualVolume,
            shiftType.snoozeMinutes,
            warning,
        )
        return if (warning) ExecutionOutcome.RingingWithCalendarWarning else ExecutionOutcome.Ringing
    }
}

sealed interface ShiftExecutionDecision {
    data object SkipForVacation : ShiftExecutionDecision
    data class Evaluated(val value: ShiftDayEvaluation) : ShiftExecutionDecision
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
    snoozeMinutes = snoozeMinutes,
)

private fun ShiftTypeAlarm.repeatsOn(dayOfWeek: DayOfWeek): Boolean =
    repeatDaysMask and (1 shl (dayOfWeek.value - 1)) != 0

private fun List<CalendarEvent>.hasVacation(): Boolean = any { event ->
    CalendarKeywordMatcher.matches(event.title, listOf("휴가"))
}
