package com.smartworkalarm.domain.repository

import java.time.Instant

sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object ExactAlarmPermissionRequired : ScheduleResult
}

interface AlarmScheduler {
    fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult
    fun cancel(alarmId: Long)
    fun scheduleSnooze(alarmId: Long, triggerAt: Instant): ScheduleResult
}
