package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundResolverTest {
    @Test
    fun `selected sound is tried before system fallbacks`() {
        assertEquals(
            listOf("selected", "alarm-default", "notification-default", AlarmSoundResolver.BUNDLED_FALLBACK),
            AlarmSoundResolver.candidates("selected", "alarm-default", "notification-default"),
        )
    }

    @Test
    fun `blank and duplicate sound candidates are removed`() {
        assertEquals(
            listOf("alarm-default", "notification-default", AlarmSoundResolver.BUNDLED_FALLBACK),
            AlarmSoundResolver.candidates(" ", "alarm-default", "notification-default"),
        )
        assertEquals(
            listOf("same", AlarmSoundResolver.BUNDLED_FALLBACK),
            AlarmSoundResolver.candidates("same", "same", null),
        )
    }

    @Test fun `missing system defaults still have a packaged sound`() {
        assertEquals(listOf(AlarmSoundResolver.BUNDLED_FALLBACK), AlarmSoundResolver.candidates(null, null, null))
    }
}
