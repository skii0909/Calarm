package com.smartworkalarm.preview

import com.smartworkalarm.data.local.AlarmPreviewDao
import com.smartworkalarm.data.local.AlarmPreviewEntity
import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.repository.AlarmRepository
import com.smartworkalarm.domain.repository.CalendarEventSource
import com.smartworkalarm.domain.usecase.CompareAlarmPreviewStateUseCase
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

class EvaluateNextDayAlarmsUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val targetDate = LocalDate.of(2026, 8, 25)

    @Test
    fun `zero selected calendars produce one clear system warning for calendar aware alarms`() = runTest {
        val useCase = EvaluateNextDayAlarmsUseCase(
            alarms = AlarmRepositoryFake(listOf(calendarAwareAlarm())),
            events = FailingEventSource,
            previews = AlarmPreviewDaoFake(),
            compare = CompareAlarmPreviewStateUseCase(),
            clock = Clock.fixed(Instant.parse("2026-08-24T13:00:00Z"), zone),
            holidays = KoreanPublicHolidayProvider(),
        )

        val evaluation = useCase.executeWithStatus(targetDate, zone)
        val rows = NextDayPreviewNotificationPolicy.rows(
            alarmEvaluation = evaluation,
            shiftEvaluation = ShiftPreviewEvaluation(),
        )

        assertTrue(evaluation.calendarReadFailed)
        assertTrue(evaluation.changes.isEmpty())
        assertEquals(
            listOf("내일 알람을 확인할 캘린더를 읽지 못했습니다. 권한과 선택한 캘린더를 확인해 주세요."),
            rows,
        )
    }

    @Test
    fun `mixed ordinary and shift failures still produce only one warning`() {
        val rows = NextDayPreviewNotificationPolicy.rows(
            alarmEvaluation = AlarmPreviewEvaluation(calendarReadFailed = true),
            shiftEvaluation = ShiftPreviewEvaluation(calendarReadFailed = true),
        )

        assertEquals(
            listOf("내일 알람을 확인할 캘린더를 읽지 못했습니다. 권한과 선택한 캘린더를 확인해 주세요."),
            rows,
        )
    }

    private fun calendarAwareAlarm() = Alarm(
        id = 1L,
        name = "출근",
        hour = 7,
        minute = 0,
        repeatDaysMask = 127,
        enabled = true,
        skipOnCalendarMatch = true,
    )
}

private object FailingEventSource : CalendarEventSource {
    override suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>> = Result.failure(IllegalStateException("No selected calendars"))
}

private class AlarmRepositoryFake(
    private val alarms: List<Alarm>,
) : AlarmRepository {
    override fun observeAll(): Flow<List<Alarm>> = flowOf(alarms)
    override suspend fun getById(id: Long): Alarm? = alarms.firstOrNull { it.id == id }
    override suspend fun getEnabled(): List<Alarm> = alarms.filter { it.enabled }
    override suspend fun upsert(alarm: Alarm): Long = alarm.id
    override suspend fun delete(id: Long) = Unit
    override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
}

private class AlarmPreviewDaoFake : AlarmPreviewDao {
    private val values = mutableMapOf<Long, AlarmPreviewEntity>()

    override suspend fun get(alarmId: Long): AlarmPreviewEntity? = values[alarmId]

    override suspend fun upsert(entity: AlarmPreviewEntity) {
        values[entity.alarmId] = entity
    }

    override suspend fun delete(alarmId: Long) {
        values.remove(alarmId)
    }
}
