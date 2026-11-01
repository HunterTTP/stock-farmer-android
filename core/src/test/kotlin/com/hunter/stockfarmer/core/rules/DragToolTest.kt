package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DragToolTest {
    private val now = Instant.ofEpochSecond(8_000_000)
    private val tile = GridPoint(24, 24)

    private fun reject(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

    @Test
    fun `dragging the planter plants each empty farmland tile`() {
        val engine = GameEngine(stateWith(money = 10.0))
        for (point in GameState.startingFarmland) engine.useTool(point, now)
        assertEquals(4, engine.state.plots.size)
    }

    @Test
    fun `dragging off the farmland is silently skipped`() {
        val engine = GameEngine(stateWith(money = 10.0))
        assertEquals(reject(Rejection.NeedFarmland), engine.useTool(GridPoint(3, 3), now))
        assertTrue(Rejection.NeedFarmland.isTileSpecific)
        assertTrue(engine.state.plots.isEmpty())
    }

    @Test
    fun `running out of money is not silent`() {
        val engine = GameEngine(stateWith(money = 0.0, mode = PlayMode.fertilize))
        engine.selectFertilizer()
        assertEquals(reject(Rejection.NeedMoney(Fertilizer.price)), engine.useTool(tile, now))
        assertFalse(Rejection.NeedMoney(1.0).isTileSpecific)
    }

    @Test
    fun `dragging fences never takes a fence back`() {
        val engine = GameEngine(stateWith(money = 100.0, mode = PlayMode.decorate))
        val spot = GridPoint(5, 5)
        engine.useTool(spot, now)
        assertNotNull(engine.state.fences[spot])
        assertTrue(engine.useTool(spot, now).isEmpty())
        assertNotNull(engine.state.fences[spot])
    }

    @Test
    fun `tapping bare ground with a crop tap does nothing`() {
        val engine = GameEngine(stateWith(money = 10.0))
        assertTrue(engine.tapCrop(tile, now).isEmpty())
        assertTrue(engine.state.plots.isEmpty())
    }

    @Test
    fun `tapping a crop reports or harvests it`() {
        val engine = GameEngine(stateWith(money = 10.0))
        engine.useTool(tile, now)
        val first = engine.tapCrop(tile, now.advancedBy(1.0)).first()
        assertTrue(first is GameEvent.Rejected && first.rejection is Rejection.NotFinishedGrowing)
        engine.select(HarvestTool.scythe)
        val ready = now.advancedBy(Crop.starter.growDuration)
        assertTrue(engine.tapCrop(tile, ready).any { it is GameEvent.Stored })
    }

    // Tool boundaries

    private val planted = Instant.ofEpochSecond(13_000_000)
    private val ready get() = planted.advancedBy(30.0)

    private fun engineWithRipeCrop(mode: PlayMode): GameEngine {
        val engine = GameEngine(stateWith(money = 100.0))
        engine.tap(tile, planted)
        engine.setMode(mode)
        return engine
    }

    @Test
    fun `dragging the planter over a ripe crop leaves it alone`() {
        val engine = engineWithRipeCrop(PlayMode.plant)
        assertEquals(reject(Rejection.UseTheScythe), engine.useTool(tile, ready))
        assertNotNull(engine.state.plots[tile])
        assertEquals(100.0, engine.state.money)
    }

    @Test
    fun `only the harvest tool harvests`() {
        val engine = engineWithRipeCrop(PlayMode.harvest)
        assertTrue(engine.useTool(tile, ready).any { it is GameEvent.Stored })
        for (mode in listOf(PlayMode.landscape, PlayMode.fertilize, PlayMode.decorate)) {
            val other = engineWithRipeCrop(mode)
            assertFalse(other.useTool(tile, ready).any { it is GameEvent.Stored })
            assertNotNull(other.state.plots[tile])
        }
    }

    @Test
    fun `direct taps harvest only with the scythe in hand`() {
        val engine = engineWithRipeCrop(PlayMode.plant)
        assertEquals(reject(Rejection.UseTheScythe), engine.tapCrop(tile, ready))
        engine.select(HarvestTool.axe)
        assertEquals(reject(Rejection.UseTheScythe), engine.tapCrop(tile, ready))
        engine.select(HarvestTool.bucket)
        assertEquals(reject(Rejection.UseTheScythe), engine.tapCrop(tile, ready))
        engine.select(HarvestTool.scythe)
        assertTrue(engine.tapCrop(tile, ready).any { it is GameEvent.Stored })
    }

    @Test
    fun `holding the wrong tool over something harvestable names the right one`() {
        val engine = engineWithRipeCrop(PlayMode.plant)
        assertEquals(Rejection.UseTheScythe, engine.missingHarvestToolRejection(tile, ready))
        assertNull(engine.missingHarvestToolRejection(tile, planted))
        engine.select(HarvestTool.scythe)
        assertNull(engine.missingHarvestToolRejection(tile, ready))
        val treeTile = GridPoint(30, 30)
        engine.state.putTree(treeTile, PlacedTree(TreeSpecies.pine.smallest, TreeSpecies.pine.colors[0], planted))
        assertEquals(Rejection.UseTheAxe, engine.missingHarvestToolRejection(treeTile, ready))
        engine.select(HarvestTool.axe)
        assertNull(engine.missingHarvestToolRejection(treeTile, ready))
        val eggTile = GridPoint(31, 20)
        engine.state.eggs = listOf(lyingEgg(eggTile, 0.0, 0.0))
        assertEquals(Rejection.UseTheBucketForEggs, engine.missingHarvestToolRejection(eggTile, ready))
        engine.select(HarvestTool.bucket)
        assertNull(engine.missingHarvestToolRejection(eggTile, ready))
    }

    @Test
    fun `fertilizer cannot be put under a crop`() {
        val engine = engineWithRipeCrop(PlayMode.fertilize)
        assertEquals(reject(Rejection.AlreadyPlanted), engine.useTool(tile, ready))
        assertTrue(engine.state.fertilized.isEmpty())
    }
}
