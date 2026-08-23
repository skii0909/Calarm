package com.smartworkalarm.preview

import com.smartworkalarm.data.local.ShiftPreviewDao
import com.smartworkalarm.data.local.ShiftPreviewEntity
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import com.smartworkalarm.domain.shift.ShiftDayEvaluator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateNextDayShiftGroupsUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 8, 24)
    private val tomorrow = today.plusDays(1)
    private val group = ShiftAlarmGroup(10, "교대", true, emergencyShiftTypeId = 1)
    private val day = shiftType(1, "주간")
    private val night = shiftType(2, "야간")
    private val previews = PreviewStates()
    private val calendar = Calendar(zone, eventsByDate = mutableMapOf())
    private val useCase = useCase(day, night)

    @Test
    fun `same night shift on consecutive days emits no change`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"))

        assertTrue(useCase.execute(today, tomorrow, zone).isEmpty())
        assertEquals("MATCHED:2", previews.get(group.id)?.stateKey)
    }

    @Test
    fun `first setup with no shift today and night tomorrow emits a change`() = runTest {
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("NONE", change.previousKey)
        assertEquals("MATCHED:2", change.currentKey)
    }

    @Test
    fun `today calendar modification is evaluated instead of persisted preview state`() = runTest {
        previews.save(group.id, "MATCHED:2")
        calendar.eventsByDate[today] = listOf(event(today, "주간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("MATCHED:1", change.previousKey)
        assertEquals("MATCHED:2", change.currentKey)
    }

    @Test
    fun `different matched shift emits the current shift`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "주간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("MATCHED:2", change.previousKey)
        assertEquals("MATCHED:1", change.currentKey)
        assertEquals(ShiftDayEvaluation.Matched(1, "주간"), change.currentEvaluation)
    }

    @Test
    fun `starting a shift after no shift emits a change`() = runTest {
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("NONE", change.previousKey)
        assertEquals("MATCHED:2", change.currentKey)
    }

    @Test
    fun `ending a shift emits no shift state`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "야간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("MATCHED:2", change.previousKey)
        assertEquals("NONE", change.currentKey)
    }

    @Test
    fun `unchanged conflict ids do not repeat when event order changes`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "주간"), event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"), event(tomorrow, "주간"), event(tomorrow, "야간"))

        assertTrue(useCase.execute(today, tomorrow, zone).isEmpty())
        assertEquals("CONFLICT:1,2", previews.get(group.id)?.stateKey)
    }

    @Test
    fun `vacation cannot hide a day and night conflict`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "여름휴가"), event(today, "주간"), event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"), event(tomorrow, "여름휴가"), event(tomorrow, "주간"))

        assertTrue(useCase.execute(today, tomorrow, zone).isEmpty())
        assertEquals("CONFLICT:1,2", previews.get(group.id)?.stateKey)
    }

    @Test
    fun `calendar holiday cannot hide a day and night conflict`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "대한민국 임시공휴일"), event(today, "주간"), event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "야간"), event(tomorrow, "대한민국 임시공휴일"), event(tomorrow, "주간"))

        assertTrue(useCase.execute(today, tomorrow, zone).isEmpty())
        assertEquals("CONFLICT:1,2", previews.get(group.id)?.stateKey)
    }

    @Test
    fun `local holiday cannot hide a day and night conflict`() = runTest {
        val holiday = LocalDate.of(2026, 8, 15)
        val nextDay = holiday.plusDays(1)
        calendar.eventsByDate[holiday] = listOf(event(holiday, "주간"), event(holiday, "야간"))
        calendar.eventsByDate[nextDay] = listOf(event(nextDay, "야간"), event(nextDay, "주간"))

        assertTrue(useCase.execute(holiday, nextDay, zone).isEmpty())
        assertEquals("CONFLICT:1,2", previews.get(group.id)?.stateKey)
    }

    @Test
    fun `none and read failure are separate states`() = runTest {
        calendar.failOn(tomorrow)

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("NONE", change.previousKey)
        assertEquals("READ_FAILURE", change.currentKey)
    }

    @Test
    fun `vacation suppresses a matched shift in tomorrow preview`() = runTest {
        calendar.eventsByDate[today] = listOf(event(today, "야간"))
        calendar.eventsByDate[tomorrow] = listOf(event(tomorrow, "여름휴가"), event(tomorrow, "야간"))

        val change = useCase.execute(today, tomorrow, zone).single()

        assertEquals("NONE", change.currentKey)
    }

    private fun event(date: LocalDate, title: String) = CalendarEvent(
        title = title,
        start = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
        allDay = true,
    )

    private fun shiftType(id: Long, name: String) = ShiftTypeAlarm(
        id = id,
        groupId = group.id,
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

    private fun useCase(vararg types: ShiftTypeAlarm) = EvaluateNextDayShiftGroupsUseCase(
        repository = Repository(group, types.toList()),
        events = calendar,
        previews = previews,
        evaluator = ShiftDayEvaluator(),
        clock = Clock.fixed(Instant.parse("2026-08-23T13:00:00Z"), zone),
    )

    private class PreviewStates : ShiftPreviewDao {
        private val values = mutableMapOf<Long, ShiftPreviewEntity>()

        suspend fun save(groupId: Long, stateKey: String) {
            values[groupId] = ShiftPreviewEntity(groupId, stateKey, 0)
        }

        override suspend fun get(groupId: Long): ShiftPreviewEntity? = values[groupId]

        override suspend fun upsert(entity: ShiftPreviewEntity) {
            values[entity.groupId] = entity
        }
    }

    private class Calendar(
        private val zone: ZoneId,
        val eventsByDate: MutableMap<LocalDate, List<CalendarEvent>>,
    ) : CalendarEventSource {
        private val failedDates = mutableSetOf<LocalDate>()

        fun failOn(date: LocalDate) {
            failedDates += date
        }

        override suspend fun getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>> {
            val date = startInclusive.atZone(zone).toLocalDate()
            return if (date in failedDates) Result.failure(IllegalStateException("calendar unavailable"))
            else Result.success(eventsByDate[date].orEmpty())
        }
    }

    private class Repository(
        private val group: ShiftAlarmGroup,
        private val types: List<ShiftTypeAlarm>,
    ) : ShiftAlarmRepository {
        override fun observeGroupsWithTypes(): Flow<List<ShiftAlarmRepository.GroupWithTypes>> =
            flowOf(listOf(ShiftAlarmRepository.GroupWithTypes(group, types)))

        override suspend fun getGroupWithTypes(groupId: Long): ShiftAlarmRepository.GroupWithTypes? = null
        override suspend fun getShiftType(id: Long): ShiftTypeAlarm? = null
        override suspend fun getEnabledShiftTypes(): List<ShiftTypeAlarm> = types
        override suspend fun upsertGroup(group: ShiftAlarmGroup): Long = group.id
        override suspend fun upsertShiftType(shiftType: ShiftTypeAlarm): Long = shiftType.id
        override suspend fun deleteGroup(groupId: Long) = Unit
        override suspend fun deleteShiftType(shiftTypeId: Long) = Unit
    }
}
