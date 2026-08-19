package com.smartworkalarm.presentation.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.usecase.DeleteAlarmUseCase
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
    val skipOnVacation: Boolean = true,
    val nameError: String? = null,
    val repeatDaysError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    private val saver: AlarmSaver,
    private val repository: AlarmRepository,
    private val deleteAlarm: DeleteAlarmUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmEditState())
    val state: StateFlow<AlarmEditState> = _state.asStateFlow()

    fun load(id: Long) {
        if (id == 0L || _state.value.id == id) return
        viewModelScope.launch {
            repository.getById(id)?.let { alarm ->
                _state.value = AlarmEditState(
                    id = alarm.id,
                    name = alarm.name,
                    hour = alarm.hour,
                    minute = alarm.minute,
                    repeatDaysMask = alarm.repeatDaysMask,
                    enabled = alarm.enabled,
                    skipOnVacation = alarm.skipOnVacation,
                )
            }
        }
    }

    fun onNameChanged(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onTimeChanged(hour: Int, minute: Int) = _state.update { it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59)) }
    fun onEnabledChanged(value: Boolean) = _state.update { it.copy(enabled = value) }
    fun onSkipOnVacationChanged(value: Boolean) = _state.update { it.copy(skipOnVacation = value) }
    fun toggleDay(day: RepeatDay) = _state.update {
        it.copy(repeatDaysMask = it.repeatDaysMask xor day.bit, repeatDaysError = null)
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = "알람 이름을 입력하세요") }
            return
        }
        if (current.repeatDaysMask == 0) {
            _state.update { it.copy(repeatDaysError = "반복 요일을 하나 이상 선택하세요") }
            return
        }
        viewModelScope.launch {
            saver.save(current.toAlarm()).onSuccess { _state.update { state -> state.copy(saved = true) } }
        }
    }

    fun delete() = viewModelScope.launch {
        if (_state.value.id != 0L) deleteAlarm.execute(_state.value.id)
        _state.update { it.copy(saved = true) }
    }

    private fun AlarmEditState.toAlarm() = Alarm(id, name, hour, minute, repeatDaysMask, enabled, skipOnVacation)

}
