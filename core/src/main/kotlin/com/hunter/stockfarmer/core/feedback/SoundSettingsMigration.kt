package com.hunter.stockfarmer.core.feedback

/** Brings older sound preferences forward; callers persist the returned preferences. */
object SoundSettingsMigration {
    private val switchKeys =
        listOf("musicEnabled", "menuSoundsEnabled", "soundEffectsEnabled", "animalSoundsEnabled", "ambienceEnabled")
    private val volumeKeys =
        listOf("musicVolume", "menuSoundsVolume", "soundEffectsVolume", "animalSoundsVolume", "ambienceVolume")
    private val retiredKeys =
        listOf(
            "masterVolume",
            "soundsFadeWithDistance",
            "soundFadeStartsAtZoom",
            "soundVolumeZoomedOut",
            "soundVolumeFarAway",
        )
    private val farmIndex = 2
    private val animalsIndex = 3

    /** Animals inherit the farm settings. Forgotten switches make the migration idempotent. */
    fun migrateSwitchesToVolumes(defaults: Map<String, Any>): Map<String, Any> {
        fun isOn(index: Int): Boolean? = defaults[switchKeys[index]] as? Boolean
        val switchedOff =
            switchKeys.indices.filter { index ->
                isOn(index)?.not() ?: (index == animalsIndex && isOn(farmIndex) == false)
            }
        val result = defaults.toMutableMap()
        if (defaults[volumeKeys[animalsIndex]] == null) {
            defaults[volumeKeys[farmIndex]]?.let { result[volumeKeys[animalsIndex]] = it }
        }
        for (index in switchedOff) result[volumeKeys[index]] = 0.0
        for (key in switchKeys + retiredKeys) result.remove(key)
        return result.toMap()
    }
}
