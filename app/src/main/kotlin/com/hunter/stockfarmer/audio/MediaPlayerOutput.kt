package com.hunter.stockfarmer.audio

import android.content.res.AssetManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** One streamed recording, prepared asynchronously. No Android objects enter the playback policies. */
internal class MediaPlayerOutput(
    private val assets: AssetManager,
    private val looping: Boolean = false,
) : MusicOutput {
    private val handler = Handler(Looper.getMainLooper())
    private val fade = VolumeFade()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var closed = false
    private var onFailure: (() -> Unit)? = null

    val isPlaying: Boolean get() = prepared && player?.isPlaying == true

    private val tick =
        object : Runnable {
            override fun run() {
                applyVolume()
                if (!closed && !fade.isFinished(seconds())) handler.postDelayed(this, 16)
            }
        }

    override fun load(
        path: String,
        ready: () -> Unit,
        completed: () -> Unit,
        failed: () -> Unit,
    ) {
        stop()
        if (closed) return
        onFailure = failed
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(gameAudioAttributes())
            assets.openFd(path).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.isLooping = looping
            next.setOnPreparedListener {
                if (player === next && !closed) {
                    prepared = true
                    applyVolume()
                    ready()
                }
            }
            next.setOnCompletionListener { if (player === next && !closed) completed() }
            next.setOnErrorListener { _, what, extra ->
                if (player === next && !closed) fail("$path: $what/$extra")
                true
            }
            next.prepareAsync()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not prepare $path", exception)
            fail(path)
        }
    }

    override fun start() {
        if (prepared && !closed) {
            try {
                player?.let { if (!it.isPlaying) it.start() }
            } catch (exception: IllegalStateException) {
                fail("Could not start playback", exception)
            }
        }
    }

    override fun pause() {
        if (prepared && !closed) {
            try {
                player?.let { if (it.isPlaying) it.pause() }
            } catch (exception: IllegalStateException) {
                fail("Could not pause playback", exception)
            }
        }
    }

    override fun stop() {
        handler.removeCallbacks(tick)
        prepared = false
        onFailure = null
        player?.release()
        player = null
    }

    override fun setVolume(
        level: Double,
        fadeSeconds: Double,
    ) {
        if (closed) return
        handler.removeCallbacks(tick)
        fade.set(level, fadeSeconds, seconds())
        tick.run()
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
    }

    private fun applyVolume() {
        if (!prepared || closed) return
        val level = fade.value(seconds()).toFloat()
        try {
            player?.setVolume(level, level)
        } catch (exception: IllegalStateException) {
            fail("Could not change playback volume", exception)
        }
    }

    private fun fail(
        message: String,
        exception: Exception? = null,
    ) {
        Log.w(TAG, message, exception)
        val callback = onFailure
        stop()
        callback?.invoke()
    }

    companion object {
        private const val TAG = "StockFarmerAudio"

        private fun seconds(): Double = SystemClock.elapsedRealtime() / 1000.0
    }
}
