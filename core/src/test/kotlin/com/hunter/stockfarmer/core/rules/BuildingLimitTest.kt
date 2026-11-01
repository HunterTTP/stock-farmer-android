package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildingLimitTest {
    @Test
    fun `every building is limited to one except pet houses coops sheds and silos`() {
        val several = mapOf("petHouse" to 10, "chickenCoop" to 4, "shed" to 4, "silo" to 4)
        for (building in Building.all) assertEquals(several[building.id] ?: 1, building.placementLimit, building.id)
    }

    @Test
    fun `a second workshop is refused`() {
        val engine =
            GameEngine(stateWith(money = 1_000_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 100_000))
        engine.state.selectedBuildingID = "stoneOven"
        val building = Building.named("stoneOven")!!

        fun tap(origin: GridPoint) =
            GridPoint(origin.row + (building.height - 1) / 2, origin.col + (building.width - 1) / 2)
        val first = GridPoint(0, 0)
        val start = Instant.ofEpochSecond(0)
        assertEquals(listOf<GameEvent>(GameEvent.Built(first, building.cost)), engine.build(tap(first), start))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit(building.name, 1))),
            engine.build(tap(GridPoint(0, 8)), start),
        )
    }

    @Test
    fun `buildings do not change how fast crops grow`() {
        val state = GameState()
        for (index in 0 until 10) state.putBuilding(GridPoint(2 * index, 0), "barn2")
        assertEquals(1.0, GrowthConditions(state).speedMultiplier(GridPoint(24, 24)))
    }

    @Test
    fun `an item is at its limit once as many are on the map`() {
        val state = GameState()
        val barn = ShopItem.BuildingItem("barn2")
        assertFalse(GameEngine(state).hasReachedPlacementLimit(barn))
        state.putBuilding(GridPoint(0, 0), "barn2")
        assertTrue(GameEngine(state).hasReachedPlacementLimit(barn))
    }

    @Test
    fun `things without a limit are never at one`() {
        val state = GameState()
        state.putBuilding(GridPoint(0, 0), "tractor1")
        assertTrue(GameEngine(state).hasReachedPlacementLimit(ShopItem.BuildingItem("tractor1")))
        assertFalse(GameEngine(state).hasReachedPlacementLimit(ShopItem.Fence))
    }

    @Test
    fun `trunks are limited`() {
        val state = GameState()
        for (index in 0 until Decoration.TRUNK_LIMIT) {
            state.putDecoration(GridPoint(0, index), PlacedDecoration("trunk"))
        }
        assertTrue(GameEngine(state).hasReachedPlacementLimit(ShopItem.DecorationItem("trunk")))
    }
}
