package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HarvesterTest {
    private val planted = Instant.ofEpochSecond(7_000_000)
    private val ready = planted.advancedBy(30.0)
    private val origin = GridPoint(24, 24)

    /** An engine whose four starting farmland tiles all hold wheat planted at [planted]. */
    private fun plantedField(money: Double = 0.0): GameEngine {
        val engine = GameEngine(stateWith(money = money))
        for (point in GameState.startingFarmland) engine.tap(point, planted)
        return engine
    }

    @Test
    fun `the scythe is the tool held at the start of a new game`() {
        assertEquals(HarvestTool.scythe, GameEngine().state.harvestTool)
        assertEquals(
            listOf("Scythe", "Axe", "Pickaxe", "Bucket", "Fishing Rod"),
            HarvestTool.entries.map { it.displayName },
        )
    }

    @Test
    fun `passing over a fully grown crop harvests it and pays XP`() {
        val engine = plantedField()
        engine.setMode(PlayMode.harvest)
        val xp = engine.state.xp
        assertEquals(listOf<GameEvent>(GameEvent.Stored(origin, Crop.starter.id, 2)), engine.useTool(origin, ready))
        assertTrue(origin !in engine.state.plots)
        assertEquals(3, engine.state.plots.size)
        assertEquals(xp + 2, engine.state.xp)
    }

    @Test
    fun `passing over growing crops or bare ground does nothing`() {
        val engine = plantedField()
        engine.setMode(PlayMode.harvest)
        val growing = engine.useTool(origin, planted.advancedBy(10.0))
        val rejection = (growing.first() as GameEvent.Rejected).rejection
        assertTrue(growing.size == 1 && rejection.isTileSpecific)
        assertTrue(engine.useTool(GridPoint(40, 40), ready).isEmpty())
        assertEquals(4, engine.state.plots.size)
    }

    @Test
    fun `tapping the map with the harvester never plants`() {
        val engine = GameEngine()
        engine.setMode(PlayMode.harvest)
        assertTrue(engine.tap(origin, planted).isEmpty())
        assertTrue(engine.state.plots.isEmpty())
        assertEquals(0.0, engine.state.money)
    }
}

class HarvesterEggTest {
    private val now = Instant.ofEpochSecond(9_500_000)
    private val spot = GridPoint(22, 20)

    private fun engine(): GameEngine {
        val engine = GameEngine()
        engine.select(HarvestTool.bucket)
        engine.place(GridPoint(20, 20), "chickenCoop")
        val chicken =
            Chicken.hatched(
                0,
                ChickenVariant.all[0],
                Chicken.DEFAULT_NAME,
                spot,
                now.advancedBy(-Chicken.GROW_DURATION),
            )
        engine.state.coops[GridPoint(20, 20)] = Coop(chickens = listOf(chicken), nextChickAt = now)
        engine.state.eggs =
            listOf(lyingEgg(spot, 0.1, 0.1), lyingEgg(spot, -0.1, 0.0), lyingEgg(GridPoint(30, 30), 0.0, 0.0))
        return engine
    }

    @Test
    fun `the bucket collects the eggs on a tile`() {
        val engine = engine()
        val egg = StorageItem.Egg.id
        val xp = LevelRules.produceXP(StorageItem.Egg, engine.state.level)
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(spot, egg, xp), GameEvent.Stored(spot, egg, xp)),
            engine.useTool(spot, now),
        )
        assertEquals(listOf(GridPoint(30, 30)), engine.state.eggs.map { it.point })
        assertEquals(mapOf(egg to 2), engine.state.storage)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `the scythe and axe leave eggs alone`() {
        val engine = engine()
        for (tool in listOf(HarvestTool.scythe, HarvestTool.axe)) {
            engine.select(tool)
            engine.useTool(spot, now)
        }
        assertEquals(3, engine.state.eggs.size)
    }

    @Test
    fun `the bucket leaves grown chickens alone`() {
        val engine = engine()
        engine.state.eggs = emptyList()
        assertTrue(engine.useTool(spot, now).isEmpty())
        assertEquals(
            1,
            engine.state.coops[GridPoint(20, 20)]
                ?.chickens
                ?.size,
        )
    }

    @Test
    fun `other tools do not collect eggs`() {
        val engine = engine()
        engine.setMode(PlayMode.plant)
        engine.useTool(spot, now)
        assertEquals(3, engine.state.eggs.size)
    }
}

class AxeTest {
    private val now = Instant.ofEpochSecond(15_000_000)
    private val spot = GridPoint(10, 10)
    private val elsewhere = GridPoint(20, 20)

    /** Where the huge oak's ground begins when its trunk is on [spot]. */
    private val treeOrigin = GridPoint(8, 9)

    private fun engine(tree: String = "oak-huge"): GameEngine {
        val engine = GameEngine()
        engine.select(HarvestTool.axe)
        val kind = Tree.named(tree) ?: Tree.starter
        engine.state.putTree(kind.origin(spot), PlacedTree(kind, kind.species.colors[0], Instant.now()))
        return engine
    }

    /** One go of the axe over a tile, then the finger lifted. */
    private fun pass(
        engine: GameEngine,
        tile: GridPoint,
    ): List<GameEvent> {
        val events = engine.useTool(tile, now)
        engine.liftTool()
        return events
    }

    private fun chopped(
        tile: GridPoint,
        origin: GridPoint,
        hits: Int,
        isNewHit: Boolean = true,
    ) = GameEvent.Chopped(tile, origin, hits, isNewHit)

    @Test
    fun `bigger trees take more passes to fell`() {
        assertEquals(3, Tree.named("pine-small")?.hitsToFell)
        assertEquals(4, Tree.named("pine-medium")?.hitsToFell)
        assertEquals(5, Tree.named("pine-big")?.hitsToFell)
        assertEquals(6, Tree.named("oak-huge")?.hitsToFell)
    }

    @Test
    fun `the last pass over the same tree fells it and stores its logs`() {
        val engine = engine()
        for (hit in 1 until 6) assertEquals(listOf<GameEvent>(chopped(spot, treeOrigin, hit)), pass(engine, spot))
        assertEquals(1, engine.state.trees.size)
        val felled = pass(engine, spot)
        assertEquals(chopped(spot, treeOrigin, 6), felled.first())
        assertEquals(List(6) { GameEvent.Stored(spot, StorageItem.Log.id, 0) }, felled.drop(1))
        assertTrue(engine.state.trees.isEmpty())
        assertEquals(mapOf(StorageItem.Log.id to 6), engine.state.storage)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `one drag across many tiles of a tree is one pass`() {
        val engine = engine()
        val tiles = listOf(GridPoint(9, 9), GridPoint(9, 10), GridPoint(10, 10))
        val events = tiles.flatMap { engine.useTool(it, now) }
        assertTrue(chopped(tiles[0], treeOrigin, 1) in events)
        assertEquals(1, events.count { it is GameEvent.Chopped && it.isNewHit })
        assertEquals(1, engine.state.trees.size)
    }

    @Test
    fun `leaving the tree and coming back without lifting is a new pass`() {
        val engine = engine()
        engine.useTool(spot, now)
        engine.useTool(elsewhere, now)
        assertEquals(listOf<GameEvent>(chopped(spot, treeOrigin, 2)), engine.useTool(spot, now))
    }

    @Test
    fun `going over another tree drops the first ones progress`() {
        val engine = engine()
        val other = GridPoint(30, 30)
        val kind = Tree.starter
        engine.state.putTree(kind.origin(other), PlacedTree(kind, kind.species.colors[0], Instant.now()))
        pass(engine, spot)
        pass(engine, spot)
        assertEquals(listOf<GameEvent>(chopped(other, other, 1)), pass(engine, other))
        assertEquals(listOf<GameEvent>(chopped(spot, treeOrigin, 1)), pass(engine, spot))
        assertEquals(2, engine.state.trees.size)
    }

    @Test
    fun `bigger trees give more logs`() {
        assertTrue(Tree.all.filter { it.size.rawValue == "small" }.all { it.logs == 3 })
        assertTrue(Tree.all.filter { it.size.rawValue == "huge" }.all { it.logs == 6 })
        assertTrue(Tree.all.all { it.logs == it.hitsToFell })
    }

    @Test
    fun `a tree stays when its logs would not fit`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("wheat" to 95)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), pass(engine, spot))
        engine.state.storage = mutableMapOf("wheat" to 94)
        repeat(5) { pass(engine, spot) }
        assertEquals(7, pass(engine, spot).size)
    }

    @Test
    fun `the axe ignores bare ground and other tools ignore trees`() {
        val engine = engine()
        assertTrue(engine.useTool(elsewhere, now).isEmpty())
        engine.select(HarvestTool.scythe)
        assertTrue(engine.useTool(spot, now).isEmpty())
        engine.select(HarvestTool.bucket)
        assertTrue(engine.useTool(spot, now).isEmpty())
        assertEquals(1, engine.state.trees.size)
    }
}

class BuildingLogTest {
    private val now = Instant.ofEpochSecond(15_000_000)

    private fun engine(
        logs: Int,
        stones: Int = 3,
    ) = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = PlayMode.build, logs = logs, stones = stones))

    @Test
    fun `every building needs logs`() {
        assertTrue(Building.all.all { it.logCost > 0 })
        assertEquals(Building.all.map { it.logCost }.sorted(), Building.all.map { it.logCost })
    }

    @Test
    fun `building consumes the logs from storage`() {
        val engine = engine(logs = 32)
        engine.state.selectedBuildingID = "chickenCoop"
        assertEquals(1, engine.build(GridPoint(10, 10), now).size)
        assertEquals(mapOf(StorageItem.Log.id to 2), engine.state.storage)
    }

    @Test
    fun `not enough logs stops a building`() {
        val engine = engine(logs = 29)
        engine.state.selectedBuildingID = "chickenCoop"
        assertEquals(Rejection.NeedLogs(30), engine.cannotStartPlacingSelectedBuilding)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedLogs(30))),
            engine.build(GridPoint(10, 10), now),
        )
        assertTrue(engine.state.buildings.isEmpty())
        assertEquals(mapOf(StorageItem.Log.id to 29, StorageItem.Stone.id to 3), engine.state.storage)
        assertEquals("Need 30 logs in storage", Rejection.NeedLogs(30).message)
    }

    @Test
    fun `moving a building costs no logs`() {
        val engine = engine(logs = 30)
        engine.state.selectedBuildingID = "chickenCoop"
        engine.build(GridPoint(10, 10), now)
        engine.beginMoving(engine.building(GridPoint(10, 10))!!, GridPoint(30, 30))
        assertEquals(1, engine.confirmPlacement(now).size)
        assertTrue(engine.state.storage.isEmpty())
    }
}

class BuildingLogBudgetTest {
    @Test
    fun `a pet house takes fifteen logs and dearer buildings take more`() {
        assertEquals(15, Building.named("petHouse")?.logCost)
        val byPrice = Building.all.sortedBy { it.cost }
        assertEquals(byPrice.map { it.logCost }.sorted(), byPrice.map { it.logCost })
        assertEquals(byPrice.size, byPrice.map { it.logCost }.toSet().size)
    }

    /** Every pass of the axe gives a log, so a map holds more logs than the buildings ask for between them. */
    @Test
    fun `a new map holds more logs than building everything takes`() {
        val maps = List(40) { newGame(SystemRandomSource) }
        val logsPerMap = maps.flatMap { it.trees.values }.sumOf { it.tree.logs }.toDouble() / maps.size
        assertTrue(logsPerMap > Building.all.sumOf { it.logCost })
    }
}
