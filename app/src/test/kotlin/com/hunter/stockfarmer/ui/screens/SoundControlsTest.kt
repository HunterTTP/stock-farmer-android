package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SoundControlsTest {
    @Test
    fun `changelog resource normalization preserves wrapped entries across Windows line endings`() {
        val text = "## 2026-10-22\r\n- First sentence\r\n  continues here.\r\n- Next entry\r\n"
        assertEquals(
            listOf("First sentence continues here.", "Next entry"),
            ScreenInformation.changelog(text).single().lines,
        )
    }

    @Test
    fun `music mute restores volume and never changes effects`() {
        val controls = SoundControls(SoundSettings().withLevel(AudioChannel.music, 0.35))
        controls.toggleMusic(false)
        assertFalse(controls.musicEnabled)
        assertEquals(1.0, controls.settings[AudioChannel.farm])
        controls.toggleMusic(true)
        assertEquals(0.35, controls.settings[AudioChannel.music])
    }

    @Test
    fun `effects switch restores the whole mix including silent channels without changing music`() {
        val original = SoundSettings().withLevel(AudioChannel.animals, 0.0).withLevel(AudioChannel.farm, 0.45)
        val controls = SoundControls(original)
        controls.toggleEffects(false)
        assertFalse(controls.effectsEnabled)
        assertEquals(1.0, controls.settings[AudioChannel.music])
        controls.toggleEffects(true)
        assertEquals(original, controls.settings)
        controls.setLevel(AudioChannel.farm, 2.0)
        assertEquals(1.0, controls.settings[AudioChannel.farm])
        controls.reset()
        assertEquals(SoundSettings(), controls.settings)
    }

    @Test
    fun `freshly loaded muted settings can be enabled`() {
        var muted = SoundSettings()
        AudioChannel.entries.forEach { muted = muted.withLevel(it, 0.0) }
        val controls = SoundControls(muted)
        controls.toggleMusic(true)
        controls.toggleEffects(true)
        assertTrue(controls.musicEnabled && controls.effectsEnabled)
        assertTrue(AudioChannel.entries.all { controls.settings[it] == 1.0 })
    }

    @Test
    fun `bundled changelog is valid core markdown and privacy describes Android storage`() {
        val folder = File("src/main/assets/information")
        val days = ScreenInformation.changelog(File(folder, "Changelog.md").readText())
        assertTrue(days.isNotEmpty())
        assertTrue(days.zipWithNext().all { (first, second) -> first.date > second.date })
        val privacy = File(folder, "Privacy.md").readText()
        assertTrue(privacy.contains("Yahoo Finance"))
        assertTrue(privacy.contains("Android"))
    }
}
