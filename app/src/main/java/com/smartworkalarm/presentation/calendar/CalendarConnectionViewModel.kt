package com.smartworkalarm.presentation.calendar

import androidx.lifecycle.ViewModel
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class CalendarConnectionViewModel @Inject constructor(
    private val repository: CalendarConnectionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CalendarConnectionState?>(null)
    val state = _state.asStateFlow()
    fun refresh() { _state.value = repository.getState() }
}
