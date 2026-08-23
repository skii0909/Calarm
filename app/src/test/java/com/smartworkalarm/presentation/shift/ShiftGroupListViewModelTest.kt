package com.smartworkalarm.presentation.shift

import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftGroupListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-08-23T03:00:00Z")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `disabled group and non repeating types have inactive status and no next alarm`() = runTest(dispatcher) {
        val disabled = item(group(enabled = false), listOf(type(1, "주간", 127)), events("주간"))
        val nonRepeating = item(group(), listOf(type(1, "주간", 1)), events("주간"))

        assertEquals(ShiftGroupDayStatus.Inactive, disabled.status)
        assertNull(disabled.nextAlarm)
        assertEquals(ShiftGroupDayStatus.Inactive, nonRepeating.status)
        assertNull(nonRepeating.nextAlarm)
    }

    @Test
    fun `vacation and public holiday statuses never expose a next alarm`() = runTest(dispatcher) {
        val vacation = item(group(), listOf(type(1, "주간", 127)), events("주간", "휴가"))
        val holidayClock = Clock.fixed(Instant.parse("2026-08-14T15:00:00Z"), ZoneOffset.UTC)
        val holiday = item(
            group(),
            listOf(type(1, "주간", 127, skipHoliday = true)),
            events("주간"),
            holidayClock,
        )

        assertEquals(ShiftGroupDayStatus.Vacation, vacation.status)
        assertNull(vacation.nextAlarm)
        assertEquals(ShiftGroupDayStatus.PublicHoliday, holiday.status)
        assertNull(holiday.nextAlarm)
    }

    @Test
    fun `none and conflict statuses do not fall back to emergency next alarm`() = runTest(dispatcher) {
        val types = listOf(type(1, "주간", 127), type(2, "야간", 127))
        val none = item(group(emergencyId = 1), types, events("회의"))
        val conflict = item(group(emergencyId = 1), types, events("주간", "야간"))

        assertEquals(ShiftGroupDayStatus.None, none.status)
        assertNull(none.nextAlarm)
        assertEquals(true, conflict.status is ShiftGroupDayStatus.Conflict)
        assertNull(conflict.nextAlarm)
    }

    @Test
    fun `read failure exposes eligible emergency next occurrence`() = runTest(dispatcher) {
        val types = listOf(type(1, "주간", 127), type(2, "야간", 127))

        val item = item(group(emergencyId = 2), types, Result.failure(IllegalStateException("calendar")))

        assertEquals(2L, item.nextAlarm?.shiftTypeId)
        assertEquals(Instant.parse("2026-08-23T22:00:00Z"), item.nextAlarm?.triggerAt)
    }

    @Test
    fun `matched status exposes only matched types calculated next occurrence`() = runTest(dispatcher) {
        val types = listOf(type(1, "주간", 127, hour = 13), type(2, "야간", 127, hour = 20))

        val item = item(group(emergencyId = 2), types, events("주간"))

        assertEquals(1L, item.nextAlarm?.shiftTypeId)
        assertEquals(Instant.parse("2026-08-23T04:00:00Z"), item.nextAlarm?.triggerAt)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.item(
        group: ShiftAlarmGroup,
        types: List<ShiftTypeAlarm>,
        events: Result<List<CalendarEvent>>,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
    ): ShiftGroupListItem {
        val repository = Repository(ShiftAlarmRepository.GroupWithTypes(group, types))
        val source = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant) = events
        }
        val viewModel = ShiftGroupListViewModel(
            repository,
            source,
            EvaluateShiftGroupDayUseCase(ShiftDayEvaluator(), KoreanPublicHolidayProvider()),
            CalculateNextOccurrenceUseCase(),
            clock,
            zone,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.groups.collect {} }
        advanceUntilIdle()
        return viewModel.groups.value.single()
    }

    private fun group(enabled: Boolean = true, emergencyId: Long? = null) =
        ShiftAlarmGroup(10, "교대", enabled, emergencyId)

    private fun type(
        id: Long,
        name: String,
        mask: Int,
        enabled: Boolean = true,
        skipHoliday: Boolean = false,
        hour: Int = 7,
    ) = ShiftTypeAlarm(id, 10, name, listOf(name), hour, 0, mask, enabled, null, false, 5, skipHoliday)

    private fun events(vararg titles: String) = Result.success(
        titles.map { CalendarEvent(it, now, now.plusSeconds(60), false) },
    )

    private class Repository(private val aggregate: ShiftAlarmRepository.GroupWithTypes) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes() = flowOf(listOf(aggregate))
        override suspend fun getGroupWithTypes(groupId: Long) = aggregate
        override suspend fun getShiftType(id: Long) = aggregate.shiftTypes.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes() = aggregate.shiftTypes.filter { it.enabled }
        override suspend fun upsertGroup(group: ShiftAlarmGroup) = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm) = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }
}
