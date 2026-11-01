package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Workshop
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WorkshopTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)

    private fun engine(buildingID: String): Pair<GameEngine, GridPoint> {
        val engine =
            GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = buildingID
        engine.build(spot, Instant.ofEpochSecond(1))
        engine.state.storage = mutableMapOf()
        return engine to engine.state.placedBuildings[0].origin
    }

    @Test
    fun `original recipes keep their order and cotton retains its existing conversion`() {
        assertEquals(
            listOf("grainMachine", "stoneOven", "woodworkBench", "tailorTable", "diyTable"),
            Recipe.all.take(5).map { it.buildingID },
        )
        for (recipe in Recipe.all) {
            val building = Building.named(recipe.buildingID)!!
            assertTrue(recipe in Recipe.options(building))
            assertTrue(building.fullAssetName != null)
            val valueIn = recipe.input.sellValue * recipe.inputCount
            if (recipe.id == "cottonCloth") {
                assertTrue(recipe.output.sellValue * recipe.outputCount < valueIn)
            } else {
                assertTrue(recipe.output.sellValue * recipe.outputCount > valueIn)
            }
        }
        assertNull(Recipe.of(Building.named("shed")!!))
        assertEquals(StorageItem.Bread, StorageItem.of("bread"))
        assertTrue(StorageItem.Cloth in StorageItem.all)
    }

    @Test
    fun `a batch takes its ingredients and works for the recipes time`() {
        val (engine, origin) = engine("grainMachine")
        engine.state.storage = mutableMapOf("wheat" to 25)
        assertEquals(WorkshopStatus.Idle, engine.workshopStatus(origin, now))
        assertEquals(listOf<GameEvent>(GameEvent.WorkStarted(origin)), engine.startWork(origin, now))
        assertEquals(mapOf("wheat" to 15), engine.state.storage)
        assertEquals(WorkshopStatus.Working(120.0), engine.workshopStatus(origin, now.advancedBy(60.0)))
        assertEquals(WorkshopStatus.Ready, engine.workshopStatus(origin, now.advancedBy(180.0)))
        assertEquals(Rejection.WorkInProgress, engine.startRejection(origin, now.advancedBy(60.0)))
        assertEquals(Rejection.GoodsWaiting, engine.startRejection(origin, now.advancedBy(180.0)))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.WorkInProgress)),
            engine.startWork(origin, now.advancedBy(60.0)),
        )
        assertEquals(mapOf("wheat" to 15), engine.state.storage)
    }

    @Test
    fun `too few ingredients says what is needed`() {
        val (engine, origin) = engine("tailorTable")
        engine.state.storage = mutableMapOf("wool" to 2)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedIngredients(3, "Wool"))),
            engine.startWork(origin, now),
        )
        assertEquals("Need 3 Wool in storage", Rejection.NeedIngredients(3, "Wool").message)
        assertTrue(engine.state.workshops.isEmpty())
    }

    @Test
    fun `the bucket collects finished goods from any tile of the workshop`() {
        val (engine, origin) = engine("stoneOven")
        engine.state.storage = mutableMapOf("flour" to 3)
        engine.startWork(origin, now)
        engine.select(HarvestTool.bucket)
        val tile = GridPoint(origin.row + 1, origin.col + 2)
        assertTrue(engine.useTool(tile, now.advancedBy(299.0)).isEmpty())
        val done = now.advancedBy(300.0)
        val stored = GameEvent.Stored(tile, "bread", 0)
        assertEquals(listOf<GameEvent>(stored, stored), engine.useTool(tile, done))
        assertEquals(mapOf("bread" to 2), engine.state.storage)
        assertEquals(WorkshopStatus.Idle, engine.workshopStatus(origin, done))
        assertTrue(engine.useTool(tile, done).isEmpty())
    }

    @Test
    fun `another tool says to use the bucket and a full storage stops it`() {
        val (engine, origin) = engine("woodworkBench")
        engine.state.storage = mutableMapOf("log" to 5)
        engine.startWork(origin, now)
        val done = now.advancedBy(240.0)
        engine.select(HarvestTool.scythe)
        assertTrue(engine.useTool(origin, done).isEmpty())
        assertEquals(Rejection.UseTheBucketForGoods, engine.missingHarvestToolRejection(origin, done))
        assertEquals(
            "Use the Bucket from the Harvest tool to collect what it made",
            Rejection.UseTheBucketForGoods.message,
        )
        engine.select(HarvestTool.bucket)
        assertNull(engine.missingHarvestToolRejection(origin, done))
        engine.state.storage = mutableMapOf("wheat" to engine.storageCapacity)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), engine.useTool(origin, done))
        engine.setSelling(true, HarvestTool.bucket)
        assertEquals(listOf<GameEvent>(GameEvent.Sold(origin, "planks", 15.0, 0)), engine.useTool(origin, done))
    }

    @Test
    fun `a finished workshop looks full until collected`() {
        val (engine, origin) = engine("diyTable")
        engine.state.storage = mutableMapOf("stone" to 6)
        engine.startWork(origin, now)
        assertTrue(engine.state.fullBuildings(now).isEmpty())
        assertEquals(setOf(origin), engine.state.fullBuildings(now.advancedBy(360.0)))
    }

    @Test
    fun `moving keeps the work and demolishing ends it`() {
        val (engine, origin) = engine("tailorTable")
        engine.state.storage = mutableMapOf("wool" to 3)
        engine.startWork(origin, now)
        engine.beginMoving(engine.building(origin)!!, GridPoint(30, 30))
        val target = engine.placement!!.origin
        engine.confirmPlacement(now)
        assertEquals(mapOf(target to Workshop(now)), engine.state.workshops)
        engine.demolish(engine.building(target)!!)
        assertTrue(engine.state.workshops.isEmpty())
    }

    @Test
    fun `workshops survive a save and a save from before them still loads`() {
        val (engine, origin) = engine("grainMachine")
        engine.state.storage = mutableMapOf("wheat" to 10)
        engine.startWork(origin, now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.workshops, loaded.workshops)
        assertTrue(GameStateJson.decode("""{"money": 1}""").workshops.isEmpty())
    }

    @Test
    fun `market stands do not change storage or immediate sale prices`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        val wheat = StorageItem.CropItem(com.hunter.stockfarmer.core.catalog.Crop.starter)
        for ((index, id) in listOf("marketStand", "marketStandLarge").withIndex()) {
            engine.state.putBuilding(GridPoint(20 + index * 10, 20), id)
            assertTrue(id in engine.state.buildings.values)
            engine.state.storage = mutableMapOf("wheat" to 10, "log" to 100, "stone" to 100)
            assertEquals(3.0, engine.saleValue(wheat, 10))
            assertEquals(100.0, engine.saleValue(StorageItem.Egg, 20))
            assertEquals(listOf<GameEvent>(GameEvent.StorageSold("wheat", 10, 3.0)), engine.sellStored(wheat, 10))
            val tile = GridPoint(5, 5)
            engine.state.eggs = listOf(lyingEgg(tile, 0.0, 0.0))
            engine.select(HarvestTool.bucket)
            engine.setSelling(true, HarvestTool.bucket)
            assertEquals(listOf<GameEvent>(GameEvent.Sold(tile, "egg", 5.0, 0)), engine.useTool(tile, now))
            engine.setMode(PlayMode.build)
        }
    }

    @Test
    fun `both market tiers have the same unlock level and placement limit`() {
        val small = Building.named("marketStand")!!
        val large = Building.named("marketStandLarge")!!
        assertEquals(small.unlockLevel, large.unlockLevel)
        assertEquals(listOf(1, 1), listOf(small, large).map { it.placementLimit })
    }

    @Test
    fun `only one market stand can stand`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "marketStand"
        engine.build(GridPoint(20, 20), now)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Market Stand", 1))),
            engine.build(GridPoint(30, 30), now),
        )
    }
}
