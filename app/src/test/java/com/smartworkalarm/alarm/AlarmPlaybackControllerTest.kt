package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import android.app.Service

class AlarmPlaybackControllerTest {
    @Test fun `only ringing starts are redelivered after process death`() {
        assertEquals(Service.START_REDELIVER_INTENT, ringingServiceRestartMode(AlarmRingingService.ACTION_START))
        assertEquals(Service.START_NOT_STICKY, ringingServiceRestartMode(AlarmRingingService.ACTION_DISMISS))
        assertEquals(Service.START_NOT_STICKY, ringingServiceRestartMode(AlarmRingingService.ACTION_SNOOZE))
        assertEquals(Service.START_NOT_STICKY, ringingServiceRestartMode(null))
    }

    private class FakePlayback : AlarmPlayback {
        val attempted = mutableListOf<String>()
        val failures = mutableListOf<() -> Unit>()
        var stopped = false
        override fun start(uri: String, onFailure: () -> Unit) {
            attempted += uri
            failures += onFailure
        }
        override fun stop() { stopped = true }
    }

    @Test fun `asynchronous decoder failure advances through fallbacks only once`() {
        val playback = FakePlayback()
        var exhausted = 0
        val controller = AlarmPlaybackController(playback) { exhausted++ }
        controller.start(listOf("selected", "system", "bundled"))
        playback.failures[0]()
        playback.failures[0]()
        playback.failures[1]()
        playback.failures[2]()
        assertEquals(listOf("selected", "system", "bundled"), playback.attempted)
        assertEquals(1, exhausted)
    }

    @Test fun `dismissal prevents late prepare failure from restarting sound`() {
        val playback = FakePlayback()
        val controller = AlarmPlaybackController(playback) {}
        controller.start(listOf("selected", "fallback"))
        controller.stop()
        playback.failures[0]()
        assertEquals(listOf("selected"), playback.attempted)
        assertEquals(true, playback.stopped)
    }

    @Test fun `new alarm ignores failure from previous playback attempt`() {
        val playback = FakePlayback()
        val controller = AlarmPlaybackController(playback) {}
        controller.start(listOf("old", "old-fallback"))
        controller.start(listOf("new", "new-fallback"))
        playback.failures[0]()
        assertEquals(listOf("old", "new"), playback.attempted)
        playback.failures[1]()
        assertEquals(listOf("old", "new", "new-fallback"), playback.attempted)
    }

    @Test fun `synchronous preparation exception tries packaged fallback`() {
        val attempted = mutableListOf<String>()
        val playback = object : AlarmPlayback {
            override fun start(uri: String, onFailure: () -> Unit) {
                attempted += uri
                if (uri == "bad") throw IllegalStateException("unreadable URI")
            }
            override fun stop() = Unit
        }
        AlarmPlaybackController(playback) {}.start(listOf("bad", "bundled"))
        assertEquals(listOf("bad", "bundled"), attempted)
    }
}
