package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class FruitTreeTest {
    private val planted = Instant.ofEpochSecond(30_000_000)

    /** The bottom left tile of the grass square a tree is planted on, and the top left of the square. */
    private val tile = GridPoint(10, 10)
    private val origin = GridPoint(9, 10)
    private val apple = Crop.named("apple") ?: Crop.starter

    /** The tiles a tree is kept on, the bottom row of the square it stands on. */
    private val roots get() = apple.plotTiles(origin).toSet()

    private fun engine(): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = LevelRules.xpToReach(4), logs = 0))
        engine.state.selectedCropID = apple.id
        return engine
    }

    /** Sets the tree down with its trunk on a tile and confirms it, as the placement arrows and OK would. */
    private fun plant(
        engine: GameEngine,
        at: GridPoint,
    ): List<GameEvent> {
        engine.beginPlacingFruitTree(at)
        return engine.confirmPlacement(planted)
    }

    private fun rejected(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

    @Test
    fun `a fruit tree comes after every third crop one unlock per level`() {
        val order = Crop.progression.map { if (it.isFruitTree) "tree" else "crop" }
        assertEquals(34, order.size)
        assertEquals(listOf("crop", "crop", "crop", "tree", "crop", "crop", "crop", "tree"), order.take(8))
        assertEquals(
            listOf("apple", "orange", "peach", "lemon", "banana"),
            Crop.all.filter { it.isFruitTree }.map { it.id },
        )
        assertEquals((1..34).toList(), Crop.progression.map { it.unlockLevel })
        assertEquals(34, LevelRules.maxLevel)
        assertEquals(listOf("wheat", "carrot", "potato"), Crop.all.take(3).map { it.id })
        assertEquals(4, apple.unlockLevel)
        assertEquals(29, Crop.progression.count { !it.isFruitTree })
    }

    @Test
    fun `plain crop prices are unchanged by the trees`() {
        assertEquals(0.3, Crop.starter.sellValue)
        assertEquals(0.32, Crop.named("carrot")?.seedPrice)
        assertEquals(73.19, Crop.named("blackberry")?.seedPrice)
    }

    @Test
    fun `a fruit tree is paid for once at twelve baskets worth`() {
        for (tree in Crop.all.filter { it.isFruitTree }) {
            assertEquals(
                (tree.sellValue * 12).roundedToCents,
                tree.seedPrice,
            )
        }
        assertEquals(18.72, apple.seedPrice)
    }

    @Test
    fun `fruit trees cost more the later they unlock and pay back within five days`() {
        val trees = Crop.all.filter { it.isFruitTree }
        assertEquals(trees.map { it.seedPrice }.sorted(), trees.map { it.seedPrice })
        for (tree in trees) {
            assertTrue(tree.seedPrice / tree.sellValue * tree.growDuration < 5 * 24 * 60 * 60)
            assertTrue(tree.seedPrice > tree.sellValue * 10)
        }
    }

    @Test
    fun `a fruit tree ripens in three grow cycles of the crop unlocked right after it`() {
        for (tree in Crop.all.filter { it.isFruitTree }) {
            val next = Crop.all[Crop.all.indexOf(tree) + 1]
            assertFalse(next.isFruitTree)
            assertTrue(abs(tree.growMinutes - 3 * next.growMinutes) < 0.0001)
        }
        assertTrue(abs(apple.growMinutes - 3 * 1.76) < 0.05)
    }

    @Test
    fun `a fruit tree is worth more and takes longer than the crops around it`() {
        for (tree in Crop.all.filter { it.isFruitTree }) {
            val index = Crop.all.indexOf(tree)
            val before = Crop.all[index - 1]
            val after = Crop.all[index + 1]
            assertTrue(tree.sellValue > before.sellValue && tree.sellValue > after.sellValue / 2)
            assertTrue(tree.growMinutes > before.growMinutes && tree.growMinutes > after.growMinutes)
        }
    }

    @Test
    fun `every level keeps its original unlock alongside optional additions`() {
        assertTrue((1..LevelRules.maxLevel).all { level -> Crop.progression.count { it.unlockLevel == level } == 1 })
        assertTrue(LevelRules.xpToLeave(4) > 0)
        assertTrue(
            Crop.progression
                .map { LevelRules.xpToLeave(it.unlockLevel) }
                .dropLast(1)
                .all { it > 0 },
        )
    }

    @Test
    fun `a tree cannot be planted where it would block the front of a home`() {
        val engine = engine()
        engine.place(GridPoint(8, 10), "petHouse")
        assertEquals(rejected(Rejection.BlocksAFront), plant(engine, tile))
        assertTrue(engine.state.plots.isEmpty())
        assertEquals(1_000.0, engine.state.money)
    }

    @Test
    fun `the tool never plants a fruit tree tile by tile`() {
        val engine = engine()
        val stroke = ToolStroke()
        val row = (0 until 4).map { GridPoint(10, 10 + it) }
        assertTrue(stroke.use(engine, row, planted).isEmpty())
        assertTrue(engine.tap(tile, planted).isEmpty())
        assertTrue(engine.state.plots.isEmpty())
        assertEquals(1_000.0, engine.state.money)
    }

    @Test
    fun `a ripe tree is not picked while storage is full`() {
        val engine = engine()
        plant(engine, tile)
        engine.state.storage["wheat"] = engine.storageCapacity
        val ripe = planted.advancedBy(apple.growDuration + 1)
        engine.select(HarvestTool.bucket)
        assertEquals(rejected(Rejection.StorageFull), engine.tap(tile, ripe))
        assertEquals(2, engine.state.plots.size)
    }

    @Test
    fun `a tree takes a four tile square of grass keeps its bottom row and starts fruitless`() {
        val engine = engine()
        assertEquals(listOf<GameEvent>(GameEvent.Planted(origin, apple.seedPrice)), plant(engine, tile))
        assertEquals(1_000 - apple.seedPrice, engine.state.money)
        assertEquals(roots, engine.state.plots.keys)
        assertTrue(
            engine.state.plots.values
                .all { it.cropID == "apple" && it.origin == origin },
        )
        assertEquals(1, engine.placedOnMap(ShopItem.Seed("apple")))
        assertTrue(
            Crop.all.filter { it.isFruitTree }.all { it.side == 2 } &&
                Crop.all.filter { !it.isFruitTree }.all { it.side == 1 },
        )
        assertEquals("Apple Tree", engine.name(ShopItem.Seed("apple")))
        assertEquals("Wheat Seeds", engine.name(ShopItem.Seed("wheat")))
    }

    @Test
    fun `a tree needs its bottom row to be free grass not farmland water a fence or a crop`() {
        val engine = engine()
        val next = GridPoint(10, 11)
        engine.state.farmland.add(next)
        assertEquals(rejected(Rejection.NeedGrass), plant(engine, tile))
        engine.state.farmland.remove(next)
        engine.state.water[next] = WaterStyle.medium
        assertEquals(rejected(Rejection.NeedGrass), plant(engine, tile))
        engine.state.water.remove(next)
        engine.state.fences[GridPoint(10, 10)] = "wood"
        assertEquals(rejected(Rejection.NeedGrass), plant(engine, tile))
        engine.state.fences.remove(GridPoint(10, 10))
        engine.state.plots[next] = Plot(Crop.starter.id, planted)
        assertEquals(rejected(Rejection.NeedGrass), plant(engine, tile))
        assertEquals(1, engine.state.plots.size)
        assertEquals(1_000.0, engine.state.money)
        assertEquals("A fruit tree needs free grass under its bottom row", Rejection.NeedGrass.message)
    }

    @Test
    fun `tapping any tile of a tree asks about the whole tree`() {
        val engine = engine()
        plant(engine, tile)
        assertEquals(roots, engine.cropsToDestroy(GridPoint(9, 11)).toSet())
        assertEquals(1, engine.plantCount(engine.cropsToDestroy(tile)))
        assertEquals(
            rejected(Rejection.NotFinishedGrowing("Apple Tree", apple.growDuration)),
            engine.tap(GridPoint(9, 11), planted),
        )
        assertEquals(listOf<GameEvent>(GameEvent.Destroyed(1)), engine.destroyCrops(engine.cropsToDestroy(tile)))
        assertTrue(engine.state.plots.isEmpty())
    }

    @Test
    fun `a tree goes from bare straight to ripe`() {
        assertEquals(FruitTreePhase.fruitless, FruitTreePhase.ofProgress(0.0))
        assertEquals(FruitTreePhase.fruitless, FruitTreePhase.ofProgress(0.5))
        assertEquals(FruitTreePhase.fruitless, FruitTreePhase.ofProgress(0.99))
        assertEquals(FruitTreePhase.ripe, FruitTreePhase.ofProgress(1.0))
    }

    @Test
    fun `the bucket stores one basket from any tile and the whole tree starts over fruitless`() {
        val engine = engine()
        plant(engine, tile)
        val later = planted.advancedBy(apple.growDuration + 1)
        engine.select(HarvestTool.scythe)
        assertEquals(rejected(Rejection.UseTheBucketForFruit), engine.useTool(GridPoint(9, 11), later))
        engine.select(HarvestTool.bucket)
        val events = engine.useTool(GridPoint(9, 11), later)
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(GridPoint(9, 11), "apple", LevelRules.harvestXP(apple))),
            events,
        )
        assertEquals(mapOf("apple" to 1), engine.state.storage)
        assertEquals(roots, engine.state.plots.keys)
        assertTrue(
            engine.state.plots.values
                .all { it == Plot("apple", later, origin) },
        )
        assertEquals(1_000 - apple.seedPrice, engine.state.money)
        val tooSoon = engine.useTool(tile, later.advancedBy(10.0))
        assertTrue(tooSoon.all { it is GameEvent.Rejected })
        assertEquals(mapOf("apple" to 1), engine.state.storage)
    }

    @Test
    fun `a basket has its own name and sells for the crop price`() {
        assertEquals("Apple Basket", StorageItem.CropItem(apple).name)
        assertEquals("Wheat", StorageItem.CropItem(Crop.starter).name)
        assertEquals(apple.sellValue, StorageItem.CropItem(apple).sellValue)
        assertTrue(StorageItem.CropItem(apple) in StorageItem.all)
    }

    @Test
    fun `the axe leaves fruit trees alone`() {
        val engine = engine()
        plant(engine, tile)
        engine.select(HarvestTool.axe)
        repeat(4) {
            engine.useTool(tile, planted)
            engine.liftTool()
        }
        assertEquals(2, engine.state.plots.size)
        assertTrue(engine.state.storage.isEmpty())
    }

    @Test
    fun `logs and stones sell for nothing so they cannot be sold`() {
        val engine = GameEngine()
        engine.state.storage = mutableMapOf(StorageItem.Log.id to 6, StorageItem.Stone.id to 3)
        assertTrue(Tree.logSalePrice == 0.0 && Rock.stoneSalePrice == 0.0)
        assertTrue(!StorageItem.Log.isSellable && !StorageItem.Stone.isSellable)
        assertEquals(
            listOf(StorageItem.Log, StorageItem.Stone) + Butterflies.Wings.all.map { StorageItem.Butterfly(it) },
            StorageItem.all.filter { !it.isSellable },
        )
        assertTrue(engine.sellStored(StorageItem.Log, 4).isEmpty() && engine.sellStored(StorageItem.Stone, 1).isEmpty())
        assertTrue(
            engine.state.storage == mapOf(StorageItem.Log.id to 6, StorageItem.Stone.id to 3) &&
                engine.state.money == 0.0,
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.StorageTrashed(StorageItem.Log.id, 4)),
            engine.trashStored(StorageItem.Log, 4),
        )
    }

    @Test
    fun `a tree is only charged when its placement is confirmed`() {
        val engine = engine()
        engine.beginPlacingFruitTree(tile)
        assertTrue(engine.isPlacingFruitTree && engine.isPlacing && !engine.isPlacingBuilding)
        assertEquals(origin, engine.fruitPlacement?.origin)
        assertTrue(engine.state.money == 1_000.0 && engine.state.plots.isEmpty())
        engine.cancelPlacement()
        assertTrue(!engine.isPlacing && engine.state.money == 1_000.0)
        engine.beginPlacingFruitTree(tile)
        assertEquals(listOf<GameEvent>(GameEvent.Planted(origin, apple.seedPrice)), engine.confirmPlacement(planted))
        assertFalse(engine.isPlacing)
    }

    @Test
    fun `a tree is nudged and dragged but stays on the map`() {
        val engine = engine()
        engine.beginPlacingFruitTree(tile)
        engine.nudgePlacement(1, 2)
        assertEquals(GridPoint(10, 12), engine.fruitPlacement?.origin)
        engine.movePlacement(GridPoint(80, -4))
        assertEquals(GridPoint(GameState.GRID_SIZE - 2, 0), engine.fruitPlacement?.origin)
        engine.nudgePlacement(5, -1)
        assertEquals(GridPoint(GameState.GRID_SIZE - 2, 0), engine.fruitPlacement?.origin)
    }

    @Test
    fun `a placement explains why it cannot be confirmed yet`() {
        val engine = engine()
        engine.state.money = 1.0
        assertEquals(Rejection.NeedMoney(apple.seedPrice), engine.cannotStartPlacingSelectedFruitTree)
        engine.state.money = 1_000.0
        engine.state.water[GridPoint(10, 10)] = WaterStyle.medium
        engine.beginPlacingFruitTree(tile)
        assertEquals(Rejection.NeedGrass, engine.placementRejection(planted))
        engine.movePlacement(GridPoint(20, 20))
        assertNull(engine.placementRejection(planted))
    }

    @Test
    fun `a moved tree keeps its growth and costs nothing`() {
        val engine = engine()
        plant(engine, tile)
        val money = engine.state.money
        engine.beginMovingFruitTree(GridPoint(10, 11))
        assertTrue(engine.fruitPlacement?.origin == origin && engine.fruitPlacement?.replacing != null)
        engine.movePlacement(GridPoint(20, 20))
        assertEquals(
            listOf<GameEvent>(GameEvent.Moved(GridPoint(20, 20))),
            engine.confirmPlacement(planted.advancedBy(60.0)),
        )
        assertEquals(money, engine.state.money)
        assertEquals(apple.plotTiles(GridPoint(20, 20)).toSet(), engine.state.plots.keys)
        assertTrue(
            engine.state.plots.values
                .all { it == Plot("apple", planted, GridPoint(20, 20)) },
        )
    }

    @Test
    fun `a tree may be nudged over its own ground but not over another tree`() {
        val engine = engine()
        plant(engine, tile)
        engine.beginMovingFruitTree(tile)
        engine.nudgePlacement(1, 0)
        assertNull(engine.placementRejection(planted))
        engine.cancelPlacement()
        assertEquals(roots, engine.state.plots.keys)
        plant(engine, GridPoint(20, 20))
        engine.beginMovingFruitTree(GridPoint(20, 20))
        engine.movePlacement(origin)
        assertEquals(Rejection.NeedGrass, engine.placementRejection(planted))
    }

    @Test
    fun `a fruit tree can only be picked up if there is one`() {
        val engine = engine()
        engine.state.plots[tile] = Plot(Crop.starter.id, planted)
        engine.beginMovingFruitTree(tile)
        engine.beginMovingFruitTree(GridPoint(30, 30))
        assertFalse(engine.isPlacing)
    }

    @Test
    fun `at most six of each fruit tree can stand on the map at once`() {
        val engine = engine()
        engine.state.money = 1_000_000.0
        for (index in 0 until Crop.MOST_FRUIT_TREES_OF_A_KIND) {
            plant(engine, GridPoint(3 + 3 * (index / 10), 3 + 3 * (index % 10)))
        }
        assertEquals(6, engine.placedOnMap(ShopItem.Seed(apple.id)))
        engine.beginPlacingFruitTree(GridPoint(40, 40))
        assertEquals(Rejection.BuildingLimit("Apple Tree", 6), engine.placementRejection(planted))
        engine.cancelPlacement()
        engine.state.selectedCropID = "orange"
        engine.state.xp = LevelRules.xpToReach(8)
        assertEquals(1, plant(engine, GridPoint(40, 40)).size)
    }

    @Test
    fun `moving a tree is not held up by the limit`() {
        val engine = engine()
        engine.state.money = 1_000_000.0
        for (index in 0 until Crop.MOST_FRUIT_TREES_OF_A_KIND) plant(engine, GridPoint(3 + 3 * index, 3))
        engine.beginMovingFruitTree(GridPoint(3, 3))
        assertNull(engine.placementRejection(planted))
        assertNotNull(engine.fruitPlacement)
    }
}
