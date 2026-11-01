package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The top row of the map is a wall of trees that stay put and are drawn in front of anything set down behind them. */
class TopRowTreeTest {
    private val now = Instant.ofEpochSecond(14_000_000)
    private val pine = Tree.starter
    private val topTile = GridPoint(0, 10)

    private fun engine(mode: PlayMode): GameEngine {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, mode = mode))
        engine.state.putTree(topTile, PlacedTree(pine, TreeColor.green, now))
        return engine
    }

    @Test
    fun `a building goes over a tree in the top row and the tree stays`() {
        val engine = engine(PlayMode.build)
        engine.state.selectedBuildingID = "petHouse"
        assertEquals(listOf<GameEvent>(GameEvent.Built(topTile, 25.0)), engine.build(topTile, now))
        assertNotNull(engine.state.trees[topTile])
    }

    @Test
    fun `a tree in any other row still blocks a building`() {
        val engine = engine(PlayMode.build)
        val lower = GridPoint(1, 14)
        engine.state.putTree(lower, PlacedTree(pine, TreeColor.green, now))
        engine.state.selectedBuildingID = "petHouse"
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace)), engine.build(lower, now))
    }

    @Test
    fun `a fruit tree goes over a tree in the top row`() {
        val engine = engine(PlayMode.plant)
        engine.state.selectedCropID = "apple"
        engine.beginPlacingFruitTree(GridPoint(1, 10))
        assertEquals(1, engine.confirmPlacement(now).size)
        assertNotNull(engine.state.plots[GridPoint(1, 10)])
        assertNotNull(engine.state.trees[topTile])
    }

    @Test
    fun `a tree goes over a tree in the top row`() {
        val engine = engine(PlayMode.landscape)
        engine.state.selectedLandscape = LandscapeTool.tree
        engine.state.selectedTreeID = pine.id
        assertEquals(listOf<GameEvent>(GameEvent.TreePlaced(topTile, 0.0)), engine.tap(topTile, now))
    }

    @Test
    fun `fences go over a tree in the top row`() {
        val engine = engine(PlayMode.decorate)
        assertTrue(engine.tap(topTile, now).any { it is GameEvent.Fenced })
        assertNotNull(engine.state.trees[topTile])
    }

    @Test
    fun `animals never stand under a tree`() {
        val engine = engine(PlayMode.plant)
        assertFalse(engine.isOpenGround(topTile))
        assertTrue(engine.isOpenGround(GridPoint(0, 11)))
    }
}
