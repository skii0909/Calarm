package com.smartworkalarm.presentation.settings

import androidx.lifecycle.ViewModel
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import com.smartworkalarm.permissions.AlarmDiagnosticItem
import com.smartworkalarm.permissions.AlarmDiagnosticsSource
import com.smartworkalarm.permissions.EmptyAlarmDiagnosticsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CalendarCatalogStatus {
    LOADING,
    PERMISSION_REQUIRED,
    PROVIDER_QUERY_FAILED,
    NO_GOOGLE_CALENDARS,
    ZERO_SELECTED_CALENDARS,
    READY,
}

enum class AccountSelection {
    NONE,
    PARTIAL,
    ALL,
}

data class SettingsCalendarUiState(
    val id: Long,
    val displayName: String,
    val selected: Boolean,
)

data class CalendarAccountUiState(
    val key: String,
    val displayName: String,
    val calendars: List<SettingsCalendarUiState>,
) {
    val selectedCount: Int = calendars.count { it.selected }
    val totalCount: Int = calendars.size
    val selection: AccountSelection = when (selectedCount) {
        0 -> AccountSelection.NONE
        totalCount -> AccountSelection.ALL
        else -> AccountSelection.PARTIAL
    }
}

data class SettingsUiState(
    val catalogStatus: CalendarCatalogStatus = CalendarCatalogStatus.LOADING,
    val accounts: List<CalendarAccountUiState> = emptyList(),
    val userMessage: String? = null,
    val diagnostics: List<AlarmDiagnosticItem> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionRepository: CalendarConnectionRepository,
    private val selectionRepository: CalendarSelectionRepository,
    private val diagnosticsSource: AlarmDiagnosticsSource = EmptyAlarmDiagnosticsSource,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun refresh() {
        val connection = connectionRepository.getState()
        _state.value = connection.toSettingsUiState().copy(
            diagnostics = runCatching(diagnosticsSource::read).getOrDefault(emptyList()),
        )
    }

    fun setCalendarSelected(calendarId: Long, selected: Boolean) {
        persistSelection(selectionRepository.setCalendarSelected(calendarId, selected))
    }

    fun setAccountSelected(accountKey: String, selected: Boolean) {
        val calendarIds = _state.value.accounts
            .firstOrNull { it.key == accountKey }
            ?.calendars
            ?.mapTo(linkedSetOf()) { it.id }
            ?: return
        persistSelection(selectionRepository.setCalendarsSelected(calendarIds, selected))
    }

    private fun persistSelection(result: Result<Unit>) {
        result
            .onSuccess { refresh() }
            .onFailure { showSelectionFailure() }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(userMessage = null)
    }

    private fun showSelectionFailure() {
        _state.value = _state.value.copy(
            userMessage = "캘린더 선택을 변경하지 못했습니다. 다시 시도해 주세요.",
        )
    }
}

private fun CalendarConnectionState.toSettingsUiState(): SettingsUiState {
    val accounts = calendars
        .groupBy { calendar -> calendar.accountKey }
        .map { (key, calendars) ->
            CalendarAccountUiState(
                key = key,
                displayName = if (key == UNNAMED_ACCOUNT_KEY) UNNAMED_ACCOUNT_LABEL else key,
                calendars = calendars
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                    .map(ConnectedCalendar::toSettingsCalendarUiState),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })

    val status = when {
        !permissionGranted -> CalendarCatalogStatus.PERMISSION_REQUIRED
        !calendarQuerySucceeded -> CalendarCatalogStatus.PROVIDER_QUERY_FAILED
        calendars.isEmpty() -> CalendarCatalogStatus.NO_GOOGLE_CALENDARS
        calendars.none { it.selected } -> CalendarCatalogStatus.ZERO_SELECTED_CALENDARS
        else -> CalendarCatalogStatus.READY
    }
    return SettingsUiState(catalogStatus = status, accounts = accounts)
}

private fun ConnectedCalendar.toSettingsCalendarUiState() = SettingsCalendarUiState(
    id = id,
    displayName = displayName,
    selected = selected,
)

private val ConnectedCalendar.accountKey: String
    get() = accountName.trim().ifBlank { UNNAMED_ACCOUNT_KEY }

private const val UNNAMED_ACCOUNT_KEY = "\u0000"
private const val UNNAMED_ACCOUNT_LABEL = "계정 정보 없음"
