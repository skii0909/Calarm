package com.smartworkalarm.domain.shift

import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftDayEvaluatorTest {
    private val evaluator = ShiftDayEvaluator()
    private val day = shiftType(id = 1, name = "주간", aliases = listOf("주간", "DAY"))
    private val night = shiftType(id = 2, name = "야간", aliases = listOf("야간", "NIGHT"))

    @Test
    fun `returns none when no event title exactly matches a shift type`() {
        assertEquals(
            ShiftDayEvaluation.None,
            evaluator.evaluate(listOf(day, night), Result.success(events("주간 근무 후 회식"))),
        )
    }

    @Test
    fun `returns matched shift type for one matching event`() {
        assertEquals(
            ShiftDayEvaluation.Matched(1, "주간"),
            evaluator.evaluate(listOf(day, night), Result.success(events("  DAY  "))),
        )
    }

    @Test
    fun `duplicate events for one shift type are not a conflict`() {
        assertEquals(
            ShiftDayEvaluation.Matched(1, "주간"),
            evaluator.evaluate(listOf(day, night), Result.success(events("주간", "DAY"))),
        )
    }

    @Test
    fun `two different shift types are conflict`() {
        val result = evaluator.evaluate(listOf(day, night), Result.success(events("주간", "야간")))

        assertTrue(result is ShiftDayEvaluation.Conflict)
        assertEquals(
            listOf(
                ShiftDayEvaluation.Matched(1, "주간"),
                ShiftDayEvaluation.Matched(2, "야간"),
            ),
            (result as ShiftDayEvaluation.Conflict).matches,
        )
        assertEquals(
            mapOf(1L to listOf("주간"), 2L to listOf("야간")),
            result.matchedCalendarTitles,
        )
    }

    @Test
    fun `returns read failure when calendar events cannot be read`() {
        assertEquals(
            ShiftDayEvaluation.ReadFailure,
            evaluator.evaluate(listOf(day, night), Result.failure(IllegalStateException("denied"))),
        )
    }

    private fun shiftType(id: Long, name: String, aliases: List<String>) = ShiftTypeAlarm(
        id = id,
        groupId = 10,
        name = name,
        exactTitles = aliases,
        hour = 7,
        minute = 0,
        repeatDaysMask = 0,
        enabled = true,
        ringtoneUri = null,
        gradualVolume = false,
        snoozeMinutes = 5,
        skipOnPublicHoliday = false,
    )

    private fun events(vararg titles: String): List<CalendarEvent> = titles.map { title ->
        CalendarEvent(
            title = title,
            start = Instant.parse("2026-08-23T00:00:00Z"),
            end = Instant.parse("2026-08-23T08:00:00Z"),
            allDay = false,
        )
    }
}
