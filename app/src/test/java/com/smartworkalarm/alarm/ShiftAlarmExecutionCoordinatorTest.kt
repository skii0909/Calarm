package com.smartworkalarm.alarm

import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.repository.ShiftAlarmScheduler
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftAlarmExecutionCoordinatorTest {
    private val now = Instant.parse("2026-08-19T20:00:00Z")
    private val holidayNow = Instant.parse("2026-08-14T20:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = shiftType(1, "주간")
    private val night = shiftType(2, "야간")

    @Test
    fun `matched night alarm rings and day alarm skips`() = runTest {
        val coordinator = coordinator(ShiftDayEvaluation.Matched(night.id, "야간"))

        assertEquals(ExecutionOutcome.Ringing, coordinator.execute(night.id, false, now))
        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(day.id, false, now))
    }

    @Test
    fun `read failure rings only emergency shift alarm`() = runTest {
        val coordinator = coordinator(ShiftDayEvaluation.ReadFailure, emergencyId = day.id)

        assertEquals(ExecutionOutcome.RingingWithCalendarWarning, coordinator.execute(day.id, false, now))
        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(night.id, false, now))
    }

    @Test
    fun `conflicting shift titles skip every shift alarm`() = runTest {
        val coordinator = coordinator(
            ShiftDayEvaluation.Conflict(
                listOf(
                    ShiftDayEvaluation.Matched(day.id, "주간"),
                    ShiftDayEvaluation.Matched(night.id, "야간"),
                ),
            ),
        )

        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(day.id, false, now))
        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(night.id, false, now))
    }

    @Test
    fun `vacation and public holiday skip before matched shift`() = runTest {
        assertEquals(
            ExecutionOutcome.SkippedForVacation,
            coordinator(ShiftDayEvaluation.Matched(night.id, "야간"), vacation = true)
                .execute(night.id, false, now),
        )
        assertEquals(
            ExecutionOutcome.SkippedForVacation,
            coordinator(ShiftDayEvaluation.Matched(night.id, "야간"), publicHoliday = true)
                .execute(night.id, false, holidayNow),
        )
    }

    @Test
    fun `next occurrence is scheduled before shift evaluation`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            ShiftDayEvaluation.None,
            onSchedule = { events += "schedule" },
            onEvaluate = { events += "evaluate" },
        )

        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(night.id, false, now))
        assertEquals(listOf("schedule", "evaluate"), events)
    }

    @Test
    fun `production calendar policy skips a vacation event before a matched shift`() = runTest {
        val coordinator = productionCoordinator(Result.success(listOf(event("여름휴가"))))

        assertEquals(ExecutionOutcome.SkippedForVacation, coordinator.execute(night.id, false, now))
    }

    @Test
    fun `production calendar policy skips an opted in calendar holiday before a matched shift`() = runTest {
        val coordinator = productionCoordinator(Result.success(listOf(event("대한민국 임시공휴일"))))

        assertEquals(ExecutionOutcome.SkippedForVacation, coordinator.execute(night.id, false, now))
    }

    @Test
    fun `production calendar query happens after the next shift occurrence is scheduled`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = productionCoordinator(
            Result.success(listOf(event("야간"))),
            onSchedule = { events += "schedule" },
            onQuery = { events += "query" },
        )

        assertEquals(ExecutionOutcome.Ringing, coordinator.execute(night.id, false, now))
        assertEquals(listOf("schedule", "query"), events)
    }

    @Test
    fun `production calendar read failure rings only the emergency shift type`() = runTest {
        val coordinator = productionCoordinator(Result.failure(IllegalStateException("no readable calendar")))

        assertEquals(ExecutionOutcome.RingingWithCalendarWarning, coordinator.execute(day.id, false, now))
        assertEquals(ExecutionOutcome.SkippedForShift, coordinator.execute(night.id, false, now))
    }

    private fun coordinator(
        result: ShiftDayEvaluation,
        emergencyId: Long? = null,
        vacation: Boolean = false,
        publicHoliday: Boolean = false,
        onSchedule: () -> Unit = {},
        onEvaluate: () -> Unit = {},
    ) = ShiftAlarmExecutionCoordinator(
        repository = Repository(ShiftAlarmGroup(10, "교대", true, emergencyId), listOf(day, night)),
        scheduler = Scheduler(onSchedule),
        calculator = CalculateNextOccurrenceUseCase(),
        decisionProvider = { _, _, _ ->
            onEvaluate()
            if (vacation || publicHoliday) ShiftExecutionDecision.SkipForVacation
            else ShiftExecutionDecision.Evaluated(result)
        },
        ringer = object : AlarmRinger {
            override fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean, snoozeMinutes: Int) = Unit
        },
        zoneId = zone,
    )

    private fun productionCoordinator(
        eventsResult: Result<List<CalendarEvent>>,
        onSchedule: () -> Unit = {},
        onQuery: () -> Unit = {},
    ) = ShiftAlarmExecutionCoordinator(
        repository = Repository(ShiftAlarmGroup(10, "교대", true, emergencyShiftTypeId = day.id), listOf(day, night)),
        scheduler = Scheduler(onSchedule),
        calculator = CalculateNextOccurrenceUseCase(),
        events = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> {
                onQuery()
                return eventsResult
            }
        },
        evaluator = ShiftDayEvaluator(),
        holidays = KoreanPublicHolidayProvider(),
        ringer = object : AlarmRinger {
            override fun start(alarmId: Long, alarmName: String, ringtoneUri: String?, gradualVolume: Boolean, snoozeMinutes: Int) = Unit
        },
        zoneId = zone,
    )

    private fun event(title: String) = CalendarEvent(
        title = title,
        start = now,
        end = now.plusSeconds(60),
        allDay = false,
    )

    private fun shiftType(id: Long, name: String) = ShiftTypeAlarm(
        id = id,
        groupId = 10,
        name = name,
        exactTitles = listOf(name),
        hour = 7,
        minute = 0,
        repeatDaysMask = 127,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeMinutes = 5,
        skipOnPublicHoliday = true,
    )

    private class Repository(
        private val group: ShiftAlarmGroup,
        private val types: List<ShiftTypeAlarm>,
    ) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> =
            flowOf(listOf(ShiftAlarmRepository.GroupWithTypes(group, types)))

        override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? =
            ShiftAlarmRepository.GroupWithTypes(group, types).takeIf { groupId == group.id }

        override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = types.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> = types
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }

    private class Scheduler(private val onSchedule: () -> Unit) : ShiftAlarmScheduler {
        override fun scheduleShift(shiftTypeId: Long, triggerAt: Instant): ScheduleResult {
            onSchedule()
            return ScheduleResult.Scheduled
        }

        override fun scheduleShiftSnooze(shiftTypeId: Long, triggerAt: Instant): ScheduleResult = ScheduleResult.Scheduled
        override fun cancelShift(shiftTypeId: Long) = Unit
    }
}
