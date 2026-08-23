package com.smartworkalarm.alarm

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
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
        val id = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        val isShiftAlarm = intent.getBooleanExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, false)
        val name = intent.getStringExtra(AlarmNotificationFactory.EXTRA_ALARM_NAME) ?: "알람"
        val calendarWarning = intent.getBooleanExtra(AlarmRingingService.EXTRA_CALENDAR_WARNING, false)
        val scheduledOccurrenceAt = intent.getLongExtra(
            AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT,
            Long.MIN_VALUE,
        )
        setContent {
            MaterialTheme {
                AlarmControls(
                    if (calendarWarning) "$name\n캘린더를 확인하세요" else name,
                    onSnooze = { send(ACTION_SNOOZE, id, isShiftAlarm, scheduledOccurrenceAt) },
                    onDismiss = { send(ACTION_DISMISS, id, isShiftAlarm, scheduledOccurrenceAt) },
                )
            }
        }
    }

    private fun send(action: String, id: Long, isShiftAlarm: Boolean, scheduledOccurrenceAt: Long) {
        startService(Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, id)
            putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, isShiftAlarm)
            if (scheduledOccurrenceAt != Long.MIN_VALUE) {
                putExtra(AlarmSchedulerImpl.EXTRA_SCHEDULED_OCCURRENCE_AT, scheduledOccurrenceAt)
            }
        })
        finish()
    }

    @Composable
    private fun AlarmControls(name: String, onSnooze: () -> Unit, onDismiss: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 32.dp)) {
                Button(onClick = onSnooze) { Text("다시 알림 (5분)") }
                Button(onClick = onDismiss) { Text("해제") }
            }
        }
    }

    companion object {
        private const val ACTION_SNOOZE = AlarmRingingService.ACTION_SNOOZE
        private const val ACTION_DISMISS = AlarmRingingService.ACTION_DISMISS
    }
}
