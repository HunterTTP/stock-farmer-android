package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.TimelapseSettings
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.persistence.InvalidSaveException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelapseScheduleTest {
    @Test fun `captures only when the selected time is crossed during active play`() {
        val schedule = TimelapseSchedule()
        val settings = TimelapseSettings()
        assertFalse(schedule.observe(1, 539.0, settings, true))
        assertTrue(schedule.observe(1, 540.0, settings, true))
        assertFalse(schedule.observe(1, 541.0, settings, true))
        assertFalse(schedule.observe(1, 539.0, settings, false))
        assertFalse(schedule.observe(1, 541.0, settings, true))
    }

    @Test fun `opening after capture time does not catch up and the next day can capture`() {
        val schedule = TimelapseSchedule()
        val settings = TimelapseSettings()
        assertFalse(schedule.observe(3, 800.0, settings, true))
        assertFalse(schedule.observe(4, 539.0, settings, true))
        assertTrue(schedule.observe(4, 541.0, settings, true))
    }

    @Test fun `clock presets capture on forward crossings but never twice on a captured day`() {
        val schedule = TimelapseSchedule()
        var settings = TimelapseSettings()
        schedule.observe(2, 400.0, settings, true)
        assertTrue(schedule.observe(2, 800.0, settings, true))
        settings = settings.copy(lastCapturedDay = 2)
        schedule.observe(2, 400.0, settings, true)
        assertFalse(schedule.observe(2, 800.0, settings, true))
    }

    @Test fun `disabling and changing the time rebaseline without an immediate capture`() {
        val schedule = TimelapseSchedule()
        var settings = TimelapseSettings()
        schedule.observe(1, 400.0, settings, true)
        settings = settings.copy(isEnabled = false)
        assertFalse(schedule.observe(1, 600.0, settings, true))
        settings = settings.copy(isEnabled = true)
        assertFalse(schedule.observe(1, 601.0, settings, true))
        settings = settings.copy(captureMinute = 602)
        assertFalse(schedule.observe(1, 601.0, settings, true))
        assertTrue(schedule.observe(1, 602.0, settings, true))
    }

    @Test fun `midnight capture works across the day boundary`() {
        val schedule = TimelapseSchedule()
        val settings = TimelapseSettings(captureMinute = 0)
        schedule.observe(1, 1439.0, settings, true)
        assertTrue(schedule.observe(2, 1.0, settings, true))
    }

    @Test fun `settings and capture marker round trip and old saves get defaults`() {
        val engine = GameEngine()
        engine.prepareTimelapse()
        engine.setTimelapseEnabled(false)
        engine.setTimelapseMinute(1020)
        engine.setTimelapseCrossfade(false)
        engine.recordTimelapseCapture(5)
        val data = GameStateJson.encode(engine.state)
        assertEquals(engine.state.timelapse, GameStateJson.decode(data).timelapse)
        val json = GameStateJson.json.parseToJsonElement(data) as JsonObject
        assertEquals(TimelapseSettings(), GameStateJson.decode(JsonObject(json - "timelapse").toString()).timelapse)
    }

    @Test fun `capture time is clamped and invalid saved settings are rejected`() {
        val engine = GameEngine()
        engine.setTimelapseMinute(-5)
        assertEquals(0, engine.state.timelapse.captureMinute)
        engine.setTimelapseMinute(2000)
        assertEquals(1439, engine.state.timelapse.captureMinute)
        val json = GameStateJson.json.parseToJsonElement(GameStateJson.encode(engine.state)) as JsonObject
        val settings = JsonObject((json["timelapse"] as JsonObject) + ("captureMinute" to JsonPrimitive(1440)))
        assertThrows(InvalidSaveException::class.java) {
            GameStateJson.decode(JsonObject(json + ("timelapse" to settings)).toString())
        }
    }
}
