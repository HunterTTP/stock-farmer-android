package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.feedback.SoundCue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelCuesTest {
    private val played = mutableListOf<SoundCue>()
    private val cues = PanelCues { played += it }

    @Test
    fun `a panel opening and closing makes the panel sounds once each`() {
        cues.update(false)
        assertTrue(played.isEmpty())
        cues.update(true)
        cues.update(true)
        cues.update(false)
        cues.update(false)
        assertEquals(listOf(SoundCue.openPanel, SoundCue.closePanel), played)
    }

    @Test
    fun `a panel closed after something was chosen in it closes quietly`() {
        cues.update(true)
        cues.closeQuietly()
        cues.update(false)
        assertEquals(listOf(SoundCue.openPanel), played)
    }

    @Test
    fun `quietness lasts for one close only`() {
        cues.update(true)
        cues.closeQuietly()
        cues.update(false)
        cues.update(true)
        cues.update(false)
        assertEquals(listOf(SoundCue.openPanel, SoundCue.openPanel, SoundCue.closePanel), played)
    }
}
