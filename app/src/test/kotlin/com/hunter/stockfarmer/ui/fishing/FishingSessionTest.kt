package com.hunter.stockfarmer.ui.fishing

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.rules.FishingContest
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.castRod
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.fishingReachFactor
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FishingSessionTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val pond = GridPoint(20, 20)
    private val point = GridPoint(21, 20)
    private val kind = FishKind.named("bluegill")!!
    private val bounds = FishingContest.Bounds(68.0, 332.0, 196.0, 630.0)

    private fun engine() =
        GameEngine(
            GameState().apply {
                water[point] = WaterStyle.deep
                fishPonds[pond] = FishPond(PondFish(kind.id, point, 1.2, now), now)
            },
        )

    private fun start(engine: GameEngine = engine()): FishingSession {
        val cast = FishingCast.start(engine, engine.castRod(point))!!
        return FishingSession(cast, bounds, SeededRandom(7))
    }

    private fun catch(fishing: FishingSession) {
        repeat(600) {
            fishing.moveRod(fishing.fish)
            fishing.advance(1.0 / 60)
        }
        assertTrue(fishing.isCaught)
    }

    @Test
    fun `a hook captures its pond weight style and rod upgrade without spending or catching`() {
        val engine = engine()
        engine.state.toolLevels[HarvestTool.rod.rawValue] = 3
        val cast = FishingCast.start(engine, engine.castRod(point))!!
        assertEquals(point, cast.point)
        assertEquals(pond, cast.pond)
        assertEquals(kind, cast.kind)
        assertEquals(1.2, cast.weight)
        assertEquals(kind.value, cast.sellPrice)
        assertEquals(kind.sizeFactor(1.2), cast.sizeFactor)
        assertEquals(WaterStyle.deep, cast.waterStyle)
        assertEquals(engine.fishingReachFactor, cast.reachFactor)
        assertEquals(44.0 * 2.25, FishingSession(cast, bounds, SeededRandom(7)).reach)
        assertTrue(cast.showsHint)
        assertTrue(engine.state.hasSeenFishingHint)
        assertFalse(FishingCast.start(engine, engine.castRod(point))!!.showsHint)
        assertNotNull(engine.state.fishPonds[pond]?.fish)
        assertTrue(engine.state.storage.isEmpty())
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `a given up fish is named in the swam away toast and the batch is empty`() {
        val engine = engine()
        val fishing = start(engine)
        assertEquals("The ${kind.name} swam away", fishing.cast.swamAwayMessage)
        assertTrue(fishing.giveUp(engine, now).isEmpty())
    }

    @Test
    fun `refused or stale hooks cannot start a contest or consume the hint`() {
        val engine = engine()
        assertNull(FishingCast.start(engine, engine.castRod(GridPoint(0, 0))))
        assertNull(FishingCast.start(engine, emptyList()))
        engine.state.fishPonds.clear()
        assertNull(FishingCast.start(engine, listOf(GameEvent.FishHooked(point))))
        assertFalse(engine.state.hasSeenFishingHint)
    }

    @Test
    fun `tracking fills the meter and catching waits for a disposition`() {
        val engine = engine()
        val fishing = start(engine)
        catch(fishing)
        assertEquals(1.0, fishing.progress)
        assertNull(fishing.rod)
        assertNotNull(engine.state.fishPonds[pond]?.fish)
        val fish = fishing.fish
        fishing.moveRod(FishingContest.Position(0.0, 0.0))
        assertFalse(fishing.advance(0.05))
        assertEquals(fish, fishing.fish)
        assertNull(fishing.rod)
    }

    @Test
    fun `released rod drains at the core rate and an empty meter never snaps the line`() {
        val fishing = start()
        repeat(30) {
            fishing.moveRod(fishing.fish)
            fishing.advance(1.0 / 60)
        }
        val before = fishing.progress
        fishing.moveRod(null)
        repeat(60) { fishing.advance(1.0 / 60) }
        assertEquals(before - 0.04, fishing.progress, 0.000001)
        repeat(12_000) { fishing.advance(0.05) }
        assertEquals(0.0, fishing.progress)
        assertFalse(fishing.isFinished)
        catch(fishing)
    }

    @Test
    fun `stalls count for at most fifty milliseconds and invalid steps do nothing`() {
        val fishing = start()
        fishing.moveRod(fishing.fish)
        fishing.advance(10.0)
        assertEquals(0.05 / 1.6, fishing.progress, 0.000001)
        val fish = fishing.fish
        val progress = fishing.progress
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { fishing.advance(it) }
        assertEquals(fish, fishing.fish)
        assertEquals(progress, fishing.progress)
    }

    @Test
    fun `going inactive lifts the rod and pauses the fish and meter`() {
        val fishing = start()
        fishing.moveRod(fishing.fish)
        fishing.advance(0.05)
        val fish = fishing.fish
        val progress = fishing.progress
        fishing.setActive(false)
        assertNull(fishing.rod)
        fishing.moveRod(fish)
        repeat(100) { fishing.advance(0.05) }
        assertEquals(fish, fishing.fish)
        assertEquals(progress, fishing.progress)
        fishing.setActive(true)
        fishing.advance(0.05)
        assertEquals(progress - 0.002, fishing.progress, 0.000001)
    }

    @Test
    fun `storing lands from the original tile once and returns the engine events`() {
        val engine = engine()
        val fishing = start(engine)
        assertTrue(fishing.land(engine, now, selling = false).isEmpty())
        catch(fishing)
        val events = fishing.land(engine, now, selling = false)
        assertTrue(events.any { it is GameEvent.Stored && it.point == point })
        assertEquals(1, engine.state.storage[StorageItem.Fish(kind).id])
        assertNull(engine.state.fishPonds[pond]?.fish)
        assertEquals(now.plusSeconds(GameEngine.FISH_INTERVAL.toLong()), engine.state.fishPonds[pond]?.nextFishAt)
        assertTrue(fishing.isFinished)
        assertTrue(fishing.land(engine, now, selling = true).isEmpty())
    }

    @Test
    fun `full storage keeps the catch available for sale even with rod auto sell selected`() {
        val engine = engine()
        engine.state.storage[StorageItem.Egg.id] = 100_000
        engine.state.sellingTools.add(HarvestTool.rod)
        val fishing = start(engine)
        catch(fishing)
        val refused = fishing.land(engine, now, selling = false)
        assertEquals(Rejection.StorageFull, refused.firstRejection)
        assertTrue(fishing.isCaught)
        assertFalse(fishing.isFinished)
        assertNotNull(engine.state.fishPonds[pond]?.fish)
        val events = fishing.land(engine, now, selling = true)
        assertTrue(events.any { it is GameEvent.Sold && it.point == point })
        assertEquals(kind.value, engine.state.money)
        assertNull(engine.state.storage[StorageItem.Fish(kind).id])
        assertTrue(fishing.isFinished)
    }

    @Test
    fun `giving up loses the hooked pond fish with the normal respawn delay`() {
        val engine = engine()
        val fishing = start(engine)
        assertTrue(fishing.giveUp(engine, now).isEmpty())
        assertNull(engine.state.fishPonds[pond]?.fish)
        assertEquals(now.plusSeconds(GameEngine.FISH_INTERVAL.toLong()), engine.state.fishPonds[pond]?.nextFishAt)
        assertTrue(engine.state.storage.isEmpty())
        assertEquals(0.0, engine.state.money)
        assertTrue(fishing.isFinished)
    }

    @Test
    fun `leaving with a caught fish stores it and a full storage refusal keeps it on show`() {
        val engine = engine()
        val fishing = start(engine)
        catch(fishing)
        engine.state.storage[StorageItem.Egg.id] = 100_000
        assertEquals(Rejection.StorageFull, fishing.giveUp(engine, now).firstRejection)
        assertFalse(fishing.isFinished)
        engine.state.storage.clear()
        assertNull(fishing.giveUp(engine, now).firstRejection)
        assertEquals(1, engine.state.storage[StorageItem.Fish(kind).id])
        assertTrue(fishing.isFinished)
    }

    @Test
    fun `equal seeds and inputs produce equal contests within the bounds`() {
        val left = start()
        val right = start()
        repeat(600) {
            left.advance(1.0 / 60)
            right.advance(1.0 / 60)
            assertEquals(left.fish, right.fish)
            assertEquals(left.heading, right.heading)
            assertTrue(left.fish.x in bounds.minX..bounds.maxX)
            assertTrue(left.fish.y in bounds.minY..bounds.maxY)
        }
    }

    private class SeededRandom(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }
}
