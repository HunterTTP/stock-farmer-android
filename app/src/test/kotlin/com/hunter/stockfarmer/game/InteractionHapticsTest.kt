package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.game.AnimalHitTarget
import com.hunter.stockfarmer.game.AnimalInteraction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class InteractionHapticsTest {
    private val now = Instant.ofEpochSecond(100_000)
    private val tile = GridPoint(10, 10)
    private val engine = GameEngine()
    private val session = GameSession(engine)
    private val light = listOf(HapticRequest.Cue(HapticCue.Light))
    private val medium = listOf(HapticRequest.Cue(HapticCue.Medium))

    @Test
    fun `crop and fertilizer confirmations buzz without changing the farm`() {
        engine.state.plots[tile] = Plot(Crop.starter.id, now)
        assertEquals(medium, collectingHaptics(session) { session.hold(tile, now) })
        assertTrue(tile in engine.state.plots)
        engine.state.plots.clear()
        engine.state.fertilized.add(tile)
        assertEquals(medium, collectingHaptics(session) { session.hold(tile, now) })
        assertTrue(tile in engine.state.fertilized)
    }

    @Test
    fun `fence and decoration confirmations use medium feedback`() {
        engine.state.fences[tile] = "white"
        assertEquals(medium, collectingHaptics(session) { session.touch(tile, now) })
        engine.state.fences.clear()
        engine.state.putDecoration(tile, PlacedDecoration(Decoration.all.first { it.id == "well" }.id))
        assertEquals(medium, collectingHaptics(session) { session.touch(tile, now) })
    }

    @Test
    fun `opening a building is light and picking it up and asking to destroy it are medium`() {
        engine.state.putBuilding(tile, Building.named("chickenCoop")!!.id)
        assertEquals(light, collectingHaptics(session) { session.touch(tile, now) })
        assertEquals(medium, collectingHaptics(session) { session.hold(tile, now) })
        assertEquals(medium, collectingHaptics(session) { session.askToDestroyWhilePlacing() })
        assertTrue(collectingHaptics(session) { session.hold(tile, now) }.isEmpty())
        session.cancelPlacement(now)
        assertTrue(collectingHaptics(session) { session.askToDestroyWhilePlacing() }.isEmpty())
    }

    @Test
    fun `placement drag buzzes only when the origin changes and arrows give light feedback`() {
        engine.state.putBuilding(tile, Building.named("chickenCoop")!!.id)
        session.hold(tile, now)
        val drag = PlacementDrag(session)
        assertTrue(drag.begin(tile, now))
        assertTrue(collectingHaptics(session) { drag.move(tile) }.isEmpty())
        assertEquals(light, collectingHaptics(session) { drag.move(GridPoint(11, 10)) })
        assertTrue(collectingHaptics(session) { drag.move(null) }.isEmpty())
        drag.end()
        assertTrue(collectingHaptics(session) { drag.move(tile) }.isEmpty())
        assertEquals(light, collectingHaptics(session) { session.nudgePlacement(0, 1) })
        assertEquals(GridPoint(11, 11), engine.buildingPlacement?.origin)
    }

    @Test
    fun `animal menu and petting give light feedback while nudging and missing animals do not`() {
        val animal = AnimalRef.Pet(tile)
        engine.state.pets[tile] = Pet(breed = Cat.starter.id, name = "Cat", position = GridPoint(20, 20))
        session.animalGestures.targets = listOf(AnimalHitTarget(animal, 100f, 100f))
        assertEquals(light, collectingHaptics(session) { session.interactWithAnimal(animal, now) })
        assertEquals(light, collectingHaptics(session) { session.interactWithAnimal(animal, AnimalInteraction.PET) })
        assertTrue(collectingHaptics(session) { session.interactWithAnimal(animal, AnimalInteraction.NUDGE) }.isEmpty())
        val missing = AnimalRef.Pet(GridPoint(0, 0))
        assertTrue(collectingHaptics(session) { session.interactWithAnimal(missing, now) }.isEmpty())
        assertTrue(collectingHaptics(session) { session.interactWithAnimal(missing, AnimalInteraction.PET) }.isEmpty())
    }
}
