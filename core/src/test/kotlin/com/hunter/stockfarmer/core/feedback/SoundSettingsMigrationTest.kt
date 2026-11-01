package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SoundSettingsMigrationTest {
    @Test fun `a kind switched off is now at volume zero`() {
        val result =
            SoundSettingsMigration.migrateSwitchesToVolumes(
                mapOf(
                    "musicEnabled" to false,
                    "musicVolume" to 0.7,
                    "ambienceEnabled" to true,
                    "ambienceVolume" to 0.4,
                ),
            )
        assertEquals(0.0, result["musicVolume"])
        assertEquals(0.4, result["ambienceVolume"])
    }

    @Test fun `the animals start as the farm was set`() {
        assertEquals(
            0.3,
            SoundSettingsMigration.migrateSwitchesToVolumes(mapOf("soundEffectsVolume" to 0.3))["animalSoundsVolume"],
        )
        val off = SoundSettingsMigration.migrateSwitchesToVolumes(mapOf("soundEffectsEnabled" to false))
        assertEquals(0.0, off["soundEffectsVolume"])
        assertEquals(0.0, off["animalSoundsVolume"])
    }

    @Test fun `animals set on their own are kept`() {
        val result =
            SoundSettingsMigration.migrateSwitchesToVolumes(
                mapOf(
                    "soundEffectsEnabled" to false,
                    "animalSoundsEnabled" to true,
                    "animalSoundsVolume" to 0.6,
                ),
            )
        assertEquals(0.6, result["animalSoundsVolume"])
    }

    @Test fun `the old switches are forgotten and a second run changes nothing`() {
        val result = SoundSettingsMigration.migrateSwitchesToVolumes(mapOf("menuSoundsEnabled" to false))
        assertNull(result["menuSoundsEnabled"])
        assertEquals(
            0.5,
            SoundSettingsMigration.migrateSwitchesToVolumes(result + ("menuSoundsVolume" to 0.5))["menuSoundsVolume"],
        )
    }

    @Test fun `a fresh install is left alone`() {
        val result = SoundSettingsMigration.migrateSwitchesToVolumes(emptyMap())
        assertNull(result["musicVolume"])
        assertNull(result["animalSoundsVolume"])
    }

    @Test fun `the retired distance settings are forgotten`() {
        val result =
            SoundSettingsMigration.migrateSwitchesToVolumes(
                mapOf(
                    "masterVolume" to 0.2,
                    "soundFadeStartsAtZoom" to 0.9,
                    "soundsFadeWithDistance" to false,
                ),
            )
        assertNull(result["soundFadeStartsAtZoom"])
        assertNull(result["soundsFadeWithDistance"])
        assertNull(result["masterVolume"])
    }
}
