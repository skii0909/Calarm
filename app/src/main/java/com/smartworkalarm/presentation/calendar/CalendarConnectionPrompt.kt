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
    requestKey: String?,
    onOpenSettings: () -> Unit,
) {
    var dismissedRequestKey by remember { mutableStateOf<String?>(null) }
    if (
        !shouldShowCalendarConnectionPrompt(
            state = state,
            requiredPermissionsMissing = requiredPermissionsMissing,
            requestKey = requestKey,
            dismissedRequestKey = dismissedRequestKey,
        )
    ) return
    AlertDialog(
        onDismissRequest = { dismissedRequestKey = requestKey },
        title = { Text("캘린더 연결이 필요합니다") },
        text = { Text("일정에 따라 알람을 자동으로 건너뛰려면 Google 또는 Outlook 캘린더를 연결해 주세요.") },
        dismissButton = {
            TextButton(onClick = { dismissedRequestKey = requestKey }) { Text("나중에") }
        },
        confirmButton = {
            TextButton(onClick = {
                dismissedRequestKey = requestKey
                onOpenSettings()
            }) { Text("연결하기") }
        },
    )
}

internal fun shouldShowCalendarConnectionPrompt(
    state: CalendarConnectionState?,
    requiredPermissionsMissing: Boolean,
    requestKey: String? = "session",
    dismissedRequestKey: String? = null,
): Boolean = state != null &&
    !state.isConnected &&
    !requiredPermissionsMissing &&
    requestKey != null &&
    requestKey != dismissedRequestKey

internal fun calendarConnectionPromptRequestKey(
    route: String?,
    backStackEntryId: String?,
): String? = when (route) {
    null,
    "settings",
    "onboarding",
    -> null

    "alarm/{alarmId}",
    "shift-group/{groupId}",
    "shift-type/{groupId}/{shiftTypeId}",
    -> "$route#${backStackEntryId.orEmpty()}"

    else -> route
}
