package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundSettings
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `levels are clamped and reset restores the defaults`() {
        val controls = SoundControls(SoundSettings().withLevel(AudioChannel.animals, 0.0))
        controls.setLevel(AudioChannel.farm, 2.0)
        assertEquals(1.0, controls.settings[AudioChannel.farm])
        assertEquals(0.0, controls.settings[AudioChannel.animals])
        controls.reset()
        assertEquals(SoundSettings(), controls.settings)
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
