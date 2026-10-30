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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag

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
    val schedulingError by viewModel.schedulingError.collectAsState()
    schedulingError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::clearSchedulingError,
            title = { Text("알람 예약 확인") },
            text = { Text(message) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::clearSchedulingError) { Text("확인") } },
        )
    }
    val upcoming by viewModel.upcoming.collectAsState()
    val refreshState by viewModel.refreshState.collectAsState()
    val calculating by viewModel.calculating.collectAsState()
    val forecastError by viewModel.forecastError.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val refreshVersion by viewModel.refreshVersion.collectAsState()
    val shiftGroups by shiftViewModel.groups.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recalculate()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.recalculate()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshVersion) { shiftViewModel.refresh() }

    AlarmListScaffold(
        alarms = alarms,
        shiftGroups = shiftGroups,
        upcoming = upcoming,
        refreshing = refreshState.refreshing || calculating,
        statusMessage = forecastError ?: when {
            refreshState.refreshing -> "캘린더 동기화 중…"
            calculating -> "공휴일·휴무·키워드 확인 중…"
            else -> listOfNotNull(lastUpdated?.let { "마지막 확인 ${DateTimeFormatter.ofPattern("HH:mm:ss").format(it.atZone(ZoneId.systemDefault()))}" }, refreshState.message.takeIf { it.isNotBlank() }).joinToString("\n")
        },
        onRefresh = viewModel::refresh,
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
internal fun AlarmListScaffold(
    alarms: List<Alarm>,
    shiftGroups: List<ShiftGroupListItem>,
    upcoming: UpcomingAlarms,
    refreshing: Boolean,
    statusMessage: String,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddShiftGroup: () -> Unit,
    onEditShiftGroup: (Long) -> Unit,
    onSettings: () -> Unit,
    onEnabled: (Long, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showSyncDetails by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            androidx.compose.material3.Surface(color = colors.background) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.large) {
                        Text("＋", fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
                        Text("새 알람", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onRefresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("alarm-list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CALARM", style = MaterialTheme.typography.labelMedium, color = colors.primary, letterSpacing = 3.sp)
                            Text("내 알람", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                        androidx.compose.material3.TextButton(onClick = onSettings) { Text("설정", color = colors.onSurfaceVariant) }
                    }
                }
                upcoming.items.firstOrNull()?.let { next ->
                    item {
                        NextAlarmHero(next) {
                            if (next.isShift) shiftGroups.firstOrNull { group -> group.shiftTypes.any { it.id == next.id } }?.let { onEditShiftGroup(it.group.id) }
                            else onEdit(next.id)
                        }
                    }
                }
                if (upcoming.items.size > 1) item { FollowingAlarms(upcoming.items.drop(1).take(3)) }
                item {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Canvas(Modifier.size(6.dp)) { drawCircle(if (upcoming.calendarReadFailed) colors.error else colors.primary) }
                            Text(if (refreshing) "일정 확인 중" else statusMessage.lineSequence().firstOrNull().orEmpty().ifBlank { "일정 확인" },
                                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            androidx.compose.material3.TextButton(onClick = { showSyncDetails = !showSyncDetails }) { Text(if (showSyncDetails) "접기" else "상세", style = MaterialTheme.typography.labelSmall) }
                        }
                        if (upcoming.calendarReadFailed) Text("캘린더 확인 필요 · 일반 알람은 조회 실패 시 울림을 우선합니다.", style = MaterialTheme.typography.bodySmall, color = colors.error)
                        if (showSyncDetails) {
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            Text("앞으로 30일의 공휴일·휴무·제외 키워드를 반영합니다. 일정이 바뀌면 예정 시각도 달라집니다.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            androidx.compose.material3.TextButton(onClick = onRefresh, enabled = !refreshing) { Text("지금 새로고침") }
                        }
                    }
                }
                if (upcoming.items.isEmpty() && alarms.any { it.enabled }) item {
                    Text(if (refreshing) "다음 알람을 확인하고 있어요." else "앞으로 30일 안에 울릴 알람이 없습니다.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                item { HomeSectionHeading("일반 알람", "${alarms.count { it.enabled }}개 켜짐") }
                if (alarms.isEmpty()) item {
                    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = colors.surface) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("나에게 맞는 아침을 시작하세요", style = MaterialTheme.typography.titleMedium)
                            Text("시간과 요일을 정하면, 쉬는 날은 알아서 건너뜁니다.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                items(alarms, key = { "regular-${it.id}" }) { alarm -> AlarmRow(alarm, onEdit, onEnabled) }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("교대 알람", style = MaterialTheme.typography.titleLarge)
                            Text("캘린더의 근무에 맞춰 울려요", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        androidx.compose.material3.TextButton(onClick = onAddShiftGroup) { Text("그룹 추가") }
                    }
                }
                if (shiftGroups.isEmpty()) item {
                    androidx.compose.material3.Surface(onClick = onAddShiftGroup, shape = MaterialTheme.shapes.large, color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("근무표에 맞춘 알람", style = MaterialTheme.typography.titleMedium)
                                Text("주간·야간처럼 근무 형태를 묶어 관리하세요.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Text("→", color = colors.primary, fontSize = 24.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                items(shiftGroups, key = { "shift-${it.group.id}" }) { item -> ShiftGroupRow(item) { onEditShiftGroup(item.group.id) } }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextAlarmHero(next: NextAlarmCardItem, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = next.triggerAt.atZone(ZoneId.systemDefault())
    androidx.compose.material3.Surface(onClick = onEdit, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = colors.inverseSurface, contentColor = colors.inverseOnSurface) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT ALARM", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                Text(if (next.isShift) "교대 알람" else "일반 알람", style = MaterialTheme.typography.labelSmall)
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                Text(DateTimeFormatter.ofPattern("HH:mm").format(time), style = MaterialTheme.typography.displayLarge,
                    fontSize = (maxWidth.value / (3.1f * scale)).coerceAtMost(76f).sp, maxLines = 1,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(next.name, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN).format(time), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("편집 ↗", style = MaterialTheme.typography.labelSmall)
            }
            if (next.calendarWarning) Text("캘린더 확인 필요", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B3A23), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FollowingAlarms(items: List<NextAlarmCardItem>) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = colors.surface) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("그다음 알람", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            items.forEachIndexed { index, next ->
                if (index > 0) androidx.compose.material3.HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
                val time = next.triggerAt.atZone(ZoneId.systemDefault())
                Row(Modifier.fillMaxWidth().testTag("following-alarm-$index"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(next.name, style = MaterialTheme.typography.titleSmall)
                        Text(DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN).format(time) + if (next.isShift) " · 교대" else " · 일반",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (next.calendarWarning) Text("캘린더 확인 필요", style = MaterialTheme.typography.bodySmall, color = colors.error)
                    }
                    Text(DateTimeFormatter.ofPattern("HH:mm").format(time), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShiftGroupRow(item: ShiftGroupListItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val warning = item.status is com.smartworkalarm.domain.shift.ShiftGroupDayStatus.Conflict ||
        item.status == com.smartworkalarm.domain.shift.ShiftGroupDayStatus.None ||
        item.status is com.smartworkalarm.domain.shift.ShiftGroupDayStatus.ReadFailure
    androidx.compose.material3.Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (warning) colors.error.copy(alpha = .45f) else colors.outlineVariant.copy(alpha = .4f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FeaturePill(if (item.group.enabled) "사용 중" else "꺼짐")
            }
            Text("오늘 · ${shiftStatusText(item.status)}", color = if (warning) colors.error else colors.primary, style = MaterialTheme.typography.bodyMedium)
            Text("내일 · ${shiftStatusText(item.tomorrowStatus)}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(item.nextAlarm?.let { "다음 알람 · ${DateTimeFormatter.ofPattern("M.d HH:mm").format(it.triggerAt.atZone(ZoneId.systemDefault()))} ${it.name}" } ?: "다음 알람 · 없음", style = MaterialTheme.typography.bodySmall)
            Text("근무 형태 ${item.shiftTypes.size}개 · ${calendarConnectionSummary(item.calendarConnection)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text("최근 조회 · ${if (item.lastCalendarQuery.succeeded) "성공" else "실패"}" + if (warning) " · 캘린더 확인 필요" else "",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AlarmRow(alarm: Alarm, onEdit: (Long) -> Unit, onEnabled: (Long, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(onClick = { onEdit(alarm.id) }, shape = MaterialTheme.shapes.large, color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .4f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(alarm.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    Text("%02d:%02d".format(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall,
                        color = if (alarm.enabled) colors.onSurface else colors.onSurfaceVariant)
                }
                Switch(checked = alarm.enabled, onCheckedChange = { onEnabled(alarm.id, it) },
                    modifier = Modifier.semantics { contentDescription = "${alarm.name} 알람 ${if (alarm.enabled) "켜짐" else "꺼짐"}" })
            }
            Text(repeatSummary(alarm.repeatDaysMask), style = MaterialTheme.typography.bodyMedium, color = if (alarm.enabled) colors.primary else colors.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (alarm.skipOnCalendarMatch) FeaturePill("일정 제외")
                if (alarm.skipOnPublicHoliday) FeaturePill("공휴일 제외")
                if (alarm.gradualVolume) FeaturePill("점점 크게")
                if (alarm.snoozeEnabled) FeaturePill("다시 울림 ${alarm.snoozeMinutes}분")
            }
        }
    }
}
private fun repeatSummary(mask: Int): String {
    if (mask == 0) return "한 번만"
    return RepeatDay.entries.filter { mask and it.bit != 0 }.joinToString(" ") {
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
}
