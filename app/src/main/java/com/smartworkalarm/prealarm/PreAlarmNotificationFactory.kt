package com.smartworkalarm.prealarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartworkalarm.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal data class PreAlarmNotificationContent(
    val title: String,
    val body: String,
)

internal object PreAlarmNotificationText {
    fun forAlarm(name: String, scheduledTime: LocalTime): PreAlarmNotificationContent =
        PreAlarmNotificationContent(
            title = "${name} 알람이 ${scheduledTime.format(TIME_FORMATTER)}에 울립니다",
            body = "오늘 이 알림을 끌까요?",
        )

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}

internal fun canPostPreAlarmNotification(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.TIRAMISU || permissionGranted

fun interface PreAlarmNotificationCanceller {
    fun cancel(kind: AlarmKind, id: Long)
    fun wasDelivered(key: AlarmOccurrenceKey): Boolean = false
}

class PreAlarmNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) : PreAlarmNotificationCanceller {
    private val deliveries = context.getSharedPreferences("pre_alarm_deliveries", Context.MODE_PRIVATE)

    override fun wasDelivered(key: AlarmOccurrenceKey): Boolean = synchronized(DELIVERY_LOCK) {
        deliveries.getString(deliveryKey(key), null) == key.encode()
    }

    suspend fun reconcileEligibility(now: Instant, isEligible: suspend (AlarmOccurrenceKey) -> Boolean) {
        val keys = synchronized(DELIVERY_LOCK) {
            deliveries.all.values.mapNotNull { (it as? String)?.let(AlarmOccurrenceKey::decode) }
        }
        keys.filter { it.scheduledAt > now }.forEach { key ->
            if (!isEligible(key)) cancel(key)
        }
    }

    fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "알람 선택",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "알람이 울리기 30분 전 선택 알림"
                enableVibration(true)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            },
        )
    }

    fun post(key: AlarmOccurrenceKey, alarmName: String) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!canPostPreAlarmNotification(Build.VERSION.SDK_INT, permissionGranted)) {
            return
        }

        val scheduledTime = key.scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime()
        val content = PreAlarmNotificationText.forAlarm(alarmName, scheduledTime)
        val skip = actionPendingIntent(PreAlarmContract.ACTION_SKIP_OCCURRENCE, key, SKIP_REQUEST_OFFSET)
        val keep = actionPendingIntent(PreAlarmContract.ACTION_KEEP_OCCURRENCE, key, KEEP_REQUEST_OFFSET)
        synchronized(DELIVERY_LOCK) {
            if (wasDelivered(key)) return
            runCatching {
                ensureChannel()
                notificationManager.notify(
                    notificationTag(key.kind, key.id),
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_alarm)
                        .setContentTitle(content.title)
                        .setContentText(content.body)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(false)
                        .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                        .setVibrate(VIBRATION_PATTERN)
                        .addAction(0, "오늘만 끄기", skip)
                        .addAction(0, "유지", keep)
                        .build(),
                )
                check(deliveries.edit().putString(deliveryKey(key), key.encode()).commit())
            }
        }
    }

    fun cancel(key: AlarmOccurrenceKey) {
        cancel(key.kind, key.id)
    }

    override fun cancel(kind: AlarmKind, id: Long) {
        runCatching {
            notificationManager.cancel(notificationTag(kind, id), NOTIFICATION_ID)
        }
    }

    private fun actionPendingIntent(
        action: String,
        key: AlarmOccurrenceKey,
        requestOffset: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        key.encode().hashCode() + requestOffset,
        Intent(context, PreAlarmReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("smartworkalarm://pre-alarm/${key.encode()}/$requestOffset")
            putExtra(PreAlarmContract.EXTRA_KIND, key.kind.name)
            putExtra(PreAlarmContract.EXTRA_ID, key.id)
            putExtra(PreAlarmContract.EXTRA_SCHEDULED_OCCURRENCE_AT, key.scheduledAt.toEpochMilli())
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private val DELIVERY_LOCK = Any()
        private fun deliveryKey(key: AlarmOccurrenceKey): String = "${key.kind}:${key.id}"
        const val CHANNEL_ID = "pre_alarm_choice"
        private const val NOTIFICATION_ID = 7310
        private const val SKIP_REQUEST_OFFSET = 6_000_000
        private const val KEEP_REQUEST_OFFSET = 7_000_000
        private val VIBRATION_PATTERN = longArrayOf(0L, 300L, 200L, 300L)

        private fun notificationTag(kind: AlarmKind, id: Long): String =
            "pre-alarm:${kind.name}:$id"
    }
}
