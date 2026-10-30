package com.smartworkalarm.domain.usecase

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.model.RepeatDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateNextOccurrenceUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val useCase = CalculateNextOccurrenceUseCase()

    @Test
    fun `Monday alarm later today returns today`() {
        val alarm = alarmAt(7, 0, RepeatDay.MONDAY)
        val now = instant(2026, 8, 17, 6, 30)

        assertEquals(instant(2026, 8, 17, 7, 0), useCase.execute(alarm, now, zone))
    }

    @Test
    fun `Friday alarm after its time advances to next Friday`() {
        val alarm = alarmAt(7, 0, RepeatDay.FRIDAY)
        val now = instant(2026, 8, 21, 7, 1)

        assertEquals(instant(2026, 8, 28, 7, 0), useCase.execute(alarm, now, zone))
    }

    @Test
    fun `Sunday advances to selected Monday`() {
        val alarm = alarmAt(7, 0, RepeatDay.MONDAY)
        val now = instant(2026, 8, 23, 12, 0)

        assertEquals(instant(2026, 8, 24, 7, 0), useCase.execute(alarm, now, zone))
    }

    @Test
    fun `daily alarm after today's time returns tomorrow`() {
        val alarm = alarmAt(7, 0, *RepeatDay.entries.toTypedArray())
        val now = instant(2026, 8, 17, 8, 0)

        assertEquals(instant(2026, 8, 18, 7, 0), useCase.execute(alarm, now, zone))
    }

    @Test
    fun `disabled alarm has no next occurrence`() {
        val alarm = alarmAt(7, 0, RepeatDay.MONDAY).copy(enabled = false)

        assertNull(useCase.execute(alarm, Instant.EPOCH, zone))
    }

    @Test
    fun `one-time alarm without repeat days later today returns today`() {
        val alarm = alarmAt(7, 0)
        val now = instant(2026, 8, 17, 6, 30)

        assertEquals(instant(2026, 8, 17, 7, 0), useCase.execute(alarm, now, zone))
    }

    @Test
    fun `one-time alarm without repeat days after its time returns tomorrow`() {
        val alarm = alarmAt(7, 0)
        val now = instant(2026, 8, 17, 7, 1)

        assertEquals(instant(2026, 8, 18, 7, 0), useCase.execute(alarm, now, zone))
    }

    private fun alarmAt(hour: Int, minute: Int, vararg days: RepeatDay) = Alarm(
        id = 1,
        name = "출근",
        hour = hour,
        minute = minute,
        repeatDaysMask = days.fold(0) { mask, day -> mask or day.bit },
        enabled = true,
        skipOnCalendarMatch = true,
    )

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()
}
