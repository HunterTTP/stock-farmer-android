package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.random.Random

internal class AnimalAssetsTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val point = GridPoint(10, 10)
    private val assets = File("src/main/assets/images/animals")

    @Test
    fun `every catalog animal and pose fits the bundled sheet without label rows`() {
        val looks =
            buildList {
                for (cat in Cat.all) add(AnimalSprites.pet(Pet(breed = cat.id, name = "Cat", position = point)))
                for (dog in Dog.all) add(AnimalSprites.pet(Pet(PetSpecies.dog, dog.id, "Dog", point)))
                for (variant in ChickenVariant.available) {
                    val chicken = Chicken(0, variant.id, "Chicken", point, hatchedAt = now)
                    add(AnimalSprites.chicken(chicken, now))
                    add(AnimalSprites.chicken(chicken, now.plusSeconds(600)))
                }
                for (species in LivestockSpecies.all) {
                    for (variant in species.variants) {
                        val animal = Livestock.born(0, variant, "Animal", point, now)
                        add(AnimalSprites.livestock(animal, now))
                        add(AnimalSprites.livestock(animal.copy(bornAt = Instant.EPOCH), now))
                        add(
                            AnimalSprites.livestock(
                                animal.copy(bornAt = Instant.EPOCH, lastWorkedAt = now, readyAt = now.plusSeconds(300)),
                                now,
                            ),
                        )
                    }
                }
            }.distinct()
        assertTrue(looks.size > 70)
        for (look in looks) {
            val file = File(assets, "${look.folder}/${look.asset}.png")
            assertTrue(file.isFile, file.path)
            ImageIO.createImageInputStream(file).use { input ->
                val reader = ImageIO.getImageReaders(input).next()
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    for (facing in Facing.entries) {
                        look.sheet.rect(look.standing(facing), width, height)
                        val clips = look.rest(3, facing, Random(42)) + look.walk(facing)
                        for (clip in clips) {
                            repeat(clip.count) { frame ->
                                look.sheet.rect(SheetFrame(clip.row, clip.firstColumn + frame), width, height)
                            }
                        }
                    }
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    @Test
    fun `all fish and the egg image are bundled`() {
        for (kind in FishKind.all) assertTrue(File(assets, "Fish/${kind.assetName}.png").isFile, kind.id)
        assertTrue(File(assets, "Chickens/Egg_Big_48x48.png").isFile)
    }

    @Test
    fun `sheet regions count rows from the image top and reject invalid cells`() {
        val sheet = AnimalSheet(144, 13)
        assertEquals(FrameRect(2592, 384, 144, 96), sheet.rect(SheetFrame(4, 18), 3456, 1248))
        assertThrows(IllegalArgumentException::class.java) { sheet.rect(SheetFrame(13, 0), 3456, 1248) }
        assertThrows(IllegalArgumentException::class.java) { sheet.rect(SheetFrame(0, 24), 3456, 1248) }
    }

    @Test
    fun `standing dog and cat frames contain measurable animal bodies`() {
        val looks =
            Dog.all.map { AnimalSprites.pet(Pet(PetSpecies.dog, it.id, "Dog", point)) } +
                Cat.all.map { AnimalSprites.pet(Pet(breed = it.id, name = "Cat", position = point)) }
        for (look in looks) {
            val image = ImageIO.read(File(assets, "${look.folder}/${look.asset}.png"))
            for (facing in Facing.entries) {
                val rect = look.sheet.rect(look.standing(facing), image.width, image.height)
                assertNotNull(
                    AnimalFootprint.measure(rect.width, rect.height) { x, y ->
                        image.getRGB(rect.x + x, rect.y + y) ushr 24
                    },
                    "${look.asset}: $facing",
                )
            }
            image.flush()
        }
    }
}
