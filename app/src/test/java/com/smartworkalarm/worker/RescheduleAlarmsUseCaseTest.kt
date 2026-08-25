package com.smartworkalarm.worker

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.AlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RescheduleAlarmsUseCaseTest {
    @Test
    fun `only enabled repository alarms receive schedules`() = runTest {
        val enabled = Alarm(1, "출근", 7, 0, 31, true, true)
        val disabled = enabled.copy(id = 2, enabled = false)
        val scheduler = Scheduler()
        val useCase = RescheduleAlarmsUseCase(
            Repository(listOf(enabled, disabled)),
            scheduler,
            CalculateNextOccurrenceUseCase(),
            ZoneId.of("Asia/Seoul"),
            ShiftRepository(emptyList()),
            ShiftScheduler(),
        )

        useCase.execute(Instant.parse("2026-08-19T20:00:00Z"))

        assertEquals(listOf(1L), scheduler.ids)
    }

    @Test
    fun `enabled shift type receives its own schedule`() = runTest {
        val shift = ShiftTypeAlarm(8, 3, "야간", listOf("야간"), 19, 0, 31, true, null, false, true, 5, false)
        val scheduler = ShiftScheduler()
        val useCase = RescheduleAlarmsUseCase(
            Repository(emptyList()),
            Scheduler(),
            CalculateNextOccurrenceUseCase(),
            ZoneId.of("Asia/Seoul"),
            ShiftRepository(listOf(shift)),
            scheduler,
        )

        useCase.execute(Instant.parse("2026-08-19T20:00:00Z"))

        assertEquals(listOf(8L), scheduler.ids)
    }

    private class Repository(private val alarms: List<Alarm>) : AlarmRepository {
        override fun observeAll(): Flow<List<Alarm>> = emptyFlow()
        override suspend fun getById(id: Long): Alarm? = alarms.firstOrNull { it.id == id }
        override suspend fun getEnabled(): List<Alarm> = alarms.filter { it.enabled }
        override suspend fun upsert(alarm: Alarm): Long = alarm.id
        override suspend fun delete(id: Long) = Unit
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
    }

    private class Scheduler : AlarmScheduler {
        val ids = mutableListOf<Long>()
        override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleResult {
            ids += alarmId
            return ScheduleResult.Scheduled
        }
        override fun cancel(alarmId: Long) = Unit
        override fun scheduleSnooze(alarmId: Long, triggerAt: Instant) = ScheduleResult.Scheduled
    }

    private class ShiftRepository(private val shiftTypes: List<ShiftTypeAlarm>) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> = flowOf(emptyList())
        override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? = null
        override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = shiftTypes.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> = shiftTypes.filter { it.enabled }
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }

    private class ShiftScheduler : ShiftAlarmScheduler {
        val ids = mutableListOf<Long>()
        override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult {
            ids += shiftTypeId
            return ScheduleResult.Scheduled
        }
        override fun scheduleShiftSnooze(
            shiftTypeId: Long,
            triggerAt: Instant,
            scheduledOccurrenceAt: Instant,
        ): ScheduleResult = ScheduleResult.Scheduled
        override fun cancelShift(shiftTypeId: Long) = Unit
    }
}
