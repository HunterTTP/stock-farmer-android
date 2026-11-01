package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TractorCargoTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)
    private val tile = GridPoint(10, 15)
    private val wheatXP get() = LevelRules.harvestXP(Crop.starter)
    private val wheat = StorageItem.CropItem(Crop.starter)

    private fun engine(): GameEngine {
        val engine = GameEngine()
        engine.state.putBuilding(home, "tractor1")
        engine.state.plots[tile] = Plot("wheat", now.advancedBy(-100.0))
        return engine
    }

    private fun finish(engine: GameEngine) {
        var steps = 0
        while (engine.state.tractorRun != null && steps++ < 200) {
            engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
        }
        assertNull(engine.state.tractorRun)
    }

    @Test
    fun `collecting uses cargo even when farm storage is full and does not sell anything`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("carrot" to 100)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        finish(engine)
        assertNull(engine.state.plots[tile])
        assertEquals(mapOf("wheat" to 1), engine.state.tractorCargo[home])
        assertTrue(engine.state.storage == mapOf("carrot" to 100) && engine.state.money == 0.0)
        assertTrue(engine.state.salesToday.isEmpty() && engine.state.xp == 0)
    }

    @Test
    fun `only unloading adds to storage or pays money or earns XP`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        finish(engine)
        assertTrue(engine.state.storage.isEmpty() && engine.state.money == 0.0 && engine.state.xp == 0)
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoSold("wheat", 1, Crop.starter.sellValue, null, wheatXP)),
            engine.sellTractorCargo("wheat", home),
        )
        assertTrue(engine.state.money == Crop.starter.sellValue && engine.state.salesToday["wheat"]?.quantity == 1)
        assertTrue(engine.state.tractorCargo[home] == null && engine.state.xp == wheatXP)
        assertTrue(engine.sellTractorCargo("wheat", home).isEmpty())
    }

    @Test
    fun `a new trip waits until the load is sold or stored`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("wheat" to 3)
        val before = engine.state.copy()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.TractorHoldsCrop("Wheat"))),
            engine.startTractorHarvest(home, "wheat", now),
        )
        assertTrue(sameState(before, engine.state))
        engine.storeTractorCargo("wheat", home)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
    }

    @Test
    fun `the load can be unloaded while the tractor is still driving`() {
        val engine = engine()
        val second = GridPoint(10, 20)
        engine.state.plots[second] = Plot("wheat", now.advancedBy(-100.0))
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        var steps = 0
        while (engine.state.tractorCargo[home] == null && steps++ < 200) {
            engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
        }
        assertTrue(engine.state.tractorRun != null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoStored("wheat", 1, null, wheatXP)),
            engine.storeTractorCargo("wheat", home),
        )
        assertTrue(engine.state.tractorCargo[home] == null && engine.state.storage["wheat"] == 1)
        finish(engine)
        assertTrue(engine.state.tractorCargo[home] == mapOf("wheat" to 1) && engine.state.plots[second] == null)
    }

    @Test
    fun `the scythe unloads the tractor parked or driving by storing or selling`() {
        for (selling in listOf(false, true)) {
            val engine = engine()
            engine.state.tractorCargo[home] = mapOf("wheat" to 3)
            engine.select(HarvestTool.scythe)
            engine.setSelling(selling, HarvestTool.scythe)
            val spot = GridPoint(home.row, home.col + 1)
            val events = engine.useTool(spot, now)
            assertTrue(!events.containsRejection)
            val xp = 3 * wheatXP
            val expected: GameEvent =
                if (selling) {
                    GameEvent.TractorCargoSold("wheat", 3, engine.saleValue(wheat, 3), spot, xp)
                } else {
                    GameEvent.TractorCargoStored("wheat", 3, spot, xp)
                }
            assertEquals(listOf(expected), events)
            assertNull(engine.state.tractorCargo[home])
            if (selling) {
                assertEquals(
                    engine.saleValue(wheat, 3),
                    engine.state.money,
                )
            } else {
                assertEquals(3, engine.state.storage["wheat"])
            }

            engine.state.tractorCargo.remove(home)
            assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
            var steps = 0
            while (engine.state.tractorCargo[home] == null && steps++ < 200) {
                engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
            }
            val drivingTile = engine.state.tractorRun?.let { TractorRules.footprint(it.position, it.facing)[0] }
            engine.useTool(drivingTile ?: home, now)
            assertNull(engine.state.tractorCargo[home])
        }
    }

    @Test
    fun `the bucket and other tools leave the load alone and say to use the scythe`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("wheat" to 3)
        for (tool in listOf(HarvestTool.bucket, HarvestTool.axe, HarvestTool.pickaxe)) {
            engine.select(tool)
            val cargo = engine.state.tractorCargo.toMap()
            val storage = engine.state.storage.toMap()
            engine.useTool(home, now)
            assertTrue(engine.state.tractorCargo == cargo && engine.state.storage == storage)
            assertEquals(Rejection.UseTheScythe, engine.missingHarvestToolRejection(home, now))
        }
        engine.select(HarvestTool.scythe)
        assertNull(engine.missingHarvestToolRejection(home, now))
    }

    @Test
    fun `the scythe on an empty tractor does nothing`() {
        val engine = engine()
        engine.select(HarvestTool.scythe)
        val before = engine.state.copy()
        engine.useTool(home, now)
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `unloading from the menu carries no tile so it does not fly`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("wheat" to 2)
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoStored("wheat", 2, null, 2 * wheatXP)),
            engine.storeTractorCargo("wheat", home),
        )
    }

    @Test
    fun `storing moves only what fits and keeps the remainder on the tractor`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("carrot" to 99)
        engine.state.tractorCargo[home] = mapOf("wheat" to 3)
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoStored("wheat", 1, null, wheatXP)),
            engine.storeTractorCargo("wheat", home),
        )
        assertEquals(mapOf("carrot" to 99, "wheat" to 1), engine.state.storage)
        assertEquals(mapOf("wheat" to 2), engine.state.tractorCargo[home])
        val full = engine.state.copy()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)),
            engine.storeTractorCargo("wheat", home),
        )
        assertTrue(sameState(full, engine.state))
        engine.trashStored(StorageItem.CropItem(Crop.named("carrot")!!), 99)
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoStored("wheat", 2, null, 2 * wheatXP)),
            engine.storeTractorCargo("wheat", home),
        )
        assertTrue(engine.state.tractorCargo[home] == null && engine.state.storage == mapOf("wheat" to 3))
    }

    @Test
    fun `overview lists the crops that pay most for each crop first`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("carrot" to 3, "wheat" to 2, "cauliflower" to 1)
        val rows = engine.tractorCropOverview(home, now)
        val rewards = rows.map { engine.saleValue(StorageItem.CropItem(it.crop), 1) }
        assertEquals(listOf("cauliflower", "carrot", "wheat"), rows.map { it.crop.id })
        assertEquals(rewards.sortedDescending(), rewards)
    }

    @Test
    fun `overview includes cargo after its last plant is gone and orders by reward`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("carrot" to 3, "wheat" to 2)
        val rows = engine.tractorCropOverview(home, now)
        assertEquals(listOf("carrot", "wheat"), rows.map { it.crop.id })
        assertEquals(listOf(3, 2), rows.map { it.gathered })
        assertEquals(listOf(0, 1), rows.map { it.plantedCount })
        assertTrue(rows[0].remaining == null && rows[1].remaining == 0.0)
        engine.state.plots = mutableMapOf()
        assertEquals(listOf("carrot", "wheat"), engine.tractorCropOverview(home, now).map { it.crop.id })
    }

    @Test
    fun `cargo survives saving and moves with its tractor`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("wheat" to 7)
        assertTrue(sameState(GameStateJson.decode(GameStateJson.encode(engine.state)), engine.state))
        val old = GameStateJson.decode("""{"money":120}""")
        assertTrue(old.tractorCargo.isEmpty() && old.money == 120.0)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.TractorCargoWaiting)),
            engine.demolish(engine.building(home)!!),
        )
        engine.beginMoving(engine.building(home)!!)
        val destination = GridPoint(30, 30)
        engine.movePlacement(destination)
        assertTrue(!engine.confirmPlacement(now).containsRejection)
        assertTrue(engine.state.tractorCargo[home] == null)
        assertNotNull(engine.state.tractorCargo[destination])
        assertEquals(mapOf("wheat" to 7), engine.state.tractorCargo[destination])
    }
}
