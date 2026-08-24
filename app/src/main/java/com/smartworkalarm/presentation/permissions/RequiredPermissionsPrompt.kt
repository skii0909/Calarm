package com.smartworkalarm.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smartworkalarm.permissions.RequiredPermission

@Composable
fun RequiredPermissionsPrompt(
    missing: List<RequiredPermission>,
    onRequestRuntime: (RequiredPermission) -> Unit,
    onOpenSettings: (RequiredPermission) -> Unit,
    onLater: () -> Unit,
) {
    val current = missing.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("필수 권한이 필요합니다") },
        text = {
            Column {
                Text("알람을 안정적으로 실행하려면 다음 권한을 허용해 주세요.")
                missing.forEach { Text("• ${it.label}") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (current) {
                        RequiredPermission.CALENDAR,
                        RequiredPermission.NOTIFICATIONS,
                        -> onRequestRuntime(current)

                        RequiredPermission.EXACT_ALARM,
                        RequiredPermission.FULL_SCREEN_ALARM,
                        -> onOpenSettings(current)
                    }
                },
            ) { Text("설정하기") }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("나중에") } },
    )
}

private val RequiredPermission.label: String
    get() = when (this) {
        RequiredPermission.CALENDAR -> "캘린더 읽기"
        RequiredPermission.NOTIFICATIONS -> "알림"
        RequiredPermission.EXACT_ALARM -> "정확한 알람"
        RequiredPermission.FULL_SCREEN_ALARM -> "전체화면 알람"
    }
