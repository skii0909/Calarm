package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class GradualVolumeTest {
    @Test fun `volume rises from five percent to full over sixty seconds`() {
        assertEquals(0.05f, GradualVolume.atSecond(0), 0.001f)
        assertEquals(0.525f, GradualVolume.atSecond(30), 0.001f)
        assertEquals(1f, GradualVolume.atSecond(60), 0.001f)
        assertEquals(1f, GradualVolume.atSecond(90), 0.001f)
    }
}
