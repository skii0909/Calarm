package com.smartworkalarm.domain.model

import java.time.DayOfWeek

data class Alarm(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val skipOnCalendarMatch: Boolean,
    val calendarKeywords: List<String> = listOf("휴가"),
    val ringtoneUri: String? = null,
    val skipOnPublicHoliday: Boolean = false,
    val gradualVolume: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
) {
    fun repeatsOn(dayOfWeek: DayOfWeek): Boolean {
        val bit = 1 shl (dayOfWeek.value - 1)
        return repeatDaysMask and bit != 0
    }
}
