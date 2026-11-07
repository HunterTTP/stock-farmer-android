package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundCueTest {
    private val here = GridPoint(2, 3)
    private val there = GridPoint(8, 1)

    private fun cues(
        events: List<GameEvent>,
        pets: (GridPoint) -> PetSpecies? = { null },
    ): List<PlacedCue> = cuesFor(events, LandscapeTool.water, pets)

    @Test
    fun `planting sounds where it happened`() {
        assertEquals(listOf(PlacedCue(SoundCue.plant, here)), cues(listOf(GameEvent.Planted(here, 1.0))))
    }

    @Test
    fun `many events of one kind make one sound at the first tile`() {
        assertEquals(
            listOf(PlacedCue(SoundCue.plant, here)),
            cues(listOf(GameEvent.Planted(here, 1.0), GameEvent.Planted(there, 1.0))),
        )
    }

    @Test
    fun `a refusal is heard alone`() {
        assertEquals(
            listOf(PlacedCue(SoundCue.notEnoughMoney)),
            cues(listOf(GameEvent.Planted(here, 1.0), GameEvent.Rejected(Rejection.NeedMoney(5.0)))),
        )
    }

    @Test
    fun `a level up replaces the other cues`() {
        val events = listOf(GameEvent.Planted(here, 1.0), GameEvent.LeveledUp(2, 0, 1))
        assertEquals(listOf(PlacedCue(SoundCue.levelUp)), cues(events))
    }

    @Test
    fun `a chopping hit that is not new is silent`() {
        assertTrue(cues(listOf(GameEvent.Chopped(here, here, 2, 4, false))).isEmpty())
        assertEquals(listOf(PlacedCue(SoundCue.destroy, here)), cues(listOf(GameEvent.Chopped(here, here, 1, 4, true))))
    }

    @Test
    fun `gathering an item into storage sounds the same as selling it`() {
        val stored = cues(listOf(GameEvent.Stored(here, "wheat", 1)))
        assertEquals(listOf(PlacedCue(SoundCue.sell, here)), stored)
        assertEquals(stored, cues(listOf(GameEvent.Sold(here, "wheat", 1.0, 1))))
    }

    @Test
    fun `many items stored in one stroke make one sound`() {
        val events = listOf(GameEvent.Stored(here, "wheat", 1), GameEvent.Stored(there, "wheat", 1))
        assertEquals(listOf(PlacedCue(SoundCue.sell, here)), cues(events))
    }

    @Test
    fun `tractor cargo stored sounds the same as tractor cargo sold`() {
        val stored = cues(listOf(GameEvent.TractorCargoStored("wheat", 1, here, 1)))
        assertEquals(listOf(PlacedCue(SoundCue.sell, here)), stored)
        assertEquals(stored, cues(listOf(GameEvent.TractorCargoSold("wheat", 1, 1.0, here, 1))))
    }

    @Test
    fun `cooked food stored keeps the cooking chime`() {
        assertEquals(listOf(PlacedCue(SoundCue.cookDone)), cues(listOf(GameEvent.Stored(here, "bread", 1))))
    }

    @Test
    fun `goods sold from storage come from no tile`() {
        assertEquals(listOf(PlacedCue(SoundCue.sell)), cues(listOf(GameEvent.StorageSold("wheat", 1, 1.0))))
    }

    @Test
    fun `a dog barks and a cat trills`() {
        assertEquals(
            listOf(PlacedCue(SoundCue.dogBark, here)),
            cues(listOf(GameEvent.Adopted(here))) { PetSpecies.dog },
        )
        assertEquals(
            listOf(PlacedCue(SoundCue.catTrill, here)),
            cues(listOf(GameEvent.Adopted(here))) { PetSpecies.cat },
        )
    }

    @Test
    fun `each kind of livestock has a voice`() {
        for (species in LivestockSpecies.all - LivestockSpecies.duck) {
            assertNotNull(SoundCue.voiceOfLivestock(species.id))
        }
    }

    @Test
    fun `ducks are silent`() {
        assertNull(SoundCue.voiceOfLivestock(LivestockSpecies.duck.id))
    }

    @Test
    fun `clips match their base name and numbered variants only`() {
        val clips = SoundClips("Animals", "Chicken", "ChickenAlternate")
        assertTrue(clips.includes("Chicken_01"))
        assertTrue(clips.includes("ChickenAlternate_02"))
        assertTrue(clips.includes("Chicken"))
        assertFalse(clips.includes("ChickenCoop_01"))
        assertFalse(clips.includes("Chickens"))
    }

    @Test
    fun `a sound is never louder than full volume`() {
        for (cue in SoundCue.entries) assertTrue(cue.trimDB <= 0)
    }

    @Test
    fun `only the fanfares duck the music`() {
        val fanfares = setOf(SoundCue.questComplete, SoundCue.levelUp, SoundCue.allCropsReady)
        for (cue in SoundCue.entries) assertEquals(cue in fanfares, cue.ducksMusic, cue.name)
    }

    @Test
    fun `the trims set the mix`() {
        assertEquals(0.0, SoundCue.levelUp.trimDB)
        assertEquals(-4.0, SoundCue.cowMoo.trimDB)
        assertEquals(-5.0, SoundCue.plant.trimDB)
        assertEquals(-5.0, SoundCue.sell.trimDB)
        assertEquals(-10.0, SoundCue.birdChirp.trimDB)
        assertEquals(-10.0, SoundCue.openPanel.trimDB)
    }

    @Test
    fun `every kind of sound has some cue`() {
        for (category in SoundCategory.entries) assertTrue(SoundCue.entries.any { it.category == category })
    }

    @Test
    fun `only the animals fade with distance`() {
        for (cue in SoundCue.entries) assertEquals(cue.category == SoundCategory.animals, cue.fadesWithDistance)
        assertTrue(SoundCue.cowMoo.fadesWithDistance)
        assertFalse(SoundCue.plant.fadesWithDistance)
        assertFalse(SoundCue.birdChirp.fadesWithDistance)
    }
}
