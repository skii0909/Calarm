package com.smartworkalarm.domain.model

data class ShiftAlarmGroup(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val emergencyShiftTypeId: Long?,
)
