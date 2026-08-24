package com.smartworkalarm.preview

import com.smartworkalarm.data.local.ShiftPreviewDao
import com.smartworkalarm.data.local.ShiftPreviewEntity
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import java.time.Clock
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

data class ShiftPreviewEvaluation(
    val changes: List<ShiftPreviewChange> = emptyList(),
    val calendarReadFailed: Boolean = false,
)

object ShiftPreviewStateKey {
    fun of(evaluation: ShiftDayEvaluation): String = when (evaluation) {
        is ShiftDayEvaluation.Matched -> "MATCHED:${evaluation.shiftTypeId}"
        ShiftDayEvaluation.None -> "NONE"
        is ShiftDayEvaluation.Conflict -> "CONFLICT:" + evaluation.matches
            .distinctBy { it.shiftTypeId }
            .sortedBy { it.shiftTypeId }
            .joinToString("|") { match ->
                val titles = evaluation.matchedCalendarTitles[match.shiftTypeId].orEmpty()
                    .distinct()
                    .sorted()
                    .joinToString("") { title -> "${title.length}:$title" }
                "${match.shiftTypeId}:$titles"
            }
        ShiftDayEvaluation.ReadFailure -> "READ_FAILURE"
    }
}

class EvaluateNextDayShiftGroupsUseCase @Inject constructor(
    private val repository: ShiftAlarmRepository,
    private val events: CalendarEventSource,
    private val previews: ShiftPreviewDao,
    private val effectiveEvaluator: EvaluateShiftGroupDayUseCase,
    private val clock: Clock,
) {
    suspend fun execute(
        today: LocalDate,
        tomorrow: LocalDate,
        zoneId: ZoneId,
    ): List<ShiftPreviewChange> = executeWithStatus(today, tomorrow, zoneId).changes

    suspend fun executeWithStatus(
        today: LocalDate,
        tomorrow: LocalDate,
        zoneId: ZoneId,
    ): ShiftPreviewEvaluation {
        val groups = repository.observeGroupsWithTypes().first()
            .filter { it.group.enabled }
        if (groups.isEmpty()) return ShiftPreviewEvaluation()

        val todayEvents = eventsFor(today, zoneId)
        val tomorrowEvents = eventsFor(tomorrow, zoneId)
        val changes = buildList {
            groups.forEach { groupWithTypes ->
                val previous = effectiveEvaluator.execute(groupWithTypes, today, todayEvents).toPreviewEvaluation()
                val current = effectiveEvaluator.execute(groupWithTypes, tomorrow, tomorrowEvents).toPreviewEvaluation()
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
        return ShiftPreviewEvaluation(
            changes = changes,
            calendarReadFailed = todayEvents.isFailure || tomorrowEvents.isFailure,
        )
    }

    private suspend fun eventsFor(date: LocalDate, zoneId: ZoneId): Result<List<CalendarEvent>> =
        events.getEventsOverlapping(
            date.atStartOfDay(zoneId).toInstant(),
            date.plusDays(1).atStartOfDay(zoneId).toInstant(),
        )

}

private fun ShiftGroupDayStatus.toPreviewEvaluation(): ShiftDayEvaluation = when (this) {
    ShiftGroupDayStatus.Inactive,
    ShiftGroupDayStatus.Vacation,
    ShiftGroupDayStatus.PublicHoliday,
    ShiftGroupDayStatus.None,
    -> ShiftDayEvaluation.None
    is ShiftGroupDayStatus.Matched -> ShiftDayEvaluation.Matched(shiftType.id, shiftType.name)
    is ShiftGroupDayStatus.Conflict -> ShiftDayEvaluation.Conflict(
        matches = shiftTypes.map { ShiftDayEvaluation.Matched(it.id, it.name) },
        matchedCalendarTitles = matchedCalendarTitles,
    )
    is ShiftGroupDayStatus.ReadFailure -> ShiftDayEvaluation.ReadFailure
}
