package com.smartworkalarm.presentation.calendar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.smartworkalarm.domain.model.CalendarConnectionState

@Composable
fun CalendarConnectionPrompt(state: CalendarConnectionState?, refresh: () -> Unit) {
    if (state == null || state.isConnected) return
    var dismissed by remember(state) { mutableStateOf(false) }
    if (dismissed) return
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("캘린더 연결이 필요합니다") },
        text = { Text("일정에 따라 알람을 자동으로 건너뛰려면 Google Calendar를 연결해 주세요.") },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text("나중에") } },
        confirmButton = {
            TextButton(onClick = {
                if (!state.permissionGranted) {
                    permission.launch(Manifest.permission.READ_CALENDAR)
                } else {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }
                        .getOrElse {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                }
            }) { Text("연결하기") }
        },
    )
}
