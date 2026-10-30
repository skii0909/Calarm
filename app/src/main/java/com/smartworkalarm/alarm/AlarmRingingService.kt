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
import android.os.PowerManager
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingService : Service() {
    @Inject lateinit var notificationFactory: AlarmNotificationFactory
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var shiftScheduler: ShiftAlarmScheduler
    @Inject lateinit var clock: Clock
    @Inject lateinit var attemptTokenFactory: RingingAttemptTokenFactory
    private var player: MediaPlayer? = null
    private val ringingSessionState = RingingSessionStateMachine()
    private val volumeHandler = Handler(Looper.getMainLooper())
    private var volumeStep = 0
    private var gradualVolume = false
    private var preparationTimeout: Runnable? = null
    private var cancelPreparation: (() -> Unit)? = null
    private var preparationWakeLock: PowerManager.WakeLock? = null
    private val playbackController = AlarmPlaybackController(
        object : AlarmPlayback {
            override fun start(uri: String, onFailure: () -> Unit) = startPlayer(uri, onFailure)
            override fun stop() = releasePlayer()
        },
        onExhausted = { Log.e("CalarmPlayback", "All alarm sounds failed, including bundled fallback") },
    )
    private val volumeRamp = object : Runnable {
        override fun run() {
            val volume = GradualVolume.atSecond(volumeStep++)
            runCatching { player?.setVolume(volume, volume) }
            if (volumeStep <= 60 && player != null) volumeHandler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRinging(intent)
            ACTION_DISMISS -> handleDismiss(intent)
            ACTION_SNOOZE -> handleSnooze(intent)
            else -> Unit
        }
        return ringingServiceRestartMode(intent?.action)
    }

    private fun handleDismiss(intent: Intent) {
        val key = intent.ringingSessionKey() ?: return
        val requestId = intent.terminalRequestId() ?: return
        val result = ringingSessionState.dismiss(key)
        publishTerminalActionResult(
            requestId = requestId,
            key = key,
            result = result,
            message = if (result == TerminalActionResult.REJECTED) STALE_ACTION_MESSAGE else null,
        )
        if (result == TerminalActionResult.SUCCESS) stopRinging()
    }

    private fun handleSnooze(intent: Intent) {
        val key = intent.ringingSessionKey() ?: return
        val requestId = intent.terminalRequestId() ?: return
        val result = ringingSessionState.requestSnooze(key, ::scheduleSnooze)
        val message = when (result) {
            TerminalActionResult.SUCCESS -> null
            TerminalActionResult.REJECTED -> STALE_ACTION_MESSAGE
            TerminalActionResult.FAILED -> SNOOZE_FAILURE_MESSAGE
        }
        publishTerminalActionResult(requestId, key, result, message)
        if (result == TerminalActionResult.SUCCESS) stopRinging()
    }

    private fun startRinging(intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        val isShiftAlarm = intent.getBooleanExtra(EXTRA_IS_SHIFT_ALARM, false)
        if (alarmId < 0L) return
        val key = resolveStartSessionKey(
            suppliedKey = intent.ringingSessionKey(),
            alarmId = alarmId,
            isShiftAlarm = isShiftAlarm,
            nextAttemptToken = attemptTokenFactory::next,
        )
        val session = ActiveRingingSession(
            key = key,
            snoozeEnabled = intent.getBooleanExtra(EXTRA_SNOOZE_ENABLED, true),
            snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5).coerceIn(5, 30),
            originalScheduledOccurrenceAt = intent.scheduledOccurrenceAt(),
        )
        ringingSessionState.start(session)
        val name = intent.getStringExtra(EXTRA_ALARM_NAME) ?: "알람"
        startForeground(
            NOTIFICATION_ID,
            notificationFactory.create(
                session.key,
                session.alarmId,
                name,
                session.isShiftAlarm,
                intent.getBooleanExtra(EXTRA_CALENDAR_WARNING, false),
                session.originalScheduledOccurrenceAt,
                session.snoozeEnabled,
                session.snoozeMinutes,
            ),
        )
        gradualVolume = intent.getBooleanExtra(EXTRA_GRADUAL_VOLUME, false)
        playbackController.start(
            AlarmSoundResolver.candidates(
                intent.getStringExtra(EXTRA_RINGTONE_URI),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString(),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString(),
            ),
        )
        runCatching {
            getSystemService<Vibrator>()?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0),
            )
        }.onFailure {
            Log.w("CalarmPlayback", "Alarm vibration unavailable", it)
        }
    }

    private fun scheduleSnooze(session: ActiveRingingSession): ScheduleResult {
        val triggerAt = clock.instant().plus(Duration.ofMinutes(session.snoozeMinutes.toLong()))
        return if (session.isShiftAlarm) {
            shiftScheduler.scheduleShiftSnooze(
                session.alarmId,
                triggerAt,
                session.originalScheduledOccurrenceAt ?: triggerAt,
            )
        } else {
            scheduler.scheduleSnooze(session.alarmId, triggerAt)
        }
    }

    private fun startPlayer(uri: String, onFailure: () -> Unit) {
        releasePlayer()
        preparationWakeLock = runCatching {
            getSystemService<PowerManager>()?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Calarm:audio-prepare",
            )?.apply { acquire(10_000L) }
        }.getOrNull()
        preparationTimeout = Runnable {
            Log.w("CalarmPlayback", "Alarm sound preparation timed out")
            onFailure()
        }.also { volumeHandler.postDelayed(it, 5_000L) }
        val appContext = applicationContext
        cancelPreparation = prepareAlarmPlayer(
            executor = AlarmPreparationExecutors.forUri(uri),
            dispatch = { completion -> volumeHandler.post { completion() }; Unit },
            create = { MediaPlayer() },
            configure = { candidate ->
                candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                candidate.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                candidate.setDataSource(appContext, Uri.parse(uri))
                candidate.isLooping = true
                candidate.prepare()
            },
            dispose = { candidate -> runCatching { candidate.release() }; Unit },
            onReady = { candidate ->
                cancelPreparation = null
                player = candidate
                preparationTimeout?.let(volumeHandler::removeCallbacks)
                preparationTimeout = null
                try {
                    candidate.setOnErrorListener { _, what, extra ->
                        Log.w("CalarmPlayback", "Alarm sound failed: $what/$extra")
                        if (player === candidate) onFailure()
                        true
                    }
                    val initialVolume = if (gradualVolume) GradualVolume.atSecond(0) else 1f
                    candidate.setVolume(initialVolume, initialVolume)
                    candidate.start()
                    releasePreparationWakeLock()
                    if (gradualVolume) {
                        volumeStep = 0
                        volumeRamp.run()
                    }
                } catch (error: Exception) {
                    Log.w("CalarmPlayback", "Unable to start prepared alarm sound", error)
                    onFailure()
                }
            },
            onFailure = { error ->
                Log.w("CalarmPlayback", "Unable to prepare alarm sound", error)
                onFailure()
            },
        )
    }

    private fun releasePreparationWakeLock() {
        runCatching { preparationWakeLock?.let { if (it.isHeld) it.release() } }
        preparationWakeLock = null
    }

    private fun releasePlayer() {
        cancelPreparation?.invoke()
        cancelPreparation = null
        volumeHandler.removeCallbacks(volumeRamp)
        preparationTimeout?.let(volumeHandler::removeCallbacks)
        preparationTimeout = null
        val previous = player
        player = null
        previous?.runCatching { release() }
        releasePreparationWakeLock()
    }

    private fun stopRinging() {
        playbackController.stop()
        runCatching { getSystemService<Vibrator>()?.cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishTerminalActionResult(
        requestId: String,
        key: RingingSessionKey,
        result: TerminalActionResult,
        message: String?,
    ) {
        alarmTerminalResultStore.publish(
            AlarmTerminalServiceResult(requestId, key, result, message),
        )
        sendBroadcast(
            Intent(ACTION_TERMINAL_RESULT)
                .setPackage(packageName)
                .apply {
                    putTerminalRequestId(requestId)
                    putRingingSessionKey(key)
                    putTerminalActionResult(result, message)
                },
        )
    }

    override fun onDestroy() {
        ringingSessionState.clear()
        playbackController.stop()
        runCatching { getSystemService<Vibrator>()?.cancel() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.smartworkalarm.action.START_RINGING"
        const val ACTION_DISMISS = "com.smartworkalarm.action.DISMISS"
        const val ACTION_SNOOZE = "com.smartworkalarm.action.SNOOZE"
        const val ACTION_TERMINAL_RESULT = "com.smartworkalarm.action.TERMINAL_RESULT"
        const val EXTRA_ALARM_NAME = "alarm_name"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_GRADUAL_VOLUME = "gradual_volume"
        const val EXTRA_SNOOZE_ENABLED = "snooze_enabled"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_IS_SHIFT_ALARM = "is_shift_alarm"
        const val EXTRA_CALENDAR_WARNING = "calendar_warning"
        const val EXTRA_RINGING_SESSION_KEY = "ringing_session_key"
        const val EXTRA_TERMINAL_REQUEST_ID = "terminal_request_id"
        const val EXTRA_TERMINAL_ACTION_RESULT = "terminal_action_result"
        const val EXTRA_TERMINAL_ACTION_MESSAGE = "terminal_action_message"
        private const val NOTIFICATION_ID = 7001
        private const val STALE_ACTION_MESSAGE =
            "현재 울리는 알람과 일치하지 않습니다. 새 알람 화면에서 다시 시도해 주세요."
        private const val SNOOZE_FAILURE_MESSAGE =
            "다시 울림을 예약하지 못했습니다. 다시 시도하거나 알람을 해제해 주세요."
    }
}

internal fun canScheduleSnooze(snoozeEnabled: Boolean, requestedAction: String?): Boolean =
    snoozeEnabled && requestedAction == AlarmRingingService.ACTION_SNOOZE

internal fun ringingServiceRestartMode(action: String?): Int =
    if (action == AlarmRingingService.ACTION_START) Service.START_REDELIVER_INTENT else Service.START_NOT_STICKY

private fun Intent.scheduledOccurrenceAt(): Instant? =
    getLongExtra(AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT, Long.MIN_VALUE)
        .takeIf { it != Long.MIN_VALUE }
        ?.let(Instant::ofEpochMilli)
