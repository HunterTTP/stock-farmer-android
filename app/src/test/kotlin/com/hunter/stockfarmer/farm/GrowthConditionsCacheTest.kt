package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GrowthConditions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class GrowthConditionsCacheTest {
    private val cache = GrowthConditionsCache()
    private val sprinkler = Decoration.named(Decoration.SPRINKLER_ID)!!
    private val scarecrow = Decoration.named("scarecrow")!!

    private fun state() =
        GameState().apply {
            farmland = mutableSetOf(GridPoint(10, 10), GridPoint(10, 11), GridPoint(20, 20), GridPoint(30, 30))
            water[GridPoint(11, 11)] = WaterStyle.deep
        }

    private fun assertMatchesFresh(state: GameState) {
        val fresh = GrowthConditions(state)
        val cached = cache.of(state)
        assertEquals(fresh.saturatedTiles, cached.saturatedTiles)
        assertEquals(fresh.fertilizedTiles, cached.fertilizedTiles)
        assertEquals(fresh.guardedTiles, cached.guardedTiles)
    }

    @Test
    fun `an unchanged farm reuses the last result`() {
        val state = state()
        val first = cache.of(state)
        assertSame(first, cache.of(state))
        assertSame(first, cache.of(state))
        assertMatchesFresh(state)
    }

    @Test
    fun `new farmland is picked up`() {
        val state = state()
        val before = cache.of(state)
        state.farmland.add(GridPoint(12, 12))
        assertNotSame(before, cache.of(state))
        assertMatchesFresh(state)
    }

    @Test
    fun `new or removed water changes what is saturated`() {
        val state = state()
        val before = cache.of(state)
        state.water[GridPoint(21, 21)] = WaterStyle.deep
        val after = cache.of(state)
        assertNotSame(before, after)
        assertEquals(true, GridPoint(20, 20) in after.saturatedTiles)
        state.water.remove(GridPoint(21, 21))
        assertMatchesFresh(state)
        assertEquals(false, GridPoint(20, 20) in cache.of(state).saturatedTiles)
    }

    @Test
    fun `a sprinkler and its spray in progress change what is saturated`() {
        val state = state()
        val before = cache.of(state)
        state.putDecoration(GridPoint(29, 29), PlacedDecoration.of(sprinkler))
        val standing = cache.of(state)
        assertNotSame(before, standing)
        assertEquals(true, GridPoint(30, 30) in standing.saturatedTiles)
        assertMatchesFresh(state)

        val spraying =
            PlacedDecoration(Decoration.SPRINKLER_ID, 0, Instant.ofEpochSecond(5_000), setOf(GridPoint(30, 30)), null)
        state.putDecoration(GridPoint(29, 29), spraying)
        assertNotSame(standing, cache.of(state))
        assertMatchesFresh(state)

        val laterSpray = spraying.copy(wateredTiles = setOf(GridPoint(30, 30), GridPoint(20, 20)))
        state.putDecoration(GridPoint(29, 29), laterSpray)
        assertMatchesFresh(state)
        assertEquals(true, GridPoint(20, 20) in cache.of(state).saturatedTiles)
    }

    @Test
    fun `a scarecrow changes what is guarded`() {
        val state = state()
        val before = cache.of(state)
        state.putDecoration(GridPoint(10, 12), PlacedDecoration.of(scarecrow))
        val after = cache.of(state)
        assertNotSame(before, after)
        assertEquals(true, GridPoint(10, 10) in after.guardedTiles)
        assertMatchesFresh(state)
    }

    @Test
    fun `lingering water from a moved sprinkler counts until it is dropped`() {
        val state = state()
        val before = cache.of(state)
        state.lingeringWater[GridPoint(20, 20)] = Instant.ofEpochSecond(9_000)
        val after = cache.of(state)
        assertNotSame(before, after)
        assertEquals(true, GridPoint(20, 20) in after.saturatedTiles)
        assertMatchesFresh(state)
        state.lingeringWater.clear()
        assertEquals(false, GridPoint(20, 20) in cache.of(state).saturatedTiles)
    }

    @Test
    fun `fertilizer changes the fertilized tiles`() {
        val state = state()
        val before = cache.of(state)
        state.fertilized.add(GridPoint(10, 10))
        val after = cache.of(state)
        assertNotSame(before, after)
        assertEquals(setOf(GridPoint(10, 10)), after.fertilizedTiles)
        state.fertilized.remove(GridPoint(10, 10))
        assertEquals(emptySet<GridPoint>(), cache.of(state).fertilizedTiles)
    }

    @Test
    fun `a different farm with different contents is never answered from another farm`() {
        val first = state()
        val second = state().apply { water.clear() }
        assertEquals(true, GridPoint(10, 10) in cache.of(first).saturatedTiles)
        assertEquals(false, GridPoint(10, 10) in cache.of(second).saturatedTiles)
        assertEquals(true, GridPoint(10, 10) in cache.of(first).saturatedTiles)
    }
}
