package com.smartworkalarm.sound

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundPreviewControllerTest {
    @Test fun `tapping a sound starts it and tapping another replaces playback`() {
        val engine = FakePreviewEngine()
        val controller = SoundPreviewController(engine)

        controller.preview("android.resource://com.smartworkalarm/raw/sunrise_pop")
        controller.preview("android.resource://com.smartworkalarm/raw/bright_chime")

        assertEquals(listOf("sunrise_pop", "bright_chime"), engine.started)
        assertEquals(1, engine.stopCount)
        assertEquals("android.resource://com.smartworkalarm/raw/bright_chime", controller.playingUri)
    }

    @Test fun `closing picker stops current preview`() {
        val engine = FakePreviewEngine()
        val controller = SoundPreviewController(engine)
        controller.preview("android.resource://com.smartworkalarm/raw/sunrise_pop")

        controller.stop()

        assertEquals(1, engine.stopCount)
        assertEquals(null, controller.playingUri)
    }

    private class FakePreviewEngine : SoundPreviewEngine {
        val started = mutableListOf<String>()
        var stopCount = 0
        override fun start(uri: String) { started += uri.substringAfterLast('/') }
        override fun stop() { stopCount++ }
    }
}
