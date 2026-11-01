package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ClockMealDecorationChoiceTest {
    private val left = Instant.ofEpochSecond(50_000_000)
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)

    private fun savedEngine(): GameEngine {
        val engine = GameEngine()
        engine.state.clockOffset = 600.0
        engine.markSaved(left)
        return engine
    }

    // Clock while away

    @Test
    fun `the farms clock reads the same when you come back as when you left`() {
        val engine = savedEngine()
        val before = engine.minuteOfDay(left)
        engine.holdClockWhileAway(left.advancedBy(5 * 86_400.0 + 1_234))
        assertEquals(before, engine.minuteOfDay(left.advancedBy(5 * 86_400.0 + 1_234)), 1e-6)
    }

    @Test
    fun `nothing is held back for a game that was never saved or whose save is in the future`() {
        val fresh = GameEngine()
        fresh.holdClockWhileAway(left)
        assertEquals(0.0, fresh.state.clockOffset)
        val engine = savedEngine()
        engine.holdClockWhileAway(left.advancedBy(-60.0))
        assertEquals(600.0, engine.state.clockOffset)
    }

    @Test
    fun `only the clock is held and not the crops`() {
        val engine = savedEngine()
        engine.state.plots[GridPoint(24, 24)] = Plot(Crop.starter.id, left)
        val plots = engine.state.plots.toMap()
        engine.holdClockWhileAway(left.advancedBy(86_400.0))
        assertEquals(plots, engine.state.plots)
    }

    @Test
    fun `opening a game holds the clock`() {
        val engine = savedEngine()
        val reopened = left.advancedBy(3 * 86_400.0)
        val before = engine.minuteOfDay(left)
        engine.reopen(reopened, SeededRandom(1))
        assertEquals(before, engine.minuteOfDay(reopened), 1e-6)
    }

    @Test
    fun `the clock can be held from any moment the game was left not just the last save`() {
        val engine = savedEngine()
        val away = left.advancedBy(30.0)
        engine.markSaved(left.advancedBy(45.0))
        val before = engine.minuteOfDay(away)
        val back = away.advancedBy(600.0)
        engine.holdClockWhileAway(away, back)
        assertEquals(before, engine.minuteOfDay(back), 1e-6)
    }

    // Animal meals

    @Test
    fun `only the fed part of an interval is doubled`() {
        val meal = AnimalMeal(now)
        assertEquals(10.0, meal.productiveSeconds(10.0, now))
        assertEquals(15.0, meal.productiveSeconds(10.0, now.advancedBy(5.0)))
        assertEquals(20.0, meal.productiveSeconds(10.0, now.advancedBy(300.0)))
        assertEquals(15.0, meal.productiveSeconds(10.0, now.advancedBy(305.0)))
        assertEquals(10.0, meal.productiveSeconds(10.0, now.advancedBy(310.0)))
    }

    @Test
    fun `pets hens and livestock round trip their own meals`() {
        val state = stateWith(logs = 0)
        state.pets[home] = Pet(breed = "grey", name = "Cat", position = home, meal = AnimalMeal(now))
        val hen =
            Chicken(0, ChickenVariant.all[0].id, "Hen", home, AnimalMeal(now.advancedBy(10.0)), now)
        state.coops[home] = Coop(chickens = listOf(hen), nextChickAt = now)
        val cow =
            Livestock(
                0,
                LivestockSpecies.cow.variants[0].id,
                "Cow",
                home,
                AnimalMeal(now.advancedBy(20.0)),
                now,
            )
        state.herds[home] = Herd(listOf(cow), now)
        assertTrue(sameState(GameStateJson.decode(GameStateJson.encode(state)), state))
        assertFalse("\"meal\":null" in GameStateJson.encode(stateWith(logs = 0)))
    }

    // Decoration choices

    private val duplicates =
        mapOf(
            "feedTrough" to listOf(1),
            "waterTrough" to listOf(1, 7),
            "tailorDisplay" to listOf(0, 1),
            "cheeseShelf" to listOf(0, 6),
            "craftingSupplies" to listOf(4, 5),
            "woodworkSupplies" to listOf(11, 12),
            "nest" to listOf(0),
        )

    @Test
    fun `duplicate variants cannot be selected for new placements`() {
        for ((id, sizes) in duplicates) {
            val decoration = Decoration.named(id)!!
            for (size in sizes) {
                val engine = GameEngine(stateWith(xp = lateGameXP))
                val before = engine.state.copy()
                engine.select(size, decoration)
                assertTrue(sameState(before, engine.state))
                assertFalse(size in decoration.offeredSizes)
            }
        }
        assertTrue(Decoration.shopChoices.none { it.id == "nest" })
    }

    @Test
    fun `existing duplicate pictures and refunds survive saving and loading`() {
        val origin = GridPoint(15, 15)
        for ((id, sizes) in duplicates) {
            val decoration = Decoration.named(id)!!
            for (size in sizes) {
                val state = stateWith(money = 100.0, xp = lateGameXP, logs = 0)
                val placed = PlacedDecoration(decoration.id, size, placedAt = now)
                state.putDecoration(origin, placed)
                state.selectedDecorationID = id
                state.lastDecorationID = id
                state.selectedDecorationSizes[id] = size
                val restored = GameStateJson.decode(GameStateJson.encode(state))
                assertEquals(placed, restored.decorations[origin])
                val engine = GameEngine(restored)
                engine.selectDecorating()
                assertTrue(engine.selectedDecoration?.id != "nest")
                if (decoration.offeredSizes.isNotEmpty()) {
                    assertTrue(engine.selectedDecorationSize in decoration.offeredSizes)
                }
                assertEquals(placed, GameStateJson.decode(GameStateJson.encode(engine.state)).decorations[origin])
                assertEquals(
                    listOf<GameEvent>(GameEvent.Undecorated(origin, decoration.cost * 0.8)),
                    engine.destroyDecoration(origin, now),
                )
            }
        }
        assertNotNull(Decoration.named("nest"))
    }

    @Test fun `a game keeps its clock and starts at eight in the morning`() {
        val fresh = newGame(SeededRandom(1), now)
        val engine = GameEngine(fresh)
        assertEquals(480.0, engine.minuteOfDay(now), 0.0001)
        engine.setTimeOfDay(60.0, now)
        assertEquals(60.0, engine.minuteOfDay(now), 0.0001)
        assertEquals(1.0, DayNightCycle.darkness(now, engine.state.clockOffset))
        engine.setTimeOfDay(5000.0, now)
        assertEquals(1439.0, engine.minuteOfDay(now), 0.0001)
        assertEquals(engine.state.clockOffset, GameStateJson.decode(GameStateJson.encode(engine.state)).clockOffset)
        assertEquals(0.0, GameStateJson.decode("""{"money":1}""").clockOffset)
    }

    @Test fun `setting the time stays within the farms day whatever the real time of day`() {
        for (realHour in listOf(0.0, 5.0, 12.0, 23.9)) {
            val real = Instant.EPOCH.advancedBy(100 * DayNightCycle.secondsPerDay + realHour * 3600)
            val engine = GameEngine()
            engine.state.clockOffset = 3 * 3600.0
            val day = NightPulse.day(real, engine.state.clockOffset)
            for (minute in listOf(0.0, 5.0, 600.0, 1439.0)) {
                engine.setTimeOfDay(minute, real)
                assertEquals(minute, engine.minuteOfDay(real), 1e-6)
                assertEquals(day, NightPulse.day(real, engine.state.clockOffset))
            }
        }
    }
}
