package com.smartworkalarm.presentation.shift

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.CalendarConnectionState
import com.smartworkalarm.domain.model.ConnectedCalendar
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.CalendarConnectionRepository
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import com.smartworkalarm.domain.usecase.ReconcileShiftAlarmsUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftGroupEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-08-23T03:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `completed group requires a nonblank name and two shift types`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes(types = listOf(type(1, "주간", listOf("주간")))))
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onGroupNameChanged("   ")

        viewModel.saveGroup()

        assertEquals("그룹 이름을 입력하세요", viewModel.state.value.nameError)
        viewModel.onGroupNameChanged("병원 근무")
        viewModel.saveGroup()
        assertEquals("근무 형태를 2개 이상 추가하세요", viewModel.state.value.shiftTypesError)
    }

    @Test
    fun `new and freshly loaded group and shift type states are pristine`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val newEditor = viewModel(repository)
        assertFalse(newEditor.state.value.hasUnsavedChanges)
        assertFalse(newEditor.shiftTypeState.value.hasUnsavedChanges)

        newEditor.loadGroup(10)
        newEditor.loadShiftType(groupId = 10, shiftTypeId = 1)
        advanceUntilIdle()

        assertFalse(newEditor.state.value.hasUnsavedChanges)
        assertFalse(newEditor.shiftTypeState.value.hasUnsavedChanges)
    }

    @Test
    fun `changing and reverting group and shift type fields updates dirty state from baselines`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        viewModel.loadShiftType(groupId = 10, shiftTypeId = 1)
        advanceUntilIdle()

        viewModel.onGroupNameChanged("야간 근무")
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        viewModel.onGroupNameChanged("병원 근무")
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.onShiftTypeNameChanged("이른 주간")
        assertTrue(viewModel.shiftTypeState.value.hasUnsavedChanges)
        viewModel.onShiftTypeNameChanged("주간")
        assertFalse(viewModel.shiftTypeState.value.hasUnsavedChanges)
    }

    @Test
    fun `group and shift type validation failures stay unsaved and dirty`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        viewModel.loadShiftType(groupId = 10, shiftTypeId = 1)
        advanceUntilIdle()

        viewModel.onGroupNameChanged("   ")
        viewModel.saveGroup()
        assertFalse(viewModel.state.value.saved)
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        viewModel.onShiftTypeNameChanged("   ")
        viewModel.saveShiftType()
        assertFalse(viewModel.shiftTypeState.value.saved)
        assertTrue(viewModel.shiftTypeState.value.hasUnsavedChanges)
    }

    @Test
    fun `successful group and shift type saves and deletes clear dirty state`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onGroupNameChanged("수술실 근무")
        viewModel.onEmergencyShiftTypeChanged(1)

        viewModel.saveGroup()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.loadShiftType(groupId = 10, shiftTypeId = 1)
        advanceUntilIdle()
        viewModel.onShiftTypeNameChanged("이른 주간")
        viewModel.saveShiftType()
        advanceUntilIdle()
        assertFalse(viewModel.shiftTypeState.value.hasUnsavedChanges)

        viewModel.onShiftTypeNameChanged("삭제할 주간")
        viewModel.deleteShiftType()
        advanceUntilIdle()
        assertFalse(viewModel.shiftTypeState.value.hasUnsavedChanges)

        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onGroupNameChanged("삭제할 그룹")
        viewModel.deleteGroup()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `aliases are normalized and globally unique within a group`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(
            groupWithTypes(
                types = listOf(
                    type(1, "주간", listOf(" 주간 ")),
                    type(2, "오전", listOf("주간", " 오전 ")),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onEmergencyShiftTypeChanged(1)

        viewModel.saveGroup()

        assertEquals("캘린더 제목 별칭은 그룹 안에서 중복될 수 없습니다", viewModel.state.value.aliasesError)
    }

    @Test
    fun `group requires emergency type from its own shift types`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onEmergencyShiftTypeChanged(99)

        viewModel.saveGroup()

        assertEquals("비상 기본 알람을 선택하세요", viewModel.state.value.emergencyError)
    }

    @Test
    fun `valid group saves trimmed name and selected emergency type`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadGroup(10)
        advanceUntilIdle()
        viewModel.onGroupNameChanged("  병원 근무  ")
        viewModel.onEmergencyShiftTypeChanged(2)

        viewModel.saveGroup()
        advanceUntilIdle()

        assertEquals("병원 근무", repository.savedGroups.last().name)
        assertEquals(2L, repository.savedGroups.last().emergencyShiftTypeId)
        assertEquals(false, repository.savedGroups.last().isDraft)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `creating the first shift type persists a hidden draft without losing enabled choice`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.onGroupNameChanged("  병원 근무  ")
        var createdId = 0L

        viewModel.createDraftAndAddShiftType { createdId = it }
        advanceUntilIdle()

        assertEquals(11L, createdId)
        assertEquals("병원 근무", repository.savedGroups.single().name)
        assertEquals(true, repository.savedGroups.single().enabled)
        assertEquals(true, repository.savedGroups.single().isDraft)
    }

    @Test
    fun `discarding changes deletes an abandoned persisted draft`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.onGroupNameChanged("병원 근무")
        viewModel.createDraftAndAddShiftType {}
        advanceUntilIdle()

        viewModel.discardGroupChanges()
        advanceUntilIdle()

        assertEquals(listOf(11L), repository.deletedGroups)
    }

    @Test
    fun `repeated draft taps create one group and navigate once`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.onGroupNameChanged("병원 근무")
        val createdIds = mutableListOf<Long>()

        viewModel.createDraftAndAddShiftType(createdIds::add)
        viewModel.createDraftAndAddShiftType(createdIds::add)
        advanceUntilIdle()

        assertEquals(1, repository.savedGroups.size)
        assertEquals(listOf(11L), createdIds)
    }

    @Test
    fun `shift type route rejects a type belonging to another group`() = runTest(dispatcher) {
        val foreign = type(1, "주간", listOf("주간")).copy(groupId = 99)
        val repository = FakeShiftRepository(groupWithTypes(types = listOf(foreign, type(2, "야간", listOf("야간")))))
        val viewModel = viewModel(repository)

        viewModel.loadShiftType(groupId = 10, shiftTypeId = foreign.id)
        advanceUntilIdle()

        assertEquals("이 그룹에 속하지 않은 근무 형태입니다", viewModel.shiftTypeState.value.loadError)
        assertEquals(0L, viewModel.shiftTypeState.value.id)
    }

    @Test
    fun `shift type save trims aliases removes blanks and rejects another types alias`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadShiftType(groupId = 10, shiftTypeId = 0)
        advanceUntilIdle()
        viewModel.onShiftTypeNameChanged("새벽")
        viewModel.onExactTitlesChanged(" 새벽 , , 주간 ")
        viewModel.toggleShiftDay(com.smartworkalarm.domain.model.RepeatDay.MONDAY)

        viewModel.saveShiftType()

        assertEquals("다른 근무 형태에서 이미 사용하는 제목입니다: 주간", viewModel.shiftTypeState.value.aliasesError)
        viewModel.onExactTitlesChanged(" 새벽, 새벽 , 새벽근무 ")
        viewModel.saveShiftType()
        advanceUntilIdle()

        assertEquals(listOf("새벽", "새벽근무"), repository.savedTypes.single().exactTitles)
    }

    @Test
    fun `saving an existing shift type preserves disabled snooze after an unrelated edit`() = runTest(dispatcher) {
        val disabledSnoozeType = type(1, "주간", listOf("주간")).copy(snoozeEnabled = false)
        val repository = FakeShiftRepository(
            groupWithTypes(types = listOf(disabledSnoozeType, type(2, "야간", listOf("야간")))),
        )
        val viewModel = viewModel(repository)

        viewModel.loadShiftType(groupId = 10, shiftTypeId = disabledSnoozeType.id)
        advanceUntilIdle()
        viewModel.onShiftTypeNameChanged("이른 주간")
        viewModel.saveShiftType()
        advanceUntilIdle()

        assertEquals(false, repository.savedTypes.single().snoozeEnabled)
    }

    @Test
    fun `shift type restores disabled snooze and minutes`() = runTest(dispatcher) {
        val restored = type(1, "주간", listOf("주간")).copy(snoozeEnabled = false, snoozeMinutes = 21)
        val viewModel = viewModel(
            FakeShiftRepository(groupWithTypes(types = listOf(restored, type(2, "야간", listOf("야간"))))),
        )

        viewModel.loadShiftType(groupId = 10, shiftTypeId = restored.id)
        advanceUntilIdle()

        assertFalse(viewModel.shiftTypeState.value.snoozeEnabled)
        assertEquals(21, viewModel.shiftTypeState.value.snoozeMinutes)
    }

    @Test
    fun `disabling shift snooze preserves selected minutes on save`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.loadShiftType(groupId = 10, shiftTypeId = 0)
        advanceUntilIdle()
        viewModel.onShiftTypeNameChanged("새벽")
        viewModel.onExactTitlesChanged("새벽")
        viewModel.toggleShiftDay(com.smartworkalarm.domain.model.RepeatDay.MONDAY)
        viewModel.onShiftSnoozeChanged(19)
        viewModel.onShiftSnoozeEnabledChanged(false)

        viewModel.saveShiftType()
        advanceUntilIdle()

        assertFalse(repository.savedTypes.single().snoozeEnabled)
        assertEquals(19, repository.savedTypes.single().snoozeMinutes)
    }

    @Test
    fun `loading group evaluates todays calendar status`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val source = eventSource(
            Result.success(listOf(CalendarEvent("야간", clock.instant(), clock.instant().plusSeconds(60), false))),
        )
        val viewModel = viewModel(repository, source)

        viewModel.loadGroup(10)
        advanceUntilIdle()

        assertEquals(ShiftGroupDayStatus.Matched(type(2, "야간", listOf("야간"))), viewModel.state.value.todayStatus)
    }

    @Test
    fun `loading group exposes tomorrow evaluation connection and last local query status`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val today = LocalDate.of(2026, 8, 23)
        val tomorrow = today.plusDays(1)
        val source = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) =
                when (startInclusive.atZone(zoneId).toLocalDate()) {
                    today -> Result.success(listOf(CalendarEvent("주간", startInclusive, endExclusive, true)))
                    tomorrow -> Result.success(listOf(CalendarEvent("야간", startInclusive, endExclusive, true)))
                    else -> Result.success(emptyList())
                }
        }
        val connection = CalendarConnectionState(true, listOf(ConnectedCalendar(7, "근무표")))
        val viewModel = viewModel(repository, source, connection)

        viewModel.loadGroup(10)
        advanceUntilIdle()

        assertEquals(2L, (viewModel.state.value.tomorrowStatus as ShiftGroupDayStatus.Matched).shiftType.id)
        assertEquals(connection, viewModel.state.value.calendarConnection)
        assertEquals(CalendarQueryStatus(clock.instant(), succeeded = true), viewModel.state.value.lastCalendarQuery)
    }

    private fun viewModel(
        repository: FakeShiftRepository,
        source: CalendarEventSource = eventSource(Result.success(emptyList())),
        connection: CalendarConnectionState = CalendarConnectionState(false, emptyList()),
    ) = ShiftGroupEditViewModel(
        repository,
        ReconcileShiftAlarmsUseCase(repository, NoOpShiftScheduler, CalculateNextOccurrenceUseCase(), clock, zoneId),
        source,
        EvaluateShiftGroupDayUseCase(ShiftDayEvaluator(), KoreanPublicHolidayProvider()),
        clock,
        zoneId,
        object : CalendarConnectionRepository {
            override fun getState() = connection
        },
    )

    private fun eventSource(result: Result<List<CalendarEvent>>) = object : CalendarEventSource {
        override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) = result
    }

    private fun groupWithTypes(
        types: List<ShiftTypeAlarm> = listOf(
            type(1, "주간", listOf("주간")),
            type(2, "야간", listOf("야간")),
        ),
    ) = ShiftAlarmRepository.GroupWithTypes(
        ShiftAlarmGroup(10, "병원 근무", true, null),
        types,
    )

    private fun type(id: Long, name: String, aliases: List<String>) = ShiftTypeAlarm(
        id = id,
        groupId = 10,
        name = name,
        exactTitles = aliases,
        hour = 7,
        minute = 0,
        repeatDaysMask = 127,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeMinutes = 5,
        skipOnPublicHoliday = false,
    )

    private class FakeShiftRepository(initial: ShiftAlarmRepository.GroupWithTypes) : ShiftAlarmRepository {
        private val groups = MutableStateFlow(listOf(initial))
        val savedGroups = mutableListOf<ShiftAlarmGroup>()
        val savedTypes = mutableListOf<ShiftTypeAlarm>()
        val deletedGroups = mutableListOf<Long>()

        override fun observeGroupsWithTypes() = groups
        override suspend fun getGroupWithTypes(groupId: Long) = groups.value.firstOrNull { it.group.id == groupId }
        override suspend fun getShiftType(id: Long) = groups.value.flatMap { it.shiftTypes }.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes() = groups.value.flatMap { it.shiftTypes }.filter { it.enabled }
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long {
            savedGroups += group
            return if (group.id == 0L) 11L else group.id
        }
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long {
            savedTypes += shiftType
            return if (shiftType.id == 0L) 3L else shiftType.id
        }
        override suspend fun deleteGroup(groupId: Long) { deletedGroups += groupId }
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }

    private object NoOpShiftScheduler : ShiftAlarmScheduler {
        override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
        override fun scheduleShiftSnooze(
            shiftTypeId: Long,
            triggerAt: Instant,
            scheduledOccurrenceAt: Instant,
        ) = ScheduleResult.Scheduled
        override fun cancelShift(shiftTypeId: Long) = Unit
    }
}
