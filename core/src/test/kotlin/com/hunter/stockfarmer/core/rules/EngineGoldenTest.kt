package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Recorded from the rules before the performance work: the same seeds must keep giving exactly these results. */
class EngineGoldenTest {
    private val scenarios = EngineGoldenScenarios()

    @Test
    fun `the late farm is the farm the audit describes`() {
        val state = LateFarm.build().state
        assertEquals(650, state.plots.size)
        assertEquals(300, state.water.size)
        assertEquals(200, state.trees.size)
        assertEquals(50, state.rocks.size)
        assertEquals(200, state.fences.size)
        assertEquals(60, GameEngine(state).animals.size)
    }

    @Test
    fun `animals shut out of their food`() = assertEquals(EngineGolden.FOOD_FENCED_OFF, scenarios.foodFencedOff())

    @Test
    fun `animals going through a gate to eat`() =
        assertEquals(EngineGolden.FOOD_BEHIND_A_GATE, scenarios.foodBehindAGate())

    @Test
    fun `animals sheltering from the rain and the night`() =
        assertEquals(EngineGolden.SHELTER, scenarios.shelterFromRainAndNight())

    @Test
    fun `animals keeping away from a driving Tractor`() =
        assertEquals(EngineGolden.TRACTOR, scenarios.tractorThroughTheAnimals())

    @Test
    fun `animals stranded where none should stand`() = assertEquals(EngineGolden.STRANDED, scenarios.strandedAnimals())

    @Test
    fun `farms of every shape changing as they run`() = assertEquals(EngineGolden.RANDOM_FARMS, scenarios.randomFarms())

    @Test
    fun `herds caught up over any time with meals births and capacity changes`() =
        assertEquals(EngineGolden.HERD_CATCH_UP, scenarios.herdCatchUp())

    @Test
    fun `trees growing over crowded overlapping ground`() =
        assertEquals(EngineGolden.TREE_GROWTH, scenarios.treeGrowth())

    @Test
    fun `water layouts as the water changes`() = assertEquals(EngineGolden.WATER_LAYOUTS, scenarios.waterLayouts())
}

internal object EngineGolden {
    const val FOOD_FENCED_OFF = "a1d9684c4e5e48679deb7960:1927"
    const val FOOD_BEHIND_A_GATE = "7fbdc38da07bc0f2979cc855:8195"
    const val SHELTER = "14dbc58e87b5d1add73f018a:6958"
    const val TRACTOR = "725f0366a43d609b7ceb891e:3580"
    const val STRANDED = "e20410afe3e0311436cab0c2:1837"
    const val RANDOM_FARMS = "14eda5a3b00c288189a0d601:18885"
    const val HERD_CATCH_UP = "219256b7671a89c9a3636638:773"
    const val TREE_GROWTH = "1fe2a0d26a7c6acccd078542:0"
    const val WATER_LAYOUTS = "e7319476c5418b41c72fcd46:0"
}
