package com.smartworkalarm.presentation.settings

import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.CalendarSelectionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.smartworkalarm.permissions.AlarmDiagnosticItem
import com.smartworkalarm.permissions.AlarmDiagnosticKind
import com.smartworkalarm.permissions.AlarmDiagnosticsSource
import com.smartworkalarm.permissions.DiagnosticStatus

class SettingsViewModelTest {
    @Test
    fun `refresh updates alarm diagnostics with calendar state`() {
        val diagnostic = AlarmDiagnosticItem(
            AlarmDiagnosticKind.BATTERY,
            DiagnosticStatus.ACTION_REQUIRED,
            "배터리 설정을 확인하세요.",
        )
        val viewModel = SettingsViewModel(
            QueueConnectionRepository(CalendarConnectionState(true, emptyList())),
            RecordingSelectionRepository(),
            AlarmDiagnosticsSource { listOf(diagnostic) },
        )

        viewModel.refresh()

        assertEquals(listOf(diagnostic), viewModel.state.value.diagnostics)
    }
    @Test
    fun `starts loading and queries only after refresh`() {
        val repository = QueueConnectionRepository(
            CalendarConnectionState(true, listOf(calendar(1, "개인", "me@example.com"))),
        )
        val viewModel = SettingsViewModel(repository, RecordingSelectionRepository())

        assertEquals(CalendarCatalogStatus.LOADING, viewModel.state.value.catalogStatus)
        assertEquals(0, repository.calls)

        viewModel.refresh()

        assertEquals(CalendarCatalogStatus.READY, viewModel.state.value.catalogStatus)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `groups accounts and sorts accounts and calendar display names`() {
        val viewModel = viewModelWith(
            calendars = listOf(
                calendar(4, "업무", "z@example.com"),
                calendar(3, "가족", "z@example.com"),
                calendar(2, "기본", "a@example.com"),
                calendar(1, "이름 없음", "   "),
            ),
        )

        val accounts = viewModel.state.value.accounts

        assertEquals(listOf("계정 정보 없음", "a@example.com", "z@example.com"), accounts.map { it.displayName })
        assertEquals(listOf("가족", "업무"), accounts.last().calendars.map { it.displayName })
    }

    @Test
    fun `derives none partial and all selection for each account`() {
        val viewModel = viewModelWith(
            calendars = listOf(
                calendar(1, "미선택 1", "none@example.com", selected = false),
                calendar(2, "미선택 2", "none@example.com", selected = false),
                calendar(3, "선택", "partial@example.com", selected = true),
                calendar(4, "미선택", "partial@example.com", selected = false),
                calendar(5, "선택 1", "all@example.com", selected = true),
                calendar(6, "선택 2", "all@example.com", selected = true),
            ),
        )

        val accounts = viewModel.state.value.accounts.associateBy { it.displayName }

        assertEquals(AccountSelection.NONE, accounts.getValue("none@example.com").selection)
        assertEquals(AccountSelection.PARTIAL, accounts.getValue("partial@example.com").selection)
        assertEquals(AccountSelection.ALL, accounts.getValue("all@example.com").selection)
        assertEquals(1, accounts.getValue("partial@example.com").selectedCount)
        assertEquals(2, accounts.getValue("partial@example.com").totalCount)
    }

    @Test
    fun `individual toggle sends exactly one calendar ID`() {
        val selection = RecordingSelectionRepository()
        val viewModel = viewModelWith(
            calendars = listOf(calendar(41, "개인", "me@example.com")),
            selection = selection,
        )

        viewModel.setCalendarSelected(calendarId = 41, selected = false)

        assertEquals(listOf(41L to false), selection.calendarCalls)
        assertEquals(emptyList<Pair<Set<Long>, Boolean>>(), selection.accountCalls)
    }

    @Test
    fun `account toggle sends exactly the IDs in that account`() {
        val selection = RecordingSelectionRepository()
        val viewModel = viewModelWith(
            calendars = listOf(
                calendar(11, "개인", "a@example.com"),
                calendar(12, "업무", "a@example.com"),
                calendar(99, "다른 계정", "b@example.com"),
            ),
            selection = selection,
        )
        val accountKey = viewModel.state.value.accounts.single { it.displayName == "a@example.com" }.key

        viewModel.setAccountSelected(accountKey = accountKey, selected = false)

        assertEquals(listOf(setOf(11L, 12L) to false), selection.accountCalls)
        assertEquals(emptyList<Pair<Long, Boolean>>(), selection.calendarCalls)
    }

    @Test
    fun `successful toggle refreshes the visible catalog immediately`() {
        val repository = QueueConnectionRepository(
            CalendarConnectionState(true, listOf(calendar(7, "개인", "me@example.com", selected = true))),
            CalendarConnectionState(true, listOf(calendar(7, "개인", "me@example.com", selected = false))),
        )
        val viewModel = SettingsViewModel(repository, RecordingSelectionRepository())
        viewModel.refresh()

        viewModel.setCalendarSelected(calendarId = 7, selected = false)

        assertEquals(2, repository.calls)
        assertEquals(CalendarCatalogStatus.ZERO_SELECTED_CALENDARS, viewModel.state.value.catalogStatus)
        assertEquals(false, viewModel.state.value.accounts.single().calendars.single().selected)
    }

    @Test
    fun `failed toggle preserves visible state exposes Korean message and can consume it`() {
        val selection = RecordingSelectionRepository(calendarResult = Result.failure(IllegalStateException("disk full")))
        val viewModel = viewModelWith(
            calendars = listOf(calendar(7, "개인", "me@example.com", selected = true)),
            selection = selection,
        )
        val before = viewModel.state.value

        viewModel.setCalendarSelected(calendarId = 7, selected = false)

        assertEquals(before.accounts, viewModel.state.value.accounts)
        assertEquals(before.catalogStatus, viewModel.state.value.catalogStatus)
        assertEquals("캘린더 선택을 변경하지 못했습니다. 다시 시도해 주세요.", viewModel.state.value.userMessage)

        viewModel.consumeMessage()

        assertNull(viewModel.state.value.userMessage)
    }

    @Test
    fun `distinguishes provider failure empty catalog zero selected and ready`() {
        val states = listOf(
            CalendarConnectionState(true, emptyList(), calendarQuerySucceeded = false) to CalendarCatalogStatus.PROVIDER_QUERY_FAILED,
            CalendarConnectionState(true, emptyList(), calendarQuerySucceeded = true) to CalendarCatalogStatus.NO_GOOGLE_CALENDARS,
            CalendarConnectionState(true, listOf(calendar(1, "개인", "me@example.com", selected = false))) to CalendarCatalogStatus.ZERO_SELECTED_CALENDARS,
            CalendarConnectionState(true, listOf(calendar(1, "개인", "me@example.com", selected = true))) to CalendarCatalogStatus.READY,
        )

        states.forEach { (connection, expected) ->
            val viewModel = SettingsViewModel(QueueConnectionRepository(connection), RecordingSelectionRepository())
            viewModel.refresh()
            assertEquals(expected, viewModel.state.value.catalogStatus)
        }
    }

    private fun viewModelWith(
        calendars: List<ConnectedCalendar>,
        selection: RecordingSelectionRepository = RecordingSelectionRepository(),
    ): SettingsViewModel = SettingsViewModel(
        connectionRepository = QueueConnectionRepository(CalendarConnectionState(true, calendars)),
        selectionRepository = selection,
    ).apply { refresh() }

    private fun calendar(
        id: Long,
        displayName: String,
        accountName: String,
        selected: Boolean = true,
    ) = ConnectedCalendar(
        id = id,
        displayName = displayName,
        accountName = accountName,
        accountType = "com.google",
        selected = selected,
    )
}

private class QueueConnectionRepository(
    vararg states: CalendarConnectionState,
) : CalendarConnectionRepository {
    private val states = ArrayDeque(states.toList())
    private var latest = states.last()
    var calls = 0
        private set

    override fun getState(): CalendarConnectionState {
        calls += 1
        if (states.isNotEmpty()) latest = states.removeFirst()
        return latest
    }
}

private class RecordingSelectionRepository(
    private val calendarResult: Result<Unit> = Result.success(Unit),
    private val accountResult: Result<Unit> = Result.success(Unit),
) : CalendarSelectionRepository {
    val calendarCalls = mutableListOf<Pair<Long, Boolean>>()
    val accountCalls = mutableListOf<Pair<Set<Long>, Boolean>>()

    override fun getSelectedCalendarIds(): Result<Set<Long>> = Result.success(emptySet())

    override fun reconcile(availableCalendarIds: Set<Long>): Result<Set<Long>> = Result.success(availableCalendarIds)

    override fun setCalendarSelected(calendarId: Long, selected: Boolean): Result<Unit> {
        calendarCalls += calendarId to selected
        return calendarResult
    }

    override fun setCalendarsSelected(calendarIds: Set<Long>, selected: Boolean): Result<Unit> {
        accountCalls += calendarIds to selected
        return accountResult
    }
}
