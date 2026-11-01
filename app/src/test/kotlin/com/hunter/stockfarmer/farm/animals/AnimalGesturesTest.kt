package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AnimalGesturesTest {
    private val now = Instant.EPOCH
    private val pet = AnimalRef.Pet(GridPoint(5, 5))
    private val hen = AnimalRef.Chicken(GridPoint(7, 7), 4)

    @Test
    fun `touch uses nearest interpolated tile center excludes inside and includes tile edges`() {
        val targets = listOf(AnimalHitTarget(pet, 150f, 100f), AnimalHitTarget(hen, 185f, 100f))
        assertEquals(pet, animalAtTouch(100f, 100f, targets))
        assertEquals(hen, animalAtTouch(180f, 100f, targets))
        assertEquals(pet, animalAtTouch(180f, 100f, targets.map { it.copy(inside = it.animal == hen) }))
        assertNull(animalAtTouch(99f, 100f, targets))
        assertNull(animalAtTouch(150f, 151f, targets))
    }

    @Test
    fun `pet carries on at once while a nudge waits for the current step`() {
        val targets = listOf(AnimalHitTarget(pet, 100f, 100f, stepRemaining = 0.2))
        assertEquals(
            AnimalReaction(pet, AnimalGesture.PET, 0.0),
            reactionAtTouch(100f, 100f, AnimalGesture.PET, targets),
        )
        assertEquals(AnimalReaction(pet, AnimalGesture.NUDGE, 0.2), animalReaction(pet, AnimalGesture.NUDGE, targets))
        assertNull(animalReaction(hen, AnimalGesture.PET, targets))
    }

    @Test
    fun `three hearts start after 600ms spaced 800ms and float for a second`() {
        val hearts = AnimalHearts()
        hearts.pet(now)
        assertTrue(hearts.hearts(now.plusMillis(599)).isEmpty())
        assertEquals(listOf(Heart(0f, 1f)), hearts.hearts(now.plusMillis(600)))
        val fading = hearts.hearts(now.plusMillis(1350)).single()
        assertEquals(30f, fading.rise, 0.0001f)
        assertEquals(0.5f, fading.alpha, 0.0001f)
        assertEquals(2, hearts.hearts(now.plusMillis(1400)).size)
        assertEquals(1, hearts.hearts(now.plusMillis(3000)).size)
        assertTrue(hearts.hearts(now.plusMillis(3200)).isEmpty())
        hearts.pet(now.plusSeconds(4))
        assertTrue(hearts.hearts(now.plusMillis(4500)).isEmpty())
    }

    @Test
    fun `shelter enters only after arrival then fades over the door and comes back out`() {
        val shelter = AnimalShelter()
        val entrance = AnimalEntrance(GridPoint(10, 10), GridPoint(9, 10))
        val pose = AnimalPose(10f, 10f, Facing.LEFT, false, false, 0.0)
        assertFalse(shelter.update(pose, entrance, false, now).inside)
        assertFalse(shelter.update(pose.copy(walking = true), entrance, true, now).inside)
        val entering = shelter.update(pose, entrance, true, now)
        assertTrue(entering.inside)
        assertEquals(1f, entering.alpha)
        val halfway = shelter.update(pose, entrance, true, now.plusMillis(250))
        assertEquals(9.5f, halfway.pose.row)
        assertEquals(Facing.UP, halfway.pose.facing)
        assertEquals(0f, shelter.update(pose, entrance, true, now.plusMillis(500)).alpha)
        val emerging = shelter.update(pose, entrance, false, now.plusSeconds(1))
        assertFalse(emerging.inside)
        assertEquals(9f, emerging.pose.row)
        assertEquals(Facing.DOWN, emerging.pose.facing)
        val outside = shelter.update(pose, entrance, false, now.plusMillis(1500))
        assertEquals(10f, outside.pose.row)
        assertEquals(1f, outside.alpha)
    }

    @Test
    fun `a loaded animal already sheltering starts hidden and ducks without a door stay visible`() {
        val pose = AnimalPose(10f, 10f, Facing.DOWN, false, false, 0.0)
        val entrance = AnimalEntrance(GridPoint(10, 10), GridPoint(9, 10))
        assertEquals(0f, AnimalShelter().update(pose, entrance, true, now).alpha)
        assertEquals(1f, AnimalShelter().update(pose, null, true, now).alpha)
    }
}
