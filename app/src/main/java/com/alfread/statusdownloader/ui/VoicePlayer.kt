package com.alfread.statusdownloader.ui

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pemutar sederhana untuk VN / audio hasil cadangan. Hanya satu yang diputar sekaligus. */
object VoicePlayer {
    private var player: MediaPlayer? = null

    var playing by mutableStateOf<String?>(null)
        private set

    fun toggle(path: String) {
        if (playing == path) { stop(); return }
        stop()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepare()
            mp.start()
            player = mp
            playing = path
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = null
    }
}
