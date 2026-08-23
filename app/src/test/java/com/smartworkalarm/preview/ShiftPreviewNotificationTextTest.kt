package com.smartworkalarm.preview

import com.smartworkalarm.domain.model.ShiftAlarmGroup
import com.smartworkalarm.domain.shift.ShiftDayEvaluation
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftPreviewNotificationTextTest {
    private val group = ShiftAlarmGroup(10, "교대", true, emergencyShiftTypeId = 1)

    @Test
    fun `day and night conflict requests calendar confirmation`() {
        val conflict = ShiftPreviewChange(
            group = group,
            previousKey = "MATCHED:1",
            currentEvaluation = ShiftDayEvaluation.Conflict(
                listOf(
                    ShiftDayEvaluation.Matched(1, "주간"),
                    ShiftDayEvaluation.Matched(2, "야간"),
                ),
                matchedCalendarTitles = mapOf(1L to listOf("주간근무"), 2L to listOf("야간")),
            ),
        )

        assertEquals(
            "내일 주간[주간근무]과 야간[야간] 일정이 함께 등록되어 있습니다. 캘린더 근무 일정을 확인해 주세요.",
            ShiftPreviewNotificationText.row(conflict),
        )
    }

    @Test
    fun `read failure tells the user to check calendar access`() {
        val failure = ShiftPreviewChange(group, "MATCHED:2", ShiftDayEvaluation.ReadFailure)

        assertEquals(
            "내일 근무 일정을 읽을 수 없습니다. 캘린더 접근 권한을 확인해 주세요.",
            ShiftPreviewNotificationText.row(failure),
        )
    }
}
