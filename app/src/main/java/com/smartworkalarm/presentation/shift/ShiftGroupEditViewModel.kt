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
    val isLoading: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
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
    val hasUnsavedChanges: Boolean = false,
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
    private var groupBaseline = ShiftGroupEditState().editableSnapshot()

    private val _shiftTypeState = MutableStateFlow(ShiftTypeEditState())
    val shiftTypeState: StateFlow<ShiftTypeEditState> = _shiftTypeState.asStateFlow()
    private var shiftTypeBaseline = ShiftTypeEditState().editableSnapshot()
    private var draftCreationInProgress = false

    fun loadGroup(groupId: Long) {
        if (groupId == 0L) {
            val fresh = ShiftGroupEditState()
            groupBaseline = fresh.editableSnapshot()
            _state.value = fresh
            return
        }
        _state.value = ShiftGroupEditState(
            id = groupId,
            isLoading = true,
            hasUnsavedChanges = true,
        )
        viewModelScope.launch {
            val aggregate = repository.getGroupWithTypes(groupId)
            if (aggregate == null) {
                _state.update { it.copy(isLoading = false, hasUnsavedChanges = false) }
                return@launch
            }
            val calendarState = evaluateCalendar(aggregate)
            val loaded = ShiftGroupEditState(
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
            if (_state.value.deleted) return@launch
            groupBaseline = loaded.editableSnapshot()
            _state.value = loaded.copy(hasUnsavedChanges = loaded.isDraft)
        }
    }

    fun onGroupNameChanged(value: String) = updateGroupState { it.copy(name = value, nameError = null) }
    fun onGroupEnabledChanged(value: Boolean) = updateGroupState { it.copy(enabled = value) }
    fun onEmergencyShiftTypeChanged(value: Long) = updateGroupState {
        it.copy(emergencyShiftTypeId = value, emergencyError = null)
    }

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
            else -> {
                val payload = current.toGroup(name = current.name.trim()).copy(isDraft = false)
                val payloadSnapshot = payload.editableSnapshot()
                viewModelScope.launch {
                    val savedId = reconcileShiftAlarms.saveGroup(payload)
                    _state.update {
                        groupBaseline = payloadSnapshot
                        val hasNewChanges = it.editableSnapshot() != payloadSnapshot
                        it.copy(
                            id = if (it.id == 0L) savedId else it.id,
                            name = if (hasNewChanges) it.name else payload.name,
                            isDraft = false,
                            saved = !hasNewChanges,
                            hasUnsavedChanges = hasNewChanges,
                        )
                    }
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
                updateGroupState { it.copy(id = id, name = it.name.trim(), isDraft = true) }
                onReady(id)
            } finally {
                draftCreationInProgress = false
            }
        }
    }

    fun deleteGroup() = viewModelScope.launch {
        val id = _state.value.id
        if (id != 0L) reconcileShiftAlarms.deleteGroup(id)
        _state.update {
            groupBaseline = it.editableSnapshot()
            it.copy(deleted = true, hasUnsavedChanges = false)
        }
    }

    fun cancelDraft() = viewModelScope.launch {
        val current = _state.value
        val persistedDraft = if (current.id != 0L && !current.isDraft) {
            repository.getGroupWithTypes(current.id)?.group?.isDraft == true
        } else {
            false
        }
        if (current.id != 0L && (current.isDraft || persistedDraft)) {
            reconcileShiftAlarms.deleteGroup(current.id)
        }
        _state.update {
            groupBaseline = it.editableSnapshot()
            it.copy(isLoading = false, deleted = true, hasUnsavedChanges = false)
        }
    }

    fun loadShiftType(groupId: Long, shiftTypeId: Long) {
        viewModelScope.launch {
            val aggregate = repository.getGroupWithTypes(groupId) ?: return@launch
            val loadedGroup = ShiftGroupEditState(
                id = aggregate.group.id,
                name = aggregate.group.name,
                enabled = aggregate.group.enabled,
                isDraft = aggregate.group.isDraft,
                emergencyShiftTypeId = aggregate.group.emergencyShiftTypeId,
                shiftTypes = aggregate.shiftTypes,
            )
            groupBaseline = loadedGroup.editableSnapshot()
            _state.value = loadedGroup.copy(hasUnsavedChanges = loadedGroup.isDraft)
            if (shiftTypeId == 0L) {
                val freshType = ShiftTypeEditState(groupId = groupId)
                shiftTypeBaseline = freshType.editableSnapshot()
                _shiftTypeState.value = freshType
                return@launch
            }
            val type = repository.getShiftType(shiftTypeId)
            val loadedType = when {
                type == null -> ShiftTypeEditState(groupId = groupId, loadError = "근무 형태를 찾을 수 없습니다")
                type.groupId != groupId -> ShiftTypeEditState(groupId = groupId, loadError = "이 그룹에 속하지 않은 근무 형태입니다")
                else -> type.toEditState()
            }
            shiftTypeBaseline = loadedType.editableSnapshot()
            _shiftTypeState.value = loadedType
        }
    }

    fun discardGroupChanges() {
        cancelDraft()
    }

    fun onShiftTypeNameChanged(value: String) = updateShiftTypeState { it.copy(name = value, nameError = null) }
    fun onExactTitlesChanged(value: String) = updateShiftTypeState { it.copy(exactTitles = value, aliasesError = null) }
    fun onShiftTimeChanged(hour: Int, minute: Int) = updateShiftTypeState {
        it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))
    }
    fun toggleShiftDay(day: RepeatDay) = updateShiftTypeState {
        it.copy(repeatDaysMask = it.repeatDaysMask xor day.bit, repeatDaysError = null)
    }
    fun onShiftEnabledChanged(value: Boolean) = updateShiftTypeState { it.copy(enabled = value) }
    fun onShiftRingtoneChanged(value: String?) = updateShiftTypeState { it.copy(ringtoneUri = value) }
    fun onShiftGradualVolumeChanged(value: Boolean) = updateShiftTypeState { it.copy(gradualVolume = value) }
    fun onShiftSnoozeEnabledChanged(enabled: Boolean) = updateShiftTypeState { it.copy(snoozeEnabled = enabled) }
    fun onShiftSnoozeChanged(value: Int) = updateShiftTypeState { it.copy(snoozeMinutes = value.coerceIn(5, 30)) }
    fun onShiftHolidayChanged(value: Boolean) = updateShiftTypeState { it.copy(skipOnPublicHoliday = value) }

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
            else -> {
                val payload = current.toShiftType(aliases)
                val payloadSnapshot = payload.editableSnapshot()
                viewModelScope.launch {
                    val savedId = reconcileShiftAlarms.saveShiftType(payload)
                    _shiftTypeState.update {
                        shiftTypeBaseline = payloadSnapshot
                        val hasNewChanges = it.editableSnapshot() != payloadSnapshot
                        it.copy(
                            id = if (it.id == 0L) savedId else it.id,
                            saved = !hasNewChanges,
                            hasUnsavedChanges = hasNewChanges,
                        )
                    }
                }
            }
        }
    }

    fun deleteShiftType() = viewModelScope.launch {
        val id = _shiftTypeState.value.id
        if (id != 0L) reconcileShiftAlarms.deleteShiftType(_shiftTypeState.value.groupId, id)
        _shiftTypeState.update {
            shiftTypeBaseline = it.editableSnapshot()
            it.copy(deleted = true, hasUnsavedChanges = false)
        }
    }

    private fun updateGroupState(transform: (ShiftGroupEditState) -> ShiftGroupEditState) {
        _state.update { current ->
            val updated = transform(current)
            updated.copy(
                hasUnsavedChanges = updated.isDraft || updated.editableSnapshot() != groupBaseline,
            )
        }
    }

    private fun updateShiftTypeState(transform: (ShiftTypeEditState) -> ShiftTypeEditState) {
        _shiftTypeState.update { current ->
            val updated = transform(current)
            updated.copy(hasUnsavedChanges = updated.editableSnapshot() != shiftTypeBaseline)
        }
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

private data class ShiftGroupEditableSnapshot(
    val name: String,
    val enabled: Boolean,
    val emergencyShiftTypeId: Long?,
)

private fun ShiftGroupEditState.editableSnapshot() = ShiftGroupEditableSnapshot(
    name = name.trim(),
    enabled = enabled,
    emergencyShiftTypeId = emergencyShiftTypeId,
)

private fun ShiftAlarmGroup.editableSnapshot() = ShiftGroupEditableSnapshot(
    name = name,
    enabled = enabled,
    emergencyShiftTypeId = emergencyShiftTypeId,
)

private data class ShiftTypeEditableSnapshot(
    val name: String,
    val exactTitles: List<String>,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val ringtoneUri: String?,
    val gradualVolume: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val skipOnPublicHoliday: Boolean,
)

private fun ShiftTypeEditState.editableSnapshot() = ShiftTypeEditableSnapshot(
    name = name.trim(),
    exactTitles = normalizeAliases(exactTitles),
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

private fun ShiftTypeAlarm.editableSnapshot() = ShiftTypeEditableSnapshot(
    name = name,
    exactTitles = exactTitles,
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
