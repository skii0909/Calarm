package com.smartworkalarm.domain.shift

import com.smartworkalarm.domain.model.ShiftTypeAlarm

sealed interface ShiftGroupDayStatus {
    data object Inactive : ShiftGroupDayStatus
    data object Vacation : ShiftGroupDayStatus
    data object PublicHoliday : ShiftGroupDayStatus
    data class Matched(val shiftType: ShiftTypeAlarm) : ShiftGroupDayStatus
    data object None : ShiftGroupDayStatus
    data class Conflict(
        val shiftTypes: List<ShiftTypeAlarm>,
        val matchedCalendarTitles: Map<Long, List<String>> = emptyMap(),
    ) : ShiftGroupDayStatus
    data class ReadFailure(val emergencyType: ShiftTypeAlarm?) : ShiftGroupDayStatus
}
