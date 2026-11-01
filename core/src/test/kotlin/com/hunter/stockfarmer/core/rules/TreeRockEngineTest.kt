package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** Trees and rocks in the engine and the world. */
class TreeRockEngineTest {
    private val now = Instant.ofEpochSecond(14_000_000)
    private val spot = GridPoint(10, 10)

    private fun treeEngine(
        level: Int = 19,
        money: Double = 100_000.0,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = money, xp = LevelRules.xpToReach(level), mode = PlayMode.landscape))
        engine.state.selectedLandscape = LandscapeTool.tree
        return engine
    }

    private fun placed(point: GridPoint): List<GameEvent> = listOf(GameEvent.TreePlaced(point, 0.0))

    private fun rejected(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))

    // Trees

    @Test fun `a planted tree always starts small whatever size was saved as selected`() {
        val engine = treeEngine()
        engine.state.selectedTreeID = "oak-huge"
        assertEquals("oak-small", engine.selectedTree.id)
        assertEquals(placed(GridPoint(9, 10)), engine.tap(spot, now))
        assertEquals("oak-small", engine.state.trees[GridPoint(9, 10)]?.treeID)
        assertEquals(now, engine.state.trees[GridPoint(9, 10)]?.grewAt)
        assertNotNull(engine.state.tree(GridPoint(10, 11)))
        assertNull(engine.state.tree(GridPoint(11, 10)))
        assertEquals(100_000.0, engine.state.money)
    }

    @Test fun `a tree costs nothing but needs free ground`() {
        val engine = treeEngine(level = 1, money = 0.0)
        engine.state.selectedTreeID = "pine-small"
        assertEquals(placed(spot), engine.tap(spot, now))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
        engine.state.water[GridPoint(20, 20)] = WaterStyle.medium
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(20, 20), now))
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(GridPoint(24, 24), now))
    }

    @Test fun `one entry per species and a color to plant it in`() {
        val engine = treeEngine(level = 1)
        assertEquals(listOf("Pine", "Oak", "Birch"), TreeSpecies.all.map { it.name })
        assertNull(engine.requiredLevel(ShopItem.Tree("oak")))
        assertNull(engine.requiredLevel(ShopItem.Tree("pine")))
        engine.select(TreeSpecies.oak)
        assertEquals("oak-small", engine.selectedTree.id)
        assertTrue(engine.isSelected(ShopItem.Tree("oak")) && !engine.isSelected(ShopItem.Tree("pine")))
        assertEquals(0.0, engine.price(ShopItem.Tree("oak")))
        engine.select(TreeSpecies.pine)
        assertEquals("pine-small", engine.selectedTree.id)
        assertEquals("Oak", engine.name(ShopItem.Tree("oak")))
        assertTrue(engine.state.mode == PlayMode.landscape && engine.state.selectedLandscape == LandscapeTool.tree)
    }

    @Test fun `trees block farmland water fences buildings and animals`() {
        val engine = treeEngine()
        engine.tap(spot, now)
        engine.setMode(PlayMode.landscape)
        engine.select(LandscapeTool.farmland)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        engine.setMode(PlayMode.decorate)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
        assertFalse(engine.isOpenGround(spot))
        assertTrue(engine.useTool(spot, now).isEmpty())
    }

    @Test fun `drags and previews show the whole ground`() {
        val engine = treeEngine()
        engine.state.selectedTreeID = "oak-small"
        assertEquals(
            setOf(GridPoint(9, 10), GridPoint(9, 11), spot, GridPoint(10, 11)),
            engine.toolFootprint(spot).toSet(),
        )
        engine.setMode(PlayMode.decorate)
        assertEquals(listOf(spot), engine.toolFootprint(spot))
    }

    @Test fun `trees survive a save`() {
        val state = GameState()
        state.putTree(spot, PlacedTree(Tree.all[3], TreeColor.darkGreen, Instant.now()))
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(state.trees, loaded.trees)
    }

    // New game worlds

    private val worlds: List<GameState> by lazy {
        val random = SeededRandom(2_024)
        (0 until 40).map { newGame(random, now) }
    }

    private fun worlds(count: Int) = worlds.take(count)

    @Test fun `a new game has ponds and trees but stays empty otherwise`() {
        for (world in worlds) {
            assertTrue(world.trees.size > 20)
            assertTrue(world.water.size > 10)
            assertTrue(world.money == 0.0 && world.plots.isEmpty() && world.buildings.isEmpty())
        }
    }

    @Test fun `ponds are deep water only`() {
        assertTrue(worlds.all { world -> world.water.values.all { it == WaterStyle.deep } })
    }

    @Test fun `ponds keep well away from the starting farmland`() {
        for (world in worlds) {
            for (tile in world.water.keys) {
                for (start in GameState.startingFarmland) {
                    assertTrue(max(abs(tile.row - start.row), abs(tile.col - start.col)) >= POND_CLEARANCE)
                }
            }
        }
    }

    private fun ponds(world: GameState): List<Set<GridPoint>> {
        val unseen = world.water.keys.toMutableSet()
        val ponds = mutableListOf<Set<GridPoint>>()
        while (unseen.isNotEmpty()) {
            val first = unseen.first()
            val pond = mutableSetOf(first)
            val frontier = mutableListOf(first)
            while (frontier.isNotEmpty()) {
                val point = frontier.removeLast()
                for (neighbor in point.neighbors) if (neighbor in unseen && pond.add(neighbor)) frontier += neighbor
            }
            unseen -= pond
            ponds += pond
        }
        return ponds
    }

    @Test fun `fewer ponds are slightly bigger and more ponds slightly smaller`() {
        val averageSize = mutableMapOf<Int, Double>()
        for (count in 2..4) {
            val random = SeededRandom(count.toLong())
            val sizes =
                (0 until 500)
                    .map { ponds(pondsOnly(random)) }
                    .filter { it.size == count }
                    .flatMap { pondList -> pondList.map { it.size } }
            averageSize[count] = sizes.sum().toDouble() / max(sizes.size, 1)
        }
        assertTrue((averageSize[2] ?: 0.0) > (averageSize[3] ?: 0.0))
        assertTrue((averageSize[3] ?: 0.0) > (averageSize[4] ?: 0.0))
        assertTrue((averageSize[2] ?: 0.0) < (averageSize[4] ?: 1.0) * 2)
    }

    @Test fun `nothing is generated near the starting farmland`() {
        for (world in worlds) {
            val tiles =
                world.water.keys.toList() + world.trees.flatMap { (origin, placed) -> placed.tree.footprint(origin) }
            for (tile in tiles) {
                for (start in GameState.startingFarmland) {
                    assertTrue(max(abs(tile.row - start.row), abs(tile.col - start.col)) >= STARTING_CLEARANCE)
                }
            }
        }
    }

    @Test fun `trees stay on the map and never overlap water or each other`() {
        for (world in worlds) {
            val tiles = world.trees.flatMap { (origin, placed) -> placed.tree.footprint(origin) }
            assertTrue(tiles.all { GameState.contains(it) && world.water[it] == null })
            assertEquals(tiles.size, tiles.toSet().size)
        }
    }

    @Test fun `trees grow denser toward the edge of the map`() {
        var inner = 0
        var outer = 0
        for (world in worlds) {
            for ((origin, placed) in world.trees) {
                val ring = max(abs(origin.row - 24.5), abs(origin.col - 24.5))
                if (ring < 12) {
                    inner += placed.tree.side * placed.tree.side
                } else if (ring >= 18) {
                    outer += placed.tree.side * placed.tree.side
                }
            }
        }
        // The outer band holds 1,156 tiles against the inner square's 576, yet far more than twice the trees.
        assertTrue(outer > inner * 6)
    }

    private fun treeCover(
        world: GameState,
        nearest: Int,
        furthest: Int,
    ): Double {
        val covered = world.trees.flatMap { (origin, placed) -> placed.tree.footprint(origin) }.toSet()
        val size = GameState.GRID_SIZE
        val band =
            (0 until size)
                .flatMap { row -> (0 until size).map { GridPoint(row, it) } }
                .filter { point ->
                    val depth = minOf(point.row, point.col, size - 1 - point.row, size - 1 - point.col)
                    depth in nearest until furthest && world.water[point] == null
                }
        return band.count { it in covered }.toDouble() / band.size
    }

    @Test fun `the edge of the map is forest and the middle is clear`() {
        for (world in worlds(20)) {
            assertTrue(treeCover(world, 0, 3) > 0.6)
            assertTrue(treeCover(world, 15, 25) < 0.3)
            assertTrue(treeCover(world, 8, 13) > treeCover(world, 15, 25))
            assertTrue(treeCover(world, 0, 3) > treeCover(world, 8, 13))
        }
    }

    @Test fun `worlds differ from game to game`() {
        val all = worlds(5)
        assertTrue(all.map { it.trees.keys.toSet() }.toSet().size > 1)
        assertTrue(all.map { it.water.size }.toSet().size > 1)
    }

    @Test fun `ponds come in several sizes`() {
        val sizes = worlds.flatMap { world -> ponds(world).map { it.size } }
        assertTrue((sizes.max()) > (sizes.min()) * 3)
        assertTrue(worlds(10).all { ponds(it).size in 2..4 })
    }

    @Test fun `ponds are round and filled in`() {
        for (world in worlds) {
            for (pond in ponds(world)) {
                val rows = pond.map { it.row }
                val cols = pond.map { it.col }
                val height = (rows.max() - rows.min() + 1).toDouble()
                val width = (cols.max() - cols.min() + 1).toDouble()
                assertTrue(width >= height && width / height <= 2.6)
                assertTrue(pond.size / (width * height) >= 0.55)
                // No lone tile poking out at an end, and no square corners: each tile has two pond tiles beside it.
                assertTrue(pond.all { point -> point.neighbors.count { it in pond } >= 2 })
                // No tile of grass inside the pond: every row and column of it is one unbroken run of water.
                for (row in rows.toSet()) {
                    val line = pond.filter { it.row == row }.map { it.col }
                    assertEquals(line.size, line.max() - line.min() + 1)
                }
                for (col in cols.toSet()) {
                    val line = pond.filter { it.col == col }.map { it.row }
                    assertEquals(line.size, line.max() - line.min() + 1)
                }
            }
        }
    }

    @Test fun `all the land is one connected stretch of grass around the ponds`() {
        for (world in worlds(10)) {
            val seen = mutableSetOf(GridPoint(0, 0))
            val frontier = mutableListOf(GridPoint(0, 0))
            while (frontier.isNotEmpty()) {
                val point = frontier.removeLast()
                for (neighbor in point.neighbors) {
                    if (GameState.contains(neighbor) && world.water[neighbor] == null &&
                        seen.add(neighbor)
                    ) {
                        frontier += neighbor
                    }
                }
            }
            assertEquals(GameState.GRID_SIZE * GameState.GRID_SIZE - world.water.size, seen.size)
        }
    }

    // Rocks

    private fun rockEngine(rock: RockSize = RockSize.big): GameEngine {
        val engine = GameEngine()
        engine.select(HarvestTool.pickaxe)
        engine.state.rocks[spot] = rock
        return engine
    }

    private fun pass(engine: GameEngine): List<GameEvent> {
        val events = engine.useTool(spot, now)
        engine.liftTool()
        return events
    }

    @Test fun `the last pass of the pickaxe breaks a rock and stores its stones`() {
        val engine = rockEngine()
        for (hit in 1 until RockSize.big.hitsToBreak) {
            assertEquals(listOf<GameEvent>(GameEvent.Mined(spot, hit, true)), pass(engine))
        }
        assertEquals(1, engine.state.rocks.size)
        val broken = pass(engine)
        assertEquals(GameEvent.Mined(spot, RockSize.big.hitsToBreak, true), broken.first())
        assertEquals(List(6) { GameEvent.Stored(spot, StorageItem.Stone.id, 0) }, broken.drop(1))
        assertTrue(engine.state.rocks.isEmpty())
        assertEquals(mapOf(StorageItem.Stone.id to 6), engine.state.storage)
        assertEquals(0.0, engine.state.money)
    }

    @Test fun `one stroke over a rock is one pass`() {
        val engine = rockEngine()
        val events = engine.useTool(spot, now) + engine.useTool(spot, now)
        assertEquals(listOf<GameEvent>(GameEvent.Mined(spot, 1, true), GameEvent.Mined(spot, 1, false)), events)
    }

    @Test fun `a rock stays when its stones would not fit`() {
        val engine = rockEngine(RockSize.medium)
        engine.state.storage.clear()
        engine.state.storage["wheat"] = 97
        assertEquals(rejected(Rejection.StorageFull), pass(engine))
        engine.state.storage["wheat"] = 96
        for (hit in 1 until RockSize.medium.hitsToBreak) pass(engine)
        assertEquals(5, pass(engine).size)
    }

    @Test fun `stones are always stored because they sell for nothing`() {
        val engine = rockEngine(RockSize.small)
        engine.setSelling(true, HarvestTool.pickaxe)
        for (hit in 1 until RockSize.small.hitsToBreak) pass(engine)
        assertEquals(GameEvent.Stored(spot, StorageItem.Stone.id, 0), pass(engine).last())
        assertEquals(mapOf(StorageItem.Stone.id to 2), engine.state.storage)
        assertEquals(0.0, StorageItem.Stone.sellValue)
        assertEquals(StorageItem.Stone, StorageItem.of("stone"))
    }

    @Test fun `only the pickaxe works on rocks and other tools say which`() {
        val engine = rockEngine()
        for (other in listOf(HarvestTool.scythe, HarvestTool.axe, HarvestTool.bucket)) {
            engine.select(other)
            assertTrue(engine.useTool(spot, now).isEmpty())
            assertEquals(Rejection.UseThePickaxe, engine.missingHarvestToolRejection(spot, now))
        }
        engine.select(HarvestTool.pickaxe)
        assertNull(engine.missingHarvestToolRejection(spot, now))
        assertTrue(engine.isMining && !engine.isChopping)
        assertEquals("Use the Pickaxe from the Harvest tool to break a rock", Rejection.UseThePickaxe.message)
        assertEquals(1, engine.state.rocks.size)
    }

    @Test fun `the axe leaves rocks alone and the pickaxe leaves trees alone`() {
        val engine = rockEngine()
        engine.select(HarvestTool.axe)
        assertTrue(engine.useTool(spot, now).isEmpty())
        val tree = Tree.starter
        val treeSpot = GridPoint(30, 30)
        engine.state.putTree(tree.origin(treeSpot), PlacedTree(tree, tree.species.colors[0], now))
        engine.select(HarvestTool.pickaxe)
        assertTrue(engine.useTool(treeSpot, now).isEmpty())
        assertTrue(engine.state.trees.size == 1 && engine.state.rocks.size == 1)
    }

    @Test fun `a rock blocks building fencing planting trees and walking`() {
        val engine = rockEngine()
        engine.state.money = 1_000.0
        engine.state.xp = lateGameXP
        engine.state.storage.clear()
        engine.state.storage[StorageItem.Log.id] = 90
        engine.state.storage[StorageItem.Stone.id] = 10
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "petHouse"
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.build(spot, now))
        engine.select(FenceColor.starter)
        assertEquals(rejected(Rejection.NotEnoughSpace), engine.tap(spot, now))
        assertTrue(engine.useTool(spot, now).isEmpty())
        engine.select(LandscapeTool.farmland)
        assertEquals(rejected(Rejection.StructureHere), engine.tap(spot, now))
        assertFalse(engine.isFreeForTree(spot))
        assertFalse(engine.isOpenGround(spot))
    }

    @Test fun `buildings need stones as well as logs`() {
        val engine =
            GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 30, stones = 2))
        engine.state.selectedBuildingID = "chickenCoop"
        assertEquals(Rejection.NeedStones(3), engine.cannotStartPlacingSelectedBuilding)
        assertEquals(rejected(Rejection.NeedStones(3)), engine.build(GridPoint(10, 10), now))
        assertEquals("Need 3 stones in storage", Rejection.NeedStones(3).message)
        engine.state.storage[StorageItem.Stone.id] = 5
        assertEquals(1, engine.build(GridPoint(10, 10), now).size)
        assertEquals(mapOf(StorageItem.Stone.id to 2), engine.state.storage)
    }

    @Test fun `every building needs stones and dearer ones need more`() {
        assertTrue(Building.all.all { it.stoneCost > 0 })
        val byPrice = Building.all.sortedBy { it.cost }
        assertEquals(byPrice.map { it.stoneCost }.sorted(), byPrice.map { it.stoneCost })
    }

    // Rock worlds

    private val rockWorlds: List<GameState> by lazy {
        val random = SeededRandom(77)
        (0 until 40).map { newGame(random, now) }
    }

    @Test fun `a new map has few rocks on bare ground away from the start`() {
        for (world in rockWorlds) {
            assertTrue(world.rocks.values.count { it.ore == null } in Rock.onANewMap)
            assertTrue(world.hasRocks)
            for (tile in world.rocks.keys) {
                assertTrue(world.water[tile] == null && world.tree(tile) == null)
                assertTrue(GameState.isClearOfStart(tile))
            }
        }
    }

    @Test fun `all sizes appear and small ones are commonest`() {
        val sizes = rockWorlds.flatMap { world -> world.rocks.values.filter { it.ore == null } }
        val counts = RockSize.stoneSizes.map { size -> sizes.count { it == size } }
        assertTrue(counts.all { it > 0 })
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2])
    }

    @Test fun `rocks hold about as many stones as buildings ask for`() {
        val stonesPerMap =
            rockWorlds
                .flatMap { world ->
                    world.rocks.values.filter { it.ore == null }
                }.sumOf { it.stones }
                .toDouble() /
                rockWorlds.size
        val buildingStones = Building.all.sumOf { it.stoneCost }.toDouble()
        assertTrue(abs(buildingStones - stonesPerMap) < stonesPerMap * 0.25)
    }

    @Test fun `a save from before rocks loads with none and is given some when opened`() {
        val old = GameStateJson.decode("""{"money": 5, "xp": 3}""")
        assertTrue(old.rocks.isEmpty() && !old.hasRocks)
        val engine = GameEngine(old)
        val random = SeededRandom(3)
        engine.scatterRocksIfNeeded(random)
        assertTrue(engine.state.rocks.size in Rock.onANewMap)
        assertTrue(engine.state.hasRocks)
        val before = engine.state.rocks.toMap()
        engine.scatterRocksIfNeeded(random)
        assertEquals(before, engine.state.rocks)
    }

    @Test fun `rocks survive saving`() {
        val state = GameState()
        state.rocks[GridPoint(3, 4)] = RockSize.medium
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(state.rocks, loaded.rocks)
    }
}
