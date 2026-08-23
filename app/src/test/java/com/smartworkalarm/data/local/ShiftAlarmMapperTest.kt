package com.smartworkalarm.data.local

import com.smartworkalarm.domain.model.ShiftTypeAlarm
import com.smartworkalarm.domain.model.ShiftAlarmGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftAlarmMapperTest {
    @Test
    fun `group mapper preserves draft visibility marker`() {
        val draft = ShiftAlarmGroup(4, "작성 중", enabled = false, emergencyShiftTypeId = null, isDraft = true)

        assertEquals(draft, draft.toEntity().toDomain())
    }

    @Test
    fun `shift type mapper preserves exact title aliases and alarm settings`() {
        val value = ShiftTypeAlarm(
            2,
            1,
            "야간",
            listOf("야간", "야간근무"),
            18,
            0,
            127,
            true,
            null,
            true,
            12,
            false,
        )

        assertEquals(value, value.toEntity().toDomain())
    }

    @Test
    fun `shift type mapper trims removes blank and deduplicates exact title aliases`() {
        val value = ShiftTypeAlarm(
            2,
            1,
            "야간",
            listOf(" 야간 ", "", "야간", "  ", "야간근무 "),
            18,
            0,
            127,
            true,
            null,
            true,
            12,
            false,
        )

        assertEquals(listOf("야간", "야간근무"), value.toEntity().toDomain().exactTitles)
    }
}
