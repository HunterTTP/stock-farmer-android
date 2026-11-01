package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeasonalSoundtrackTest {
    private val assigned = Season.entries.flatMap { SeasonalSoundtrack.tracks(it) }

    @Test
    fun `spring keeps its original songs`() {
        val spring = SeasonalSoundtrack.tracks(Season.spring)
        assertTrue(spring.containsAll(listOf("Far Far Away", "Forgotten Biomes", "What Clouds Are Made Of")))
    }

    @Test
    fun `every recording has one season`() {
        assertEquals(20, assigned.size)
        assertEquals(assigned.size, assigned.toSet().size)
        assertEquals(7, SeasonalSoundtrack.tracks(Season.spring).size)
        assertEquals(5, SeasonalSoundtrack.tracks(Season.fall).size)
        assertEquals(8, SeasonalSoundtrack.tracks(Season.winter).size)
    }

    @Test
    fun `retired recordings stay out`() {
        assertFalse(assigned.any { "Golden Gleam" in it || "Minuet (Slower)" in it || "Vienna" in it })
    }

    @Test
    fun `fall and winter keep their own folders`() {
        assertTrue("fall/Sunlight Through Leaves" in SeasonalSoundtrack.tracks(Season.fall))
        assertTrue("winter/Polar Lights" in SeasonalSoundtrack.tracks(Season.winter))
        assertTrue("winter/Wind Over The Trees" in SeasonalSoundtrack.tracks(Season.winter))
    }
}
