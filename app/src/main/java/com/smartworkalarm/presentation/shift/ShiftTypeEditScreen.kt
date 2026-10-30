package com.smartworkalarm.presentation.shift

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.presentation.alarmedit.AlarmRingtonePickerField
import com.smartworkalarm.presentation.alarmedit.AlarmTimePickerField
import com.smartworkalarm.presentation.alarmedit.SettingSwitch
import com.smartworkalarm.presentation.alarmedit.readableTextFieldColors
import com.smartworkalarm.presentation.components.DayToggle
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.NightPulseSection
import com.smartworkalarm.presentation.components.EditorScreenHeader
import com.smartworkalarm.presentation.components.PersistentSaveScaffold
import com.smartworkalarm.presentation.components.UnsavedChangesBackHandler

@Composable
fun ShiftTypeEditScreen(
    viewModel: ShiftGroupEditViewModel,
    groupId: Long,
    shiftTypeId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit = onDone,
) {
    val state by viewModel.shiftTypeState.collectAsState()
    LaunchedEffect(groupId, shiftTypeId) { viewModel.loadShiftType(groupId, shiftTypeId) }
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onDone() }
    UnsavedChangesBackHandler(
        hasUnsavedChanges = state.hasUnsavedChanges,
        onBack = onBack,
        onSave = viewModel::saveShiftType,
        onDiscard = onBack,
    )
    ShiftTypeEditScreen(
        state = state,
        onNameChanged = viewModel::onShiftTypeNameChanged,
        onAliasesChanged = viewModel::onExactTitlesChanged,
        onTimeChanged = viewModel::onShiftTimeChanged,
        onToggleDay = viewModel::toggleShiftDay,
        onRingtoneChanged = viewModel::onShiftRingtoneChanged,
        onSnoozeEnabledChanged = viewModel::onShiftSnoozeEnabledChanged,
        onSnoozeChanged = viewModel::onShiftSnoozeChanged,
        onHolidayChanged = viewModel::onShiftHolidayChanged,
        onGradualVolumeChanged = viewModel::onShiftGradualVolumeChanged,
        onEnabledChanged = viewModel::onShiftEnabledChanged,
        onSave = viewModel::saveShiftType,
        onDelete = viewModel::deleteShiftType,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftTypeEditScreen(
    state: ShiftTypeEditState,
    onNameChanged: (String) -> Unit = {},
    onAliasesChanged: (String) -> Unit = {},
    onTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    onToggleDay: (RepeatDay) -> Unit = {},
    onRingtoneChanged: (String?) -> Unit = {},
    onSnoozeEnabledChanged: (Boolean) -> Unit = {},
    onSnoozeChanged: (Int) -> Unit = {},
    onHolidayChanged: (Boolean) -> Unit = {},
    onGradualVolumeChanged: (Boolean) -> Unit = {},
    onEnabledChanged: (Boolean) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    PersistentSaveScaffold(
        saveLabel = "근무 형태 저장",
        onSave = onSave,
    ) {
        EditorScreenHeader(if (state.id == 0L) "근무 형태 추가" else "근무 형태 편집", "근무 일정에 맞춰 하루를 준비해요.")
        state.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        GradientHero {
            Text("알람 시간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RepeatDay.entries.forEach { day ->
                    DayToggle(dayLabel(day), state.repeatDaysMask and day.bit != 0, modifier = Modifier.size(48.dp)) { onToggleDay(day) }
                }
            }
            state.repeatDaysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        NightPulseSection("알람음과 다시 울림", "이 근무 형태에 사용할 알람 설정") {
            AlarmRingtonePickerField(state.ringtoneUri, onRingtoneChanged)
            ShiftSnoozeSettings(
                snoozeEnabled = state.snoozeEnabled,
                snoozeMinutes = state.snoozeMinutes,
                onSnoozeEnabledChanged = onSnoozeEnabledChanged,
                onSnoozeChanged = onSnoozeChanged,
            )
        }
        NightPulseSection("추가 설정") {
            SettingSwitch("공휴일 자동 끄기", state.skipOnPublicHoliday, onHolidayChanged)
            SettingSwitch("벨소리 점점 크게", state.gradualVolume, onGradualVolumeChanged)
            SettingSwitch("알람 활성화", state.enabled, onEnabledChanged)
        }
        if (state.id != 0L) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("근무 형태 삭제") }
        }
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

@Composable
private fun ShiftSnoozeSettings(
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onSnoozeEnabledChanged: (Boolean) -> Unit,
    onSnoozeChanged: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("다시 울림 사용", style = MaterialTheme.typography.titleMedium)
            Text(
                "길게 눌러 설정한 시간 뒤 다시 울립니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = snoozeEnabled, onCheckedChange = onSnoozeEnabledChanged)
    }
    if (snoozeEnabled) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { onSnoozeChanged(snoozeMinutes - 1) }, enabled = snoozeMinutes > 5, modifier = Modifier.semantics { contentDescription = "다시 울림 간격 1분 줄이기" }) { Text("−") }
            Text("${snoozeMinutes}분", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onSnoozeChanged(snoozeMinutes + 1) }, enabled = snoozeMinutes < 30, modifier = Modifier.semantics { contentDescription = "다시 울림 간격 1분 늘리기" }) { Text("+") }
        }
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
