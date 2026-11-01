package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class EggTest {
    private val now = Instant.ofEpochSecond(9_800_000)
    private val coopOrigin = GridPoint(20, 20)
    private val spot = GridPoint(22, 20)
    private val sixHours = 6 * 60 * 60

    /** A meal that keeps a hen laying without boosting it: eaten well before now, so it has long since ended. */
    private fun longAgoMeal() = AnimalMeal(now.advancedBy(-AnimalMeal.DURATION - 100))

    private fun engine(
        variant: String = ChickenVariant.all[0].id,
        hatchedAgo: Double = Chicken.GROW_DURATION,
    ): GameEngine {
        val engine = GameEngine()
        engine.state.putBuilding(coopOrigin, "chickenCoop")
        val hen =
            Chicken(0, variant, Chicken.DEFAULT_NAME, spot, longAgoMeal(), now.advancedBy(-hatchedAgo))
        engine.state.coops[coopOrigin] = Coop(chickens = listOf(hen), nextChickAt = now)
        return engine
    }

    /** Lets a lot of time pass in small steps, as the game does, and returns how many eggs were laid. */
    private fun eggsLaid(
        engine: GameEngine,
        seconds: Int,
        seed: Long = 1,
    ): Int {
        val generator = SeededRandom(seed)
        repeat(seconds / 2) { engine.layEggs(2.0, now, generator) }
        return engine.state.eggs.size
    }

    @Test
    fun `a grown hen eventually lays eggs on its tile`() {
        val engine = engine()
        assertTrue(eggsLaid(engine, sixHours) > 0)
        assertTrue(engine.state.eggs.all { it.point == spot })
    }

    @Test
    fun `eggs sit slightly off the center of the tile`() {
        val offsets = mutableListOf<Double>()
        for (trial in 0 until 30) {
            val engine = engine()
            eggsLaid(engine, sixHours, trial.toLong())
            offsets += engine.state.eggs.flatMap { listOf(it.offsetX, it.offsetY) }
        }
        assertTrue(offsets.all { abs(it) <= Egg.MAX_OFFSET })
        assertTrue(offsets.toSet().size > 1)
    }

    @Test
    fun `chicks lay nothing`() {
        assertEquals(0, eggsLaid(engine(hatchedAgo = 60.0), sixHours))
    }

    @Test
    fun `a hen has one egg at a time and lays another once it is collected`() {
        val engine = engine()
        assertEquals(1, Egg.MAX_PER_HEN)
        assertEquals(1, eggsLaid(engine, sixHours))
        engine.state.eggs = engine.state.eggs.drop(1)
        assertEquals(1, eggsLaid(engine, sixHours))
    }

    @Test
    fun `a hens egg floats over her and follows wherever she walks`() {
        val engine = engine()
        eggsLaid(engine, sixHours)
        assertTrue(engine.hasEggWaiting(coopOrigin, 0) && engine.state.eggs.map { it.point } == listOf(spot))
        val moved = GridPoint(25, 22)
        engine.setPosition(moved, AnimalRef.Chicken(coopOrigin, 0))
        assertEquals(listOf(moved), engine.state.eggs.map { it.point })
        engine.select(HarvestTool.bucket)
        assertTrue(engine.useTool(moved, now).any { it is GameEvent.Stored && it.itemID == "egg" })
        assertFalse(engine.hasEggWaiting(coopOrigin, 0))
    }

    @Test
    fun `every grown hen has its own egg`() {
        val engine = engine()
        val adult = now.advancedBy(-Chicken.GROW_DURATION)
        val coop = engine.state.coops[coopOrigin]!!
        val more =
            listOf(1, 2).map { id ->
                Chicken(id, ChickenVariant.all[id].id, Chicken.DEFAULT_NAME, spot, longAgoMeal(), adult)
            }
        engine.state.coops[coopOrigin] = coop.copy(chickens = coop.chickens + more)
        assertEquals(3, eggsLaid(engine, sixHours))
    }

    @Test
    fun `a favorite crop meal makes hens lay faster`() {
        var unfed = 0
        var fed = 0
        for (trial in 0 until 200) {
            val plain = engine()
            val hay = engine()
            val coop = hay.state.coops[coopOrigin]!!
            hay.state.coops[coopOrigin] =
                coop.copy(chickens = listOf(coop.chickens[0].copy(meal = AnimalMeal(now.advancedBy(-2.0)))))
            unfed += eggsLaid(plain, 60, trial.toLong())
            fed += eggsLaid(hay, 60, trial.toLong())
        }
        assertTrue(fed > unfed * 3 / 2, "fed $fed unfed $unfed")
    }

    @Test
    fun `hens keep laying at the normal pace`() {
        assertEquals(Egg.MAX_PER_HEN, eggsLaid(engine(), sixHours))
    }

    @Test
    fun `demolishing the coop takes its eggs with it`() {
        val engine = engine()
        eggsLaid(engine, sixHours)
        assertEquals(Egg.MAX_PER_HEN, engine.state.eggs.size)
        assertTrue(engine.demolish(engine.building(coopOrigin)!!).any { it is GameEvent.Demolished })
        assertTrue(engine.state.eggs.isEmpty())
    }

    @Test
    fun `moving the coop keeps its hens eggs`() {
        val engine = engine()
        eggsLaid(engine, sixHours)
        engine.moveCoop(coopOrigin, GridPoint(30, 30))
        assertEquals(Egg.MAX_PER_HEN, eggsLaid(engine, sixHours))
    }

    @Test
    fun `eggs survive a save and load`() {
        val state = GameState()
        state.eggs = listOf(lyingEgg(spot, 0.2, -0.1))
        val loaded = GameStateJson.decode(GameStateJson.encode(state))
        assertEquals(state.eggs, loaded.eggs)
        assertTrue(sameState(state, loaded))
    }
}
