package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundResolverTest {
    @Test
    fun `selected sound is tried before system fallbacks`() {
        assertEquals(
            listOf("selected", "alarm-default", "notification-default"),
            AlarmSoundResolver.candidates("selected", "alarm-default", "notification-default"),
        )
    }

    @Test
    fun `blank and duplicate sound candidates are removed`() {
        assertEquals(
            listOf("alarm-default", "notification-default"),
            AlarmSoundResolver.candidates(" ", "alarm-default", "notification-default"),
        )
        assertEquals(
            listOf("same"),
            AlarmSoundResolver.candidates("same", "same", null),
        )
    }
}
