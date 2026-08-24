package com.smartworkalarm.presentation.calendar

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smartworkalarm.domain.model.CalendarConnectionState

@Composable
fun CalendarConnectionPrompt(
    state: CalendarConnectionState?,
    requiredPermissionsMissing: Boolean,
    onOpenSettings: () -> Unit,
) {
    if (!shouldShowCalendarConnectionPrompt(state, requiredPermissionsMissing)) return
    var dismissed by remember(state) { mutableStateOf(false) }
    if (dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("캘린더 연결이 필요합니다") },
        text = { Text("일정에 따라 알람을 자동으로 건너뛰려면 Google Calendar를 연결해 주세요.") },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text("나중에") } },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                onOpenSettings()
            }) { Text("연결하기") }
        },
    )
}

internal fun shouldShowCalendarConnectionPrompt(
    state: CalendarConnectionState?,
    requiredPermissionsMissing: Boolean,
): Boolean = state != null &&
    !state.isConnected &&
    state.permissionGranted &&
    !requiredPermissionsMissing
