package com.smartworkalarm.domain.model

data class ShiftTypeAlarm(
    val id: Long,
    val groupId: Long,
    val name: String,
    val exactTitles: List<String>,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val ringtoneUri: String?,
    val gradualVolume: Boolean,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int,
    val skipOnPublicHoliday: Boolean,
)
