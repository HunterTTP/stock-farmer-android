package com.hunter.stockfarmer.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaySettingsTest {
    @Test
    fun `loading clamps stored floats without rewriting preferences`() {
        for ((stored, intensity, reach) in listOf(
            Triple(-Float.MAX_VALUE, 0f, 0f),
            Triple(Float.MAX_VALUE, 1f, 200f),
            Triple(Float.NEGATIVE_INFINITY, 0f, 0f),
            Triple(Float.POSITIVE_INFINITY, 1f, 200f),
        )) {
            val storage =
                MemoryStorage().apply {
                    putFloat("shadowIntensity", stored)
                    putFloat("toolReach", stored)
                }
            val before = storage.values.toMap()
            val settings = PlaySettings(storage)
            assertEquals(intensity, settings.shadowIntensity)
            assertEquals(reach, settings.toolReach)
            assertEquals(before, storage.values)
        }
    }

    @Test
    fun `reach rounds half points upward before persisting the bounded value`() {
        val storage = MemoryStorage()
        val settings = PlaySettings(storage)
        for ((input, expected) in listOf(
            -0.5f to 0f,
            0.49f to 0f,
            0.5f to 1f,
            16.49f to 16f,
            16.5f to 17f,
            199.5f to 200f,
            200.5f to 200f,
        )) {
            settings.setToolReach(input)
            assertEquals(expected, settings.toolReach, "input=$input")
            assertEquals(expected, storage.values["toolReach"])
            assertEquals(expected, PlaySettings(storage).toolReach)
        }
    }

    @Test
    fun `shadow setters persist clamped values including endpoints`() {
        val storage = MemoryStorage()
        val settings = PlaySettings(storage)
        for ((input, expected) in listOf(-10f to 0f, 0f to 0f, 0.375f to 0.375f, 1f to 1f, 10f to 1f)) {
            settings.setShadowIntensity(input)
            assertEquals(expected, settings.shadowIntensity)
            assertEquals(expected, storage.values["shadowIntensity"])
            assertEquals(expected, PlaySettings(storage).shadowIntensity)
        }
    }

    @Test
    fun `repeated graphics reset persists every default while preserving gameplay and unrelated preferences`() {
        val storage = MemoryStorage().apply { values["unrelated"] = "keep" }
        val settings =
            PlaySettings(storage).apply {
                setDynamicShadows(true)
                setShadowIntensity(0f)
                setGridShown(false)
                setMoneyShown(false)
                setLevelShown(false)
                setToolReach(0f)
            }
        repeat(2) { settings.resetGraphicsDefaults() }
        assertEquals(
            mapOf(
                "unrelated" to "keep",
                "dynamicShadows" to false,
                "shadowIntensity" to 0.5f,
                "gridLinesShown" to true,
                "moneyPillShown" to true,
                "levelPillShown" to true,
                "toolReach" to 0f,
            ),
            storage.values,
        )
        val reopened = PlaySettings(storage)
        assertFalse(reopened.isDynamicShadowsEnabled)
        assertEquals(0.5f, reopened.shadowIntensity)
        assertTrue(reopened.isGridShown && reopened.isMoneyShown && reopened.isLevelShown)
        assertEquals(0f, reopened.toolReach)
    }

    @Test
    fun `gameplay reset writes only reach even when graphics preferences need clamping`() {
        val storage =
            MemoryStorage().apply {
                putFloat("toolReach", 200f)
                putFloat("shadowIntensity", 5f)
                putBoolean("dynamicShadows", true)
                putBoolean("gridLinesShown", false)
                putBoolean("moneyPillShown", false)
                putBoolean("levelPillShown", false)
            }
        val before = storage.values.toMap()
        val settings = PlaySettings(storage)
        repeat(2) { settings.resetGameplayDefaults() }
        assertEquals(before + ("toolReach" to 16f), storage.values)
        assertEquals(16f, PlaySettings(storage).toolReach)
        assertEquals(1f, settings.shadowIntensity)
    }

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
    fun `a new install starts with the default settings`() {
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
