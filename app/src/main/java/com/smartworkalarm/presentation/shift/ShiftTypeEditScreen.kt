package com.smartworkalarm.presentation.shift

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.presentation.alarmedit.AlarmRingtonePickerField
import com.smartworkalarm.presentation.alarmedit.AlarmTimePickerField
import com.smartworkalarm.presentation.alarmedit.SettingSwitch
import com.smartworkalarm.presentation.alarmedit.readableTextFieldColors
import com.smartworkalarm.presentation.components.DayToggle
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.NightPulseSection

@Composable
fun ShiftTypeEditScreen(
    viewModel: ShiftGroupEditViewModel,
    groupId: Long,
    shiftTypeId: Long,
    onDone: () -> Unit,
) {
    val state by viewModel.shiftTypeState.collectAsState()
    LaunchedEffect(groupId, shiftTypeId) { viewModel.loadShiftType(groupId, shiftTypeId) }
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onDone() }
    ShiftTypeEditScreen(
        state = state,
        onNameChanged = viewModel::onShiftTypeNameChanged,
        onAliasesChanged = viewModel::onExactTitlesChanged,
        onTimeChanged = viewModel::onShiftTimeChanged,
        onToggleDay = viewModel::toggleShiftDay,
        onRingtoneChanged = viewModel::onShiftRingtoneChanged,
        onSnoozeChanged = viewModel::onShiftSnoozeChanged,
        onHolidayChanged = viewModel::onShiftHolidayChanged,
        onGradualVolumeChanged = viewModel::onShiftGradualVolumeChanged,
        onEnabledChanged = viewModel::onShiftEnabledChanged,
        onSave = viewModel::saveShiftType,
        onDelete = viewModel::deleteShiftType,
    )
}

@Composable
fun ShiftTypeEditScreen(
    state: ShiftTypeEditState,
    onNameChanged: (String) -> Unit = {},
    onAliasesChanged: (String) -> Unit = {},
    onTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    onToggleDay: (RepeatDay) -> Unit = {},
    onRingtoneChanged: (String?) -> Unit = {},
    onSnoozeChanged: (Int) -> Unit = {},
    onHolidayChanged: (Boolean) -> Unit = {},
    onGradualVolumeChanged: (Boolean) -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (state.id == 0L) "근무 형태 추가" else "근무 형태 편집", style = MaterialTheme.typography.headlineSmall)
        GradientHero {
            Text("알람 시간", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .75f))
            AlarmTimePickerField(state.hour, state.minute, onTimeChanged, hero = true)
        }
        NightPulseSection("근무 형태 정보", "캘린더 제목 전체가 별칭과 같을 때 이 알람이 선택됩니다") {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text("근무 형태 이름") },
                placeholder = { Text("주간, 야간, 새벽") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = readableTextFieldColors(),
            )
            OutlinedTextField(
                value = state.exactTitles,
                onValueChange = onAliasesChanged,
                label = { Text("정확히 일치할 캘린더 제목") },
                placeholder = { Text("야간, 야간근무, 야간1") },
                isError = state.aliasesError != null,
                supportingText = {
                    Text(state.aliasesError ?: "제목 전체가 일치할 때만 적용됩니다")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = readableTextFieldColors(),
            )
            Text("반복 요일", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RepeatDay.entries.forEach { day ->
                    DayToggle(dayLabel(day), state.repeatDaysMask and day.bit != 0) { onToggleDay(day) }
                }
            }
            state.repeatDaysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        NightPulseSection("알람음과 다시 울림", "이 근무 형태에 사용할 알람 설정") {
            AlarmRingtonePickerField(state.ringtoneUri, onRingtoneChanged)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("다시 울림 ${state.snoozeMinutes}분", style = MaterialTheme.typography.titleSmall)
                Row {
                    OutlinedButton(onClick = { onSnoozeChanged(state.snoozeMinutes - 1) }, enabled = state.snoozeMinutes > 5) { Text("−") }
                    OutlinedButton(onClick = { onSnoozeChanged(state.snoozeMinutes + 1) }, enabled = state.snoozeMinutes < 30) { Text("+") }
                }
            }
        }
        NightPulseSection("추가 설정") {
            SettingSwitch("공휴일 자동 끄기", state.skipOnPublicHoliday, onHolidayChanged)
            SettingSwitch("벨소리 점점 크게", state.gradualVolume, onGradualVolumeChanged)
            SettingSwitch("알람 활성화", state.enabled, onEnabledChanged)
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("근무 형태 저장") }
        if (state.id != 0L) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("근무 형태 삭제") }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("근무 형태를 삭제할까요?") },
            text = { Text("이 근무 형태의 알람 설정과 제목 별칭이 삭제됩니다.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제") } },
        )
    }
}

private fun dayLabel(day: RepeatDay): String = when (day) {
    RepeatDay.MONDAY -> "월"
    RepeatDay.TUESDAY -> "화"
    RepeatDay.WEDNESDAY -> "수"
    RepeatDay.THURSDAY -> "목"
    RepeatDay.FRIDAY -> "금"
    RepeatDay.SATURDAY -> "토"
    RepeatDay.SUNDAY -> "일"
}
