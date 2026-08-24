package com.smartworkalarm.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    private val presentationState = AlarmPresentationState()

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
        presentationState.replace(IntentAlarmPayload(intent))
        setContent {
            val presentation = presentationState.current
            WorkAlarmTheme {
                AlarmRingingScreen(
                    alarmName = presentation.alarmName,
                    timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    snoozeMinutes = presentation.snoozeMinutes,
                    isShiftAlarm = presentation.isShiftAlarm,
                    calendarWarning = presentation.calendarWarning,
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
        startService(Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, presentation.alarmId)
            putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, presentation.isShiftAlarm)
            putExtra(AlarmRingingService.EXTRA_ALARM_NAME, presentation.alarmName)
            putExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, presentation.calendarWarning)
            putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, presentation.snoozeMinutes)
            if (presentation.scheduledOccurrenceAt != Long.MIN_VALUE) {
                putExtra(
                    AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
                    presentation.scheduledOccurrenceAt,
                )
            }
        })
        finish()
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

@Composable
fun AlarmRingingScreen(
    alarmName: String,
    timeText: String,
    snoozeMinutes: Int,
    isShiftAlarm: Boolean,
    calendarWarning: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val night = Color(0xFF07111F)
    val blue = Color(0xFF4DA3FF)
    val mint = Color(0xFF65E0C2)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF102846), night, Color(0xFF050A12))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = blue.copy(alpha = 0.16f), shape = CircleShape) {
                    Text(
                        if (isShiftAlarm) "근무 알람" else "WORK ALARM",
                        color = if (isShiftAlarm) mint else blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(56.dp))
                Text(timeText, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.dp))
                Text(
                    alarmName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (calendarWarning) "캘린더 일정을 확인해 주세요" else "좋은 하루를 시작할 시간이에요",
                    color = if (calendarWarning) Color(0xFFFFC76B) else Color(0xFFB7C8DD),
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, blue.copy(alpha = 0.75f)),
                ) { Text("${snoozeMinutes.coerceIn(5, 30)}분 후 다시 울림", fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = Color(0xFF06101C)),
                ) { Text("알람 해제", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
        }
    }
}
