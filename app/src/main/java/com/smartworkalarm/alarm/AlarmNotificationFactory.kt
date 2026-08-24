package com.smartworkalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class AlarmNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {
    fun create(
        alarmId: Long,
        name: String,
        isShiftAlarm: Boolean = false,
        calendarWarning: Boolean = false,
        scheduledOccurrenceAt: Instant? = null,
        snoozeMinutes: Int = 5,
    ): Notification {
        ensureChannel()
        val fullScreen = PendingIntent.getActivity(
            context,
            alarmId.hashCode() + if (isShiftAlarm) SHIFT_FULL_SCREEN_REQUEST_OFFSET else 0,
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_NAME, name)
                putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, isShiftAlarm)
                putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, calendarWarning)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceIn(5, 30))
                scheduledOccurrenceAt?.let {
                    putExtra(AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT, it.toEpochMilli())
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(name)
            .setContentText(if (calendarWarning) "캘린더를 확인하세요" else "알람이 울리고 있습니다")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Work Alarm 실행 알림"
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ringing_alarm"
        const val EXTRA_ALARM_NAME = "alarm_name"
        private const val SHIFT_FULL_SCREEN_REQUEST_OFFSET = 4_000_000
    }
}
