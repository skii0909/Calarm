package com.smartworkalarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    private val presentationState = AlarmPresentationState()
    private val terminalActionGate = TerminalAlarmActionGate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        presentationState.replace(IntentAlarmPayload(intent))
        setContent {
            val presentation = presentationState.current
            WorkAlarmTheme {
                AlarmRingingScreen(
                    presentation = presentation,
                    onSnooze = { send(ACTION_SNOOZE, presentationState.current) },
                    onDismiss = { send(ACTION_DISMISS, presentationState.current) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        presentationState.replace(IntentAlarmPayload(intent))
    }

    private fun send(action: String, presentation: AlarmPresentation) {
        if (!terminalActionGate.tryAcquire()) return

        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, presentation.alarmId)
            putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, presentation.isShiftAlarm)
            putExtra(AlarmRingingService.EXTRA_ALARM_NAME, presentation.alarmName)
            putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, presentation.calendarWarning)
            putExtra(AlarmRingingService.EXTRA_SNOOZE_ENABLED, presentation.snoozeEnabled)
            putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, presentation.snoozeMinutes)
            if (presentation.scheduledOccurrenceAt != Long.MIN_VALUE) {
                putExtra(
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
                    presentation.scheduledOccurrenceAt,
                )
            }
        }
        try {
            startService(serviceIntent)
            finish()
        } catch (error: RuntimeException) {
            terminalActionGate.release()
            throw error
        }
    }

    companion object {
        private const val ACTION_SNOOZE = AlarmRingingService.ACTION_SNOOZE
        private const val ACTION_DISMISS = AlarmRingingService.ACTION_DISMISS
    }
}

internal data class AlarmPresentation(
    val alarmId: Long = -1L,
    val isShiftAlarm: Boolean = false,
    val alarmName: String = "알람",
    val calendarWarning: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val scheduledOccurrenceAt: Long = Long.MIN_VALUE,
)

internal interface AlarmPayload {
    fun getLong(key: String, defaultValue: Long): Long
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String): String?
    fun getInt(key: String, defaultValue: Int): Int
}

internal class AlarmPresentationState {
    var current by mutableStateOf(AlarmPresentation())
        private set

    fun replace(payload: AlarmPayload) {
        current = parseAlarmPresentation(payload)
    }
}

internal fun parseAlarmPresentation(payload: AlarmPayload): AlarmPresentation = AlarmPresentation(
    alarmId = payload.getLong(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L),
    isShiftAlarm = payload.getBoolean(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, false),
    alarmName = payload.getString(AlarmNotificationFactory.EXTRA_ALARM_NAME) ?: "알람",
    calendarWarning = payload.getBoolean(AlarmRingingService.EXTRA_CALENDAR_WARNING, false),
    snoozeEnabled = payload.getBoolean(AlarmRingingService.EXTRA_SNOOZE_ENABLED, true),
    snoozeMinutes = payload.getInt(AlarmRingingService.EXTRA_SNOOZE_MINUTES, 5).coerceIn(5, 30),
    scheduledOccurrenceAt = payload.getLong(
        AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
        Long.MIN_VALUE,
    ),
)

private class IntentAlarmPayload(private val intent: Intent) : AlarmPayload {
    override fun getLong(key: String, defaultValue: Long): Long = intent.getLongExtra(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        intent.getBooleanExtra(key, defaultValue)

    override fun getString(key: String): String? = intent.getStringExtra(key)

    override fun getInt(key: String, defaultValue: Int): Int = intent.getIntExtra(key, defaultValue)
}
