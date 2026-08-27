package com.smartworkalarm.prealarm

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.EvaluateShiftGroupDayUseCase
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreAlarmEligibilityTest {
    private val key = AlarmOccurrenceKey(
        AlarmKind.SHIFT,
        73L,
        Instant.parse("2026-08-26T00:30:00Z"),
    )
    private val shiftType = ShiftTypeAlarm(
        id = key.id,
        groupId = 10L,
        name = "주간",
        exactTitles = listOf("주간"),
        hour = 7,
        minute = 0,
        repeatDaysMask = 127,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeMinutes = 5,
        skipOnPublicHoliday = false,
    )
    private val nextShiftType = shiftType.copy(
        id = 74L,
        name = "야간2",
        exactTitles = listOf("야간2"),
        hour = 21,
        minute = 5,
    )

    @Test
    fun `shift pre-alert is shown only for the shift selected by that date calendar`() = runTest {
        val occurrence = Instant.parse("2026-08-27T12:00:00Z")
        val events = listOf(
            CalendarEvent(
                title = "야간2",
                start = Instant.parse("2026-08-27T00:00:00Z"),
                end = Instant.parse("2026-08-28T00:00:00Z"),
                allDay = true,
            ),
        )
        val eligibility = eligibility(
            group(enabled = true),
            shiftTypes = listOf(shiftType, nextShiftType),
            events = Result.success(events),
        )

        assertNull(
            eligibility.alarmNameIfEligible(
                AlarmOccurrenceKey(AlarmKind.SHIFT, shiftType.id, occurrence),
            ),
        )
        assertEquals(
            "야간2",
            eligibility.alarmNameIfEligible(
                AlarmOccurrenceKey(AlarmKind.SHIFT, nextShiftType.id, occurrence.plusSeconds(300)),
            ),
        )
    }

    @Test
    fun `shift pre-alert is ineligible when owning group is disabled`() = runTest {
        val eligibility = eligibility(group(enabled = false))

        assertNull(eligibility.alarmNameIfEligible(key))
    }

    @Test
    fun `shift pre-alert is ineligible while owning group is a draft`() = runTest {
        val eligibility = eligibility(group(enabled = true, isDraft = true))

        assertNull(eligibility.alarmNameIfEligible(key))
    }

    @Test
    fun `enabled shift type in enabled completed group exposes its name`() = runTest {
        val eligibility = eligibility(group(enabled = true, isDraft = false))

        assertEquals("주간", eligibility.alarmNameIfEligible(key))
    }

    private fun eligibility(
        group: ShiftAlarmGroup,
        shiftTypes: List<ShiftTypeAlarm> = listOf(shiftType),
        events: Result<List<CalendarEvent>> = Result.success(
            listOf(
                CalendarEvent(
                    title = shiftType.exactTitles.first(),
                    start = key.scheduledAt.minusSeconds(60),
                    end = key.scheduledAt.plusSeconds(60),
                    allDay = false,
                ),
            ),
        ),
    ) = PreAlarmEligibility(
        alarmRepository = EmptyAlarmRepository,
        shiftAlarmRepository = ShiftRepository(group, shiftTypes),
        calendarEventSource = object : CalendarEventSource {
            override suspend fun getEventsOverlapping(
                startInclusive: Instant,
                endExclusive: Instant,
            ): Result<List<CalendarEvent>> = events
        },
        evaluateShiftGroupDay = EvaluateShiftGroupDayUseCase(
            ShiftDayEvaluator(),
            KoreanPublicHolidayProvider(),
        ),
        zoneId = ZoneId.of("Asia/Seoul"),
    )

    private fun group(enabled: Boolean, isDraft: Boolean = false) = ShiftAlarmGroup(
        id = 10L,
        name = "교대",
        enabled = enabled,
        emergencyShiftTypeId = shiftType.id,
        isDraft = isDraft,
    )

    private object EmptyAlarmRepository : AlarmRepository {
        override fun observeAll(): Flow<List<Alarm>> = flowOf(emptyList())
        override suspend fun getById(id: Long): Alarm? = null
        override suspend fun getEnabled(): List<Alarm> = emptyList()
        override suspend fun upsert(alarm: Alarm): Long = alarm.id
        override suspend fun delete(id: Long) = Unit
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
    }

    private class ShiftRepository(
        private val group: ShiftAlarmGroup,
        private val shiftTypes: List<ShiftTypeAlarm>,
    ) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> =
            flowOf(listOf(ShiftAlarmRepository.GroupWithTypes(group, shiftTypes)))

        override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? =
            ShiftAlarmRepository.GroupWithTypes(group, shiftTypes).takeIf { group.id == groupId }

        override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = shiftTypes.firstOrNull { it.id == id }
        override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> = shiftTypes
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }
}
