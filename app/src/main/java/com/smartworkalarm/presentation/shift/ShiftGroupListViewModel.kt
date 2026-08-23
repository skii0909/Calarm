package com.smartworkalarm.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.toAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ShiftGroupListItem(
    val group: ShiftAlarmGroup,
    val shiftTypes: List<ShiftTypeAlarm>,
    val status: ShiftGroupDayStatus,
    val nextAlarm: ShiftGroupNextAlarm?,
)

data class ShiftGroupNextAlarm(
    val shiftTypeId: Long,
    val name: String,
    val triggerAt: java.time.Instant,
)

@HiltViewModel
class ShiftGroupListViewModel @Inject constructor(
    repository: ShiftAlarmRepository,
    private val calendarEventSource: CalendarEventSource,
    private val evaluator: EvaluateShiftGroupDayUseCase,
    private val calculator: CalculateNextOccurrenceUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    val groups: StateFlow<List<ShiftGroupListItem>> = repository.observeGroupsWithTypes()
        .map { aggregates ->
            val date = clock.instant().atZone(zoneId).toLocalDate()
            val start = date.atStartOfDay(zoneId).toInstant()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val events = calendarEventSource.getEventsOverlapping(start, end)
            aggregates.map { aggregate ->
                val status = evaluator.execute(aggregate, date, events)
                ShiftGroupListItem(
                    group = aggregate.group,
                    shiftTypes = aggregate.shiftTypes,
                    status = status,
                    nextAlarm = nextAlarm(status),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun nextAlarm(status: ShiftGroupDayStatus): ShiftGroupNextAlarm? {
        val type = when (status) {
            is ShiftGroupDayStatus.Matched -> status.shiftType
            is ShiftGroupDayStatus.ReadFailure -> status.emergencyType
            else -> null
        } ?: return null
        val triggerAt = calculator.execute(type.toAlarm(), clock.instant(), zoneId) ?: return null
        return ShiftGroupNextAlarm(type.id, type.name, triggerAt)
    }
}
