package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.covers
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildingTest {
    private val now = Instant.ofEpochSecond(4_000_000)
    private val origin = GridPoint(10, 10)

    private fun engine(
        building: String = "petHouse",
        money: Double = 10_000_000.0,
    ): GameEngine {
        val state = stateWith(money = money, xp = lateGameXP, mode = PlayMode.build, logs = 1_000)
        state.selectedBuildingID = building
        return GameEngine(state)
    }

    @Test
    fun `placing charges the cost and removes farmland under it`() {
        val engine = engine()
        engine.state.farmland.add(origin)
        assertEquals(listOf<GameEvent>(GameEvent.Built(origin, 25.0)), engine.build(origin, now))
        assertEquals(10_000_000.0 - 25, engine.state.money)
        assertFalse(origin in engine.state.farmland)
        assertFalse(engine.isPlacingBuilding)
    }

    @Test
    fun `fertilizer under a new building is gone with the farmland`() {
        val engine = engine()
        engine.state.farmland.add(origin)
        engine.state.fertilized.add(origin)
        engine.build(origin, now)
        assertNotNull(engine.state.buildings[origin])
        assertTrue(engine.state.fertilized.isEmpty())
    }

    @Test
    fun `placing needs enough money and stays open to try again`() {
        val engine = engine(money = 24.0)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedMoney(25.0))), engine.build(origin, now))
        assertTrue(engine.state.buildings.isEmpty())
        assertEquals(24.0, engine.state.money)
        assertTrue(engine.isPlacingBuilding)
    }

    @Test
    fun `locked buildings cannot be placed`() {
        val engine = engine(building = "barn2")
        engine.state.xp = 0
        engine.beginPlacing(origin)
        assertFalse(engine.isPlacingBuilding)
    }

    @Test
    fun `market stand is the third unlock and shed follows at a higher price`() {
        assertEquals(listOf("petHouse", "chickenCoop", "marketStand", "shed"), Building.all.take(4).map { it.id })
        assertEquals(Building.all.map { it.unlockLevel }.sorted(), Building.all.map { it.unlockLevel })
        assertEquals(Building.all.map { it.cost }.sorted(), Building.all.map { it.cost })
        assertEquals(6, Building.marketStand.unlockLevel)
        assertEquals(1_000.0, Building.marketStand.cost)
        assertEquals(35, Building.marketStand.logCost)
        assertEquals(4, Building.marketStand.stoneCost)
        assertEquals(7, Building.named("shed")?.unlockLevel)
        assertEquals(2_500.0, Building.named("shed")?.cost)
        val engine = engine(building = "marketStand", money = 1_000.0)
        engine.state.xp = LevelRules.xpToReach(5)
        assertEquals(Rejection.NeedLevel(6), engine.cannotStartPlacingSelectedBuilding)
        engine.state.xp = LevelRules.xpToReach(6)
        assertEquals(listOf<GameEvent>(GameEvent.Built(origin, 1_000.0)), engine.build(origin, now))
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `a homes door is the building tile just behind its front tile`() {
        val coop = Building.named("chickenCoop")!!
        assertEquals(GridPoint(12, 10), coop.frontTile(GridPoint(10, 10)))
        assertEquals(GridPoint(11, 10), coop.doorTile(GridPoint(10, 10)))
        val house = Building.named("petHouse")!!
        assertEquals(GridPoint(4, 7), house.doorTile(GridPoint(4, 7)))
    }

    @Test
    fun `buildings cannot share a bottom row tile`() {
        val engine = engine(building = "shed")
        engine.build(origin, now)
        val moneyAfterFirst = engine.state.money
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.build(GridPoint(10, 11), now),
        )
        assertEquals(moneyAfterFirst, engine.state.money)
    }

    @Test
    fun `house and coop need a free tile in front`() {
        val engine = engine(building = "petHouse")
        engine.place(GridPoint(11, 10), "barn1")
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedsFrontTile)), engine.build(origin, now))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedsFrontTile)),
            engine.build(GridPoint(49, 10), now),
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Built(GridPoint(30, 30), 25.0)),
            engine.build(GridPoint(30, 30), now),
        )
    }

    @Test
    fun `nothing may be built on a tile in front of a house`() {
        val engine = engine(building = "petHouse")
        engine.build(origin, now)
        engine.state.selectedBuildingID = "barn1"
        engine.beginPlacing(GridPoint(11, 10))
        assertEquals(Rejection.BlocksAFront, engine.placementRejection(now))
    }

    @Test
    fun `a moved house may use its own old tiles in front`() {
        val engine = engine(building = "chickenCoop")
        engine.build(origin, now)
        engine.beginMoving(engine.building(origin)!!)
        engine.nudgePlacement(-1, 0)
        assertNull(engine.placementRejection(now))
    }

    @Test
    fun `moving a building starts where it stands`() {
        val engine = engine()
        engine.build(origin, now)
        engine.beginMoving(engine.building(origin)!!)
        assertEquals(origin, engine.buildingPlacement!!.origin)
    }

    @Test
    fun `buildings cannot cover crops or water`() {
        val engine = engine(building = "barn1")
        engine.state.plots[GridPoint(12, 12)] = Plot("wheat", now)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotFinishedGrowing("Wheat", 30.0))),
            engine.build(origin, now),
        )
        engine.state.plots = mutableMapOf()
        engine.state.water[GridPoint(31, 30)] = WaterStyle.deep
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.build(GridPoint(29, 29), now),
        )
    }

    @Test
    fun `a placement starts centered on the tile and stays on the map`() {
        val engine = engine(building = "barn1")
        engine.beginPlacing(GridPoint(20, 20))
        assertEquals(GridPoint(18, 19), engine.buildingPlacement!!.origin)
        engine.beginPlacing(GridPoint(49, 49))
        assertTrue(engine.buildingPlacement!!.footprint.all(GameState::contains))
    }

    @Test
    fun `nudging moves one tile and stops at the edge`() {
        val engine = engine(building = "chickenCoop")
        engine.beginPlacing(origin)
        engine.nudgePlacement(0, 1)
        assertEquals(GridPoint(10, 11), engine.buildingPlacement!!.origin)
        engine.nudgePlacement(-1, 0)
        assertEquals(GridPoint(9, 11), engine.buildingPlacement!!.origin)
        repeat(80) { engine.nudgePlacement(1, 1) }
        assertTrue(engine.buildingPlacement!!.footprint.all(GameState::contains))
    }

    @Test
    fun `dragging puts the corner where asked and stays on the map`() {
        val engine = engine(building = "chickenCoop")
        engine.beginPlacing(origin)
        engine.movePlacement(GridPoint(30, 12))
        assertEquals(GridPoint(30, 12), engine.buildingPlacement!!.origin)
        engine.movePlacement(GridPoint(200, -5))
        assertTrue(engine.buildingPlacement!!.footprint.all(GameState::contains))
    }

    @Test
    fun `the placement check says whether the spot works`() {
        val engine = engine()
        engine.state.water[GridPoint(10, 11)] = WaterStyle.deep
        engine.beginPlacing(origin)
        assertNull(engine.placementRejection(now))
        engine.nudgePlacement(0, 1)
        assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
    }

    @Test
    fun `cancelling leaves the map and money alone`() {
        val engine = engine()
        engine.beginPlacing(origin)
        engine.cancelPlacement()
        assertFalse(engine.isPlacingBuilding)
        assertTrue(engine.state.buildings.isEmpty())
        assertEquals(10_000_000.0, engine.state.money)
    }

    @Test
    fun `demolishing refunds eighty percent from any mode`() {
        val engine = engine()
        engine.build(origin, now)
        val moneyAfterBuying = engine.state.money
        engine.state.mode = PlayMode.plant
        val placed = engine.building(origin)!!
        assertEquals(listOf<GameEvent>(GameEvent.Demolished(origin, 20.0)), engine.demolish(placed))
        assertTrue(engine.state.buildings.isEmpty())
        assertEquals(moneyAfterBuying + 20, engine.state.money)
    }

    @Test
    fun `destroying also returns eighty percent of each material`() {
        val engine = engine(building = "barn1")
        val barn = Building.named("barn1")!!
        engine.state.storage = mutableMapOf(StorageItem.Log.id to barn.logCost, StorageItem.Stone.id to barn.stoneCost)
        engine.build(origin, now)
        val placed = engine.building(origin)!!
        val logs = Math.round(barn.logCost * 0.8).toInt()
        val stones = Math.round(barn.stoneCost * 0.8).toInt()
        assertEquals(logs to stones, engine.materialRefund(barn))
        assertTrue(logs > 0 && stones > 0)
        engine.state.storage = mutableMapOf()
        engine.demolish(placed)
        assertEquals(logs, engine.state.storage[StorageItem.Log.id])
        assertEquals(stones, engine.state.storage[StorageItem.Stone.id])
    }

    @Test
    fun `moving keeps the money and puts the building elsewhere`() {
        val engine = engine(building = "chickenCoop")
        engine.build(origin, now)
        val money = engine.state.money
        val placed = engine.building(origin)!!
        engine.beginMoving(placed, GridPoint(20, 20))
        val target = engine.buildingPlacement!!.origin
        assertEquals(listOf<GameEvent>(GameEvent.Moved(target)), engine.confirmPlacement(now))
        assertEquals(1, engine.state.buildings.size)
        assertNull(engine.state.buildings[origin])
        assertEquals("chickenCoop", engine.state.buildings[target])
        assertEquals(money, engine.state.money)
        assertFalse(engine.isPlacingBuilding)
    }

    @Test
    fun `a moving building may overlap its own old tiles`() {
        val engine = engine(building = "chickenCoop")
        engine.build(origin, now)
        engine.beginMoving(engine.building(origin)!!, origin)
        engine.nudgePlacement(1, 0)
        assertNull(engine.placementRejection(now))
    }

    @Test
    fun `a moving building still cannot overlap another one`() {
        val engine = engine(building = "chickenCoop")
        engine.build(origin, now)
        engine.build(GridPoint(20, 20), now)
        engine.beginMoving(engine.building(origin)!!, GridPoint(20, 20))
        assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
    }

    @Test
    fun `cancelling a move leaves the building where it was`() {
        val engine = engine()
        engine.build(origin, now)
        engine.beginMoving(engine.building(origin)!!, GridPoint(30, 30))
        engine.cancelPlacement()
        assertEquals("petHouse", engine.state.buildings[origin])
    }

    @Test
    fun `a placement ends when another mode is chosen`() {
        val engine = engine()
        engine.beginPlacing(origin)
        engine.setMode(PlayMode.plant)
        assertFalse(engine.isPlacingBuilding)
        engine.setMode(PlayMode.build)
        assertFalse(engine.isPlacingBuilding)
    }

    @Test
    fun `any tile of a building finds it`() {
        val engine = engine(building = "chickenCoop")
        engine.build(origin, now)
        assertNotNull(engine.building(GridPoint(origin.row + 1, origin.col)))
        assertNull(engine.building(GridPoint(origin.row + 2, origin.col)))
    }

    @Test
    fun `buildings are renamed without numbers`() {
        assertEquals(
            listOf(
                "Pet House",
                "Chicken Coop",
                "Market Stand",
                "Shed",
                "Rabbit Hutch",
                "Grain Machine",
                "Sheep Barn",
                "Silo",
                "House",
                "Goat Barn",
                "Tractor",
                "Stone Oven",
                "Pig Barn",
                "Stable",
                "Woodwork Bench",
                "Tailor Table",
                "DIY Crafting Table",
                "Luxury Barn",
            ),
            Building.all.take(18).map { it.name },
        )
    }

    @Test
    fun `footprint covers width by height`() {
        val placed = PlacedBuilding(origin, Building.named("rabbitHutch")!!)
        assertEquals(6, placed.footprint.size)
        assertTrue(placed.covers(GridPoint(11, 12)))
        assertFalse(placed.covers(GridPoint(12, 10)))
    }

    @Test
    fun `starting a building needs the money and the level`() {
        assertNull(engine(money = 10_000_000.0).cannotStartPlacingSelectedBuilding)
        assertEquals(Rejection.NeedMoney(25.0), engine(money = 24.0).cannotStartPlacingSelectedBuilding)
        val locked = engine(building = "barn2")
        locked.state.xp = 0
        assertEquals(
            Rejection.NeedLevel(Building.named("barn2")!!.unlockLevel),
            locked.cannotStartPlacingSelectedBuilding,
        )
    }
}

class FirstBuildingCelebrationTest {
    private val now = Instant.ofEpochSecond(0)

    private fun engine(building: String): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = building
        return engine
    }

    @Test
    fun `the first of a kind is celebrated but the next one is not`() {
        val engine = engine("petHouse")
        engine.beginPlacing(GridPoint(10, 10))
        assertTrue(engine.isPlacingFirstOfItsKind)
        engine.confirmPlacement(now)
        engine.beginPlacing(GridPoint(10, 20))
        assertFalse(engine.isPlacingFirstOfItsKind)
    }
}
