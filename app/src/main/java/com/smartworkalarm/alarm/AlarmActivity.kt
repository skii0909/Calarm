package com.smartworkalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

internal typealias AlarmTerminalCommandDispatcher = (Context, Intent) -> Unit

@Volatile
internal var alarmTerminalCommandDispatcher: AlarmTerminalCommandDispatcher = { context, command ->
    context.startService(command)
}

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    private val presentationState = AlarmPresentationState()
    private lateinit var terminalActionController: AlarmTerminalActionController
    private lateinit var terminalResultRouter: AlarmTerminalResultRouter
    private var terminalResultReceiverRegistered = false
    private val terminalResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AlarmRingingService.ACTION_TERMINAL_RESULT) return
            val requestId = intent.terminalRequestId() ?: return
            val resultKey = intent.ringingSessionKey() ?: return
            val result = intent.terminalActionResult() ?: return
            acceptServiceEffect(
                terminalResultRouter.acceptBroadcast(
                    AlarmTerminalServiceResult(
                        requestId = requestId,
                        sessionKey = resultKey,
                        result = result,
                        message = intent.getStringExtra(
                            AlarmRingingService.EXTRA_TERMINAL_ACTION_MESSAGE,
                        ),
                    ),
                ),
            )
        }
    }

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
        val initialSessionKey = presentationState.current.sessionKey
        if (initialSessionKey == null) {
            finish()
            return
        }
        terminalActionController = AlarmTerminalActionController(
            restoreAlarmTerminalUiState(
                initialSessionKey = initialSessionKey,
                savedSessionKey = savedInstanceState?.getString(STATE_TERMINAL_SESSION_KEY),
                savedInFlightRequestId = savedInstanceState?.getString(STATE_TERMINAL_REQUEST_ID),
                savedPersistentError = savedInstanceState?.getString(STATE_TERMINAL_ERROR),
            ),
        )
        terminalResultRouter = AlarmTerminalResultRouter(
            terminalActionController,
            alarmTerminalResultStore,
        )
        ContextCompat.registerReceiver(
            this,
            terminalResultReceiver,
            IntentFilter(AlarmRingingService.ACTION_TERMINAL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        terminalResultReceiverRegistered = true
        acceptServiceEffect(terminalResultRouter.acceptStoredResult())
        if (isFinishing) return
        setContent {
            val presentation = presentationState.current
            val terminalState = terminalActionController.state
            val isCurrentSession = presentation.sessionKey == terminalState.sessionKey
            WorkAlarmTheme {
                key(presentation.sessionKey) {
                    AlarmRingingScreen(
                        presentation = presentation,
                        actionsEnabled = isCurrentSession && terminalState.actionsEnabled,
                        persistentError = terminalState.persistentError.takeIf {
                            isCurrentSession
                        },
                        onSnooze = { send(ACTION_SNOOZE, presentation) },
                        onDismiss = { send(ACTION_DISMISS, presentation) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        presentationState.replace(IntentAlarmPayload(intent))
        presentationState.current.sessionKey?.let(terminalActionController::replaceSession)
    }

    private fun send(action: String, presentation: AlarmPresentation) {
        val sessionKey = presentation.sessionKey ?: return
        val requestId = newTerminalRequestId()
        if (!terminalActionController.tryStartRequest(sessionKey, requestId)) return

        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putTerminalRequestId(requestId)
            putRingingSessionKey(sessionKey)
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
            alarmTerminalCommandDispatcher(this, serviceIntent)
        } catch (error: RuntimeException) {
            terminalActionController.acceptDispatchFailure(
                sessionKey,
                requestId,
                "알람 서비스에 요청을 보내지 못했습니다. 다시 시도해 주세요.",
            )
        }
    }

    private fun acceptServiceEffect(effect: AlarmTerminalUiEffect) {
        when (effect) {
            AlarmTerminalUiEffect.FINISH -> finish()
            AlarmTerminalUiEffect.IGNORE,
            AlarmTerminalUiEffect.STAY_OPEN,
            -> Unit
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!::terminalActionController.isInitialized) {
            super.onSaveInstanceState(outState)
            return
        }
        val terminalState = terminalActionController.state
        outState.putString(STATE_TERMINAL_SESSION_KEY, terminalState.sessionKey.encode())
        terminalState.inFlightRequestId?.let {
            outState.putString(STATE_TERMINAL_REQUEST_ID, it)
        }
        terminalState.persistentError?.let {
            outState.putString(STATE_TERMINAL_ERROR, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (terminalResultReceiverRegistered) {
            unregisterReceiver(terminalResultReceiver)
            terminalResultReceiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SNOOZE = AlarmRingingService.ACTION_SNOOZE
        private const val ACTION_DISMISS = AlarmRingingService.ACTION_DISMISS
        private const val STATE_TERMINAL_SESSION_KEY = "terminal_session_key"
        private const val STATE_TERMINAL_REQUEST_ID = "terminal_request_id"
        private const val STATE_TERMINAL_ERROR = "terminal_error"
    }
}

internal data class AlarmPresentation(
    val sessionKey: RingingSessionKey? = null,
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

internal fun parseAlarmPresentation(payload: AlarmPayload): AlarmPresentation {
    val alarmId = payload.getLong(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
    val isShiftAlarm = payload.getBoolean(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, false)
    val scheduledOccurrenceAt = payload.getLong(
        AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
        Long.MIN_VALUE,
    )
    val encodedSessionKey = payload.getString(AlarmRingingService.EXTRA_RINGING_SESSION_KEY)
    val sessionKey = RingingSessionKey.decode(encodedSessionKey)
    return AlarmPresentation(
        sessionKey = sessionKey,
        alarmId = alarmId,
        isShiftAlarm = isShiftAlarm,
        alarmName = payload.getString(AlarmNotificationFactory.EXTRA_ALARM_NAME) ?: "알람",
        calendarWarning = payload.getBoolean(AlarmRingingService.EXTRA_CALENDAR_WARNING, false),
        snoozeEnabled = payload.getBoolean(AlarmRingingService.EXTRA_SNOOZE_ENABLED, true),
        snoozeMinutes = payload.getInt(AlarmRingingService.EXTRA_SNOOZE_MINUTES, 5).coerceIn(5, 30),
        scheduledOccurrenceAt = scheduledOccurrenceAt,
    )
}

private class IntentAlarmPayload(private val intent: Intent) : AlarmPayload {
    override fun getLong(key: String, defaultValue: Long): Long = intent.getLongExtra(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        intent.getBooleanExtra(key, defaultValue)

    override fun getString(key: String): String? = intent.getStringExtra(key)

    override fun getInt(key: String, defaultValue: Int): Int = intent.getIntExtra(key, defaultValue)
}
