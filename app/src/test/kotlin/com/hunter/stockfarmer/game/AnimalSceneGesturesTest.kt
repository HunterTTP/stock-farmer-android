package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.position
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.farm.animals.AnimalGesture
import com.hunter.stockfarmer.farm.animals.AnimalHitTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AnimalSceneGesturesTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val home = GridPoint(10, 10)
    private val animal = AnimalRef.Pet(home)
    private val point = GridPoint(20, 20)
    private val state = GameState().apply { pets[home] = Pet(breed = Cat.starter.id, name = "Cat", position = point) }
    private val engine = GameEngine(state)
    private val driver = AnimalSceneGestures(RandomSource { 1L })

    @Test
    fun `pet requests do not change rules or movement and missing animals are ignored`() {
        driver.targets = listOf(AnimalHitTarget(animal, 100f, 100f))
        assertTrue(driver.interact(animal, AnimalGesture.PET, now))
        assertFalse(driver.interact(AnimalRef.Pet(GridPoint(0, 0)), AnimalGesture.PET, now))
        val before = state.copy()
        driver.advance(engine, now)
        assertEquals(before.pets, state.pets)
        assertEquals(listOf(AnimalSceneGestures.Petting(animal, now)), driver.drainPetting())
        assertTrue(driver.drainPetting().isEmpty())
    }

    @Test
    fun `nudge finishes the active step then takes two to five legal steps 500ms apart`() {
        driver.targets = listOf(AnimalHitTarget(animal, 100f, 100f, stepRemaining = 0.25))
        driver.interact(animal, AnimalGesture.NUDGE, now)
        driver.advance(engine, now)
        assertEquals(point, engine.position(animal))
        assertTrue(driver.isNudging(animal))
        driver.advance(engine, now.plusMillis(250))
        val first = engine.position(animal)!!
        assertEquals(1, kotlin.math.abs(first.row - point.row) + kotlin.math.abs(first.col - point.col))
        driver.advance(engine, now.plusMillis(749))
        assertEquals(first, engine.position(animal))
        driver.advance(engine, now.plusMillis(750))
        val second = engine.position(animal)!!
        assertEquals(2, kotlin.math.abs(second.row - point.row) + kotlin.math.abs(second.col - point.col))
        driver.advance(engine, now.plusMillis(1250))
        assertFalse(driver.isNudging(animal))
    }

    @Test
    fun `nudge cannot move through a ring of fences and removed animals leave no pending walk`() {
        state.fences.putAll(point.neighbors.associateWith { "white" })
        driver.targets = listOf(AnimalHitTarget(animal, 100f, 100f))
        driver.interact(animal, AnimalGesture.NUDGE, now)
        driver.advance(engine, now)
        assertEquals(point, engine.position(animal))
        assertFalse(driver.isNudging(animal))
        assertEquals(listOf(animal), driver.drainNudging())
        driver.interact(animal, AnimalGesture.NUDGE, now)
        state.pets.clear()
        driver.advance(engine, now)
        assertFalse(driver.isNudging(animal))
    }
}
