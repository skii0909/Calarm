package com.smartworkalarm.presentation.alarmedit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.sound.RecommendedSoundCatalog
import com.smartworkalarm.sound.AndroidSoundPreviewEngine
import com.smartworkalarm.sound.SoundPreviewController
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.NightPulseSection
import com.smartworkalarm.presentation.components.DayToggle
import com.smartworkalarm.presentation.components.EditorScreenHeader
import com.smartworkalarm.presentation.components.PersistentSaveScaffold
import com.smartworkalarm.presentation.components.UnsavedChangesBackHandler

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    viewModel: AlarmEditViewModel,
    alarmId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit = onDone,
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val nameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(state.nameError) {
        if (state.nameError != null) {
            scrollState.animateScrollTo(0)
            nameFocusRequester.requestFocus()
        }
    }
    UnsavedChangesBackHandler(
        hasUnsavedChanges = state.hasUnsavedChanges,
        onBack = onBack,
        onSave = viewModel::save,
        onDiscard = onBack,
    )
    PersistentSaveScaffold(
        saveLabel = "알람 저장",
        onSave = viewModel::save,
        scrollState = scrollState,
    ) {
        EditorScreenHeader(if (alarmId == 0L) "새 알람" else "알람 편집", "나의 하루가 시작되는 시간을 정해요.")
        GradientHero {
            Text("알람 시간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AlarmTimePickerField(state.hour, state.minute, viewModel::onTimeChanged, hero = true)
        }
        NightPulseSection("알람 정보", "요일을 선택하지 않으면 한 번만 울립니다") {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("알람 이름") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester), colors = readableTextFieldColors(),
            )
            Text("반복 요일", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RepeatDay.entries.forEach { day ->
                    val selected = state.repeatDaysMask and day.bit != 0
                    DayToggle(dayLabel(day), selected, modifier = Modifier.size(48.dp)) { viewModel.toggleDay(day) }
                }
            }
            if (state.repeatDaysMask == 0) {
                Text(
                    "반복 없음 · 가장 가까운 설정 시각에 한 번만 울림",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            state.repeatDaysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        NightPulseSection("알람음과 다시 울림", "매일 사용할 소리와 다시 울림 간격") {
            AlarmRingtonePickerField(state.ringtoneUri, viewModel::onRingtoneChanged)
            AlarmSnoozeSettings(
                snoozeEnabled = state.snoozeEnabled,
                snoozeMinutes = state.snoozeMinutes,
                onSnoozeEnabledChanged = viewModel::onSnoozeEnabledChanged,
                onSnoozeMinutesChanged = viewModel::onSnoozeMinutesChanged,
            )
        }
        NightPulseSection("자동 건너뛰기", "쉬는 날은 이 알람만 조용하게") {
            SettingSwitch("캘린더 일정으로 건너뛰기", state.skipOnCalendarMatch, viewModel::onSkipOnCalendarMatchChanged)
            OutlinedTextField(
                value = state.calendarKeywords, onValueChange = viewModel::onCalendarKeywordsChanged,
                readOnly = !state.skipOnCalendarMatch, label = { Text("건너뛸 일정 키워드") },
                placeholder = { Text("휴가, 연차") },
                supportingText = {
                    Text(if (state.skipOnCalendarMatch) "여러 키워드는 쉼표(,)로 구분하세요." else "위 설정을 켜면 키워드를 편집할 수 있습니다.")
                },
                modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors(),
            )
            if (state.skipOnCalendarMatch) {
                Text("키워드 일치 방식", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = !state.calendarKeywordExactMatch,
                        onClick = { viewModel.onCalendarKeywordExactMatchChanged(false) },
                        label = { Text("포함") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = state.calendarKeywordExactMatch,
                        onClick = { viewModel.onCalendarKeywordExactMatchChanged(true) },
                        label = { Text("전체 일치") },
                    )
                }
                Text(
                    if (state.calendarKeywordExactMatch) "‘휴가’는 제목이 ‘휴가’인 일정만 제외합니다."
                    else "‘휴가’는 ‘오전 휴가’, ‘가족휴가’도 제외합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingSwitch("공휴일 자동 끄기", state.skipOnPublicHoliday, viewModel::onSkipOnPublicHolidayChanged)
            Text("대한민국 법정공휴일 2026~2031 지원", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NightPulseSection("고급 설정", "필요할 때만 조정하세요") {
            SettingSwitch("벨소리 점점 크게", state.gradualVolume, viewModel::onGradualVolumeChanged)
            Text("알람 소리가 1분 동안 서서히 커집니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSwitch("알람 활성화", state.enabled, viewModel::onEnabledChanged)
        }
        if (alarmId != 0L) OutlinedButton(onClick = { viewModel.delete() }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { Text("알람 삭제") }
    }
}

@Composable
fun AlarmEditScreenForTest(snoozeEnabled: Boolean, snoozeMinutes: Int) {
    AlarmSnoozeSettings(
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = snoozeMinutes,
        onSnoozeEnabledChanged = {},
        onSnoozeMinutesChanged = {},
    )
}

@Composable
private fun AlarmSnoozeSettings(
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onSnoozeEnabledChanged: (Boolean) -> Unit,
    onSnoozeMinutesChanged: (Int) -> Unit,
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
        SnoozeMinutesSelector(snoozeMinutes, onSnoozeMinutesChanged)
    }
}

@Composable
private fun SnoozeMinutesSelector(value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        OutlinedButton(onClick = { onValueChange(value - 1) }, enabled = value > 5, modifier = Modifier.semantics { contentDescription = "다시 울림 간격 1분 줄이기" }) { Text("−") }
        Text("${value}분", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onValueChange(value + 1) }, enabled = value < 30, modifier = Modifier.semantics { contentDescription = "다시 울림 간격 1분 늘리기" }) { Text("+") }
    }
}

@Suppress("DEPRECATION")
@Composable
fun AlarmRingtonePickerField(ringtoneUri: String?, onSelected: (String?) -> Unit) {
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("알람음", style = MaterialTheme.typography.titleSmall)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showRecommended = true }) { Text("추천음") }
            TextButton(
                onClick = {
                    launcher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let(Uri::parse))
                    })
                },
            ) { Text("휴대폰") }
        }
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
fun readableTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmTimePickerField(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    hero: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    Button(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).semantics { contentDescription = "알람 시간 %02d:%02d, 시간 변경".format(hour, minute) },
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (hero) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (hero) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val timeSize = minOf(MaterialTheme.typography.displayLarge.fontSize.value, maxWidth.value / 3.5f / LocalDensity.current.fontScale).sp
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displayLarge, fontSize = timeSize, maxLines = 1)
                Text("눌러서 시간 변경", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, bright: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = null)
    }
}
