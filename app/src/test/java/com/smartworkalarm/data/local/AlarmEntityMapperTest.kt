package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmEntityMapperTest {
    @Test
    fun `alarm mapper preserves disabled snooze`() {
        val alarm = sampleAlarm().copy(snoozeEnabled = false, snoozeMinutes = 17)

        assertFalse(alarm.toEntity(createdAt = 0L).toDomain().snoozeEnabled)
        assertEquals(17, alarm.toEntity(createdAt = 0L).toDomain().snoozeMinutes)
    }

    @Test
    fun `entity round trip preserves user settings`() {
        val alarm = sampleAlarm()

        val entity = alarm.toEntity(createdAt = 123L)

        assertEquals(alarm, entity.toDomain())
        assertEquals(123L, entity.createdAt)
    }

    private fun sampleAlarm() = Alarm(
            id = 4,
            name = "출근",
            hour = 7,
            minute = 0,
            repeatDaysMask = 31,
            enabled = true,
            skipOnCalendarMatch = true,
            calendarKeywords = listOf("휴가", "연차"),
            ringtoneUri = "content://media/alarm/7",
            skipOnPublicHoliday = true,
            gradualVolume = true,
            snoozeMinutes = 12,
        )
}
