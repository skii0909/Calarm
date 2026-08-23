package com.smartworkalarm.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

class AndroidSoundPreviewEngine(private val context: Context) : SoundPreviewEngine {
    private var player: MediaPlayer? = null

    override fun start(uri: String) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(context, Uri.parse(uri))
            isLooping = true
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }
}
