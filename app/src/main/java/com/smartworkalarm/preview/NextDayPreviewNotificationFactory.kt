package com.smartworkalarm.preview

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.smartworkalarm.MainActivity
import com.smartworkalarm.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NextDayPreviewNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: NotificationManager,
) {
    fun post(changes: List<AlarmPreviewChange>) {
        if (changes.isEmpty()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "다음 날 알람 변경", NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(true)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
            },
        )
        val text = PreviewNotificationText.format(changes)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_alarm)
                .setContentTitle(text.title)
                .setContentText(text.content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.detail))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "next_day_alarm_changes"
        private const val NOTIFICATION_ID = 7200
    }
}
