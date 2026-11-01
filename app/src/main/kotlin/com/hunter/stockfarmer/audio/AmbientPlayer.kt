package com.hunter.stockfarmer.audio

/** Rain is streamed separately from the bounded pool of short effects. Supply Weather.eased(rain). */
class AmbientPlayer(
    private val output: MusicOutput,
) : AutoCloseable {
    private var amount = 0.0
    private var level = 1.0
    private var active = false
    private var loaded = false
    private var ready = false
    private var playing = false
    private var closed = false

    fun update(
        easedRain: Double,
        isSnowing: Boolean,
        level: Double,
    ) {
        amount = if (isSnowing || !easedRain.isFinite()) 0.0 else easedRain.coerceIn(0.0, 1.0)
        this.level = SoundSettings().withLevel(AudioChannel.ambience, level)[AudioChannel.ambience]
        refresh()
    }

    fun setActive(active: Boolean) {
        this.active = active
        refresh()
    }

    private fun refresh() {
        if (closed) return
        if (!active || amount <= 0.0 || level <= 0.0) {
            output.pause()
            playing = false
            return
        }
        if (!loaded) {
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
        if (!ready) return
        if (!playing) {
            output.setVolume(0.0)
            output.start()
            playing = true
        }
        output.setVolume(0.5 * level * amount, 1.0)
    }

    override fun close() {
        if (closed) return
        closed = true
        output.close()
    }
}
