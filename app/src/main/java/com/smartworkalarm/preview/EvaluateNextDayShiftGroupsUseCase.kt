package com.smartworkalarm.preview

import com.smartworkalarm.data.local.ShiftPreviewDao
import com.smartworkalarm.data.local.ShiftPreviewEntity
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.domain.usecase.CalendarHolidayMatcher
import com.smartworkalarm.domain.usecase.CalendarKeywordMatcher
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class ShiftPreviewChange(
    val group: ShiftAlarmGroup,
    val previousKey: String?,
    val currentEvaluation: ShiftDayEvaluation,
) {
    val currentKey: String get() = ShiftPreviewStateKey.of(currentEvaluation)
}

object ShiftPreviewStateKey {
    fun of(evaluation: ShiftDayEvaluation): String = when (evaluation) {
        is ShiftDayEvaluation.Matched -> "MATCHED:${evaluation.shiftTypeId}"
        ShiftDayEvaluation.None -> "NONE"
        is ShiftDayEvaluation.Conflict -> "CONFLICT:${evaluation.matches.map { it.shiftTypeId }.distinct().sorted().joinToString(",")}"
        ShiftDayEvaluation.ReadFailure -> "READ_FAILURE"
    }
}

class EvaluateNextDayShiftGroupsUseCase @Inject constructor(
    private val repository: ShiftAlarmRepository,
    private val events: CalendarEventSource,
    private val previews: ShiftPreviewDao,
    private val evaluator: ShiftDayEvaluator,
    private val clock: Clock,
    private val holidays: KoreanPublicHolidayProvider = KoreanPublicHolidayProvider(),
) {
    suspend fun execute(targetDate: LocalDate, zoneId: ZoneId): List<ShiftPreviewChange> {
        val groups = repository.observeGroupsWithTypes().first()
            .filter { it.group.enabled }
        if (groups.isEmpty()) return emptyList()

        val eventsResult = events.getEventsOverlapping(
            targetDate.atStartOfDay(zoneId).toInstant(),
            targetDate.plusDays(1).atStartOfDay(zoneId).toInstant(),
        )
        return buildList {
            groups.forEach { groupWithTypes ->
                val current = evaluateEffectiveState(
                    types = groupWithTypes.shiftTypes,
                    targetDate = targetDate,
                    eventsResult = eventsResult,
                )
                val currentKey = ShiftPreviewStateKey.of(current)
                val previousKey = previews.get(groupWithTypes.group.id)?.stateKey
                if (previousKey != null && previousKey != currentKey) {
                    add(ShiftPreviewChange(groupWithTypes.group, previousKey, current))
                }
                previews.upsert(
                    ShiftPreviewEntity(groupWithTypes.group.id, currentKey, clock.millis()),
                )
            }
        }
    }

    private fun evaluateEffectiveState(
        types: List<ShiftTypeAlarm>,
        targetDate: LocalDate,
        eventsResult: Result<List<CalendarEvent>>,
    ): ShiftDayEvaluation {
        val scheduledTypes = types.filter { it.enabled && it.repeatsOn(targetDate.dayOfWeek) }
        if (scheduledTypes.isEmpty()) return ShiftDayEvaluation.None

        val localHoliday = holidays.find(targetDate) != null
        if (localHoliday && scheduledTypes.all(ShiftTypeAlarm::skipOnPublicHoliday)) return ShiftDayEvaluation.None

        return eventsResult.fold(
            onSuccess = { dayEvents ->
                if (dayEvents.any { CalendarKeywordMatcher.matches(it.title, listOf("휴가")) }) {
                    ShiftDayEvaluation.None
                } else {
                    val publicHoliday = localHoliday || dayEvents.any { CalendarHolidayMatcher.matches(it.title) }
                    val evaluation = evaluator.evaluate(scheduledTypes, Result.success(dayEvents))
                    if (
                        publicHoliday &&
                        evaluation is ShiftDayEvaluation.Matched &&
                        scheduledTypes.first { it.id == evaluation.shiftTypeId }.skipOnPublicHoliday
                    ) {
                        ShiftDayEvaluation.None
                    } else {
                        evaluation
                    }
                }
            },
            onFailure = { ShiftDayEvaluation.ReadFailure },
        )
    }
}

private fun ShiftTypeAlarm.repeatsOn(dayOfWeek: DayOfWeek): Boolean =
    repeatDaysMask and (1 shl (dayOfWeek.value - 1)) != 0
