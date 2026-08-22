package com.smartworkalarm.presentation.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.usecase.SetAlarmEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val setEnabled: SetAlarmEnabledUseCase,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        setEnabled.execute(id, enabled)
    }
}
