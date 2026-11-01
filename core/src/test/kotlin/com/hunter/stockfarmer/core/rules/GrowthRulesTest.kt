package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class GrowthRulesTest {
    private val planted = Instant.ofEpochSecond(1_000_000)
    private val point = GridPoint(24, 25)

    @Test
    fun `progress scales with speed multiplier`() {
        val carrot = Crop.named("carrot")!!
        val plot = Plot(carrot.id, planted)
        val halfway = planted.advancedBy(carrot.growDuration / 2)
        assertTrue(abs(GrowthRules.progress(plot, carrot, now = halfway) - 0.5) < 1e-9)
        assertTrue(GrowthRules.progress(plot, carrot, speedMultiplier = 2.0, now = halfway) > 1 - 1e-9)
    }

    @Test
    fun `progress never exceeds one or goes negative`() {
        val wheat = Crop.starter
        val plot = Plot(wheat.id, planted)
        assertEquals(0.0, GrowthRules.progress(plot, wheat, now = planted.advancedBy(-10.0)))
        assertEquals(1.0, GrowthRules.progress(plot, wheat, now = planted.advancedBy(9_999.0)))
    }

    private fun phase(
        quadrant: Int,
        progress: Double,
        plantedAt: Instant = planted,
        crop: Crop = Crop.starter,
    ): GrowthPhase = GrowthTimelines(Plot(crop.id, plantedAt), point, crop).phase(quadrant, progress)

    @Test
    fun `every quadrant starts as a sprout and only ever advances`() {
        for (quadrant in 0 until 4) {
            assertEquals(GrowthPhase.sprout, phase(quadrant, 0.0))
            var previous = GrowthPhase.sprout.spriteNumber
            for (step in 0..100) {
                val current = phase(quadrant, step / 100.0).spriteNumber
                assertTrue(current >= previous)
                previous = current
            }
        }
    }

    @Test
    fun `exactly one quadrant reaches its final form at completion`() {
        for (offset in 0 until 50) {
            val start = planted.advancedBy(offset * 60.0)
            val justBefore = (0 until 4).filter { phase(it, 0.9999, start) == GrowthPhase.ready }
            assertEquals(3, justBefore.size)
            assertTrue((0 until 4).all { phase(it, 1.0, start) == GrowthPhase.ready })
        }
    }

    @Test
    fun `the four sprites finish spread across the whole growth`() {
        for (offset in 0 until 50) {
            val timelines = StageTimeline.quadrants("tile-$offset")
            val finishes = timelines.map { it.finalStart }.sorted()
            assertEquals(1.0, finishes.last())
            assertTrue(finishes[0] >= 0.2 && finishes[0] < 0.4)
            assertTrue(finishes[1] > 0.45 && finishes[1] < 0.65)
            assertTrue(finishes[2] > 0.7 && finishes[2] < 0.9)
            assertEquals(4, finishes.toSet().size)
            assertTrue(timelines.all { it.growingStart > 0 && it.growingStart < it.finalStart })
        }
    }

    @Test
    fun `the last sprite is not always the same one`() {
        val lasts =
            (0 until 60).map { offset -> StageTimeline.quadrants("tile-$offset").indexOfFirst { it.finalStart == 1.0 } }
        assertEquals(4, lasts.toSet().size)
    }

    @Test
    fun `remaining time accounts for saturated soil`() {
        val state = GameState()
        state.farmland.add(point)
        state.water[GridPoint(point.row, point.col + 1)] = WaterStyle.deep
        val carrot = Crop.named("carrot")!!
        val plot = Plot(carrot.id, planted)
        val plain = GrowthConditions(GameState())
        val saturated = GrowthConditions(state)
        val later = planted.advancedBy(10.0)
        val expected = carrot.growDuration - 10
        assertTrue(abs(plain.remainingTime(plot, carrot, point, later) - expected) < 1e-9)
        assertTrue(saturated.remainingTime(plot, carrot, point, later) < expected)
        assertEquals(0.0, plain.remainingTime(plot, carrot, point, planted.advancedBy(500.0)))
    }
}
