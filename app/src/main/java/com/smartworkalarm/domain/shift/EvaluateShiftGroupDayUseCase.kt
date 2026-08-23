package com.smartworkalarm.domain.shift

import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.usecase.CalendarHolidayMatcher
import com.smartworkalarm.domain.usecase.CalendarKeywordMatcher
import java.time.LocalDate
import javax.inject.Inject

class EvaluateShiftGroupDayUseCase @Inject constructor(
    private val evaluator: ShiftDayEvaluator,
    private val holidays: KoreanPublicHolidayProvider,
) {
    fun execute(
        aggregate: ShiftAlarmRepository.GroupWithTypes,
        date: LocalDate,
        eventsResult: Result<List<CalendarEvent>>,
    ): ShiftGroupDayStatus {
        if (!aggregate.group.enabled) return ShiftGroupDayStatus.Inactive
        val repeatingTypes = aggregate.shiftTypes.filter { it.enabled && it.repeatsOn(date) }
        if (repeatingTypes.isEmpty()) return ShiftGroupDayStatus.Inactive

        val afterStatutoryHoliday = if (holidays.find(date) != null) {
            repeatingTypes.filterNot { it.skipOnPublicHoliday }
        } else {
            repeatingTypes
        }
        if (afterStatutoryHoliday.isEmpty()) return ShiftGroupDayStatus.PublicHoliday

        return eventsResult.fold(
            onFailure = { readFailure(aggregate, afterStatutoryHoliday) },
            onSuccess = { events -> evaluateEvents(aggregate, afterStatutoryHoliday, events) },
        )
    }

    private fun evaluateEvents(
        aggregate: ShiftAlarmRepository.GroupWithTypes,
        repeatingTypes: List<ShiftTypeAlarm>,
        events: List<CalendarEvent>,
    ): ShiftGroupDayStatus {
        if (events.any { CalendarKeywordMatcher.matches(it.title, listOf("휴가")) }) {
            return ShiftGroupDayStatus.Vacation
        }
        val effectiveTypes = if (events.any { CalendarHolidayMatcher.matches(it.title) }) {
            repeatingTypes.filterNot { it.skipOnPublicHoliday }
        } else {
            repeatingTypes
        }
        if (effectiveTypes.isEmpty()) return ShiftGroupDayStatus.PublicHoliday

        return when (val evaluation = evaluator.evaluate(effectiveTypes, Result.success(events))) {
            is ShiftDayEvaluation.Matched -> ShiftGroupDayStatus.Matched(
                checkNotNull(effectiveTypes.firstOrNull { it.id == evaluation.shiftTypeId }),
            )
            ShiftDayEvaluation.None -> ShiftGroupDayStatus.None
            is ShiftDayEvaluation.Conflict -> ShiftGroupDayStatus.Conflict(
                evaluation.matches.mapNotNull { match -> effectiveTypes.firstOrNull { it.id == match.shiftTypeId } },
            )
            ShiftDayEvaluation.ReadFailure -> readFailure(aggregate, effectiveTypes)
        }
    }

    private fun readFailure(
        aggregate: ShiftAlarmRepository.GroupWithTypes,
        effectiveTypes: List<ShiftTypeAlarm>,
    ) = ShiftGroupDayStatus.ReadFailure(
        effectiveTypes.firstOrNull { it.id == aggregate.group.emergencyShiftTypeId },
    )
}

private fun ShiftTypeAlarm.repeatsOn(date: LocalDate): Boolean =
    repeatDaysMask and (1 shl (date.dayOfWeek.value - 1)) != 0
