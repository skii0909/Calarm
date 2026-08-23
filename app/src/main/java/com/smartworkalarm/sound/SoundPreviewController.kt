package com.smartworkalarm.sound

interface SoundPreviewEngine {
    fun start(uri: String)
    fun stop()
}

class SoundPreviewController(private val engine: SoundPreviewEngine) {
    var playingUri: String? = null
        private set

    fun preview(uri: String) {
        if (playingUri != null) engine.stop()
        engine.start(uri)
        playingUri = uri
    }

    fun stop() {
        if (playingUri != null) engine.stop()
        playingUri = null
    }
}
