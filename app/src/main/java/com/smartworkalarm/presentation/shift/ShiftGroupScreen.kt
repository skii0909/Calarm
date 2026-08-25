package com.smartworkalarm.presentation.shift

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.presentation.alarmedit.readableTextFieldColors
import com.smartworkalarm.presentation.components.FeaturePill
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.NightPulseSection
import com.smartworkalarm.presentation.calendar.calendarConnectionSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ShiftGroupScreen(
    viewModel: ShiftGroupEditViewModel,
    groupId: Long,
    onDone: () -> Unit,
    onEditShiftType: (groupId: Long, shiftTypeId: Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onDone() }
    BackHandler(enabled = state.isDraft) { viewModel.cancelDraft() }
    ShiftGroupScreen(
        state = state,
        onNameChanged = viewModel::onGroupNameChanged,
        onEnabledChanged = viewModel::onGroupEnabledChanged,
        onEmergencyChanged = viewModel::onEmergencyShiftTypeChanged,
        onAddShiftType = {
            viewModel.createDraftAndAddShiftType { persistedGroupId ->
                onEditShiftType(persistedGroupId, 0)
            }
        },
        onEditShiftType = { onEditShiftType(state.id, it) },
        onSave = viewModel::saveGroup,
        onDelete = viewModel::deleteGroup,
    )
}

@Composable
fun ShiftGroupScreen(
    state: ShiftGroupEditState,
    onNameChanged: (String) -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit = {},
    onEmergencyChanged: (Long) -> Unit = {},
    onAddShiftType: () -> Unit = {},
    onEditShiftType: (Long) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var showEmergencyMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("교대 알람 그룹", style = MaterialTheme.typography.headlineSmall)
        GradientHero {
            Text("오늘 근무", color = Color.White.copy(alpha = .75f))
            Text(shiftStatusText(state.todayStatus), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(shiftStatusDetail(state.todayStatus, "오늘"), color = Color.White.copy(alpha = .82f))
        }
        todayCalendarWarning(state.todayStatus)?.let { CalendarWarning(it) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("내일 근무", style = MaterialTheme.typography.titleSmall)
                Text(shiftStatusText(state.tomorrowStatus), style = MaterialTheme.typography.titleLarge)
                Text(shiftStatusDetail(state.tomorrowStatus, "내일"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val connectionText = calendarConnectionSummary(state.calendarConnection)
                Text("캘린더 상태", style = MaterialTheme.typography.titleSmall)
                Text(connectionText)
                state.lastCalendarQuery?.let { query ->
                    val time = DateTimeFormatter.ofPattern("MM.dd HH:mm")
                        .format(query.queriedAt.atZone(ZoneId.systemDefault()))
                    Text(
                        "최근 캘린더 조회 · ${if (query.succeeded) "성공" else "실패"} · $time",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        NightPulseSection("그룹 설정", "이 그룹 안에서는 캘린더에 맞는 근무 알람 하나만 적용됩니다") {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text("그룹 이름") },
                placeholder = { Text("병원 근무, 공장 교대") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = readableTextFieldColors(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("그룹 활성화", modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = onEnabledChanged)
            }
            Column {
                Text("비상 기본 알람", style = MaterialTheme.typography.titleSmall)
                Text(
                    "캘린더 조회에 실패했을 때 사용할 근무 형태",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showEmergencyMenu = true },
                    enabled = state.shiftTypes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(state.shiftTypes.firstOrNull { it.id == state.emergencyShiftTypeId }?.name ?: "선택하세요")
                }
                DropdownMenu(expanded = showEmergencyMenu, onDismissRequest = { showEmergencyMenu = false }) {
                    state.shiftTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.name} · %02d:%02d".format(type.hour, type.minute)) },
                            onClick = { onEmergencyChanged(type.id); showEmergencyMenu = false },
                        )
                    }
                }
                state.emergencyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        NightPulseSection("근무 형태", "2개 이상 추가하고 각 캘린더 제목과 알람을 설정하세요") {
            state.shiftTypes.forEach { type ->
                ShiftTypeCard(type) { onEditShiftType(type.id) }
            }
            OutlinedButton(onClick = onAddShiftType, modifier = Modifier.fillMaxWidth()) { Text("＋ 근무 형태 추가") }
            state.shiftTypesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.aliasesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Text("교대 그룹 저장")
        }
        if (state.id != 0L) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("교대 그룹 삭제") }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("교대 그룹을 삭제할까요?") },
            text = { Text("그룹 안의 모든 근무 형태도 함께 삭제됩니다.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제") } },
        )
    }
}

@Composable
internal fun ShiftTypeCard(type: ShiftTypeAlarm, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(type.name, style = MaterialTheme.typography.titleMedium)
                Text(type.exactTitles.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FeaturePill("%02d:%02d".format(type.hour, type.minute))
                    if (type.gradualVolume) FeaturePill("점점 크게")
                    if (type.snoozeEnabled) FeaturePill("${type.snoozeMinutes}분 후")
                }
            }
            Text("편집")
        }
    }
}

@Composable
private fun CalendarWarning(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

fun shiftStatusText(status: ShiftGroupDayStatus?): String = when (status) {
    ShiftGroupDayStatus.Inactive -> "비활성"
    ShiftGroupDayStatus.Vacation -> "휴가"
    ShiftGroupDayStatus.PublicHoliday -> "공휴일"
    is ShiftGroupDayStatus.Matched -> status.shiftType.name
    ShiftGroupDayStatus.None -> "근무 형태 없음"
    is ShiftGroupDayStatus.Conflict -> "근무 형태 중복"
    is ShiftGroupDayStatus.ReadFailure -> "캘린더 조회 실패"
    null -> "확인 중"
}

fun todayStatus(status: ShiftGroupDayStatus?): String = shiftStatusText(status)

fun todayCalendarWarning(status: ShiftGroupDayStatus?): String? = when (status) {
    is ShiftGroupDayStatus.ReadFailure -> if (status.emergencyType == null) {
        "오늘 사용할 수 있는 비상 기본 알람이 없어 울리지 않습니다. 그룹 설정과 캘린더를 확인해 주세요."
    } else {
        "캘린더를 읽을 수 없어 비상 기본 알람이 적용됩니다."
    }
    ShiftGroupDayStatus.None -> "오늘 캘린더에서 등록된 근무 형태를 찾지 못했습니다."
    is ShiftGroupDayStatus.Conflict -> "여러 근무 형태가 함께 등록되어 있어 교대 알람이 울리지 않습니다."
    ShiftGroupDayStatus.Inactive,
    ShiftGroupDayStatus.Vacation,
    ShiftGroupDayStatus.PublicHoliday,
    is ShiftGroupDayStatus.Matched,
    null,
    -> null
}

fun shiftStatusDetail(status: ShiftGroupDayStatus?, dayLabel: String): String = when (status) {
    ShiftGroupDayStatus.Inactive -> "$dayLabel 활성화된 반복 근무 알람이 없습니다."
    ShiftGroupDayStatus.Vacation -> "휴가 일정이 우선되어 $dayLabel 교대 알람은 울리지 않습니다."
    ShiftGroupDayStatus.PublicHoliday -> "공휴일 자동 끄기가 적용되었습니다."
    is ShiftGroupDayStatus.Matched -> "${dayLabel}은 ${status.shiftType.name} 알람이 적용됩니다."
    ShiftGroupDayStatus.None -> "$dayLabel 교대 그룹 알람은 울리지 않습니다."
    is ShiftGroupDayStatus.Conflict -> status.shiftTypes.joinToString(" · ") { it.name }
    is ShiftGroupDayStatus.ReadFailure -> if (status.emergencyType == null) {
        "$dayLabel 사용할 수 있는 비상 기본 알람이 없습니다."
    } else {
        "지정한 비상 기본 알람을 사용합니다."
    }
    null -> "캘린더 일정을 확인하고 있습니다."
}
