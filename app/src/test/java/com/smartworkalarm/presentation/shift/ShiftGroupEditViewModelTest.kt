package com.smartworkalarm.presentation.shift

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import java.time.Clock
import java.time.Instant
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
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `creating the first shift type persists an inactive draft group`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val viewModel = viewModel(repository)
        viewModel.onGroupNameChanged("  병원 근무  ")
        var createdId = 0L

        viewModel.createDraftAndAddShiftType { createdId = it }
        advanceUntilIdle()

        assertEquals(11L, createdId)
        assertEquals("병원 근무", repository.savedGroups.single().name)
        assertEquals(false, repository.savedGroups.single().enabled)
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
    fun `loading group evaluates todays calendar status`() = runTest(dispatcher) {
        val repository = FakeShiftRepository(groupWithTypes())
        val source = eventSource(
            Result.success(listOf(CalendarEvent("야간", clock.instant(), clock.instant().plusSeconds(60), false))),
        )
        val viewModel = viewModel(repository, source)

        viewModel.loadGroup(10)
        advanceUntilIdle()

        assertEquals(ShiftDayEvaluation.Matched(2, "야간"), viewModel.state.value.todayEvaluation)
    }

    private fun viewModel(
        repository: FakeShiftRepository,
        source: CalendarEventSource = eventSource(Result.success(emptyList())),
    ) = ShiftGroupEditViewModel(repository, source, ShiftDayEvaluator(), clock, zoneId)

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
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }
}
