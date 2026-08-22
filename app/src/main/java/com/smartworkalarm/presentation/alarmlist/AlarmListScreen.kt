package com.smartworkalarm.presentation.alarmlist

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay

@Composable
fun AlarmListScreen(viewModel: AlarmListViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val alarms by viewModel.alarms.collectAsState()
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exactMissing = Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()
    val calendarMissing = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val notificationMissing = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val appSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item { Text("내 알람", style = MaterialTheme.typography.headlineMedium) }
            if (exactMissing) item {
                PermissionCard("정확한 알람 권한이 필요합니다") {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
            if (calendarMissing) item {
                PermissionCard("캘린더 권한이 없어 휴가 자동 건너뛰기가 동작하지 않습니다", appSettings)
            }
            if (notificationMissing) item {
                PermissionCard("알람 알림 권한이 필요합니다", appSettings)
            }
            if (alarms.isEmpty()) item { Text("첫 출근 알람을 추가해 보세요.", modifier = Modifier.padding(top = 48.dp)) }
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(alarm, onEdit, viewModel::setEnabled)
            }
        }
    }
}

@Composable
private fun PermissionCard(message: String, action: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            action?.let { Button(onClick = it) { Text("설정") } }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onEdit: (Long) -> Unit, onEnabled: (Long, Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onEdit(alarm.id) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("%02d:%02d".format(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineSmall)
                Text(alarm.name)
                Text(repeatSummary(alarm.repeatDaysMask), style = MaterialTheme.typography.bodySmall)
                if (alarm.skipOnCalendarMatch) Text("캘린더 일정에 따라 건너뜀", style = MaterialTheme.typography.labelMedium)
            }
            Switch(checked = alarm.enabled, onCheckedChange = { onEnabled(alarm.id, it) })
        }
    }
}

private fun repeatSummary(mask: Int): String = RepeatDay.entries
    .filter { mask and it.bit != 0 }
    .joinToString(" ") {
        when (it) {
            RepeatDay.MONDAY -> "월"
            RepeatDay.TUESDAY -> "화"
            RepeatDay.WEDNESDAY -> "수"
            RepeatDay.THURSDAY -> "목"
            RepeatDay.FRIDAY -> "금"
            RepeatDay.SATURDAY -> "토"
            RepeatDay.SUNDAY -> "일"
        }
    }
