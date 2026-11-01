package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

internal class AnimalSpritesTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val point = GridPoint(10, 10)

    @Test
    fun `directional frames use right up left down sheet order`() {
        val hen = AnimalSprites.chicken(Chicken(0, "hen-brown", "Hen", point, hatchedAt = Instant.EPOCH), now)
        for (facing in Facing.entries) {
            assertEquals(SheetFrame(0, facing.ordinal), hen.standing(facing))
            assertEquals(SheetFrame(1, facing.ordinal * 6), hen.walk(facing).frame(0.0))
            assertEquals(SheetFrame(1, facing.ordinal * 6 + 5), hen.walk(facing).frame(0.499))
            assertEquals(0.5, hen.walk(facing).duration, 0.000001)
        }
    }

    @Test
    fun `cats have four walking frames and retain square tile size`() {
        val cat = AnimalSprites.pet(Pet(breed = Cat.starter.id, name = "Cat", position = point))
        assertEquals(AnimalSheet(32, 9), cat.sheet)
        assertEquals(100f, cat.drawWidth)
        assertEquals(SheetFrame(1, 0), cat.standing(Facing.LEFT))
        assertEquals(SheetFrame(8, 3), cat.walk(Facing.LEFT).frame(0.499))
        val reverse = AnimationClip(3, 0, 4, 0.2, reversed = true)
        assertEquals(SheetFrame(3, 3), reverse.frame(0.0))
        assertEquals(SheetFrame(3, 0), reverse.frame(0.799))
    }

    @Test
    fun `dogs skip label rows and use breed specific dimensions`() {
        for (dog in Dog.all) {
            val look = AnimalSprites.pet(Pet(PetSpecies.dog, dog.id, "Dog", point))
            val basenji = dog.id.startsWith("basenji")
            assertEquals(AnimalSheet(if (basenji) 96 else 144, if (basenji) 15 else 13), look.sheet)
            assertEquals(SheetFrame(2, 18), look.standing(Facing.DOWN))
            assertEquals(SheetFrame(4, 18), look.walk(Facing.DOWN).frame(0.0))
        }
    }

    @Test
    fun `growth selects baby art until the exact growth boundary`() {
        val chicken = Chicken(0, ChickenVariant.all.first().id, "Chick", point, hatchedAt = now)
        assertEquals(25f, AnimalSprites.chicken(chicken, now).drawWidth)
        assertEquals("Chick_48x48", AnimalSprites.chicken(chicken, now).asset)
        assertEquals(52f, AnimalSprites.chicken(chicken, now.plusSeconds(600)).drawWidth)
        for (species in LivestockSpecies.all) {
            val animal = Livestock.born(0, species.variants.first(), "Baby", point, now)
            val baby = AnimalSprites.livestock(animal, now)
            val grown = AnimalSprites.livestock(animal, now.plusSeconds(species.growDuration.toLong()))
            assertTrue(baby.baby)
            assertFalse(grown.baby)
            assertEquals(animal.look.babyAssetName, baby.asset)
            assertEquals(animal.look.assetName, grown.asset)
        }
    }

    @Test
    fun `sheared sheep use six frame directional rows until wool is ready`() {
        val variant = LivestockSpecies.sheep.variants.first { it.hasShearedPictures }
        val sheep =
            Livestock
                .born(0, variant, "Sheep", point, Instant.EPOCH)
                .copy(lastWorkedAt = now, readyAt = now.plusSeconds(300))
        val sheared = AnimalSprites.livestock(sheep, now)
        assertEquals(8, sheared.sheet.rows)
        assertEquals(SheetFrame(5, 12), sheared.standing(Facing.LEFT))
        assertEquals(SheetFrame(6, 12), sheared.walk(Facing.LEFT).frame(0.0))
        assertEquals(SheetFrame(0, 2), AnimalSprites.livestock(sheep, sheep.readyAt).standing(Facing.LEFT))
        assertEquals(4, AnimalSprites.livestock(sheep.copy(bornAt = now), now).sheet.rows)
    }

    @Test
    fun `ducks have directional waterlines and no grazing animation`() {
        val duck = Livestock.born(0, LivestockSpecies.duck.variants.first(), "Duck", point, Instant.EPOCH)
        val look = AnimalSprites.livestock(duck, now)
        assertEquals(0.77f, look.waterline(Facing.UP))
        assertEquals(0.8f, look.waterline(Facing.DOWN))
        assertEquals(0.75f, look.waterline(Facing.LEFT))
        repeat(20) { seed -> assertEquals(1, look.rest(1, Facing.DOWN, Random(seed)).size) }
        val cow = Livestock.born(0, LivestockSpecies.cow.variants.first(), "Cow", point, Instant.EPOCH)
        assertNull(AnimalSprites.livestock(cow, now).waterline(Facing.DOWN))
    }

    @Test
    fun `chicks never lie down and seeded rest plans are repeatable`() {
        val chick = AnimalSprites.chicken(Chicken(0, "hen-brown", "Chick", point, hatchedAt = now), now)
        repeat(20) { seed -> assertEquals(1, chick.rest(1, Facing.DOWN, Random(seed)).size) }
        val cat = AnimalSprites.pet(Pet(breed = Cat.starter.id, name = "Cat", position = point))
        assertEquals(cat.rest(3, Facing.RIGHT, Random(42)), cat.rest(3, Facing.RIGHT, Random(42)))
        val clips = listOf(AnimationClip(1, 0, 2, 0.45), AnimationClip(3, 0, 4, 0.2))
        assertEquals(SheetFrame(3, 0), clips.frame(0.9))
        assertEquals(SheetFrame(3, 3), clips.frame(100.0))
    }
}
