package com.smartworkalarm.domain.model

import java.time.DayOfWeek

data class Alarm(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val skipOnVacation: Boolean,
) {
    fun repeatsOn(dayOfWeek: DayOfWeek): Boolean {
        val bit = 1 shl (dayOfWeek.value - 1)
        return repeatDaysMask and bit != 0
    }
}
