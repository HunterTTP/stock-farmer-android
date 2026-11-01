package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DecorationOverlapTest {
    private val now = Instant.ofEpochSecond(61_000_000)
    private val spot = GridPoint(20, 20)
    private val refused = listOf<GameEvent>(GameEvent.Rejected(Rejection.NotEnoughSpace))

    private fun engine(selecting: Decoration): GameEngine {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP))
        engine.select(selecting)
        return engine
    }

    private fun placed(
        decoration: Decoration,
        origin: GridPoint,
        engine: GameEngine,
    ) = engine.state.putDecoration(origin, PlacedDecoration(decoration.id, placedAt = now))

    @Test
    fun `no new decoration can take the top left tile of another and replace it`() {
        val weathercock = Decoration.named("weathercock")!!
        for (decoration in Decoration.shopChoices.filter { !it.isRock }) {
            val engine = engine(decoration)
            placed(weathercock, spot, engine)
            val before = engine.state.copy()
            val tapped = GridPoint(spot.row + decoration.height - 1, spot.col)
            val events =
                if (decoration.isSprinkler) {
                    engine.beginPlacingSelectedDecoration(tapped)
                    engine.confirmPlacement(now)
                } else {
                    engine.tap(tapped, now)
                }
            assertEquals(refused, events, decoration.id)
            assertTrue(sameState(before, engine.state))
        }
    }

    @Test
    fun `only the bottom row blocks so props can be layered over another upper row`() {
        val scarecrow = Decoration.named("scarecrow")!!
        val engine = engine(scarecrow)
        placed(Decoration.named("well")!!, spot, engine)
        val upperRight = GridPoint(spot.row, spot.col + 1)
        assertEquals(
            listOf<GameEvent>(GameEvent.Decorated(upperRight, scarecrow.cost)),
            engine.tap(upperRight, now),
        )
        assertEquals(2, engine.state.decorations.size)
    }

    @Test
    fun `a bottom row on another bottom row is still refused`() {
        val engine = engine(Decoration.named("scarecrow")!!)
        placed(Decoration.named("well")!!, spot, engine)
        val before = engine.state.copy()
        assertEquals(refused, engine.tap(GridPoint(spot.row + 1, spot.col), now))
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `no moved decoration can take the top left tile of another and replace it`() {
        val weathercock = Decoration.named("weathercock")!!
        val from = GridPoint(40, 40)
        for (decoration in Decoration.all.filter { !it.isRock }) {
            val engine = engine(decoration)
            placed(decoration, from, engine)
            placed(weathercock, spot, engine)
            val before = engine.state.copy()
            engine.beginMovingDecoration(from)
            engine.movePlacement(spot)
            assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now), decoration.id)
            assertEquals(refused, engine.confirmPlacement(now))
            assertTrue(sameState(before, engine.state))
            engine.cancelPlacement()
            assertTrue(sameState(before, engine.state))
        }
    }

    @Test
    fun `moving onto another decorations upper row is allowed`() {
        val weathercock = Decoration.named("weathercock")!!
        val from = GridPoint(30, 30)
        val engine = engine(weathercock)
        placed(Decoration.named("well")!!, spot, engine)
        placed(weathercock, from, engine)
        engine.beginMovingDecoration(from)
        val upperRight = GridPoint(spot.row, spot.col + 1)
        engine.movePlacement(upperRight)
        assertNull(engine.placementRejection(now))
        assertEquals(listOf<GameEvent>(GameEvent.Moved(upperRight)), engine.confirmPlacement(now))
    }

    @Test
    fun `moving may overlap its own footprint and confirm without losing anything`() {
        val well = Decoration.named("well")!!
        val engine = engine(well)
        placed(well, spot, engine)
        val money = engine.state.money
        engine.beginMovingDecoration(spot)
        val target = GridPoint(21, 20)
        engine.movePlacement(target)
        assertNull(engine.placementRejection(now))
        assertEquals(listOf<GameEvent>(GameEvent.Moved(target)), engine.confirmPlacement(now))
        assertEquals(1, engine.state.decorations.size)
        assertEquals(well.id, engine.state.decorations[target]?.decorationID)
        assertEquals(money, engine.state.money)
    }

    @Test
    fun `old overlapping decorations are preserved and can be separated`() {
        val well = Decoration.named("well")!!
        val weathercock = Decoration.named("weathercock")!!
        val neighbor = GridPoint(20, 21)
        val state = stateWith(money = 100_000.0, xp = lateGameXP)
        state.putDecoration(spot, PlacedDecoration(well.id, placedAt = now))
        state.putDecoration(neighbor, PlacedDecoration(weathercock.id, placedAt = now))
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertTrue(sameState(loaded, state))
        val engine = GameEngine(loaded)
        val snapshot = loaded.copy()
        engine.beginMovingDecoration(GridPoint(21, 20))
        engine.movePlacement(neighbor)
        assertEquals(refused, engine.confirmPlacement(now))
        assertTrue(sameState(snapshot, engine.state))
        val target = GridPoint(19, 19)
        engine.movePlacement(target)
        assertEquals(listOf<GameEvent>(GameEvent.Moved(target)), engine.confirmPlacement(now))
        assertEquals(2, engine.state.decorations.size)
        assertEquals(snapshot.decorations[neighbor], engine.state.decorations[neighbor])
        assertEquals(snapshot.money, engine.state.money)
    }

    @Test
    fun `decorations can still stand directly beside and below one another`() {
        val well = Decoration.named("well")!!
        val engine = engine(well)
        placed(well, spot, engine)
        for (origin in listOf(GridPoint(20, 22), GridPoint(22, 20))) {
            val tapped = GridPoint(origin.row + 1, origin.col)
            assertEquals(listOf<GameEvent>(GameEvent.Decorated(origin, well.cost)), engine.tap(tapped, now))
        }
        assertEquals(3, engine.state.decorations.size)
        assertEquals(100_000.0 - 2 * well.cost, engine.state.money)
    }

    @Test
    fun `a taller building cannot take the top left tile of a shorter one and replace it`() {
        val engine =
            GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.putBuilding(GridPoint(10, 10), "grainMachine")
        engine.state.selectedBuildingID = "silo"
        val before = engine.state.copy()
        assertEquals(refused, engine.build(GridPoint(12, 10), Instant.ofEpochSecond(0)))
        assertTrue(sameState(before, engine.state))
    }
}
