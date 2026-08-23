package com.smartworkalarm.presentation.shift

import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.shift.ShiftGroupDayStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftGroupStatusTextTest {
    @Test
    fun `read failure says configured eligible emergency will apply`() {
        val status = ShiftGroupDayStatus.ReadFailure(type())

        assertEquals(
            "캘린더를 읽을 수 없어 비상 기본 알람이 적용됩니다.",
            todayCalendarWarning(status),
        )
    }

    @Test
    fun `read failure without eligible emergency says no alarm will ring`() {
        val status = ShiftGroupDayStatus.ReadFailure(emergencyType = null)

        assertEquals(
            "오늘 사용할 수 있는 비상 기본 알람이 없어 울리지 않습니다. 그룹 설정과 캘린더를 확인해 주세요.",
            todayCalendarWarning(status),
        )
    }

    private fun type() = ShiftTypeAlarm(
        id = 1,
        groupId = 10,
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
}
