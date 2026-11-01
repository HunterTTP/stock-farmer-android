package com.hunter.stockfarmer.ui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeSheetTest {
    @Test
    fun `the presets set the times iOS does`() {
        assertEquals(listOf(1.0, 360.0, 720.0, 840.0, 1080.0, 1320.0), TimePreset.entries.map { it.minute })
        assertEquals("12:01 AM", TimePreset.Midnight.clockText)
        assertEquals("2:00 PM", TimePreset.Rain.clockText)
    }

    @Test
    fun `the sky is bluest at noon, darkest at night and grey in the rain`() {
        val noon = TimeSky.color(12 * 60.0)
        val night = TimeSky.color(23 * 60.0)
        val rain = TimeSky.color(15 * 60.0)
        assertTrue(noon.blue > night.blue)
        assertTrue(rain.blue < noon.blue)
        assertEquals("☀️", TimeSky.symbol(12 * 60.0))
        assertEquals("🌙", TimeSky.symbol(23 * 60.0))
    }
}
