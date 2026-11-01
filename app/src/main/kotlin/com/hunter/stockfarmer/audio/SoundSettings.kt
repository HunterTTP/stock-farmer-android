@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundCategory

enum class AudioChannel(
    val preferenceKey: String,
) {
    music("musicVolume"),
    `interface`("menuSoundsVolume"),
    farm("soundEffectsVolume"),
    animals("animalSoundsVolume"),
    ambience("ambienceVolume"),
    ;

    companion object {
        fun of(category: SoundCategory): AudioChannel =
            when (category) {
                SoundCategory.`interface` -> `interface`
                SoundCategory.farm -> farm
                SoundCategory.animals -> animals
                SoundCategory.ambience -> ambience
            }
    }
}

/** Zero is the off switch. */
@ConsistentCopyVisibility
data class SoundSettings private constructor(
    private val levels: Map<AudioChannel, Double>,
) {
    constructor() : this(AudioChannel.entries.associateWith { 1.0 })

    operator fun get(channel: AudioChannel): Double = levels.getValue(channel)

    fun withLevel(
        channel: AudioChannel,
        level: Double,
    ): SoundSettings = SoundSettings(levels + (channel to clamp(level)))

    fun toPreferences(): Map<String, Double> = levels.mapKeys { it.key.preferenceKey }

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
