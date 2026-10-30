package com.smartworkalarm.alarm

internal interface AlarmPlayback {
    fun start(uri: String, onFailure: () -> Unit)
    fun stop()
}

/** All calls and playback callbacks are serialized on the service main thread. */
internal class AlarmPlaybackController(
    private val playback: AlarmPlayback,
    private val onExhausted: () -> Unit,
) {
    private var generation = 0L
    private var attempt = 0
    private var remaining: Iterator<String> = emptyList<String>().iterator()

    fun start(candidates: List<String>) {
        stop()
        remaining = candidates.iterator()
        advance(generation)
    }

    fun stop() {
        generation++
        remaining = emptyList<String>().iterator()
        playback.stop()
    }

    private fun advance(session: Long) {
        if (session != generation) return
        val currentAttempt = ++attempt
        if (!remaining.hasNext()) {
            playback.stop()
            onExhausted()
            return
        }
        val uri = remaining.next()
        val failure = {
            if (session == generation && currentAttempt == attempt) advance(session)
        }
        try {
            playback.start(uri, failure)
        } catch (_: Exception) {
            failure()
        }
    }
}
