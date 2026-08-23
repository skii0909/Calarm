package com.smartworkalarm.domain.shift

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftTypeAlarm

class ShiftDayEvaluator {
    fun evaluate(
        types: List<ShiftTypeAlarm>,
        eventsResult: Result<List<CalendarEvent>>,
    ): ShiftDayEvaluation = eventsResult.fold(
        onSuccess = { events -> evaluateEvents(types, events) },
        onFailure = { ShiftDayEvaluation.ReadFailure },
    )

    private fun evaluateEvents(
        types: List<ShiftTypeAlarm>,
        events: List<CalendarEvent>,
    ): ShiftDayEvaluation {
        val matches = types
            .filter { type ->
                events.any { event ->
                    ExactShiftTitleMatcher.matches(event.title, type.exactTitles)
                }
            }
            .distinctBy { it.id }
            .map { type -> ShiftDayEvaluation.Matched(type.id, type.name) }

        return when (matches.size) {
            0 -> ShiftDayEvaluation.None
            1 -> matches.single()
            else -> ShiftDayEvaluation.Conflict(matches)
        }
    }
}
