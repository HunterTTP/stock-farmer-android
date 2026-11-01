package com.hunter.stockfarmer.audio

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Leave existing media alone, and pause instead of ducking when another app or a call interrupts music. */
internal class MusicAudioFocus(
    private val manager: AudioManager,
    private val ownMusicPlaying: () -> Boolean,
    private val allowedChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var held = false
    private var requested = false
    private var allowed = false
    private var closed = false
    private val request =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(gameAudioAttributes())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                held = change == AudioManager.AUDIOFOCUS_GAIN
                publish(active && held)
            }, handler)
            .build()

    private val check =
        object : Runnable {
            override fun run() {
                if (!active || closed) return
                val otherMedia = !ownMusicPlaying() && manager.isMusicActive
                val interrupted = manager.mode != AudioManager.MODE_NORMAL
                if (otherMedia || interrupted) {
                    publish(false)
                } else if (!held) {
                    requested = true
                    held = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    publish(held)
                } else {
                    publish(true)
                }
                handler.postDelayed(this, 1000)
            }
        }

    fun setActive(value: Boolean) {
        if (closed) return
        active = value
        handler.removeCallbacks(check)
        if (active) {
            check.run()
        } else {
            publish(false)
            if (requested) manager.abandonAudioFocusRequest(request)
            requested = false
            held = false
        }
    }

    private fun publish(value: Boolean) {
        if (value == allowed) return
        allowed = value
        allowedChanged(value)
    }

    override fun close() {
        if (closed) return
        setActive(false)
        closed = true
        handler.removeCallbacksAndMessages(null)
    }
}
