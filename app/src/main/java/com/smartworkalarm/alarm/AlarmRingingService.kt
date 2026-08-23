package com.smartworkalarm.alarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.Handler
import android.os.Looper
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
    private var snoozeMinutes: Int = 5
    private val volumeHandler = Handler(Looper.getMainLooper())
    private var volumeStep = 0
    private val volumeRamp = object : Runnable {
        override fun run() {
            val volume = GradualVolume.atSecond(volumeStep++)
            player?.setVolume(volume, volume)
            if (volumeStep <= 60 && player != null) volumeHandler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, currentAlarmId)
                if (id >= 0) scheduler.scheduleSnooze(id, clock.instant().plus(Duration.ofMinutes(snoozeMinutes.toLong())))
                stopRinging()
            }
            else -> startRinging(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent?) {
        currentAlarmId = intent?.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L) ?: -1L
        val name = intent?.getStringExtra(EXTRA_ALARM_NAME) ?: "알람"
        snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)?.coerceIn(5, 30) ?: 5
        startForeground(NOTIFICATION_ID, notificationFactory.create(currentAlarmId, name))
        if (player == null) {
            val selected = intent?.getStringExtra(EXTRA_RINGTONE_URI)
            val candidates = AlarmSoundResolver.candidates(
                selected,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString(),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString(),
            )
            player = candidates.firstNotNullOfOrNull(::startPlayer)
            if (intent?.getBooleanExtra(EXTRA_GRADUAL_VOLUME, false) == true && player != null) {
                volumeStep = 0
                volumeHandler.removeCallbacks(volumeRamp)
                volumeRamp.run()
            }
        }
        getSystemService<Vibrator>()?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0),
        )
    }

    private fun startPlayer(uri: String): MediaPlayer? {
        val candidate = MediaPlayer()
        return runCatching {
            candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            candidate.setDataSource(this, Uri.parse(uri))
            candidate.isLooping = true
            candidate.prepare()
            candidate.start()
            candidate
        }.getOrElse {
            candidate.release()
            null
        }
    }

    private fun stopRinging() {
        volumeHandler.removeCallbacks(volumeRamp)
        player?.runCatching { stop() }
        player?.release()
        player = null
        getSystemService<Vibrator>()?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        volumeHandler.removeCallbacks(volumeRamp)
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
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_GRADUAL_VOLUME = "gradual_volume"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        private const val NOTIFICATION_ID = 7001
    }
}
