package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LevelCelebrationsTest {
    @Test fun `each event batch containing level ups queues one celebration without a flow collector`() {
        val session = GameSession(GameEngine())
        val events = listOf(GameEvent.LeveledUp(2, emptyList(), 0, 1), GameEvent.LeveledUp(3, emptyList(), 0, 2))
        assertEquals(events, session.publish(events))
        session.publish(emptyList())
        session.publish(listOf(GameEvent.Planted(GridPoint(24, 24), 1.0)))
        session.publish(listOf(GameEvent.LeveledUp(4, emptyList(), 0, 3)))
        var celebrations = 0
        session.celebrations.drainAcross { celebrations++ }
        session.celebrations.drainAcross { celebrations++ }
        assertEquals(2, celebrations)
    }

    @Test fun `building and level celebrations are independent and clearing forgets both`() {
        val session = GameSession(GameEngine())
        val building = BuildingCelebration(GridPoint(24, 24), 2, 3)
        session.celebrations.celebrate(building)
        session.publish(listOf(GameEvent.LeveledUp(2, emptyList(), 0, 1)))
        val buildings = mutableListOf<BuildingCelebration>()
        session.celebrations.drain(buildings::add)
        assertEquals(listOf(building), buildings)
        session.celebrations.clear()
        var across = 0
        session.celebrations.drainAcross { across++ }
        session.celebrations.drain(buildings::add)
        assertEquals(0, across)
        assertEquals(listOf(building), buildings)
    }
}
