package com.smartworkalarm.presentation.alarmedit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.sound.RecommendedSoundCatalog
import com.smartworkalarm.sound.AndroidSoundPreviewEngine
import com.smartworkalarm.sound.SoundPreviewController

@Composable
fun AlarmEditScreen(viewModel: AlarmEditViewModel, alarmId: Long, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
            colors = readableTextFieldColors(),
        )
        AlarmTimePickerField(state.hour, state.minute, viewModel::onTimeChanged)
        Text("반복 요일")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RepeatDay.entries.forEach { day ->
                FilterChip(
                    selected = state.repeatDaysMask and day.bit != 0,
                    onClick = { viewModel.toggleDay(day) },
                    label = { Text(dayLabel(day)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.repeatDaysMask and day.bit != 0,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 2.dp,
                    ),
                )
            }
        }
        state.repeatDaysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SettingSwitch("알람 활성화", state.enabled, viewModel::onEnabledChanged)
        SettingSwitch("캘린더 일정에 따라 건너뛰기", state.skipOnCalendarMatch, viewModel::onSkipOnCalendarMatchChanged)
        OutlinedTextField(
            value = state.calendarKeywords,
            onValueChange = viewModel::onCalendarKeywordsChanged,
            enabled = state.skipOnCalendarMatch,
            label = { Text("건너뛸 일정 키워드") },
            placeholder = { Text("휴가, 연차, 병가") },
            supportingText = { Text("여러 키워드는 쉼표(,)로 구분하세요. 일정 제목에 키워드가 하나라도 포함되면 이 알람을 건너뜁니다.") },
            modifier = Modifier.fillMaxWidth(),
            colors = readableTextFieldColors(),
        )
        SettingSwitch("공휴일 자동 끄기", state.skipOnPublicHoliday, viewModel::onSkipOnPublicHolidayChanged)
        Text("대한민국 법정공휴일(2026~2031)에는 이 알람만 건너뜁니다.", style = MaterialTheme.typography.bodySmall)
        SettingSwitch("벨소리 점점 크게", state.gradualVolume, viewModel::onGradualVolumeChanged)
        Text("알람 소리가 1분 동안 서서히 커집니다.", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("다시 울림 ${state.snoozeMinutes}분")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.onSnoozeMinutesChanged(state.snoozeMinutes - 1) },
                    enabled = state.snoozeMinutes > 5,
                ) { Text("−") }
                Text("${state.snoozeMinutes}분", modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(
                    onClick = { viewModel.onSnoozeMinutesChanged(state.snoozeMinutes + 1) },
                    enabled = state.snoozeMinutes < 30,
                ) { Text("+") }
            }
        }
        AlarmRingtonePickerField(state.ringtoneUri, viewModel::onRingtoneChanged)
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        if (alarmId != 0L) OutlinedButton(onClick = { viewModel.delete() }, modifier = Modifier.fillMaxWidth()) { Text("삭제") }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun AlarmRingtonePickerField(ringtoneUri: String?, onSelected: (String?) -> Unit) {
    val context = LocalContext.current
    val title = remember(ringtoneUri) {
        ringtoneUri?.let { uri ->
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
        } ?: "시스템 기본 알람음"
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onSelected(picked?.toString())
        }
    }
    var showRecommended by remember { mutableStateOf(false) }
    val preview = remember(context) { SoundPreviewController(AndroidSoundPreviewEngine(context)) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("알람음")
        OutlinedButton(onClick = { showRecommended = true }, modifier = Modifier.fillMaxWidth()) {
            Text("추천 벨소리 선택")
        }
        OutlinedButton(
            onClick = {
                launcher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let(Uri::parse))
                })
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(title) }
    }
    if (showRecommended) {
        AlertDialog(
            onDismissRequest = { preview.stop(); showRecommended = false },
            title = { Text("추천 벨소리") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("앱에서 직접 만든 저작권 안전 사운드입니다.", style = MaterialTheme.typography.bodySmall)
                    RecommendedSoundCatalog.all.forEach { sound ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { preview.preview(sound.uri) }, modifier = Modifier.weight(1f)) {
                                Text("▶ ${sound.title} · ${sound.description}")
                            }
                            TextButton(onClick = {
                                preview.stop()
                                onSelected(sound.uri)
                                showRecommended = false
                            }) { Text("선택") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview.stop(); showRecommended = false }) { Text("닫기") } },
        )
    }
}

@Composable
private fun readableTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.secondary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.secondary,
    focusedBorderColor = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmTimePickerField(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.headlineSmall)
    }
    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showPicker = false }) {
            androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("시간 선택", style = MaterialTheme.typography.titleLarge)
                    TimePicker(state = pickerState, modifier = Modifier.padding(top = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPicker = false }) { Text("취소") }
                        TextButton(onClick = {
                            onTimeSelected(pickerState.hour, pickerState.minute)
                            showPicker = false
                        }) { Text("확인") }
                    }
                }
            }
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

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
