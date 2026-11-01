package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.ReleasedButterfly
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.advanceTractor
import com.hunter.stockfarmer.core.rules.startTractorHarvest
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FarmRenderChangesTest {
    @Test
    fun `tractor departure and arrival refresh the parked building but driving does not`() {
        val now = Instant.ofEpochSecond(40_000_000)
        val home = GridPoint(10, 10)
        val engine = GameEngine()
        engine.state.putBuilding(home, "tractor1")
        engine.state.plots[GridPoint(10, 20)] = Plot("wheat", now.advancedBy(-100.0))
        val parked = engine.state.copy()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val started = engine.state.copy()
        assertTrue(FarmRenderChanges(parked, started).buildings)
        engine.advanceTractor(0.1, true, now)
        val changes = FarmRenderChanges(started, engine.state)
        assertFalse(changes.buildings || changes.growth || changes.terrain || changes.fireflies)
        engine.state.tractorRun = null
        assertTrue(FarmRenderChanges(started, engine.state).buildings)
    }

    @Test
    fun `drying invalidates terrain and growth without changing buildings`() {
        val before = GameState()
        before.lingeringWater[GridPoint(24, 24)] = Instant.ofEpochSecond(100)
        val after = before.copy()
        after.lingeringWater = mutableMapOf()
        val changes = FarmRenderChanges(before, after)
        assertTrue(changes.terrain && changes.growth)
        assertFalse(changes.buildings)
    }

    @Test
    fun `releasing a butterfly refreshes the butterflies`() {
        val before = GameState()
        val after = before.copy()
        after.releasedButterflies =
            listOf(ReleasedButterfly(wings = Butterflies.Wings.Shifting, home = GridPoint(10, 10)))
        assertTrue(FarmRenderChanges(before, after).fireflies)
    }

    @Test
    fun `style only change invalidates buildings and footprint consumers`() {
        val before = GameState()
        before.putBuilding(GridPoint(10, 10), "barn2")
        val after = before.copy()
        after.putBuilding(GridPoint(10, 10), "barn2", "blue")
        val changes = FarmRenderChanges(before, after)
        assertTrue(changes.buildings && changes.nightPulse && changes.fireflies)
        assertFalse(changes.terrain || changes.growth)
    }

    @Test
    fun `no op and animal motion do not invalidate structural layers`() {
        val before = GameState()
        val home = GridPoint(10, 10)
        before.pets[home] = Pet(breed = Cat.starter.id, name = "Cat", position = home)
        val after = before.copy()
        after.pets[home] = after.pets[home]!!.copy(position = GridPoint(11, 10))
        for (state in listOf(before, after)) {
            val changes = FarmRenderChanges(before, state)
            assertFalse(
                changes.terrain || changes.growth || changes.buildings || changes.nightPulse || changes.fireflies,
            )
        }
    }
}
