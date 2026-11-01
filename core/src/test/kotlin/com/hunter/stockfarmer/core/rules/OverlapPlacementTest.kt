package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Things may be set down over the upper tiles of one another, since a picture rises above the ground it stands on:
 * only the bottom row of a building, tree, fruit tree or decoration, or the one tile of a crop or rock, is taken.
 * Only a shared top-left tile is refused, since things are stored under it.
 */
class OverlapPlacementTest {
    private val now = Instant.ofEpochSecond(61_000_000)
    private val apple = Crop.named("apple") ?: Crop.starter
    private val spot = GridPoint(20, 20)

    private fun engine(mode: PlayMode = PlayMode.plant) =
        GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = mode, logs = 1_000))

    private fun bigTree(): PlacedTree {
        val medium = TreeSpecies.pine.trees.firstOrNull { it.side == 2 } ?: Tree.starter
        return PlacedTree(medium, TreeColor.green, now)
    }

    private fun plantApple(
        engine: GameEngine,
        aroundTile: GridPoint,
    ): List<GameEvent> {
        engine.state.mode = PlayMode.plant
        engine.state.selectedCropID = apple.id
        engine.beginPlacingFruitTree(aroundTile)
        return engine.confirmPlacement(now)
    }

    @Test
    fun `a building goes over a trees upper row but not its bottom row`() {
        val engine = engine(PlayMode.build)
        engine.state.putTree(spot, bigTree())
        engine.state.selectedBuildingID = "shed"
        val over = GridPoint(19, 19)
        assertEquals(listOf<GameEvent>(GameEvent.Built(over, 2500.0)), engine.build(over, now))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.build(GridPoint(20, 19), now),
        )
        val below = GridPoint(21, 19)
        assertEquals(listOf<GameEvent>(GameEvent.Built(below, 2500.0)), engine.build(below, now))
    }

    @Test
    fun `a tree goes over a buildings upper rows but not its bottom row`() {
        val engine = engine(PlayMode.landscape)
        engine.state.putBuilding(spot, "shed")
        engine.state.selectedLandscape = LandscapeTool.tree
        engine.state.selectedTreeID = Tree.starter.id
        assertEquals(listOf<GameEvent>(GameEvent.TreePlaced(spot, 0.0)), engine.tap(spot, now))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.tap(GridPoint(21, 20), now),
        )
    }

    @Test
    fun `a fruit tree is kept on its bottom row only`() {
        val engine = engine()
        assertEquals(
            listOf<GameEvent>(GameEvent.Planted(GridPoint(9, 10), apple.seedPrice)),
            plantApple(engine, GridPoint(10, 10)),
        )
        assertEquals(setOf(GridPoint(10, 10), GridPoint(10, 11)), engine.state.plots.keys)
        assertTrue(
            engine.state.plots.values
                .all { it.origin == GridPoint(9, 10) },
        )
        assertEquals(1, engine.placedOnMap(ShopItem.Seed(apple.id)))
    }

    @Test
    fun `a fruit tree goes over another fruit trees upper row but not its bottom row`() {
        val engine = engine()
        plantApple(engine, GridPoint(10, 10))
        assertEquals(
            listOf<GameEvent>(GameEvent.Planted(GridPoint(8, 10), apple.seedPrice)),
            plantApple(engine, GridPoint(9, 10)),
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedGrass)),
            plantApple(engine, GridPoint(10, 10)),
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Planted(GridPoint(10, 10), apple.seedPrice)),
            plantApple(engine, GridPoint(11, 10)),
        )
        assertEquals(3, engine.placedOnMap(ShopItem.Seed(apple.id)))
    }

    @Test
    fun `a crop grows on a fruit trees upper tile and the tree is still the tree there`() {
        val engine = engine()
        plantApple(engine, GridPoint(10, 10))
        val upper = GridPoint(9, 11)
        engine.state.farmland.add(upper)
        engine.select(Crop.starter)
        assertEquals(listOf<GameEvent>(GameEvent.Planted(upper, 0.0)), engine.tap(upper, now))
        assertEquals(listOf(upper), engine.cropsToDestroy(upper))
        engine.state.farmland.remove(upper)
        engine.state.plots.remove(upper)
        assertEquals(
            setOf(GridPoint(10, 10), GridPoint(10, 11)),
            engine.cropsToDestroy(upper).toSet(),
        )
        assertEquals(
            listOf<GameEvent>(
                GameEvent.Rejected(Rejection.NotFinishedGrowing("Apple Tree", apple.growDuration)),
            ),
            engine.tap(upper, now),
        )
    }

    @Test
    fun `a decoration and a fence go over a buildings upper rows`() {
        val engine = engine(PlayMode.decorate)
        engine.state.putBuilding(spot, "shed")
        engine.select(Decoration.named("scarecrow")!!)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(spot, 150.0)), engine.tap(spot, now))
        engine.select(FenceColor.starter)
        val fence = GridPoint(20, 21)
        assertEquals(listOf<GameEvent>(GameEvent.Fenced(fence, FenceColor.price)), engine.tap(fence, now))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.tap(GridPoint(21, 21), now),
        )
    }

    @Test
    fun `a crop or a rock takes its one tile for good`() {
        val engine = engine()
        engine.state.farmland.add(spot)
        engine.state.plots[spot] = Plot(Crop.starter.id, now)
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "petHouse"
        assertTrue(engine.build(spot, now).any { it is GameEvent.Rejected })
        engine.state.rocks[GridPoint(25, 25)] = RockSize.small
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)),
            engine.build(GridPoint(25, 25), now),
        )
    }

    @Test
    fun `only the bottom row of what is being placed has to be free`() {
        val engine = engine()
        engine.state.water[GridPoint(9, 10)] = WaterStyle.deep
        engine.state.farmland.add(GridPoint(9, 11))
        assertEquals(
            listOf<GameEvent>(GameEvent.Planted(GridPoint(9, 10), apple.seedPrice)),
            plantApple(engine, GridPoint(10, 10)),
        )
        engine.state.water[GridPoint(20, 21)] = WaterStyle.deep
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedGrass)),
            plantApple(engine, GridPoint(20, 20)),
        )
    }

    @Test
    fun `animals still never stand on any tile under a picture`() {
        val engine = engine()
        engine.state.putTree(spot, bigTree())
        engine.state.putBuilding(GridPoint(30, 30), "shed")
        assertFalse(engine.isOpenGround(spot))
        assertFalse(engine.isOpenGround(GridPoint(21, 21)))
        assertFalse(engine.isOpenGround(GridPoint(30, 30)))
        assertFalse(engine.isOpenGround(GridPoint(31, 31)))
        assertTrue(engine.isOpenGround(GridPoint(22, 20)))
    }
}
