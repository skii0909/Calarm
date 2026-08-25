package com.smartworkalarm.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.toAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn

data class ShiftGroupListItem(
    val group: ShiftAlarmGroup,
    val shiftTypes: List<ShiftTypeAlarm>,
    val status: ShiftGroupDayStatus,
    val tomorrowStatus: ShiftGroupDayStatus,
    val nextAlarm: ShiftGroupNextAlarm?,
    val calendarConnection: CalendarConnectionState,
    val lastCalendarQuery: CalendarQueryStatus,
)

data class CalendarQueryStatus(
    val queriedAt: Instant,
    val succeeded: Boolean,
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
    private val connectionRepository: CalendarConnectionRepository,
) : ViewModel() {
    private val refreshVersion = MutableStateFlow(0L)

    val groups: StateFlow<List<ShiftGroupListItem>> = combine(
        repository.observeGroupsWithTypes(),
        refreshVersion,
    ) { aggregates, _ ->
            val now = clock.instant()
            val today = now.atZone(zoneId).toLocalDate()
            val eventsByDate = (0L..7L).associate { offset ->
                val date = today.plusDays(offset)
                date to calendarEventSource.getEventsOverlapping(
                    date.atStartOfDay(zoneId).toInstant(),
                    date.plusDays(1).atStartOfDay(zoneId).toInstant(),
                )
            }
            val connection = connectionRepository.getState()
            val queryStatus = CalendarQueryStatus(now, eventsByDate.values.all(Result<*>::isSuccess))
            aggregates.map { aggregate ->
                val statuses = eventsByDate.mapValues { (date, events) ->
                    evaluator.execute(aggregate, date, events)
                }
                ShiftGroupListItem(
                    group = aggregate.group,
                    shiftTypes = aggregate.shiftTypes,
                    status = checkNotNull(statuses[today]),
                    tomorrowStatus = checkNotNull(statuses[today.plusDays(1)]),
                    nextAlarm = nextAlarm(statuses, now),
                    calendarConnection = connection,
                    lastCalendarQuery = queryStatus,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        refreshVersion.update { it + 1L }
    }

    private fun nextAlarm(statuses: Map<LocalDate, ShiftGroupDayStatus>, now: Instant): ShiftGroupNextAlarm? =
        statuses.mapNotNull { (date, status) ->
            val type = when (status) {
                is ShiftGroupDayStatus.Matched -> status.shiftType
                is ShiftGroupDayStatus.ReadFailure -> status.emergencyType
                else -> null
            } ?: return@mapNotNull null
            val reference = maxOf(now, date.atStartOfDay(zoneId).toInstant().minusNanos(1))
            val triggerAt = calculator.execute(type.toAlarm(), reference, zoneId)
                ?.takeIf { it.atZone(zoneId).toLocalDate() == date }
                ?: return@mapNotNull null
            ShiftGroupNextAlarm(type.id, type.name, triggerAt)
        }.minByOrNull { it.triggerAt }
}
