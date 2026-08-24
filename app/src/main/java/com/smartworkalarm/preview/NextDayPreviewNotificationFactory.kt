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
    fun post(
        alarmEvaluation: AlarmPreviewEvaluation,
        shiftEvaluation: ShiftPreviewEvaluation,
    ) {
        val rows = NextDayPreviewNotificationPolicy.rows(alarmEvaluation, shiftEvaluation)
        if (rows.isEmpty()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "다음 날 알람 변경", NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(true)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
            },
        )
        val text = format(rows)
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

    private fun format(rows: List<String>): PreviewNotificationContent = if (rows.size == 1) {
        PreviewNotificationContent("내일 알람 변경", rows.single(), rows.single())
    } else {
        PreviewNotificationContent(
            "내일 알람 ${rows.size}개 변경",
            "캘린더 일정에 따른 변경 내용을 확인하세요.",
            rows.joinToString("\n"),
        )
    }

    companion object {
        const val CHANNEL_ID = "next_day_alarm_changes"
        private const val NOTIFICATION_ID = 7200
    }
}

internal object NextDayPreviewNotificationPolicy {
    const val CALENDAR_READ_WARNING =
        "내일 알람을 확인할 캘린더를 읽지 못했습니다. 권한과 선택한 캘린더를 확인해 주세요."

    fun rows(
        alarmEvaluation: AlarmPreviewEvaluation,
        shiftEvaluation: ShiftPreviewEvaluation,
    ): List<String> = buildList {
        addAll(alarmEvaluation.changes.map(PreviewNotificationText::row))
        if (!shiftEvaluation.calendarReadFailed) {
            addAll(shiftEvaluation.changes.map(ShiftPreviewNotificationText::row))
        }
        if (alarmEvaluation.calendarReadFailed || shiftEvaluation.calendarReadFailed) {
            add(CALENDAR_READ_WARNING)
        }
    }
}
