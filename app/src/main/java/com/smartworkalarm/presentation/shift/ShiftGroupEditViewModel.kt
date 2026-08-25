package com.smartworkalarm.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartworkalarm.domain.model.RepeatDay
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.ReconcileShiftAlarmsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShiftGroupEditState(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val isDraft: Boolean = false,
    val emergencyShiftTypeId: Long? = null,
    val shiftTypes: List<ShiftTypeAlarm> = emptyList(),
    val todayStatus: ShiftGroupDayStatus? = null,
    val tomorrowStatus: ShiftGroupDayStatus? = null,
    val calendarConnection: CalendarConnectionState = CalendarConnectionState(false, emptyList()),
    val lastCalendarQuery: CalendarQueryStatus? = null,
    val nameError: String? = null,
    val shiftTypesError: String? = null,
    val aliasesError: String? = null,
    val emergencyError: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

data class ShiftTypeEditState(
    val id: Long = 0,
    val groupId: Long = 0,
    val name: String = "",
    val exactTitles: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDaysMask: Int = 0,
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val gradualVolume: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val skipOnPublicHoliday: Boolean = false,
    val nameError: String? = null,
    val aliasesError: String? = null,
    val repeatDaysError: String? = null,
    val loadError: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class ShiftGroupEditViewModel @Inject constructor(
    private val repository: ShiftAlarmRepository,
    private val reconcileShiftAlarms: ReconcileShiftAlarmsUseCase,
    private val calendarEventSource: CalendarEventSource,
    private val evaluator: EvaluateShiftGroupDayUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val connectionRepository: CalendarConnectionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShiftGroupEditState())
    val state: StateFlow<ShiftGroupEditState> = _state.asStateFlow()

    private val _shiftTypeState = MutableStateFlow(ShiftTypeEditState())
    val shiftTypeState: StateFlow<ShiftTypeEditState> = _shiftTypeState.asStateFlow()
    private var draftCreationInProgress = false

    fun loadGroup(groupId: Long) {
        if (groupId == 0L) {
            _state.value = ShiftGroupEditState()
            return
        }
        viewModelScope.launch {
            val aggregate = repository.getGroupWithTypes(groupId) ?: return@launch
            val calendarState = evaluateCalendar(aggregate)
            _state.value = ShiftGroupEditState(
                id = aggregate.group.id,
                name = aggregate.group.name,
                enabled = aggregate.group.enabled,
                isDraft = aggregate.group.isDraft,
                emergencyShiftTypeId = aggregate.group.emergencyShiftTypeId,
                shiftTypes = aggregate.shiftTypes,
                todayStatus = calendarState.today,
                tomorrowStatus = calendarState.tomorrow,
                calendarConnection = connectionRepository.getState(),
                lastCalendarQuery = calendarState.queryStatus,
            )
        }
    }

    fun onGroupNameChanged(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onGroupEnabledChanged(value: Boolean) = _state.update { it.copy(enabled = value) }
    fun onEmergencyShiftTypeChanged(value: Long) = _state.update { it.copy(emergencyShiftTypeId = value, emergencyError = null) }

    fun saveGroup() {
        val current = _state.value
        when {
            current.name.isBlank() -> _state.update { it.copy(nameError = "그룹 이름을 입력하세요") }
            current.shiftTypes.size < 2 -> _state.update { it.copy(shiftTypesError = "근무 형태를 2개 이상 추가하세요") }
            current.shiftTypes.any { it.exactTitles.isEmpty() } ->
                _state.update { it.copy(aliasesError = "각 근무 형태에 캘린더 제목을 입력하세요") }
            hasDuplicateAliases(current.shiftTypes) ->
                _state.update { it.copy(aliasesError = "캘린더 제목 별칭은 그룹 안에서 중복될 수 없습니다") }
            current.emergencyShiftTypeId !in current.shiftTypes.map { it.id } ->
                _state.update { it.copy(emergencyError = "비상 기본 알람을 선택하세요") }
            else -> viewModelScope.launch {
                val savedId = reconcileShiftAlarms.saveGroup(
                    current.toGroup(name = current.name.trim()).copy(isDraft = false),
                )
                _state.update {
                    it.copy(id = if (it.id == 0L) savedId else it.id, name = it.name.trim(), isDraft = false, saved = true)
                }
            }
        }
    }

    fun createDraftAndAddShiftType(onReady: (Long) -> Unit) {
        if (draftCreationInProgress) return
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = "그룹 이름을 입력하세요") }
            return
        }
        if (current.id != 0L) {
            onReady(current.id)
            return
        }
        draftCreationInProgress = true
        viewModelScope.launch {
            try {
                val id = repository.upsertGroup(
                    current.toGroup(name = current.name.trim()).copy(isDraft = true),
                )
                _state.update { it.copy(id = id, name = it.name.trim(), isDraft = true) }
                onReady(id)
            } finally {
                draftCreationInProgress = false
            }
        }
    }

    fun deleteGroup() = viewModelScope.launch {
        val id = _state.value.id
        if (id != 0L) reconcileShiftAlarms.deleteGroup(id)
        _state.update { it.copy(deleted = true) }
    }

    fun cancelDraft() = viewModelScope.launch {
        val current = _state.value
        if (current.id != 0L && current.isDraft) reconcileShiftAlarms.deleteGroup(current.id)
        _state.update { it.copy(deleted = true) }
    }

    fun loadShiftType(groupId: Long, shiftTypeId: Long) {
        viewModelScope.launch {
            val aggregate = repository.getGroupWithTypes(groupId) ?: return@launch
            _state.value = ShiftGroupEditState(
                id = aggregate.group.id,
                name = aggregate.group.name,
                enabled = aggregate.group.enabled,
                isDraft = aggregate.group.isDraft,
                emergencyShiftTypeId = aggregate.group.emergencyShiftTypeId,
                shiftTypes = aggregate.shiftTypes,
            )
            if (shiftTypeId == 0L) {
                _shiftTypeState.value = ShiftTypeEditState(groupId = groupId)
                return@launch
            }
            val type = repository.getShiftType(shiftTypeId)
            _shiftTypeState.value = when {
                type == null -> ShiftTypeEditState(groupId = groupId, loadError = "근무 형태를 찾을 수 없습니다")
                type.groupId != groupId -> ShiftTypeEditState(groupId = groupId, loadError = "이 그룹에 속하지 않은 근무 형태입니다")
                else -> type.toEditState()
            }
        }
    }

    fun onShiftTypeNameChanged(value: String) = _shiftTypeState.update { it.copy(name = value, nameError = null) }
    fun onExactTitlesChanged(value: String) = _shiftTypeState.update { it.copy(exactTitles = value, aliasesError = null) }
    fun onShiftTimeChanged(hour: Int, minute: Int) = _shiftTypeState.update {
        it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))
    }
    fun toggleShiftDay(day: RepeatDay) = _shiftTypeState.update {
        it.copy(repeatDaysMask = it.repeatDaysMask xor day.bit, repeatDaysError = null)
    }
    fun onShiftEnabledChanged(value: Boolean) = _shiftTypeState.update { it.copy(enabled = value) }
    fun onShiftRingtoneChanged(value: String?) = _shiftTypeState.update { it.copy(ringtoneUri = value) }
    fun onShiftGradualVolumeChanged(value: Boolean) = _shiftTypeState.update { it.copy(gradualVolume = value) }
    fun onShiftSnoozeEnabledChanged(enabled: Boolean) = _shiftTypeState.update { it.copy(snoozeEnabled = enabled) }
    fun onShiftSnoozeChanged(value: Int) = _shiftTypeState.update { it.copy(snoozeMinutes = value.coerceIn(5, 30)) }
    fun onShiftHolidayChanged(value: Boolean) = _shiftTypeState.update { it.copy(skipOnPublicHoliday = value) }

    fun saveShiftType() {
        val current = _shiftTypeState.value
        if (current.loadError != null) return
        val aliases = normalizeAliases(current.exactTitles)
        val duplicate = duplicateAliasInOtherType(current.id, aliases)
        when {
            current.name.isBlank() -> _shiftTypeState.update { it.copy(nameError = "근무 형태 이름을 입력하세요") }
            aliases.isEmpty() -> _shiftTypeState.update { it.copy(aliasesError = "캘린더 제목을 하나 이상 입력하세요") }
            duplicate != null -> _shiftTypeState.update {
                it.copy(aliasesError = "다른 근무 형태에서 이미 사용하는 제목입니다: $duplicate")
            }
            current.repeatDaysMask == 0 -> _shiftTypeState.update { it.copy(repeatDaysError = "반복 요일을 하나 이상 선택하세요") }
            else -> viewModelScope.launch {
                val savedId = reconcileShiftAlarms.saveShiftType(current.toShiftType(aliases))
                _shiftTypeState.update { it.copy(id = if (it.id == 0L) savedId else it.id, saved = true) }
            }
        }
    }

    fun deleteShiftType() = viewModelScope.launch {
        val id = _shiftTypeState.value.id
        if (id != 0L) reconcileShiftAlarms.deleteShiftType(_shiftTypeState.value.groupId, id)
        _shiftTypeState.update { it.copy(deleted = true) }
    }

    private suspend fun evaluateCalendar(aggregate: ShiftAlarmRepository.GroupWithTypes): GroupCalendarState {
        val queriedAt = clock.instant()
        val today = queriedAt.atZone(zoneId).toLocalDate()
        val tomorrow = today.plusDays(1)
        val todayEvents = calendarEventSource.getEventsOverlapping(
            today.atStartOfDay(zoneId).toInstant(),
            tomorrow.atStartOfDay(zoneId).toInstant(),
        )
        val tomorrowEvents = calendarEventSource.getEventsOverlapping(
            tomorrow.atStartOfDay(zoneId).toInstant(),
            tomorrow.plusDays(1).atStartOfDay(zoneId).toInstant(),
        )
        return GroupCalendarState(
            today = evaluator.execute(aggregate, today, todayEvents),
            tomorrow = evaluator.execute(aggregate, tomorrow, tomorrowEvents),
            queryStatus = CalendarQueryStatus(queriedAt, todayEvents.isSuccess && tomorrowEvents.isSuccess),
        )
    }

    private fun duplicateAliasInOtherType(typeId: Long, aliases: List<String>): String? {
        val otherAliases = _state.value.shiftTypes
            .filterNot { it.id == typeId }
            .flatMap { it.exactTitles }
            .map(::aliasKey)
            .toSet()
        return aliases.firstOrNull { aliasKey(it) in otherAliases }
    }

    private fun ShiftGroupEditState.toGroup(name: String) = ShiftAlarmGroup(
        id = id,
        name = name,
        enabled = enabled,
        emergencyShiftTypeId = emergencyShiftTypeId,
        isDraft = isDraft,
    )

    private fun ShiftTypeEditState.toShiftType(aliases: List<String>) = ShiftTypeAlarm(
        id = id,
        groupId = groupId,
        name = name.trim(),
        exactTitles = aliases,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        enabled = enabled,
        ringtoneUri = ringtoneUri,
        gradualVolume = gradualVolume,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = snoozeMinutes,
        skipOnPublicHoliday = skipOnPublicHoliday,
    )

    private fun ShiftTypeAlarm.toEditState() = ShiftTypeEditState(
        id = id,
        groupId = groupId,
        name = name,
        exactTitles = exactTitles.joinToString(", "),
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        enabled = enabled,
        ringtoneUri = ringtoneUri,
        gradualVolume = gradualVolume,
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = snoozeMinutes,
        skipOnPublicHoliday = skipOnPublicHoliday,
    )
}

private data class GroupCalendarState(
    val today: ShiftGroupDayStatus,
    val tomorrow: ShiftGroupDayStatus,
    val queryStatus: CalendarQueryStatus,
)

private fun hasDuplicateAliases(types: List<ShiftTypeAlarm>): Boolean {
    val aliases = types.flatMap { it.exactTitles }.map(::aliasKey)
    return aliases.size != aliases.distinct().size
}

private fun normalizeAliases(value: String): List<String> = value
    .split(',', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(::aliasKey)

private fun aliasKey(value: String): String = value.trim().lowercase(Locale.ROOT)
