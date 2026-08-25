package com.smartworkalarm.presentation.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.usecase.SetAlarmEnabledUseCase
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.presentation.shift.ShiftGroupNextAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

data class NextAlarmCardItem(
    val id: Long,
    val name: String,
    val triggerAt: Instant,
    val isShift: Boolean,
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val setEnabled: SetAlarmEnabledUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val refreshNow = MutableStateFlow(clock.instant())

    val nextRegularAlarm: StateFlow<NextAlarmCardItem?> = combine(alarms, refreshNow) { current, now ->
        selectNextRegularAlarm(current, now, zoneId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        setEnabled.execute(id, enabled)
    }

    fun refresh() {
        refreshNow.value = clock.instant()
    }
}

internal fun selectNextRegularAlarm(
    alarms: List<Alarm>,
    now: Instant,
    zoneId: ZoneId,
): NextAlarmCardItem? {
    val calculator = CalculateNextOccurrenceUseCase()
    return alarms.mapNotNull { alarm ->
        calculator.execute(alarm, now, zoneId)?.let { triggerAt ->
            NextAlarmCardItem(alarm.id, alarm.name, triggerAt, isShift = false)
        }
    }.minByOrNull(NextAlarmCardItem::triggerAt)
}

internal fun selectNextAlarmCard(
    regular: NextAlarmCardItem?,
    shifts: List<ShiftGroupNextAlarm>,
): NextAlarmCardItem? = buildList {
    regular?.let(::add)
    shifts.forEach { add(NextAlarmCardItem(it.shiftTypeId, it.name, it.triggerAt, isShift = true)) }
}.minByOrNull(NextAlarmCardItem::triggerAt)
