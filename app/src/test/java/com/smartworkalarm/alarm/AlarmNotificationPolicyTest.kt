package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmNotificationPolicyTest {
    @Test
    fun `versioned ringing channel delegates sound and vibration only to the service`() {
        var soundDisabled = false
        var vibrationEnabled: Boolean? = null

        applyRingingChannelPolicy(
            disableSound = { soundDisabled = true },
            setVibrationEnabled = { vibrationEnabled = it },
        )

        assertNotEquals("ringing_alarm", RingingChannelPolicy.ID)
        assertTrue(RingingChannelPolicy.ID.contains("v2"))
        assertTrue(soundDisabled)
        assertEquals(false, vibrationEnabled)
    }

    @Test
    fun `full screen intent identity separates alarm kinds and long hash collisions`() {
        val firstId = 0L
        val collidingId = 4_294_967_297L
        assertEquals(firstId.hashCode(), collidingId.hashCode())

        assertNotEquals(
            fullScreenIntentIdentity(firstId, isShiftAlarm = false),
            fullScreenIntentIdentity(collidingId, isShiftAlarm = false),
        )
        assertNotEquals(
            fullScreenIntentIdentity(firstId, isShiftAlarm = false),
            fullScreenIntentIdentity(firstId, isShiftAlarm = true),
        )
    }
}
