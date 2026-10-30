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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.smartworkalarm.domain.repository.ShiftAlarmRepository

data class NextAlarmCardItem(
    val id: Long,
    val name: String,
    val triggerAt: Instant,
    val isShift: Boolean,
    val calendarWarning: Boolean = false,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class)
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val setEnabled: SetAlarmEnabledUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
    shifts: ShiftAlarmRepository,
    private val upcomingUseCase: UpcomingAlarmsUseCase,
    private val calendarRefresh: CalendarRefreshController,
) : ViewModel() {
    val schedulingError = MutableStateFlow<String?>(null)
    fun clearSchedulingError() { schedulingError.value = null }
    val alarms: StateFlow<List<Alarm>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshVersion = MutableStateFlow(0L)
    val refreshState = calendarRefresh.state
    val upcoming = MutableStateFlow(UpcomingAlarms(emptyList(), false))
    val calculating = MutableStateFlow(true)
    val forecastError = MutableStateFlow<String?>(null)
    val lastUpdated = MutableStateFlow<Instant?>(null)

    init {
        viewModelScope.launch {
            calendarRefresh.changes.debounce(300).collect { recalculate() }
        }
        viewModelScope.launch {
            calendarRefresh.state.map { it.revision }.distinctUntilChanged().collect { recalculate() }
        }
        viewModelScope.launch {
            combine(repository.observeAll(), shifts.observeGroupsWithTypes(), refreshVersion) { current, groups, _ -> current to groups }
                .collectLatest { (current, groups) ->
                    calculating.value = true
                    forecastError.value = null
                    try {
                        upcoming.value = withContext(Dispatchers.IO) {
                            upcomingUseCase.execute(current, groups, clock.instant(), ZoneId.systemDefault())
                        }
                        lastUpdated.value = clock.instant()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { forecastError.value = "예정 알람을 확인하지 못했습니다. 아래로 당겨 다시 시도해 주세요." }
                    finally { calculating.value = false }
                }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        try { setEnabled.execute(id, enabled) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { schedulingError.value = error.message ?: "알람 예약을 변경하지 못했습니다. 다시 확인해 주세요." }
    }

    fun refresh() { viewModelScope.launch { calendarRefresh.refresh() } }
    fun recalculate() { refreshVersion.update { it + 1L } }
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
