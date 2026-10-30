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
import org.junit.Assert.assertTrue
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
        assertEquals(
            listOf(3L to Instant.parse("2026-08-24T00:00:00Z")),
            scheduler.scheduled,
        )
    }

    @Test
    fun `saving a type inside a draft group never schedules an alarm`() = runTest {
        val repository = Repository(group(isDraft = true), emptyList())
        val scheduler = Scheduler()

        useCase(repository, scheduler).saveShiftType(type(id = 0, hour = 9))

        assertEquals(emptyList<Pair<Long, Instant>>(), scheduler.scheduled)
    }

    @Test
    fun `editing time replaces occurrence without cancelling the existing reservation first`() = runTest {
        val repository = Repository(group(), listOf(type(id = 1, hour = 7)))
        val scheduler = Scheduler()
        val useCase = useCase(repository, scheduler)

        useCase.saveShiftType(type(id = 1, hour = 9))

        assertEquals(emptyList<Long>(), scheduler.cancelled)
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
    fun `enabling group schedules enabled types and cancels only disabled types`() = runTest {
        val repository = Repository(group(enabled = false), listOf(type(1), type(2, enabled = false)))
        val scheduler = Scheduler()

        useCase(repository, scheduler).saveGroup(group(enabled = true))

        assertEquals(listOf(2L), scheduler.cancelled)
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

    @Test fun `database save failure leaves existing shift reservation untouched`() = runTest {
        val original = type(1)
        val repository = Repository(group(), listOf(original)).apply { failTypeSave = true }
        val scheduler = Scheduler()
        assertTrue(runCatching { useCase(repository, scheduler).saveShiftType(original.copy(hour = 9)) }.isFailure)
        assertEquals(emptyList<Long>(), scheduler.cancelled)
        assertEquals(emptyList<Pair<Long, Instant>>(), scheduler.scheduled)
        assertEquals(original, repository.getShiftType(1))
    }

    @Test fun `permission denied edit restores existing type and reports failure`() = runTest {
        val original = type(1)
        val repository = Repository(group(), listOf(original))
        val scheduler = Scheduler().apply { result = ScheduleResult.ExactAlarmPermissionRequired }
        assertTrue(runCatching { useCase(repository, scheduler).saveShiftType(original.copy(hour = 9)) }.isFailure)
        assertEquals(original, repository.getShiftType(1))
        assertEquals(emptyList<Long>(), scheduler.cancelled)
    }

    @Test fun `failed new shift reservation does not leave an enabled duplicate on retry`() = runTest {
        val repository = Repository(group(), emptyList())
        val scheduler = Scheduler().apply { result = ScheduleResult.ExactAlarmPermissionRequired }
        assertTrue(runCatching { useCase(repository, scheduler).saveShiftType(type(0)) }.isFailure)
        assertTrue(repository.types.isEmpty())
    }

    @Test fun `group scheduling attempts every type after one failure and restores previous group`() = runTest {
        val original = group(enabled = false)
        val repository = Repository(original, listOf(type(1), type(2)))
        val scheduler = Scheduler().apply { failingId = 1 }
        assertTrue(runCatching { useCase(repository, scheduler).saveGroup(original.copy(enabled = true)) }.isFailure)
        assertEquals(listOf(1L, 2L), scheduler.attempted)
        assertEquals(original, repository.group)
        assertEquals(listOf(1L, 2L), scheduler.cancelled)
    }

    @Test fun `saving draft group cancels types even if enabled flag is true`() = runTest {
        val repository = Repository(group(), listOf(type(1), type(2)))
        val scheduler = Scheduler()
        useCase(repository, scheduler).saveGroup(group(isDraft = true))
        assertEquals(listOf(1L, 2L), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun `snooze cleanup failure does not roll back a successfully replaced shift`() = runTest {
        val repository = Repository(group(), listOf(type(1)))
        val scheduler = Scheduler().apply { failSnoozeCleanup = true }
        useCase(repository, scheduler).saveShiftType(type(1, hour = 9))
        assertEquals(9, repository.getShiftType(1)?.hour)
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), scheduler.scheduled.single().second)
    }

    private fun useCase(repository: Repository, scheduler: Scheduler) = ReconcileShiftAlarmsUseCase(
        repository,
        scheduler,
        CalculateNextOccurrenceUseCase(),
        Clock.fixed(now, ZoneOffset.UTC),
        zone,
    )

    private fun group(enabled: Boolean = true, emergencyId: Long? = null, isDraft: Boolean = false) =
        ShiftAlarmGroup(10, "교대", enabled, emergencyId, isDraft)

    private fun type(id: Long, hour: Int = 7, enabled: Boolean = true) = ShiftTypeAlarm(
        id, 10, if (id == 2L) "야간" else "주간", listOf(if (id == 2L) "야간" else "주간"),
        hour, 0, 127, enabled, null, false, true, 5, false,
    )

    private class Scheduler : ShiftAlarmScheduler {
        var result: ScheduleResult = ScheduleResult.Scheduled
        var failSnoozeCleanup = false
        var failingId: Long? = null
        val attempted = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        val scheduled = mutableListOf<Pair<Long, Instant>>()
        override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult {
            attempted += shiftTypeId
            if (shiftTypeId == failingId) error("scheduler unavailable")
            scheduled += shiftTypeId to triggerAt
            return result
        }
        override fun scheduleShiftSnooze(
            shiftTypeId: Long,
            triggerAt: Instant,
            scheduledOccurrenceAt: Instant,
        ) = ScheduleResult.Scheduled
        override fun cancelShift(shiftTypeId: Long) { cancelled += shiftTypeId }
        override fun cancelShiftSnooze(shiftTypeId: Long) { check(!failSnoozeCleanup) { "cleanup unavailable" } }
    }

    private class Repository(initialGroup: ShiftAlarmGroup, initialTypes: List<ShiftTypeAlarm>) : ShiftAlarmRepository {
        var failTypeSave = false
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
            check(!failTypeSave) { "database unavailable" }
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
