package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.covers
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
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

/** What stands on a tile is answered from an index kept beside `buildings` and `trees`; these check it never drifts. */
class OccupancyIndexTest {
    private val now = Instant.ofEpochSecond(15_000_000)
    private val spot = GridPoint(30, 30)

    private fun assertIndexMatchesWhatStands(state: GameState) {
        val placed =
            state.buildings.mapNotNull { (origin, id) -> Building.named(id)?.let { PlacedBuilding(origin, it) } }
        val fronts = placed.filter { it.building.needsFrontTile }.map { it.building.frontTile(it.origin) }.toSet()
        assertEquals(fronts, state.homeFrontTiles)
        assertEquals(state.buildings.keys, state.placedBuildings.map { it.origin }.toSet())
        for (row in 0 until GameState.GRID_SIZE) {
            for (col in 0 until GameState.GRID_SIZE) {
                val tile = GridPoint(row, col)
                assertEquals(placed.firstOrNull { it.covers(tile) }?.origin, state.building(tile)?.origin)
                val tree =
                    state.trees.entries
                        .firstOrNull { tile in it.value.tree.footprint(it.key) }
                        ?.key
                assertEquals(tree, state.tree(tile)?.first)
            }
        }
    }

    @Test
    fun `buildings are found on every tile they cover after being built moved and demolished`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP))
        engine.select(Building.named("chickenCoop")!!)
        engine.build(spot, now)
        assertIndexMatchesWhatStands(engine.state)
        assertNotNull(engine.state.building(spot))

        val placed = engine.building(spot)!!
        engine.beginMoving(placed, GridPoint(10, 10))
        engine.confirmPlacement(now)
        assertIndexMatchesWhatStands(engine.state)
        assertNull(engine.state.building(spot))

        val moved = engine.building(GridPoint(10, 10))!!
        engine.demolish(moved)
        assertIndexMatchesWhatStands(engine.state)
        assertTrue(engine.state.buildings.isEmpty() && engine.state.homeFrontTiles.isEmpty())
    }

    @Test
    fun `trees are found on every tile of their ground until they are removed`() {
        val state = GameState()
        val tree = Tree.all[3]
        state.putTree(spot, PlacedTree(tree, tree.species.colors[0], now))
        assertIndexMatchesWhatStands(state)
        assertEquals(spot, state.tree(tree.footprint(spot).last())?.first)
        state.removeTree(spot)
        assertIndexMatchesWhatStands(state)
        assertNull(state.tree(spot))
    }

    @Test
    fun `a loaded game has its index built at once`() {
        val state = GameState()
        state.putBuilding(spot, "chickenCoop")
        val tree = Tree.all[3]
        state.putTree(GridPoint(5, 5), PlacedTree(tree, tree.species.colors[0], now))
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertIndexMatchesWhatStands(loaded)
        assertTrue(loaded.building(spot) != null && loaded.tree(GridPoint(5, 5)) != null)
    }

    @Test
    fun `a fresh worlds trees are all indexed`() {
        val state = newGame(SeededRandom(1))
        assertFalse(state.trees.isEmpty())
        assertIndexMatchesWhatStands(state)
    }

    @Test
    fun `the index is not part of the save`() {
        val state = GameState()
        state.putBuilding(spot, "chickenCoop")
        val json = GameStateJson.encode(state)
        assertFalse(json.lowercase().contains("occupan") || json.contains("homeFront"))
    }
}
