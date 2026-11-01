package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TreeGrowthEngineTest {
    private val planted = Instant.ofEpochSecond(30_000_000)
    private val trunk = GridPoint(20, 20)
    private val interval = Tree.growthInterval

    private fun engineWithAPine() =
        GameEngine(
            GameState().also {
                it.putTree(trunk, PlacedTree(Tree.starter, TreeColor.green, planted, false))
            },
        )

    private fun sizes(engine: GameEngine) =
        engine.state.trees.values
            .map { it.treeID }

    @Test fun `a tree grows one size each interval around the same trunk`() {
        val engine = engineWithAPine()
        assertFalse(engine.growTrees(planted.advancedBy(interval - 1)))
        assertEquals(listOf("pine-small"), sizes(engine))
        assertTrue(engine.growTrees(planted.advancedBy(interval)))
        assertEquals(listOf("pine-medium"), sizes(engine))
        val medium = Tree.named("pine-medium")!!
        assertEquals(planted.advancedBy(interval), engine.state.trees[medium.origin(trunk)]?.grewAt)
        assertNotNull(engine.state.tree(trunk))
        assertFalse(engine.growTrees(planted.advancedBy(interval + 60)))
    }

    @Test fun `a tree left for long enough grows all the way and stops at huge`() {
        val engine = engineWithAPine()
        assertTrue(engine.growTrees(planted.advancedBy(interval * 10)))
        assertEquals(listOf("pine-huge"), sizes(engine))
        val tree = engine.state.tree(trunk)!!
        assertTrue(trunk in tree.second.tree.footprint(tree.first))
        assertFalse(engine.growTrees(planted.advancedBy(interval * 20)))
    }

    @Test fun `a crowded tree waits until there is room and then grows`() {
        val engine = engineWithAPine()
        val blocker = GridPoint(19, 21)
        engine.state.water[blocker] = WaterStyle.medium
        assertFalse(engine.growTrees(planted.advancedBy(interval * 3)))
        assertEquals(listOf("pine-small"), sizes(engine))
        engine.state.water.remove(blocker)
        assertTrue(engine.growTrees(planted.advancedBy(interval * 3)))
        assertEquals(listOf("pine-huge"), sizes(engine))
    }

    @Test fun `a tree never grows over the front of a home`() {
        val engine = engineWithAPine()
        val medium = Tree.named("pine-medium")!!
        val extra = medium.footprint(medium.origin(trunk)).first { it != trunk }
        engine.state.putBuilding(GridPoint(extra.row - 1, extra.col), "petHouse")
        assertTrue(extra in engine.state.homeFrontTiles)
        assertFalse(engine.growTrees(planted.advancedBy(interval)))
    }

    @Test fun `a new game starts its trees at every size and partway through their cycle`() {
        val state = newGame(SeededRandom(2024), planted)
        assertEquals(
            TreeSize.entries.toSet(),
            state.trees.values
                .map { it.tree.size }
                .toSet(),
        )
        val ages = state.trees.values.map { planted.secondsSince(it.grewAt) }
        assertTrue(ages.all { it >= 0 && it < interval })
        assertTrue(ages.min() < interval * 0.1 && ages.max() > interval * 0.9)
    }

    @Test fun `selected birch grows and survives saving without changing old trees`() {
        val engine = GameEngine()
        engine.select(TreeSpecies.named("birch")!!)
        val tile = GridPoint(15, 15)
        val origin = engine.selectedTree.origin(tile)
        assertEquals(listOf<GameEvent>(GameEvent.TreePlaced(origin, 0.0)), engine.tap(tile, planted))
        assertTrue(engine.growTrees(planted.advancedBy(interval * 3)))
        val grown =
            engine.state.trees.values
                .single()
        assertEquals("birch-huge", grown.tree.id)
        assertEquals(6, grown.tree.logs)
        assertEquals(engine.state.trees, GameStateJson.decode(GameStateJson.encode(engine.state)).trees)
        assertEquals(1, Tree.named("pine-small")?.side)
        assertEquals(3, Tree.named("oak-huge")?.side)
    }

    @Test fun `existing world generation keeps its pine and oak mix`() {
        val state = newGame(SeededRandom(721), planted)
        assertTrue(state.trees.isNotEmpty())
        assertTrue(state.trees.values.all { it.tree.species.id in listOf("pine", "oak") })
    }
}
