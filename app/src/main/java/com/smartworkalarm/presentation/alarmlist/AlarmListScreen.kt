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
import com.smartworkalarm.presentation.shift.ShiftGroupListItem
import com.smartworkalarm.presentation.shift.ShiftGroupListViewModel
import com.smartworkalarm.presentation.shift.todayStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlarmListScreen(
    viewModel: AlarmListViewModel,
    shiftViewModel: ShiftGroupListViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddShiftGroup: () -> Unit,
    onEditShiftGroup: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val alarms by viewModel.alarms.collectAsState()
    val shiftGroups by shiftViewModel.groups.collectAsState()
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
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("교대 알람 그룹", style = MaterialTheme.typography.titleLarge)
                        Text("캘린더 근무 형태에 맞는 알람 하나를 적용합니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onAddShiftGroup) { Text("＋ 그룹") }
                }
            }
            if (shiftGroups.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Text("교대 근무가 있다면 그룹을 만들고 근무 형태를 2개 이상 추가하세요.", Modifier.padding(16.dp))
                }
            }
            items(shiftGroups, key = { "shift-${it.group.id}" }) { item ->
                ShiftGroupRow(item) { onEditShiftGroup(item.group.id) }
            }
            item {
                Text("일반 알람", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
                Text("교대 그룹과 별도로 항상 독립적으로 동작합니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alarms.isEmpty()) item {
                GradientHero {
                    Text("좋은 아침을 준비해 볼까요?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("＋ 버튼을 눌러 첫 출근 알람을 만들어 보세요.", color = Color.White.copy(alpha = .8f))
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(alarm, onEdit, viewModel::setEnabled)
            }
        }
    }
}

@Composable
private fun ShiftGroupRow(item: ShiftGroupListItem, onClick: () -> Unit) {
    val warning = item.status is com.smartworkalarm.domain.shift.ShiftGroupDayStatus.Conflict ||
        item.status == com.smartworkalarm.domain.shift.ShiftGroupDayStatus.None ||
        item.status is com.smartworkalarm.domain.shift.ShiftGroupDayStatus.ReadFailure
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = item.group.enabled, onCheckedChange = null)
            }
            Text("오늘 근무 · ${todayStatus(item.status)}", color = MaterialTheme.colorScheme.secondary)
            Text(
                item.nextAlarm?.let {
                    val time = DateTimeFormatter.ofPattern("MM.dd HH:mm").format(it.triggerAt.atZone(ZoneId.systemDefault()))
                    "다음 알람 · $time ${it.name}"
                } ?: "다음 알람 · 없음",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("근무 형태 ${item.shiftTypes.size}개 · ${if (warning) "캘린더 확인 필요" else "캘린더 연결됨"}", style = MaterialTheme.typography.bodySmall)
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
