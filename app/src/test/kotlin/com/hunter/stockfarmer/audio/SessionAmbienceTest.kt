package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.ViewSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionAmbienceTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val tile = GridPoint(15, 15)

    private fun session(state: GameState): GameSession {
        state.clockOffset = DayNightCycle.offset(12.0 * 60, now)
        return GameSession(GameEngine(state)).apply {
            view.snapshot =
                ViewSnapshot(
                    (tile.col + 0.5f) * TILE,
                    (GameState.GRID_SIZE - tile.row - 0.5f) * TILE,
                    1f,
                    800,
                    600,
                    0f,
                )
        }
    }

    @Test fun `snapshot maps species and uses rooster voice only after growing`() {
        val state = GameState()
        val home = GridPoint(10, 10)
        state.pets[home] = Pet(PetSpecies.dog, "dog", "Dog", tile)
        state.coops[home] =
            Coop(
                chickens =
                    listOf(
                        Chicken(1, "cock-brown", "Rooster", tile, hatchedAt = now.minusSeconds(600)),
                        Chicken(2, "cock-brown", "Chick", tile, hatchedAt = now),
                    ),
                nextChickAt = now.plusSeconds(100),
            )
        state.herds[home] =
            Herd(
                animals =
                    listOf(
                        Livestock(
                            1,
                            LivestockSpecies.goat.variants
                                .first()
                                .id,
                            "Goat",
                            tile,
                            bornAt = now,
                        ),
                    ),
                nextBirthAt = now.plusSeconds(100),
            )
        val session = session(state)
        val snapshot = session.ambienceSnapshot(now)
        assertEquals(
            listOf(SoundCue.dogBark, SoundCue.roosterCrow, SoundCue.henCluck, SoundCue.goatBleat),
            snapshot.animals.map { it.cue },
        )
        assertTrue(snapshot.animals.all { it.placement.isCloseUp && it.placement.pan == 0.0 })
        assertEquals(0.0, snapshot.rain)
        assertEquals(0.0, snapshot.darkness)
        assertEquals(state.coops[home], session.read { it.coops[home] })
    }

    @Test fun `animals inside shelter or offscreen are omitted and no camera means no voices`() {
        val state = GameState()
        val home = GridPoint(14, 14)
        val building = Building.named("petHouse")!!
        state.putBuilding(home, building.id)
        val front = building.frontTile(home)
        state.pets[home] = Pet(PetSpecies.cat, "cat", "Cat", front)
        val session = session(state)
        assertEquals(1, session.ambienceSnapshot(now).animals.size)
        session.act { it.state.clockOffset = DayNightCycle.offset(0.0, now) }
        assertTrue(session.ambienceSnapshot(now).animals.isEmpty())
        session.act {
            it.state.clockOffset = DayNightCycle.offset(12.0 * 60, now)
            it.state.pets[home] =
                it.state.pets
                    .getValue(home)
                    .copy(position = GridPoint(0, 0))
        }
        assertTrue(session.ambienceSnapshot(now).animals.isEmpty())
        session.view.snapshot = null
        assertTrue(session.ambienceSnapshot(now).animals.isEmpty())
    }

    @Test fun `ready state uses core growth and weather uses the farm clock and season`() {
        val crop = Crop.starter
        val state = GameState()
        state.farmland = mutableSetOf(tile)
        state.plots[tile] = Plot(crop.id, now.minusSeconds(crop.growDuration.toLong() + 1))
        val session = session(state)
        assertTrue(session.ambienceSnapshot(now).isWholeFieldReady)
        assertEquals(listOf(ReadyCrop(crop.id, crop.name)), session.ambienceSnapshot(now).readyCrops)
        session.act {
            it.state.plots[tile] = Plot(crop.id, now)
            it.state.clockOffset = DayNightCycle.offset(14.5 * 60, now)
            it.state.season = Season.winter
        }
        val snapshot = session.ambienceSnapshot(now)
        assertFalse(snapshot.isWholeFieldReady)
        assertTrue(snapshot.readyCrops.isEmpty())
        assertEquals(1.0, snapshot.rain)
        assertTrue(snapshot.isSnowing)
    }
}
