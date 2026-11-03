package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The sounds the game side asks for, which the activity plays: a popup opening (iOS `ask`, `openAnimalMenu`). */
class SessionCuesTest {
    private val now = Instant.ofEpochSecond(100_000)
    private val tile = GridPoint(10, 10)
    private val engine = GameEngine()
    private val session = GameSession(engine)

    private fun collectingCues(action: () -> Unit): List<SoundCue> =
        runBlocking {
            val received = mutableListOf<SoundCue>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.cues.collect { received += it } }
            try {
                action()
                yield()
                received.toList()
            } finally {
                collector.cancel()
            }
        }

    @Test
    fun `asking the player to confirm opens a popup`() {
        val cues = collectingCues { session.ask(Confirmation("Sell?", "", "Sell") {}) }
        assertEquals(listOf(SoundCue.openPopup), cues)
    }

    @Test
    fun `tapping an animal opens its menu with the popup sound and a missing animal makes none`() {
        val animal = AnimalRef.Pet(tile)
        engine.state.pets[tile] = Pet(breed = Cat.starter.id, name = "Cat", position = GridPoint(20, 20))
        assertEquals(listOf(SoundCue.openPopup), collectingCues { session.interactWithAnimal(animal, now) })
        assertTrue(collectingCues { session.interactWithAnimal(AnimalRef.Pet(GridPoint(0, 0)), now) }.isEmpty())
    }

    @Test
    fun `a sound asked for by the game is sent once`() {
        assertEquals(listOf(SoundCue.photoSaved), collectingCues { session.cue(SoundCue.photoSaved) })
    }
}
