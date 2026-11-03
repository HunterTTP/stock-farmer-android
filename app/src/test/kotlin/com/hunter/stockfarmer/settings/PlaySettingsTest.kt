package com.hunter.stockfarmer.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaySettingsTest {
    @Test
    fun `loading clamps stored floats without rewriting preferences`() {
        for ((stored, reach) in listOf(
            -Float.MAX_VALUE to 0f,
            Float.MAX_VALUE to 200f,
            Float.NEGATIVE_INFINITY to 0f,
            Float.POSITIVE_INFINITY to 200f,
        )) {
            val storage = MemoryStorage().apply { putFloat("toolReach", stored) }
            val before = storage.values.toMap()
            val settings = PlaySettings(storage)
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
    fun `repeated graphics reset persists every default while preserving gameplay and unrelated preferences`() {
        val storage = MemoryStorage().apply { values["unrelated"] = "keep" }
        val settings =
            PlaySettings(storage).apply {
                setGridShown(false)
                setMoneyShown(false)
                setLevelShown(false)
                setToolReach(0f)
            }
        repeat(2) { settings.resetGraphicsDefaults() }
        assertEquals(
            mapOf(
                "unrelated" to "keep",
                "gridLinesShown" to true,
                "moneyPillShown" to true,
                "levelPillShown" to true,
                "toolReach" to 0f,
            ),
            storage.values,
        )
        val reopened = PlaySettings(storage)
        assertTrue(reopened.isGridShown && reopened.isMoneyShown && reopened.isLevelShown)
        assertEquals(0f, reopened.toolReach)
    }

    @Test
    fun `gameplay reset writes only reach even when graphics preferences need clamping`() {
        val storage =
            MemoryStorage().apply {
                putFloat("toolReach", 200f)
                putBoolean("gridLinesShown", false)
                putBoolean("moneyPillShown", false)
                putBoolean("levelPillShown", false)
            }
        val before = storage.values.toMap()
        val settings = PlaySettings(storage)
        repeat(2) { settings.resetGameplayDefaults() }
        assertEquals(before + ("toolReach" to 16f), storage.values)
        assertEquals(16f, PlaySettings(storage).toolReach)
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
        assertTrue(settings.isGridShown)
        assertTrue(settings.isMoneyShown)
        assertTrue(settings.isLevelShown)
        assertEquals(16f, settings.toolReach)
    }

    @Test
    fun `changes are kept for the next run`() {
        val storage = MemoryStorage()
        PlaySettings(storage).apply {
            setGridShown(false)
            setMoneyShown(false)
            setLevelShown(false)
            setToolReach(40f)
        }
        val reopened = PlaySettings(storage)
        assertFalse(reopened.isGridShown)
        assertFalse(reopened.isMoneyShown)
        assertFalse(reopened.isLevelShown)
        assertEquals(40f, reopened.toolReach)
    }

    @Test
    fun `tool reach is a whole number up to 200`() {
        val settings = PlaySettings(MemoryStorage())
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
        settings.setGridShown(false)
        settings.setMoneyShown(false)
        settings.setLevelShown(false)
        settings.setToolReach(80f)

        settings.resetGameplayDefaults()
        assertEquals(16f, settings.toolReach)
        assertFalse(settings.isGridShown)

        settings.setToolReach(80f)
        settings.resetGraphicsDefaults()
        assertTrue(settings.isGridShown)
        assertTrue(settings.isMoneyShown)
        assertTrue(settings.isLevelShown)
        assertEquals(80f, settings.toolReach)
    }
}
