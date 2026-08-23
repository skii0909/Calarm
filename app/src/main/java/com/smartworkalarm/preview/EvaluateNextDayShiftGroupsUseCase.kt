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
    val previousKey: String,
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
    suspend fun execute(
        today: LocalDate,
        tomorrow: LocalDate,
        zoneId: ZoneId,
    ): List<ShiftPreviewChange> {
        val groups = repository.observeGroupsWithTypes().first()
            .filter { it.group.enabled }
        if (groups.isEmpty()) return emptyList()

        val todayEvents = eventsFor(today, zoneId)
        val tomorrowEvents = eventsFor(tomorrow, zoneId)
        return buildList {
            groups.forEach { groupWithTypes ->
                val previous = evaluateEffectiveState(
                    types = groupWithTypes.shiftTypes,
                    targetDate = today,
                    eventsResult = todayEvents,
                )
                val current = evaluateEffectiveState(
                    types = groupWithTypes.shiftTypes,
                    targetDate = tomorrow,
                    eventsResult = tomorrowEvents,
                )
                val previousKey = ShiftPreviewStateKey.of(previous)
                val currentKey = ShiftPreviewStateKey.of(current)
                if (previousKey != currentKey) {
                    add(ShiftPreviewChange(groupWithTypes.group, previousKey, current))
                }
                previews.upsert(
                    ShiftPreviewEntity(groupWithTypes.group.id, currentKey, clock.millis()),
                )
            }
        }
    }

    private suspend fun eventsFor(date: LocalDate, zoneId: ZoneId): Result<List<CalendarEvent>> =
        events.getEventsOverlapping(
            date.atStartOfDay(zoneId).toInstant(),
            date.plusDays(1).atStartOfDay(zoneId).toInstant(),
        )

    private fun evaluateEffectiveState(
        types: List<ShiftTypeAlarm>,
        targetDate: LocalDate,
        eventsResult: Result<List<CalendarEvent>>,
    ): ShiftDayEvaluation {
        val scheduledTypes = types.filter { it.enabled && it.repeatsOn(targetDate.dayOfWeek) }
        if (scheduledTypes.isEmpty()) return ShiftDayEvaluation.None

        val evaluation = evaluator.evaluate(scheduledTypes, eventsResult)
        if (evaluation !is ShiftDayEvaluation.Matched) return evaluation
        val dayEvents = eventsResult.getOrNull() ?: return evaluation
        val skippedForVacation = dayEvents.any { CalendarKeywordMatcher.matches(it.title, listOf("휴가")) }
        val skippedForPublicHoliday =
            (holidays.find(targetDate) != null || dayEvents.any { CalendarHolidayMatcher.matches(it.title) }) &&
                scheduledTypes.first { it.id == evaluation.shiftTypeId }.skipOnPublicHoliday
        return if (skippedForVacation || skippedForPublicHoliday) ShiftDayEvaluation.None else evaluation
    }
}

private fun ShiftTypeAlarm.repeatsOn(dayOfWeek: DayOfWeek): Boolean =
    repeatDaysMask and (1 shl (dayOfWeek.value - 1)) != 0
