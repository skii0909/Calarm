package com.smartworkalarm.presentation.alarmedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartworkalarm.domain.model.RepeatDay

@Composable
fun AlarmEditScreen(viewModel: AlarmEditViewModel, alarmId: Long, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (alarmId == 0L) "알람 추가" else "알람 수정", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("알람 이름") },
            isError = state.nameError != null,
            supportingText = { state.nameError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.hour.toString(),
                onValueChange = { it.toIntOrNull()?.let { hour -> viewModel.onTimeChanged(hour, state.minute) } },
                label = { Text("시") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.minute.toString(),
                onValueChange = { it.toIntOrNull()?.let { minute -> viewModel.onTimeChanged(state.hour, minute) } },
                label = { Text("분") },
                modifier = Modifier.weight(1f),
            )
        }
        Text("반복 요일")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RepeatDay.entries.forEach { day ->
                FilterChip(
                    selected = state.repeatDaysMask and day.bit != 0,
                    onClick = { viewModel.toggleDay(day) },
                    label = { Text(dayLabel(day)) },
                )
            }
        }
        state.repeatDaysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SettingSwitch("알람 활성화", state.enabled, viewModel::onEnabledChanged)
        SettingSwitch("캘린더 일정에 따라 건너뛰기", state.skipOnCalendarMatch, viewModel::onSkipOnCalendarMatchChanged)
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        if (alarmId != 0L) OutlinedButton(onClick = { viewModel.delete() }, modifier = Modifier.fillMaxWidth()) { Text("삭제") }
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

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
