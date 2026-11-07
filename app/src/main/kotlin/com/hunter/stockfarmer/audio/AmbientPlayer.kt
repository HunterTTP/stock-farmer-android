package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundMix

/** Rain is streamed separately from the bounded pool of short effects. Supply Weather.eased(rain). */
class AmbientPlayer(
    private val output: MusicOutput,
) : AutoCloseable {
    private var amount = 0.0
    private var gain = 1.0
    private var active = false
    private var loaded = false
    private var ready = false
    private var playing = false
    private var closed = false

    /** Whether the rain loop is not still opening: it is ready, failed to open, or was never asked for. */
    val isPrepared: Boolean get() = closed || !loaded || ready

    /** The gain is the share of full volume the Ambience and Master sliders leave. */
    fun update(
        easedRain: Double,
        isSnowing: Boolean,
        gain: Double,
    ) {
        amount = if (isSnowing || !easedRain.isFinite()) 0.0 else easedRain.coerceIn(0.0, 1.0)
        this.gain = if (gain.isFinite()) gain.coerceIn(0.0, 1.0) else 0.0
        refresh()
    }

    /** Opens the rain loop now, so the first rain does not wait for it; one that will not open is logged and rain falls silently. */
    fun prepare() {
        if (!closed && !loaded) load()
    }

    fun setActive(active: Boolean) {
        this.active = active
        refresh()
    }

    private fun refresh() {
        if (closed) return
        if (!active || amount <= 0.0 || gain <= 0.0) {
            output.pause()
            playing = false
            return
        }
        if (!loaded) load()
        if (!ready) return
        if (!playing) {
            output.setVolume(0.0)
            output.start()
            playing = true
        }
        output.setVolume(gain * SoundMix.rainTrimGain * amount, 1.0)
    }

    private fun load() {
        loaded = true
        output.load(
            "audio/Effects/Ambience/steady-rain-loop.ogg",
            ready = {
                if (!closed) {
                    ready = true
                    refresh()
                }
            },
            completed = {},
            failed = {
                loaded = false
                ready = false
                playing = false
            },
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        output.close()
    }
}
