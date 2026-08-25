package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class AlarmSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val permission: ExactAlarmPermission,
) : AlarmScheduler, ShiftAlarmScheduler {
    override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = false)

    override fun scheduleSnooze(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = true)

    override fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, snooze = false, Instant.EPOCH))
        alarmManager.cancel(pendingIntent(alarmId, snooze = true, Instant.EPOCH))
    }

    override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult =
        scheduleShiftExact(shiftTypeId, triggerAt, snooze = false, scheduledOccurrenceAt = triggerAt)

    override fun scheduleShiftSnooze(
        shiftTypeId: Long,
        triggerAt: Instant,
        scheduledOccurrenceAt: Instant,
    ): ScheduleResult = scheduleShiftExact(shiftTypeId, triggerAt, snooze = true, scheduledOccurrenceAt)

    override fun cancelShift(shiftTypeId: Long) {
        alarmManager.cancel(shiftPendingIntent(shiftTypeId, snooze = false, Instant.EPOCH))
        alarmManager.cancel(shiftPendingIntent(shiftTypeId, snooze = true, Instant.EPOCH))
    }

    private fun scheduleExact(alarmId: Long, triggerAt: Instant, snooze: Boolean): ScheduleResult {
        if (!permission.isGranted()) return ScheduleResult.ExactAlarmPermissionRequired
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            pendingIntent(alarmId, snooze, triggerAt),
        )
        return ScheduleResult.Scheduled
    }

    private fun scheduleShiftExact(
        shiftTypeId: Long,
        triggerAt: Instant,
        snooze: Boolean,
        scheduledOccurrenceAt: Instant,
    ): ScheduleResult {
        if (!permission.isGranted()) return ScheduleResult.ExactAlarmPermissionRequired
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            shiftPendingIntent(shiftTypeId, snooze, scheduledOccurrenceAt),
        )
        return ScheduleResult.Scheduled
    }

    private fun pendingIntent(
        alarmId: Long,
        snooze: Boolean,
        scheduledOccurrenceAt: Instant,
    ): PendingIntent {
        val intent = Intent(ACTION_ALARM).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, snooze)
            putExtra(EXTRA_SCHEDULED_OCCURRENCE_AT, scheduledOccurrenceAt.toEpochMilli())
        }
        val namespace = if (snooze) SNOOZE_REQUEST_OFFSET else 0
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() + namespace,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shiftPendingIntent(
        shiftTypeId: Long,
        snooze: Boolean,
        scheduledOccurrenceAt: Instant,
    ): PendingIntent {
        val intent = Intent(ACTION_SHIFT_ALARM).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SHIFT_TYPE_ID, shiftTypeId)
            putExtra(EXTRA_IS_SNOOZE, snooze)
            putExtra(EXTRA_SCHEDULED_OCCURRENCE_AT, scheduledOccurrenceAt.toEpochMilli())
        }
        val namespace = if (snooze) SHIFT_SNOOZE_REQUEST_OFFSET else SHIFT_REQUEST_OFFSET
        return PendingIntent.getBroadcast(
            context,
            shiftTypeId.hashCode() + namespace,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_ALARM = "com.smartworkalarm.ACTION_ALARM"
        const val ACTION_SHIFT_ALARM = "com.smartworkalarm.ACTION_SHIFT_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SHIFT_TYPE_ID = "shift_type_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_SCHEDULED_OCCURRENCE_AT = "scheduled_occurrence_at"
        private const val SNOOZE_REQUEST_OFFSET = 1_000_000
        private const val SHIFT_REQUEST_OFFSET = 2_000_000
        private const val SHIFT_SNOOZE_REQUEST_OFFSET = 3_000_000
    }
}
