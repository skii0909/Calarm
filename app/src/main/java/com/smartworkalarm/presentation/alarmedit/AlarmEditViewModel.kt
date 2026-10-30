package com.smartworkalarm.presentation.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.usecase.DeleteAlarmUseCase
import com.smartworkalarm.domain.usecase.CalendarKeywordMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmEditState(
    val id: Long = 0,
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDaysMask: Int = 0,
    val enabled: Boolean = true,
    val skipOnCalendarMatch: Boolean = true,
    val calendarKeywords: String = "",
    val calendarKeywordExactMatch: Boolean = false,
    val ringtoneUri: String? = null,
    val skipOnPublicHoliday: Boolean = false,
    val gradualVolume: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val nameError: String? = null,
    val repeatDaysError: String? = null,
    val saved: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    private val saver: AlarmSaver,
    private val repository: AlarmRepository,
    private val deleteAlarm: DeleteAlarmUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmEditState())
    val state: StateFlow<AlarmEditState> = _state.asStateFlow()
    private var baseline = AlarmEditState().editableSnapshot()

    fun load(id: Long) {
        if (id == 0L || _state.value.id == id) return
        viewModelScope.launch {
            repository.getById(id)?.let { alarm ->
                val loaded = AlarmEditState(
                    id = alarm.id,
                    name = alarm.name,
                    hour = alarm.hour,
                    minute = alarm.minute,
                    repeatDaysMask = alarm.repeatDaysMask,
                    enabled = alarm.enabled,
                    skipOnCalendarMatch = alarm.skipOnCalendarMatch,
                    calendarKeywords = alarm.calendarKeywords.joinToString(", "),
                    calendarKeywordExactMatch = alarm.calendarKeywordExactMatch,
                    ringtoneUri = alarm.ringtoneUri,
                    skipOnPublicHoliday = alarm.skipOnPublicHoliday,
                    gradualVolume = alarm.gradualVolume,
                    snoozeEnabled = alarm.snoozeEnabled,
                    snoozeMinutes = alarm.snoozeMinutes,
                )
                baseline = loaded.editableSnapshot()
                _state.value = loaded
            }
        }
    }

    fun onNameChanged(value: String) = updateEditorState { it.copy(name = value, nameError = null) }
    fun onTimeChanged(hour: Int, minute: Int) = updateEditorState {
        it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))
    }
    fun onEnabledChanged(value: Boolean) = updateEditorState { it.copy(enabled = value) }
    fun onSkipOnCalendarMatchChanged(value: Boolean) = updateEditorState { it.copy(skipOnCalendarMatch = value) }
    fun onCalendarKeywordsChanged(value: String) = updateEditorState { it.copy(calendarKeywords = value) }
    fun onCalendarKeywordExactMatchChanged(value: Boolean) = updateEditorState { it.copy(calendarKeywordExactMatch = value) }
    fun onRingtoneChanged(value: String?) = updateEditorState { it.copy(ringtoneUri = value) }
    fun onSkipOnPublicHolidayChanged(value: Boolean) = updateEditorState { it.copy(skipOnPublicHoliday = value) }
    fun onGradualVolumeChanged(value: Boolean) = updateEditorState { it.copy(gradualVolume = value) }
    fun onSnoozeEnabledChanged(enabled: Boolean) = updateEditorState { it.copy(snoozeEnabled = enabled) }
    fun onSnoozeMinutesChanged(value: Int) = updateEditorState { it.copy(snoozeMinutes = value.coerceIn(5, 30)) }
    fun toggleDay(day: RepeatDay) = updateEditorState {
        it.copy(repeatDaysMask = it.repeatDaysMask xor day.bit, repeatDaysError = null)
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = "알람 이름을 입력하세요") }
            return
        }
        val payload = current.toAlarm()
        val payloadSnapshot = payload.editableSnapshot()
        viewModelScope.launch {
            saver.save(payload).onSuccess { savedId ->
                _state.update { state ->
                    baseline = payloadSnapshot
                    val hasNewChanges = state.editableSnapshot() != payloadSnapshot
                    state.copy(
                        id = if (state.id == 0L) savedId else state.id,
                        saved = !hasNewChanges,
                        hasUnsavedChanges = hasNewChanges,
                    )
                }
            }
        }
    }

    fun delete() = viewModelScope.launch {
        if (_state.value.id != 0L) deleteAlarm.execute(_state.value.id)
        _state.update {
            baseline = it.editableSnapshot()
            it.copy(saved = true, hasUnsavedChanges = false)
        }
    }

    private fun updateEditorState(transform: (AlarmEditState) -> AlarmEditState) {
        _state.update { current ->
            val updated = transform(current)
            updated.copy(hasUnsavedChanges = updated.editableSnapshot() != baseline)
        }
    }

    private fun AlarmEditState.toAlarm() = Alarm(
        id = id,
        name = name,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        enabled = enabled,
        skipOnCalendarMatch = skipOnCalendarMatch,
        calendarKeywordExactMatch = calendarKeywordExactMatch,
        calendarKeywords = CalendarKeywordMatcher.normalize(calendarKeywords),
        ringtoneUri = ringtoneUri,
        skipOnPublicHoliday = skipOnPublicHoliday,
        gradualVolume = gradualVolume,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = snoozeMinutes.coerceIn(5, 30),
    )

}

private data class AlarmEditableSnapshot(
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val skipOnCalendarMatch: Boolean,
    val calendarKeywords: List<String>,
    val calendarKeywordExactMatch: Boolean,
    val ringtoneUri: String?,
    val skipOnPublicHoliday: Boolean,
    val gradualVolume: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
)

private fun AlarmEditState.editableSnapshot() = AlarmEditableSnapshot(
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = skipOnCalendarMatch,
    calendarKeywordExactMatch = calendarKeywordExactMatch,
    calendarKeywords = CalendarKeywordMatcher.normalize(calendarKeywords),
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
)

private fun Alarm.editableSnapshot() = AlarmEditableSnapshot(
    name = name,
    hour = hour,
    minute = minute,
    repeatDaysMask = repeatDaysMask,
    enabled = enabled,
    skipOnCalendarMatch = skipOnCalendarMatch,
    calendarKeywordExactMatch = calendarKeywordExactMatch,
    calendarKeywords = calendarKeywords,
    ringtoneUri = ringtoneUri,
    skipOnPublicHoliday = skipOnPublicHoliday,
    gradualVolume = gradualVolume,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
)
