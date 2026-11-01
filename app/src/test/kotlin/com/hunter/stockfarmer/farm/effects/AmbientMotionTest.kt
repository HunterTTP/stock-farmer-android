package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AmbientMotionTest {
    private val view = View(0.0, 0.0, 1000.0, 800.0)

    @Test
    fun `wind pixels are unique snapped and mirrored without changing their height`() {
        val right = WindWisp.path(1.0, 10.0, 20.0, true)
        val left = WindWisp.path(1.0, 10.0, 20.0, false)
        assertEquals(right.size, right.distinct().size)
        assertEquals(right.map { Point(-it.x, it.y) }, left)
        assertTrue(right.all { it.x % 4 == 0.0 && it.y % 4 == 0.0 })
        assertEquals(0.0, WindWisp.alpha(-1.0, 0, 0.8))
        assertEquals(0.8, WindWisp.alpha(0.3, 0, 0.8))
        assertEquals(0.0, WindWisp.alpha(3.0, 109, 0.8))
    }

    @Test
    fun `gusts arrive every thirty seconds and leaves wait a minute in fall`() {
        val motion = WindMotion(seeded(4))
        motion.update(0.0, Season.fall, false, true, view, view)
        motion.update(29.0, Season.fall, false, true, view, view)
        assertTrue(motion.wisps.isEmpty())
        motion.update(1.0, Season.fall, false, true, view, view)
        assertEquals(4, motion.wisps.size)
        assertTrue(motion.leaves.isEmpty())
        motion.update(30.0, Season.fall, false, false, view, view)
        assertTrue(motion.leaves.size in 5..10)
        assertTrue(motion.leaves.all { !it.right && it.born in 61.0..63.5 })
        motion.update(0.1, Season.fall, true, false, view, view)
        assertTrue(motion.leaves.isEmpty())
        motion.update(60.0, Season.spring, false, false, view, view)
        assertTrue(motion.leaves.isEmpty())
    }

    @Test
    fun `leaves traverse the whole viewport and expired particles are removed`() {
        val leaf = Leaf(0.0, "red", 60.0, true, 40.0, 8.0, 100.0, 20.0, 0.1)
        assertEquals(-560.0, leaf.point(0.0, 1000.0).x)
        assertEquals(560.0, leaf.point(8.0, 1000.0).x)
        val motion = WindMotion(seeded(5))
        motion.update(0.0, Season.fall, false, true, view, view)
        motion.update(60.0, Season.fall, false, true, view, view)
        assertFalse(motion.leaves.isEmpty())
        motion.update(20.0, Season.fall, false, true, view, view)
        assertTrue(motion.leaves.isEmpty())
        assertTrue(motion.wisps.isEmpty())
    }

    @Test
    fun `midnight wave keeps its origin for the night and starts a new one next night`() {
        val motion = PulseMotion()
        val state = GameState()
        val first = Point(20.0, 20.0)
        motion.tiles(state, Instant.ofEpochSecond(5), first)
        motion.tiles(state, Instant.ofEpochSecond(10), Point(30.0, 30.0))
        assertEquals(first, motion.origin)
        assertEquals(0, motion.tiles(state, Instant.ofEpochSecond(100), first).size)
        motion.tiles(state, Instant.ofEpochSecond(1445), Point(30.0, 30.0))
        assertEquals(Point(30.0, 30.0), motion.origin)
    }

    @Test
    fun `midnight wave omits farmland water and buildings`() {
        val state = GameState()
        state.water[GridPoint(18, 18)] = com.hunter.stockfarmer.core.catalog.WaterStyle.deep
        state.putBuilding(GridPoint(18, 20), "house1")
        val tiles = PulseMotion().tiles(state, Instant.ofEpochSecond(5), Point(20.0, 20.0))
        assertTrue(tiles.size > 0)
        val covered = state.farmland + state.water.keys + state.placedBuildings.flatMap { it.footprint }
        val glowing = 0 until tiles.size
        assertTrue(glowing.none { GridPoint(tiles.row(it), tiles.col(it)) in covered })
        assertTrue(glowing.all { tiles.intensity(it) > 0 && tiles.intensity(it) <= 1 && tiles.twinkle(it) in 0.4..1.0 })
    }

    @Test
    fun `door lights use the front edge and never light the tractor`() {
        val state = GameState()
        state.putBuilding(GridPoint(10, 10), "house1")
        state.putBuilding(GridPoint(20, 20), "tractor1")
        val house = state.placedBuildings.first { it.building.id == "house1" }
        val lights = NightLights.torches(state, 3.0, 0.8)
        assertEquals(1, lights.size)
        assertEquals((10 + house.building.width / 2.0) * TILE, lights.single().point.x)
        assertEquals(WORLD - (10 + house.building.height - 0.3) * TILE, lights.single().point.y)
        assertTrue(lights.single().alpha in 0.32..0.4)
        assertTrue(NightLights.torches(state, 3.0, 0.0).all { it.alpha == 0.0 })
    }

    @Test
    fun `fireworks delay launches trail rise burst and clean themselves up`() {
        val motion = FireworkMotion(seeded(5))
        motion.launch(FireworkArea(10.0, 20.0, 0.0, 0.0), 4, 1.0)
        assertEquals(4, motion.count)
        assertTrue(motion.advance(0.0).isEmpty())
        val rising = motion.advance(0.3)
        assertTrue(rising.any { it.point.y > 20 && it.size == 6.0 })
        assertTrue(rising.any { it.size == 4.0 })
        val burst = motion.advance(0.8)
        assertTrue(burst.any { it.glowSize == 340.0 })
        assertTrue(burst.count { it.glowSize == 36.0 } >= 28)
        assertTrue(motion.advance(10.0).isEmpty())
        assertEquals(0, motion.count)
    }

    @Test
    fun `daytime sparks do not glow and reset cancels scheduled rockets`() {
        val motion = FireworkMotion(seeded(1))
        motion.launch(FireworkArea(0.0, 0.0, 100.0, 100.0), 9, 0.0)
        val burst = motion.advance(1.1)
        assertTrue(burst.none { it.glowSize == 36.0 })
        motion.reset()
        assertTrue(motion.advance(1.0).isEmpty())
        assertEquals(0, motion.count)
    }
}
