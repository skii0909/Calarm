package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcileShiftAlarmsUseCaseTest {
    private val now = Instant.parse("2026-08-23T03:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `creating enabled type schedules its first occurrence`() = runTest {
        val repository = Repository(group(), emptyList())
        val scheduler = Scheduler()
        val useCase = useCase(repository, scheduler)

        val id = useCase.saveShiftType(type(id = 0, hour = 9))

        assertEquals(3L, id)
        assertEquals(emptyList<Long>(), scheduler.cancelled)
        assertEquals(listOf(3L), scheduler.scheduled.map { it.first })
    }

    @Test
    fun `editing time cancels stale pending intent and schedules new occurrence`() = runTest {
        val repository = Repository(group(), listOf(type(id = 1, hour = 7)))
        val scheduler = Scheduler()
        val useCase = useCase(repository, scheduler)

        useCase.saveShiftType(type(id = 1, hour = 9))

        assertEquals(listOf(1L), scheduler.cancelled)
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), scheduler.scheduled.single().second)
    }

    @Test
    fun `disabling type cancels without rescheduling`() = runTest {
        val repository = Repository(group(), listOf(type(id = 1)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).saveShiftType(type(id = 1, enabled = false))

        assertEquals(listOf(1L), scheduler.cancelled)
        assertEquals(emptyList<Pair<Long, Instant>>(), scheduler.scheduled)
    }

    @Test
    fun `disabling group cancels every group type`() = runTest {
        val repository = Repository(group(), listOf(type(1), type(2)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).saveGroup(group(enabled = false))

        assertEquals(listOf(1L, 2L), scheduler.cancelled)
        assertEquals(emptyList<Pair<Long, Instant>>(), scheduler.scheduled)
    }

    @Test
    fun `enabling group schedules every enabled type after cancelling stale intents`() = runTest {
        val repository = Repository(group(enabled = false), listOf(type(1), type(2, enabled = false)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).saveGroup(group(enabled = true))

        assertEquals(listOf(1L, 2L), scheduler.cancelled)
        assertEquals(listOf(1L), scheduler.scheduled.map { it.first })
    }

    @Test
    fun `deleting type disables incomplete group clears emergency and cancels remaining schedules`() = runTest {
        val repository = Repository(group(emergencyId = 1), listOf(type(1), type(2)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).deleteShiftType(groupId = 10, shiftTypeId = 1)

        assertEquals(listOf(1L, 2L), scheduler.cancelled)
        assertFalse(repository.group.enabled)
        assertNull(repository.group.emergencyShiftTypeId)
        assertEquals(listOf(2L), repository.types.map { it.id })
    }

    @Test
    fun `deleting group cancels every pending shift intent`() = runTest {
        val repository = Repository(group(), listOf(type(1), type(2)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).deleteGroup(10)

        assertEquals(listOf(1L, 2L), scheduler.cancelled)
        assertEquals(null, repository.getGroupWithTypes(10))
    }

    private fun useCase(repository: Repository, scheduler: Scheduler) = ReconcileShiftAlarmsUseCase(
        repository,
        scheduler,
        CalculateNextOccurrenceUseCase(),
        Clock.fixed(now, ZoneOffset.UTC),
        zone,
    )

    private fun group(enabled: Boolean = true, emergencyId: Long? = null) =
        ShiftAlarmGroup(10, "교대", enabled, emergencyId)

    private fun type(id: Long, hour: Int = 7, enabled: Boolean = true) = ShiftTypeAlarm(
        id, 10, if (id == 2L) "야간" else "주간", listOf(if (id == 2L) "야간" else "주간"),
        hour, 0, 127, enabled, null, false, 5, false,
    )

    private class Scheduler : ShiftAlarmScheduler {
        val cancelled = mutableListOf<Long>()
        val scheduled = mutableListOf<Pair<Long, Instant>>()
        override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult {
            scheduled += shiftTypeId to triggerAt
            return ScheduleResult.Scheduled
        }
        override fun scheduleShiftSnooze(shiftTypeId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
        override fun cancelShift(shiftTypeId: Long) { cancelled += shiftTypeId }
    }

    private class Repository(initialGroup: ShiftAlarmGroup, initialTypes: List<ShiftTypeAlarm>) : ShiftAlarmRepository {
        var group = initialGroup
        var types = initialTypes.toMutableList()
        private var deleted = false
        private val flow = MutableStateFlow(listOf(ShiftAlarmRepository.GroupWithTypes(group, types)))

        override fun observeGroupsWithTypes() = flow
        override suspend fun getGroupWithTypes(groupId: Long) =
            if (!deleted && group.id == groupId) ShiftAlarmRepository.GroupWithTypes(group, types.toList()) else null
        override suspend fun getShiftType(id: Long) = types.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes() = types.filter { it.enabled && group.enabled }
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long {
            this.group = group
            emit()
            return group.id
        }
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long {
            val id = if (shiftType.id == 0L) 3L else shiftType.id
            types.removeAll { it.id == id }
            types += shiftType.copy(id = id)
            emit()
            return id
        }
        override suspend fun deleteGroup(groupId: Long) { deleted = true; types.clear(); flow.value = emptyList() }
        override suspend fun deleteShiftType(shiftTypeId: Long) { types.removeAll { it.id == shiftTypeId }; emit() }
        private fun emit() { flow.value = listOf(ShiftAlarmRepository.GroupWithTypes(group, types.toList())) }
    }
}
