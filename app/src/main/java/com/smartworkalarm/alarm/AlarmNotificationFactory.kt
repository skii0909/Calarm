package com.smartworkalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class AlarmNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {
    internal fun create(
        sessionKey: RingingSessionKey,
        alarmId: Long,
        name: String,
        isShiftAlarm: Boolean = false,
        calendarWarning: Boolean = false,
        scheduledOccurrenceAt: Instant? = null,
        snoozeEnabled: Boolean = true,
        snoozeMinutes: Int = 5,
    ): Notification {
        ensureChannel()
        val fullScreen = PendingIntent.getActivity(
            context,
            FULL_SCREEN_REQUEST_CODE,
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse(fullScreenIntentIdentity(sessionKey))
                putRingingSessionKey(sessionKey)
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_NAME, name)
                putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, isShiftAlarm)
                putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, calendarWarning)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_ENABLED, snoozeEnabled)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceIn(5, 30))
                scheduledOccurrenceAt?.let {
                    putExtra(AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT, it.toEpochMilli())
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, RingingChannelPolicy.ID)
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
        val channel = NotificationChannel(RingingChannelPolicy.ID, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Work Alarm 실행 알림"
        }
        applyRingingChannelPolicy(
            disableSound = { channel.setSound(null, null) },
            setVibrationEnabled = channel::enableVibration,
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = RingingChannelPolicy.ID
        const val EXTRA_ALARM_NAME = "alarm_name"
        private const val FULL_SCREEN_REQUEST_CODE = 0
    }
}

internal object RingingChannelPolicy {
    const val ID = "ringing_alarm_v2_silent"
}

internal fun applyRingingChannelPolicy(
    disableSound: () -> Unit,
    setVibrationEnabled: (Boolean) -> Unit,
) {
    disableSound()
    setVibrationEnabled(false)
}

internal fun fullScreenIntentIdentity(alarmId: Long, isShiftAlarm: Boolean): String {
    return fullScreenIntentIdentity(
        RingingSessionKey.create(isShiftAlarm, alarmId, scheduledOccurrenceAt = null),
    )
}

internal fun fullScreenIntentIdentity(sessionKey: RingingSessionKey): String =
    "smartworkalarm://ringing/${sessionKey.encode()}"
