package com.smartworkalarm.presentation.alarmlist

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.presentation.components.FeaturePill
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.calendar.calendarConnectionSummary
import com.smartworkalarm.presentation.shift.ShiftGroupListItem
import com.smartworkalarm.presentation.shift.ShiftGroupListViewModel
import com.smartworkalarm.presentation.shift.shiftStatusText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val nextRegular by viewModel.nextRegularAlarm.collectAsState()
    val shiftGroups by shiftViewModel.groups.collectAsState()
    val nextAlarm = selectNextAlarmCard(nextRegular, shiftGroups.mapNotNull { it.nextAlarm })

    AlarmListScaffold(
        alarms = alarms,
        shiftGroups = shiftGroups,
        nextAlarm = nextAlarm,
        onRefresh = {
            viewModel.refresh()
            shiftViewModel.refresh()
        },
        onAdd = onAdd,
        onEdit = onEdit,
        onAddShiftGroup = onAddShiftGroup,
        onEditShiftGroup = onEditShiftGroup,
        onSettings = onSettings,
        onEnabled = viewModel::setEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmListScaffold(
    alarms: List<Alarm>,
    shiftGroups: List<ShiftGroupListItem>,
    nextAlarm: NextAlarmCardItem?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddShiftGroup: () -> Unit,
    onEditShiftGroup: (Long) -> Unit,
    onSettings: () -> Unit,
    onEnabled: (Long, Boolean) -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Text("＋", fontSize = 26.sp)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRefresh()
                scope.launch {
                    delay(450)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Calarm", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text("내 알람", style = MaterialTheme.typography.headlineMedium)
                    }
                    Button(onClick = onSettings, shape = MaterialTheme.shapes.extraLarge) { Text("도움말 · 설정") }
                }
            }
            nextAlarm?.let { next ->
                item {
                    GradientHero {
                        Text("NEXT ALARM", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .75f))
                        val zoned = next.triggerAt.atZone(ZoneId.systemDefault())
                        Text("%02d:%02d".format(zoned.hour, zoned.minute), style = MaterialTheme.typography.displayMedium, color = Color.White)
                        Text(next.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            DateTimeFormatter.ofPattern("M월 d일 (E) · ", Locale.KOREAN).format(zoned) +
                                if (next.isShift) "교대 알람" else "일반 알람",
                            color = Color.White.copy(alpha = .85f),
                        )
                    }
                }
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
                AlarmRow(alarm, onEdit, onEnabled)
            }
            item { Spacer(Modifier.height(88.dp)) }
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
            Text("오늘 근무 · ${shiftStatusText(item.status)}", color = MaterialTheme.colorScheme.secondary)
            Text("내일 근무 · ${shiftStatusText(item.tomorrowStatus)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                item.nextAlarm?.let {
                    val time = DateTimeFormatter.ofPattern("MM.dd HH:mm").format(it.triggerAt.atZone(ZoneId.systemDefault()))
                    "다음 알람 · $time ${it.name}"
                } ?: "다음 알람 · 없음",
                style = MaterialTheme.typography.bodyMedium,
            )
            val connectionText = calendarConnectionSummary(item.calendarConnection)
            Text("근무 형태 ${item.shiftTypes.size}개 · $connectionText", style = MaterialTheme.typography.bodySmall)
            Text(
                "최근 조회 · ${if (item.lastCalendarQuery.succeeded) "성공" else "실패"}" +
                    if (warning) " · 캘린더 확인 필요" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun AlarmRow(alarm: Alarm, onEdit: (Long) -> Unit, onEnabled: (Long, Boolean) -> Unit) {
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
                    if (alarm.snoozeEnabled) FeaturePill("${alarm.snoozeMinutes}분 후")
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
