package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundSettings

/** The sound page's levels: one slider per channel, and a reset to how the game first came. */
internal class SoundControls(
    initial: SoundSettings,
) {
    var settings: SoundSettings = initial
        private set

    fun setLevel(
        channel: AudioChannel,
        value: Double,
    ) {
        settings = settings.withLevel(channel, value)
    }

    fun reset() {
        settings = SoundSettings()
    }
}
