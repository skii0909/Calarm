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
        val matches = types.distinctBy { it.id }.mapNotNull { type ->
            val calendarTitles = events.mapNotNull { event ->
                ExactShiftTitleMatcher.normalizedMatch(event.title, type.exactTitles)
            }
                .distinct()
                .sorted()
            if (calendarTitles.isEmpty()) null
            else ShiftDayEvaluation.Matched(type.id, type.name) to calendarTitles
        }

        return when (matches.size) {
            0 -> ShiftDayEvaluation.None
            1 -> matches.single().first
            else -> ShiftDayEvaluation.Conflict(
                matches = matches.map { it.first },
                matchedCalendarTitles = matches.associate { (match, titles) -> match.shiftTypeId to titles },
            )
        }
    }
}
