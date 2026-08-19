package com.smartworkalarm.alarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.smartworkalarm.domain.repository.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingService : Service() {
    @Inject lateinit var notificationFactory: AlarmNotificationFactory
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var clock: Clock
    private var player: MediaPlayer? = null
    private var currentAlarmId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, currentAlarmId)
                if (id >= 0) scheduler.scheduleSnooze(id, clock.instant().plus(Duration.ofMinutes(5)))
                stopRinging()
            }
            else -> startRinging(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent?) {
        currentAlarmId = intent?.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L) ?: -1L
        val name = intent?.getStringExtra(EXTRA_ALARM_NAME) ?: "알람"
        startForeground(NOTIFICATION_ID, notificationFactory.create(currentAlarmId, name))
        if (player == null) {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(this@AlarmRingingService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                isLooping = true
                prepare()
                start()
            }
        }
        getSystemService<Vibrator>()?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0),
        )
    }

    private fun stopRinging() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        getSystemService<Vibrator>()?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        getSystemService<Vibrator>()?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.smartworkalarm.action.START_RINGING"
        const val ACTION_DISMISS = "com.smartworkalarm.action.DISMISS"
        const val ACTION_SNOOZE = "com.smartworkalarm.action.SNOOZE"
        const val EXTRA_ALARM_NAME = "alarm_name"
        private const val NOTIFICATION_ID = 7001
    }
}
