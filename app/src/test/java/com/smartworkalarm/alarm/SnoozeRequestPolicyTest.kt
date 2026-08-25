package com.smartworkalarm.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeRequestPolicyTest {
    @Test
    fun `disabled snooze rejects snooze action`() {
        assertFalse(canScheduleSnooze(false, AlarmRingingService.ACTION_SNOOZE))
        assertTrue(canScheduleSnooze(true, AlarmRingingService.ACTION_SNOOZE))
    }

    @Test
    fun `enabled snooze rejects unrelated and missing actions`() {
        assertFalse(canScheduleSnooze(true, AlarmRingingService.ACTION_DISMISS))
        assertFalse(canScheduleSnooze(true, null))
    }
}
