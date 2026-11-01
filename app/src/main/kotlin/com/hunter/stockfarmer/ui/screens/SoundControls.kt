package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundSettings

/** Switches mute a group; turning it back on restores its previous mix, including intentionally silent channels. */
internal class SoundControls(
    initial: SoundSettings,
) {
    var settings: SoundSettings = initial
        private set
    private var previousMusic = initial[AudioChannel.music].takeIf { it > 0 } ?: 1.0
    private var previousEffects = initial
    private val effects = AudioChannel.entries.filter { it != AudioChannel.music }

    val musicEnabled: Boolean get() = settings[AudioChannel.music] > 0
    val effectsEnabled: Boolean get() = effects.any { settings[it] > 0 }

    fun setLevel(
        channel: AudioChannel,
        value: Double,
    ) {
        settings = settings.withLevel(channel, value)
    }

    fun toggleMusic(enabled: Boolean) {
        if (enabled == musicEnabled) return
        if (!enabled) previousMusic = settings[AudioChannel.music]
        settings = settings.withLevel(AudioChannel.music, if (enabled) previousMusic else 0.0)
    }

    fun toggleEffects(enabled: Boolean) {
        if (enabled == effectsEnabled) return
        if (!enabled) previousEffects = settings
        val hasPreviousMix = effects.any { previousEffects[it] > 0 }
        effects.forEach { channel ->
            val restored = if (hasPreviousMix) previousEffects[channel] else 1.0
            settings = settings.withLevel(channel, if (enabled) restored else 0.0)
        }
    }

    fun reset() {
        settings = SoundSettings()
    }
}
