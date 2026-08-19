package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEntityMapperTest {
    @Test
    fun `entity round trip preserves user settings`() {
        val alarm = Alarm(4, "출근", 7, 0, 31, true, true)

        val entity = alarm.toEntity(createdAt = 123L)

        assertEquals(alarm, entity.toDomain())
        assertEquals(123L, entity.createdAt)
    }
}
