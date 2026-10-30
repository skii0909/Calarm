package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCrudUseCasesTest {
    @Test
    fun `snooze cleanup failure cannot roll database back after new reservation succeeds`() = runTest {
        val repository = FakeRepository(existing = alarm())
        val scheduler = FakeScheduler(onCancelSnooze = { error("cleanup failed") })
        val result = SaveAlarmUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)
            .execute(alarm().copy(hour = 8))
        assertTrue(result.isSuccess)
        assertEquals(8, repository.existing?.hour)
    }
    @Test
    fun `failed edit persistence must preserve existing operating system reservation`() = runTest {
        val scheduler = FakeScheduler()
        val repository = FakeRepository(existing = alarm(), failUpsert = true)
        assertTrue(SaveAlarmUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)
            .execute(alarm()).isFailure)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun `missing exact permission cannot report successful enabled save`() = runTest {
        val repository = FakeRepository()
        val scheduler = FakeScheduler(scheduleResult = ScheduleResult.ExactAlarmPermissionRequired)
        assertTrue(SaveAlarmUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)
            .execute(alarm(id = 0)).isFailure)
        assertEquals(false, repository.existing?.enabled)
    }

    @Test
    fun `failed toggle restores disabled state`() = runTest {
        val repository = FakeRepository(existing = alarm().copy(enabled = false))
        val scheduler = FakeScheduler(scheduleResult = ScheduleResult.ExactAlarmPermissionRequired)
        runCatching { SetAlarmEnabledUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone).execute(1, true) }
        assertEquals(false, repository.existing?.enabled)
    }

    private val instant = Instant.parse("2026-08-19T20:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(instant, zone)

    @Test
    fun `saving enabled alarm persists then schedules returned id`() = runTest {
        val repository = FakeRepository(savedId = 42)
        val scheduler = FakeScheduler()
        val useCase = SaveAlarmUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)

        val result = useCase.execute(alarm(id = 0))

        assertEquals(42L, result.getOrThrow())
        assertEquals(
            listOf(42L to Instant.parse("2026-08-19T22:00:00Z")),
            scheduler.scheduled,
        )
    }

    @Test
    fun `blank name is rejected`() = runTest {
        val repository = FakeRepository()
        val useCase = SaveAlarmUseCase(repository, FakeScheduler(), CalculateNextOccurrenceUseCase(), clock, zone)

        assertTrue(useCase.execute(alarm(name = " ")).isFailure)
        assertEquals(0, repository.upsertCount)
    }

    @Test
    fun `saving alarm without repeat days schedules one nearest occurrence`() = runTest {
        val repository = FakeRepository(savedId = 42)
        val scheduler = FakeScheduler()
        val useCase = SaveAlarmUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)

        assertEquals(42L, useCase.execute(alarm(id = 0, mask = 0)).getOrThrow())
        assertEquals(
            listOf(42L to Instant.parse("2026-08-19T22:00:00Z")),
            scheduler.scheduled,
        )
    }

    @Test
    fun `disabling alarm persists state and cancels schedule`() = runTest {
        val repository = FakeRepository(existing = alarm())
        val scheduler = FakeScheduler()

        SetAlarmEnabledUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)
            .execute(1, false)

        assertEquals(false, repository.existing?.enabled)
        assertEquals(listOf(1L), scheduler.cancelled)
    }

    @Test
    fun `enabling alarm schedules exactly one next occurrence`() = runTest {
        val repository = FakeRepository(existing = alarm().copy(enabled = false))
        val scheduler = FakeScheduler()

        SetAlarmEnabledUseCase(repository, scheduler, CalculateNextOccurrenceUseCase(), clock, zone)
            .execute(1, true)

        assertEquals(true, repository.existing?.enabled)
        assertEquals(
            listOf(1L to Instant.parse("2026-08-19T22:00:00Z")),
            scheduler.scheduled,
        )
    }

    @Test
    fun `deleting alarm cancels before deleting record`() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeRepository(onDelete = { events += "delete" })
        val scheduler = FakeScheduler(onCancel = { events += "cancel" })

        DeleteAlarmUseCase(repository, scheduler).execute(1)

        assertEquals(listOf("cancel", "delete"), events)
    }

    private fun alarm(
        id: Long = 1,
        name: String = "출근",
        mask: Int = 31,
    ) = Alarm(id, name, 7, 0, mask, true, true)

    private class FakeRepository(
        private val savedId: Long = 1,
        var existing: Alarm? = null,
        private val onDelete: () -> Unit = {},
        private val failUpsert: Boolean = false,
    ) : AlarmRepository {
        var upsertCount = 0
        override fun observeAll(): Flow<List<Alarm>> = flowOf(listOfNotNull(existing))
        override suspend fun getById(id: Long): Alarm? = existing
        override suspend fun getEnabled(): List<Alarm> = listOfNotNull(existing?.takeIf { it.enabled })
        override suspend fun upsert(alarm: Alarm): Long { if (failUpsert) error("disk failure"); upsertCount++; existing = alarm.copy(id = savedId); return savedId }
        override suspend fun delete(id: Long) { onDelete(); existing = null }
        override suspend fun setEnabled(id: Long, enabled: Boolean) { existing = existing?.copy(enabled = enabled) }
    }

    private class FakeScheduler(
        private val onCancel: () -> Unit = {},
        private val scheduleResult: ScheduleResult = ScheduleResult.Scheduled,
        private val onCancelSnooze: () -> Unit = {},
    ) : AlarmScheduler {
        val scheduled = mutableListOf<Pair<Long, Instant>>()
        val cancelled = mutableListOf<Long>()
        override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult {
            scheduled += alarmId to triggerAt
            return scheduleResult
        }
        override fun cancel(alarmId: Long) { cancelled += alarmId; onCancel() }
        override fun cancelSnooze(alarmId: Long) { onCancelSnooze() }
        override fun scheduleSnooze(alarmId: Long, triggerAt: Instant): ScheduleResult = ScheduleResult.Scheduled
    }
}
