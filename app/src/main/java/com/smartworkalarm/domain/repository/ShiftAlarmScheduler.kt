package com.smartworkalarm.domain.repository

import java.time.Instant

interface ShiftAlarmScheduler {
    fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult
    fun scheduleShiftSnooze(shiftTypeId: Long, triggerAt: Instant): ScheduleResult
    fun cancelShift(shiftTypeId: Long)
}
