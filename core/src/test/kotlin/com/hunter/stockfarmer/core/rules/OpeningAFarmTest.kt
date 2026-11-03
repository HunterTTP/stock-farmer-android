package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class OpeningAFarmTest {
    @Test
    fun `nothing under way on the last farm carries over to the next`() {
        val engine = GameEngine(stateWith(money = 5.0))
        engine.placement = BuildingPlacement(Building.starter, GridPoint(10, 10), null)
        engine.chopPass = ChopPass(GridPoint(4, 4), 2)
        engine.storagePlacementQuietUntil = Instant.ofEpochSecond(40_000_000)

        val next = GameState()
        engine.open(next)

        assertSame(next, engine.state)
        assertNull(engine.placement)
        assertNull(engine.fruitPlacement)
        assertNull(engine.objectPlacement)
        assertNull(engine.chopPass)
        assertNull(engine.storagePlacementQuietUntil)
        assertEquals(0.0, engine.state.money)
    }
}
