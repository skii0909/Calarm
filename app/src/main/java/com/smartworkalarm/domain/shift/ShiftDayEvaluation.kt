package com.smartworkalarm.domain.shift

sealed interface ShiftDayEvaluation {
    data class Matched(
        val shiftTypeId: Long,
        val name: String,
    ) : ShiftDayEvaluation

    data object None : ShiftDayEvaluation

    data class Conflict(
        val matches: List<Matched>,
    ) : ShiftDayEvaluation

    data object ReadFailure : ShiftDayEvaluation
}
