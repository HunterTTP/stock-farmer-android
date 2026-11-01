package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RemainingTractorTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)
    private val near = GridPoint(10, 15)
    private val far = GridPoint(10, 20)

    private fun engine(): GameEngine =
        GameEngine(
            GameState().also { state ->
                state.putBuilding(home, "tractor1")
                for (tile in listOf(near, far)) {
                    state.farmland.add(tile)
                    state.plots[tile] = Plot("wheat", now.advancedBy(-100.0))
                }
            },
        )

    private fun finish(engine: GameEngine): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var steps = 0
        while (engine.state.tractorRun != null && steps++ < 300) {
            events += engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
        }
        assertNull(engine.state.tractorRun)
        return events
    }

    private fun load(
        engine: GameEngine,
        version: Int,
    ): GameState {
        val json = GameStateJson.json.parseToJsonElement(GameStateJson.encode(engine.state)) as JsonObject
        val trip = JsonObject((json["tractorRun"] as JsonObject) + ("version" to JsonPrimitive(version)))
        return GameStateJson.decode(JsonObject(json + ("tractorRun" to trip)).toString())
    }

    private fun roundTrip(state: GameState) =
        assertTrue(
            sameState(
                state,
                GameStateJson.decode(GameStateJson.encode(state)),
            ),
        )

    @Test fun `parked tractor reserves its whole ground footprint`() {
        val tractor = Building.named("tractor1")!!
        for (style in listOf("right", "left", "front", "back")) {
            val building = tractor.styled(style)
            for (tile in building.footprint(home)) {
                val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, logs = 100_000))
                engine.select(tractor.styles.first { it.id == style }, tractor)
                engine.beginPlacing(home)
                engine.movePlacement(home)
                engine.state.rocks[tile] = RockSize.small
                assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
                engine.state.rocks.remove(tile)
                engine.state.fences[tile] = FenceColor.all[0].id
                assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
                engine.state.fences.remove(tile)
                assertFalse(engine.confirmPlacement(now).containsRejection)
                assertEquals(home, engine.state.buildingBlocking(tile)?.origin)
                engine.beginMoving(engine.building(home)!!)
                engine.movePlacement(GridPoint(home.row + 1, home.col))
                assertFalse(engine.confirmPlacement(now).containsRejection)
            }
        }
    }

    @Test fun `version three vertical trips at edges keep their straight progress`() {
        for ((facing, head, next) in listOf(
            Triple(TractorFacing.up, GridPoint(48, 20), GridPoint(47, 20)),
            Triple(TractorFacing.down, GridPoint(1, 20), GridPoint(2, 20)),
        )) {
            val engine = engine()
            engine.state.tractorCargo[home] = mapOf("wheat" to 2)
            engine.state.tractorRun =
                TractorRun(
                    home,
                    "wheat",
                    head,
                    nextTile = next,
                    facing = facing,
                    progress = 0.25,
                    remaining = setOf(far),
                )
            val loaded = load(engine, 3)
            val restored = loaded.tractorRun!!
            assertEquals(3, loaded.tractorOccupiedTiles.size)
            assertTrue(loaded.tractorOccupiedTiles.all(GameState::contains))
            assertEquals(head, restored.position)
            assertEquals(next, restored.nextTile)
            assertEquals(0.25, restored.progress)
            assertEquals(setOf(far), restored.remaining)
            assertEquals(mapOf("wheat" to 2), loaded.tractorCargo[home])
            roundTrip(loaded)
        }
    }

    @Test fun `three tile trips keep their front and cargo while replanning turns`() {
        for (version in listOf(3, 4)) {
            for (facing in TractorFacing.entries) {
                for (heading in TractorFacing.entries) {
                    val head = GridPoint(20, 20)
                    val distance = if (facing == heading) 1 else 2
                    val next =
                        GridPoint(
                            head.row + heading.direction.row * distance,
                            head.col + heading.direction.col * distance,
                        )
                    val engine = engine()
                    engine.state.plots.remove(near)
                    engine.state.tractorCargo[home] = mapOf("wheat" to 2)
                    engine.state.tractorRun =
                        TractorRun(
                            home,
                            "wheat",
                            head,
                            nextTile = next,
                            facing = facing,
                            progress = 0.25,
                            remaining = setOf(far),
                        )
                    val loaded = load(engine, version)
                    val restored = loaded.tractorRun!!
                    assertEquals(head, restored.position)
                    assertEquals(facing, restored.facing)
                    assertEquals(setOf(far), restored.remaining)
                    assertEquals(if (facing == heading) next else null, restored.nextTile)
                    assertEquals(if (facing == heading) 0.25 else 0.0, restored.progress)
                    assertEquals(2, TractorRules.footprint(restored.position, restored.facing).size)
                    assertEquals(mapOf("wheat" to 2), loaded.tractorCargo[home])
                    roundTrip(loaded)
                    val reopened = GameEngine(loaded)
                    assertFalse(finish(reopened).containsRejection)
                    assertEquals(mapOf("wheat" to 3), reopened.state.tractorCargo[home])
                }
            }
        }
    }

    @Test fun `current trips reject steps longer than one tile`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val run = engine.state.tractorRun!!
        engine.state.tractorRun = run.copy(nextTile = GridPoint(run.position.row, run.position.col + 2))
        assertThrows(Exception::class.java) { GameStateJson.decode(GameStateJson.encode(engine.state)) }
    }

    @Test fun `version four canceled turns replan without resuming harvesting`() {
        val engine = engine()
        engine.state.tractorCargo[home] = mapOf("wheat" to 2)
        val run =
            TractorRun(
                home,
                "wheat",
                GridPoint(22, 20),
                nextTile = GridPoint(20, 20),
                nextPoseFacing = TractorFacing.right,
                facing = TractorFacing.down,
                progress = 0.75,
                returning = true,
            )
        engine.state.tractorRun = run
        val reopened = GameEngine(load(engine, 4))
        val restored = reopened.state.tractorRun!!
        assertEquals(run.position, restored.position)
        assertEquals(TractorFacing.down, restored.facing)
        assertTrue(restored.returning && restored.remaining.isEmpty())
        assertNull(restored.nextTile)
        assertNull(restored.nextPoseFacing)
        assertEquals(0.0, restored.progress)
        assertFalse(finish(reopened).containsRejection)
        assertEquals(mapOf("wheat" to 2), reopened.state.tractorCargo[home])
        assertEquals(2, reopened.state.plots.size)
    }

    @Test fun `older two tile trips keep turns and map edges without losing cargo`() {
        for ((facing, head, next) in listOf(
            Triple(TractorFacing.right, GridPoint(49, 1), GridPoint(49, 2)),
            Triple(TractorFacing.left, GridPoint(0, 48), GridPoint(0, 47)),
            Triple(TractorFacing.up, GridPoint(48, 0), GridPoint(47, 0)),
            Triple(TractorFacing.down, GridPoint(1, 49), GridPoint(2, 49)),
            Triple(TractorFacing.right, GridPoint(20, 20), GridPoint(21, 20)),
        )) {
            val engine = engine()
            engine.state.tractorCargo[home] = mapOf("wheat" to 2)
            engine.state.tractorRun =
                TractorRun(
                    home,
                    "wheat",
                    head,
                    nextTile = next,
                    facing = facing,
                    progress = 0.25,
                    remaining = setOf(far),
                )
            val loaded = load(engine, 2)
            val restored = loaded.tractorRun!!
            assertTrue(loaded.tractorOccupiedTiles.all(GameState::contains))
            assertEquals(head, restored.position)
            assertEquals(next, restored.nextTile)
            assertEquals(0.25, restored.progress)
            assertEquals(setOf(far), restored.remaining)
            assertEquals(home, restored.home)
            assertEquals(mapOf("wheat" to 2), loaded.tractorCargo[home])
            roundTrip(loaded)
        }
    }

    @Test fun `malformed saved trip is rejected before driving`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        engine.state.tractorRun = engine.state.tractorRun!!.copy(nextTile = GridPoint(Int.MAX_VALUE, Int.MAX_VALUE))
        assertThrows(Exception::class.java) { GameStateJson.decode(GameStateJson.encode(engine.state)) }
    }

    @Test fun `old trips ignore retired selling choice and continue into cargo`() {
        for (selling in listOf(false, true)) {
            val engine = engine()
            engine.state.plots.remove(far)
            engine.state.money = 17.0
            engine.state.storage = mutableMapOf("carrot" to 2)
            val json =
                (GameStateJson.json.parseToJsonElement(GameStateJson.encode(engine.state)) as JsonObject)
                    .toMutableMap()
            json.remove("tractorCargo")
            json["tractorRun"] =
                GameStateJson.json.parseToJsonElement(
                    """{
                "home":{"row":10,"col":10},"position":{"row":10,"col":12},"nextTile":{"row":10,"col":13},
                "cropID":"wheat","selling":$selling,"progress":0.25,"returning":false,
                "remaining":[{"row":10,"col":15}]}""",
                )
            val reopened = GameEngine(GameStateJson.decode(JsonObject(json).toString()))
            assertEquals(GridPoint(10, 13), reopened.state.tractorRun?.position)
            finish(reopened)
            assertEquals(mapOf("wheat" to 1), reopened.state.tractorCargo[home])
            assertEquals(mapOf("carrot" to 2), reopened.state.storage)
            assertEquals(17.0, reopened.state.money)
        }
    }
}
