package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.StockMarketRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildingUpgradeStateTest {
    private val now = Instant.ofEpochSecond(30_000_000)

    @Test
    fun `building upgrades include market stand, animal homes, and house with correct kinds`() {
        val engine = GameEngine(GameState().apply { money = 100_000.0 })
        val standOrigin = GridPoint(5, 5)
        val coopOrigin = GridPoint(10, 10)
        val houseOrigin = GridPoint(15, 15)

        engine.state.putBuilding(standOrigin, "marketStand", null)
        engine.state.putBuilding(coopOrigin, "chickenCoop", null)
        engine.state.putBuilding(houseOrigin, "house1", null)

        val upgrades = BuildingUpgradeState.all(engine, now)
        assertEquals(3, upgrades.size)

        val marketUpgrade = upgrades.first { it.kind == BuildingUpgradeKind.MARKET }
        assertEquals(standOrigin, marketUpgrade.origin)
        assertTrue(marketUpgrade.market)
        assertFalse(marketUpgrade.house)
        assertFalse(marketUpgrade.home)

        val homeUpgrade = upgrades.first { it.kind == BuildingUpgradeKind.HOME }
        assertEquals(coopOrigin, homeUpgrade.origin)
        assertTrue(homeUpgrade.home)
        assertFalse(homeUpgrade.market)
        assertFalse(homeUpgrade.house)

        val houseUpgrade = upgrades.first { it.kind == BuildingUpgradeKind.HOUSE }
        assertEquals(houseOrigin, houseUpgrade.origin)
        assertTrue(houseUpgrade.house)
        assertFalse(houseUpgrade.market)
        assertFalse(houseUpgrade.home)
        assertEquals("Luxury House", houseUpgrade.upgrade.title)
    }

    @Test
    fun `upgraded buildings are omitted from building upgrade states`() {
        val engine = GameEngine(GameState().apply { money = 100_000.0 })
        engine.state.putBuilding(GridPoint(5, 5), "marketStandLarge", null)
        engine.state.putBuilding(GridPoint(15, 15), "house2", null)

        val upgrades = BuildingUpgradeState.all(engine, now)
        assertTrue(upgrades.none { it.kind == BuildingUpgradeKind.MARKET })
        assertTrue(upgrades.none { it.kind == BuildingUpgradeKind.HOUSE })
    }
}
