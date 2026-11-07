@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundCategory
import com.hunter.stockfarmer.core.feedback.SoundMix

/**
 * The volumes the player can set: the master, which every sound follows, the music, the effects (the interface, the farm
 * at work and the animals) and the farm's surroundings.
 */
enum class AudioChannel(
    val preferenceKey: String,
    /** How loud the channel plays with its slider at 100%, in decibels. */
    val fullDB: Double,
) {
    master("masterVolume", 0.0),
    music("musicVolume", SoundMix.musicFullDB),
    effects("effectsVolume", SoundMix.effectsFullDB),
    ambience("ambienceVolume", SoundMix.ambienceFullDB),
    ;

    companion object {
        /** Every channel played through the effects pool. */
        val effectChannels: List<AudioChannel> = listOf(effects, ambience)

        fun of(category: SoundCategory): AudioChannel =
            when (category) {
                SoundCategory.`interface`, SoundCategory.farm, SoundCategory.animals -> effects
                SoundCategory.ambience -> ambience
            }
    }
}

/** Zero is the off switch, for a channel or for the master. */
@ConsistentCopyVisibility
data class SoundSettings private constructor(
    private val levels: Map<AudioChannel, Double>,
) {
    constructor() : this(AudioChannel.entries.associateWith { 1.0 })

    /** The slider's position, from 0 to 1. */
    operator fun get(channel: AudioChannel): Double = levels.getValue(channel)

    /** The share of full volume the channel's slider leaves, evenly in decibels along the slider. */
    fun gain(channel: AudioChannel): Double = SoundMix.gain(forSlider = this[channel], fullDB = channel.fullDB)

    /** The share of full volume the channel plays at once the master is applied. */
    fun mixedGain(channel: AudioChannel): Double =
        if (channel == AudioChannel.master) gain(channel) else gain(channel) * gain(AudioChannel.master)

    /** Whether a kind of sound can be heard at all: a volume of nothing, its own or the master's, turns it off. */
    fun isAudible(channel: AudioChannel): Boolean = mixedGain(channel) > 0

    fun withLevel(
        channel: AudioChannel,
        level: Double,
    ): SoundSettings = SoundSettings(levels + (channel to clamp(level)))

    companion object {
        fun from(preferences: Map<String, *>): SoundSettings =
            SoundSettings(
                AudioChannel.entries.associateWith { channel ->
                    clamp((preferences[channel.preferenceKey] as? Number)?.toDouble() ?: 1.0)
                },
            )

        private fun clamp(value: Double): Double = if (value.isNaN()) 1.0 else value.coerceIn(0.0, 1.0)
    }
}
