package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MarketStandColorTest {
    private val now = Instant.ofEpochSecond(40_000_000)

    @Test
    fun `every stand color has a matching paid upgrade`() {
        val small = Building.marketStand
        val large = Building.named("marketStandLarge")!!
        assertEquals(listOf("yellow", "blue", "green", "pink"), small.styles.map { it.id })
        assertEquals(small.styles.map { it.id }, large.styles.map { it.id })
        assertEquals("market_stand_small", small.assetName)
        assertEquals("market_stand_big", large.assetName)
        assertTrue(Building.all.none { it.id == large.id })
    }

    @Test
    fun `selected color survives placement upgrade move and save`() {
        val blue = Building.marketStand.styles.first { it.id == "blue" }
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 1_000))
        engine.select(blue, Building.marketStand)
        assertEquals("blue", engine.selectedBuilding.styleID)
        engine.build(GridPoint(15, 15), now)
        val origin =
            engine.state.placedBuildings
                .first()
                .origin
        assertEquals("blue", engine.state.buildingStyles[origin])
        engine.state.completedMarketQuests = 10
        val random = SeededRandom(1)
        engine.prepareQuests(origin, random)
        val quests = engine.quests(origin)
        assertEquals(
            listOf<GameEvent>(GameEvent.Built(origin, 10_000.0)),
            engine.upgradeMarketStand(origin, now, random),
        )
        val upgraded = engine.building(origin)!!
        assertEquals("marketStandLarge", upgraded.building.id)
        assertEquals("blue", upgraded.building.styleID)
        assertEquals("market_stand_blue_big", upgraded.building.assetName)
        assertEquals(quests, engine.quests(origin).take(quests.size))
        engine.beginMoving(upgraded)
        engine.movePlacement(GridPoint(30, 30))
        engine.confirmPlacement(now)
        val saved = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals("blue", saved.buildingStyles[GridPoint(30, 30)])
        assertEquals("blue", saved.selectedBuildingStyleIDs[Building.marketStand.id])
    }

    @Test
    fun `reopening a normal stand preserves its color`() {
        val origin = GridPoint(15, 15)
        val state = GameState()
        state.putBuilding(origin, "marketStand", "blue")
        val engine = GameEngine(state)
        engine.reopen(now, SeededRandom(72))
        assertEquals("blue", engine.state.buildingStyles[origin])
        assertEquals("market_stand_blue_small", engine.building(origin)?.building?.assetName)
    }
}
