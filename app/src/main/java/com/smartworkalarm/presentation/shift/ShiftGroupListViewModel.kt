package com.smartworkalarm.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
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
    val todayEvaluation: ShiftDayEvaluation,
)

@HiltViewModel
class ShiftGroupListViewModel @Inject constructor(
    repository: ShiftAlarmRepository,
    private val calendarEventSource: CalendarEventSource,
    private val evaluator: ShiftDayEvaluator,
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
                ShiftGroupListItem(
                    group = aggregate.group,
                    shiftTypes = aggregate.shiftTypes,
                    todayEvaluation = evaluator.evaluate(aggregate.shiftTypes, events),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
