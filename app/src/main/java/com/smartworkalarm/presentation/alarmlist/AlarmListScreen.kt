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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.presentation.components.FeaturePill
import com.smartworkalarm.presentation.components.GradientHero

@Composable
fun AlarmListScreen(viewModel: AlarmListViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, onSettings: () -> Unit) {
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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Text("＋", fontSize = 26.sp)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WORK ALARM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text("내 알람", style = MaterialTheme.typography.headlineMedium)
                    }
                    Button(onClick = onSettings, shape = MaterialTheme.shapes.extraLarge) { Text("도움말 · 설정") }
                }
            }
            alarms.firstOrNull { it.enabled }?.let { next ->
                item {
                    GradientHero {
                        Text("NEXT ALARM", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .75f))
                        Text("%02d:%02d".format(next.hour, next.minute), style = MaterialTheme.typography.displayMedium, color = Color.White)
                        Text(next.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(repeatSummary(next.repeatDaysMask), color = Color.White.copy(alpha = .85f))
                    }
                }
            }
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
            if (alarms.isEmpty()) item {
                GradientHero {
                    Text("좋은 아침을 준비해 볼까요?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("＋ 버튼을 눌러 첫 출근 알람을 만들어 보세요.", color = Color.White.copy(alpha = .8f))
                }
            }
            if (alarms.isNotEmpty()) item { Text("모든 알람", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(alarm.id) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("%02d:%02d".format(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineMedium)
                    Text(alarm.name, modifier = Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(repeatSummary(alarm.repeatDaysMask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (alarm.skipOnCalendarMatch) FeaturePill("캘린더")
                    if (alarm.skipOnPublicHoliday) FeaturePill("공휴일")
                    if (alarm.gradualVolume) FeaturePill("점점 크게")
                    FeaturePill("${alarm.snoozeMinutes}분 후")
                }
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
