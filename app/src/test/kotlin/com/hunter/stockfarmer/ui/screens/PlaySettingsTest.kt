package com.hunter.stockfarmer.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaySettingsTest {
    private class MemoryStorage : PlaySettingsStorage {
        val values = mutableMapOf<String, Any>()

        override fun boolean(
            key: String,
            default: Boolean,
        ) = values[key] as? Boolean ?: default

        override fun float(
            key: String,
            default: Float,
        ) = values[key] as? Float ?: default

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) {
            values[key] = value
        }

        override fun putFloat(
            key: String,
            value: Float,
        ) {
            values[key] = value
        }
    }

    @Test
    fun `a new install starts as the iOS game does`() {
        val settings = PlaySettings(MemoryStorage())
        assertFalse(settings.isDynamicShadowsEnabled)
        assertEquals(0.5f, settings.shadowIntensity)
        assertTrue(settings.isGridShown)
        assertTrue(settings.isMoneyShown)
        assertTrue(settings.isLevelShown)
        assertEquals(16f, settings.toolReach)
    }

    @Test
    fun `changes are kept for the next run`() {
        val storage = MemoryStorage()
        PlaySettings(storage).apply {
            setDynamicShadows(true)
            setShadowIntensity(0.8f)
            setGridShown(false)
            setMoneyShown(false)
            setLevelShown(false)
            setToolReach(40f)
        }
        val reopened = PlaySettings(storage)
        assertTrue(reopened.isDynamicShadowsEnabled)
        assertEquals(0.8f, reopened.shadowIntensity)
        assertFalse(reopened.isGridShown)
        assertFalse(reopened.isMoneyShown)
        assertFalse(reopened.isLevelShown)
        assertEquals(40f, reopened.toolReach)
    }

    @Test
    fun `shadow intensity stays between 0 and 1 and tool reach is a whole number up to 200`() {
        val settings = PlaySettings(MemoryStorage())
        settings.setShadowIntensity(3f)
        assertEquals(1f, settings.shadowIntensity)
        settings.setShadowIntensity(-1f)
        assertEquals(0f, settings.shadowIntensity)
        settings.setToolReach(12.6f)
        assertEquals(13f, settings.toolReach)
        settings.setToolReach(500f)
        assertEquals(200f, settings.toolReach)
        settings.setToolReach(-5f)
        assertEquals(0f, settings.toolReach)
    }

    @Test
    fun `a stored reach out of range is clamped on load`() {
        val storage = MemoryStorage().apply { putFloat("toolReach", 999f) }
        assertEquals(200f, PlaySettings(storage).toolReach)
    }

    @Test
    fun `each page resets only its own settings`() {
        val settings = PlaySettings(MemoryStorage())
        settings.setDynamicShadows(true)
        settings.setShadowIntensity(0.9f)
        settings.setGridShown(false)
        settings.setMoneyShown(false)
        settings.setLevelShown(false)
        settings.setToolReach(80f)

        settings.resetGameplayDefaults()
        assertEquals(16f, settings.toolReach)
        assertTrue(settings.isDynamicShadowsEnabled)
        assertFalse(settings.isGridShown)

        settings.setToolReach(80f)
        settings.resetGraphicsDefaults()
        assertFalse(settings.isDynamicShadowsEnabled)
        assertEquals(0.5f, settings.shadowIntensity)
        assertTrue(settings.isGridShown)
        assertTrue(settings.isMoneyShown)
        assertTrue(settings.isLevelShown)
        assertEquals(80f, settings.toolReach)
    }
}
