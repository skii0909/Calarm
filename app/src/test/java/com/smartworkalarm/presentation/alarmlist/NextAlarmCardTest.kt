package com.smartworkalarm.presentation.alarmlist

import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.presentation.shift.ShiftGroupNextAlarm
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NextAlarmCardTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-08-24T23:00:00Z") // 월요일 08:00 KST

    @Test
    fun `목록 순서가 아니라 실제 발생 시각이 가장 빠른 일반 알람을 선택한다`() {
        val later = alarm(id = 1, name = "늦은 알람", hour = 9)
        val sooner = alarm(id = 2, name = "빠른 알람", hour = 8, minute = 30)

        val result = selectNextRegularAlarm(listOf(later, sooner), now, zone)

        assertEquals(2L, result?.id)
        assertEquals("빠른 알람", result?.name)
    }

    @Test
    fun `일반 알람보다 교대 알람이 빠르면 상단 카드에 교대 알람을 선택한다`() {
        val regular = NextAlarmCardItem(1, "일반", Instant.parse("2026-08-25T00:00:00Z"), false)
        val shift = ShiftGroupNextAlarm(2, "야간", Instant.parse("2026-08-24T23:30:00Z"))

        val result = selectNextAlarmCard(regular, listOf(shift))

        assertEquals("야간", result?.name)
        assertEquals(true, result?.isShift)
    }

    private fun alarm(
        id: Long,
        name: String,
        hour: Int,
        minute: Int = 0,
    ) = Alarm(
        id = id,
        name = name,
        hour = hour,
        minute = minute,
        repeatDaysMask = 1 shl 1, // Tuesday
        enabled = true,
        skipOnCalendarMatch = false,
    )
}
