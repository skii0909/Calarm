package com.smartworkalarm.domain.shift

import com.smartworkalarm.domain.holiday.KoreanPublicHolidayProvider
import com.smartworkalarm.domain.model.CalendarEvent
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.repository.ShiftAlarmRepository
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluateShiftGroupDayUseCaseTest {
    private val useCase = EvaluateShiftGroupDayUseCase(ShiftDayEvaluator(), KoreanPublicHolidayProvider())
    private val sunday = LocalDate.of(2026, 8, 23)

    @Test
    fun `disabled group has inactive status even when calendar title matches`() {
        val aggregate = aggregate(group(enabled = false), listOf(type(1, "주간", sundayMask)))

        val result = useCase.execute(aggregate, sunday, events("주간"))

        assertEquals(ShiftGroupDayStatus.Inactive, result)
    }

    @Test
    fun `disabled and non repeating types are excluded from evaluation`() {
        val aggregate = aggregate(
            group(),
            listOf(
                type(1, "주간", sundayMask, enabled = false),
                type(2, "야간", mondayMask),
            ),
        )

        val result = useCase.execute(aggregate, sunday, events("주간", "야간"))

        assertEquals(ShiftGroupDayStatus.Inactive, result)
    }

    @Test
    fun `vacation takes priority over matching shift`() {
        val aggregate = aggregate(group(), listOf(type(1, "주간", sundayMask)))

        val result = useCase.execute(aggregate, sunday, events("주간", "여름휴가"))

        assertEquals(ShiftGroupDayStatus.Vacation, result)
    }

    @Test
    fun `public holiday suppresses opted in shift types`() {
        val holiday = LocalDate.of(2026, 8, 15)
        val aggregate = aggregate(group(), listOf(type(1, "주간", allDaysMask, skipHoliday = true)))

        val result = useCase.execute(aggregate, holiday, events("주간"))

        assertEquals(ShiftGroupDayStatus.PublicHoliday, result)
    }

    @Test
    fun `matched status carries only effective shift type`() {
        val day = type(1, "주간", sundayMask)
        val aggregate = aggregate(group(), listOf(day, type(2, "야간", mondayMask)))

        val result = useCase.execute(aggregate, sunday, events("주간", "야간"))

        assertEquals(ShiftGroupDayStatus.Matched(day), result)
    }

    @Test
    fun `read failure exposes emergency only when it is effective today`() {
        val day = type(1, "주간", sundayMask)
        val aggregate = aggregate(group(emergencyId = day.id), listOf(day, type(2, "야간", mondayMask)))

        val result = useCase.execute(aggregate, sunday, Result.failure(IllegalStateException("calendar")))

        assertEquals(day, (result as ShiftGroupDayStatus.ReadFailure).emergencyType)
        val inactiveEmergency = useCase.execute(
            aggregate(group(emergencyId = 2), listOf(day, type(2, "야간", mondayMask))),
            sunday,
            Result.failure(IllegalStateException("calendar")),
        )
        assertNull((inactiveEmergency as ShiftGroupDayStatus.ReadFailure).emergencyType)
    }

    @Test
    fun `holiday related calendar notes do not suppress matching shift`() {
        val day = type(1, "주간", sundayMask, skipHoliday = true)
        val aggregate = aggregate(group(), listOf(day))
        assertEquals(ShiftGroupDayStatus.Matched(day), useCase.execute(aggregate, sunday, events("주간", "한글날 행사 준비")))
        assertEquals(ShiftGroupDayStatus.Matched(day), useCase.execute(aggregate, sunday, events("주간", "대체공휴일")))
    }

    @Test
    fun `all day canonical public holiday suppresses matching opted in shift`() {
        val day = type(1, "주간", sundayMask, skipHoliday = true)
        val events = events("주간").getOrThrow() + CalendarEvent("대한민국 임시공휴일", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), true)
        assertEquals(ShiftGroupDayStatus.PublicHoliday, useCase.execute(aggregate(group(), listOf(day)), sunday, Result.success(events)))
    }

    private fun group(enabled: Boolean = true, emergencyId: Long? = null) =
        ShiftAlarmGroup(10, "교대", enabled, emergencyId)

    private fun aggregate(group: ShiftAlarmGroup, types: List<ShiftTypeAlarm>) =
        ShiftAlarmRepository.GroupWithTypes(group, types)

    private fun type(
        id: Long,
        name: String,
        mask: Int,
        enabled: Boolean = true,
        skipHoliday: Boolean = false,
    ) = ShiftTypeAlarm(
        id, 10, name, listOf(name), 7, 0, mask, enabled, null, false, true, 5, skipHoliday,
    )

    private fun events(vararg titles: String): Result<List<CalendarEvent>> = Result.success(
        titles.map { CalendarEvent(it, Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false) },
    )

    companion object {
        private const val mondayMask = 1
        private const val sundayMask = 64
        private const val allDaysMask = 127
    }
}
