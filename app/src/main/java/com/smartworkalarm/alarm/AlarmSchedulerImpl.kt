package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.prealarm.AlarmKind
import com.smartworkalarm.prealarm.AlarmOccurrenceKey
import com.smartworkalarm.prealarm.PreAlarmContract
import com.smartworkalarm.prealarm.PreAlarmReceiver
import com.smartworkalarm.prealarm.preAlarmTriggerAt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class AlarmSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val permission: ExactAlarmPermission,
    private val auditStore: AlarmScheduleAuditStore,
    private val clock: Clock,
) : AlarmScheduler, ShiftAlarmScheduler {
    override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = false).also { result ->
            if (result == ScheduleResult.Scheduled) {
                schedulePreAlarmSafely(AlarmKind.REGULAR, alarmId, triggerAt)
            }
        }

    override fun scheduleSnooze(alarmId: Long, triggerAt: Instant): ScheduleResult =
        scheduleExact(alarmId, triggerAt, snooze = true)

    override fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, snooze = false, Instant.EPOCH))
        recordAuditSafely { auditStore.recordCancelled(regularAuditKey(alarmId, snooze = false)) }
        alarmManager.cancel(pendingIntent(alarmId, snooze = true, Instant.EPOCH))
        recordAuditSafely { auditStore.recordCancelled(regularAuditKey(alarmId, snooze = true)) }
        alarmManager.cancel(preAlarmPendingIntent(AlarmKind.REGULAR, alarmId, Instant.EPOCH))
    }

    override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult =
        scheduleShiftExact(shiftTypeId, triggerAt, snooze = false, scheduledOccurrenceAt = triggerAt)
            .also { result ->
                if (result == ScheduleResult.Scheduled) {
                    schedulePreAlarmSafely(AlarmKind.SHIFT, shiftTypeId, triggerAt)
                }
            }

    override fun scheduleShiftSnooze(
        shiftTypeId: Long,
        triggerAt: Instant,
        scheduledOccurrenceAt: Instant,
    ): ScheduleResult = scheduleShiftExact(shiftTypeId, triggerAt, snooze = true, scheduledOccurrenceAt)

    override fun cancelShift(shiftTypeId: Long) {
        alarmManager.cancel(shiftPendingIntent(shiftTypeId, snooze = false, Instant.EPOCH))
        recordAuditSafely { auditStore.recordCancelled(shiftAuditKey(shiftTypeId, snooze = false)) }
        alarmManager.cancel(shiftPendingIntent(shiftTypeId, snooze = true, Instant.EPOCH))
        recordAuditSafely { auditStore.recordCancelled(shiftAuditKey(shiftTypeId, snooze = true)) }
        alarmManager.cancel(preAlarmPendingIntent(AlarmKind.SHIFT, shiftTypeId, Instant.EPOCH))
    }

    private fun scheduleExact(alarmId: Long, triggerAt: Instant, snooze: Boolean): ScheduleResult {
        if (!permission.isGranted()) return ScheduleResult.ExactAlarmPermissionRequired
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            pendingIntent(alarmId, snooze, triggerAt),
        )
        recordAuditSafely { auditStore.recordScheduled(regularAuditKey(alarmId, snooze), triggerAt) }
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
        recordAuditSafely { auditStore.recordScheduled(shiftAuditKey(shiftTypeId, snooze), triggerAt) }
        return ScheduleResult.Scheduled
    }

    private fun regularAuditKey(alarmId: Long, snooze: Boolean): String =
        "regular:$alarmId" + if (snooze) SNOOZE_AUDIT_SUFFIX else ""

    private fun shiftAuditKey(shiftTypeId: Long, snooze: Boolean): String =
        "shift:$shiftTypeId" + if (snooze) SNOOZE_AUDIT_SUFFIX else ""

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

    private fun schedulePreAlarmSafely(kind: AlarmKind, id: Long, occurrenceAt: Instant) {
        runCatching {
            val triggerAt = preAlarmTriggerAt(clock.instant(), occurrenceAt) ?: return@runCatching
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                preAlarmPendingIntent(kind, id, occurrenceAt),
            )
        }
    }

    private fun preAlarmPendingIntent(
        kind: AlarmKind,
        id: Long,
        occurrenceAt: Instant,
    ): PendingIntent {
        val key = AlarmOccurrenceKey(kind, id, occurrenceAt)
        val intent = Intent(context, PreAlarmReceiver::class.java).apply {
            action = PreAlarmContract.ACTION_PRE_ALARM
            putExtra(PreAlarmContract.EXTRA_KIND, key.kind.name)
            putExtra(PreAlarmContract.EXTRA_ID, key.id)
            putExtra(
                PreAlarmContract.EXTRA_SCHEDULED_OCCURRENCE_AT,
                key.scheduledAt.toEpochMilli(),
            )
        }
        val namespace = when (kind) {
            AlarmKind.REGULAR -> PRE_ALARM_REQUEST_OFFSET
            AlarmKind.SHIFT -> SHIFT_PRE_ALARM_REQUEST_OFFSET
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode() + namespace,
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
        private const val PRE_ALARM_REQUEST_OFFSET = 4_000_000
        private const val SHIFT_PRE_ALARM_REQUEST_OFFSET = 5_000_000
        private const val SNOOZE_AUDIT_SUFFIX = ":snooze"
    }
}

internal inline fun recordAuditSafely(record: () -> Unit) {
    runCatching(record)
}
