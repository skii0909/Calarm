package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class AlarmSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val permission: ExactAlarmPermission,
) : AlarmScheduler {
    override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = false)

    override fun scheduleSnooze(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = true)

    override fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, snooze = false))
        alarmManager.cancel(pendingIntent(alarmId, snooze = true))
    }

    private fun scheduleExact(alarmId: Long, triggerAt: Instant, snooze: Boolean): ScheduleResult {
        if (!permission.isGranted()) return ScheduleResult.ExactAlarmPermissionRequired
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            pendingIntent(alarmId, snooze),
        )
        return ScheduleResult.Scheduled
    }

    private fun pendingIntent(alarmId: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(ACTION_ALARM).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, snooze)
        }
        val namespace = if (snooze) SNOOZE_REQUEST_OFFSET else 0
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() + namespace,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_ALARM = "com.smartworkalarm.ACTION_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        private const val SNOOZE_REQUEST_OFFSET = 1_000_000
    }
}
