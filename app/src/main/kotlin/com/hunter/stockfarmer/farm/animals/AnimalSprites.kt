package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import java.time.Instant
import kotlin.random.Random

internal enum class AnimalKind { CAT, DOG, CHICKEN, LIVESTOCK }

/** Sheet layouts and timings per animal kind. */
internal data class AnimalSprites(
    val folder: String,
    val asset: String,
    val sheet: AnimalSheet,
    val kind: AnimalKind,
    val drawWidth: Float,
    val baby: Boolean = false,
    val sheared: Boolean = false,
    val duck: Boolean = false,
) {
    val defaultFacing: Facing get() = if (kind == AnimalKind.CAT) Facing.RIGHT else Facing.DOWN

    fun standing(facing: Facing): SheetFrame =
        when (kind) {
            AnimalKind.CAT -> SheetFrame(1, 0)
            AnimalKind.DOG -> SheetFrame(2, facing.ordinal * 6)
            else -> SheetFrame(if (sheared) 5 else 0, facing.ordinal * if (sheared) 6 else 1)
        }

    fun walk(facing: Facing): AnimationClip =
        when (kind) {
            AnimalKind.CAT -> AnimationClip(8, 0, 4, STEP_DURATION / 4)
            AnimalKind.DOG -> AnimationClip(4, facing.ordinal * 6, 6, STEP_DURATION / 6)
            AnimalKind.CHICKEN -> AnimationClip(1, facing.ordinal * 6, 6, STEP_DURATION / 6)
            AnimalKind.LIVESTOCK -> AnimationClip(if (sheared) 6 else 2, facing.ordinal * 6, 6, STEP_DURATION / 6)
        }

    fun waterline(facing: Facing): Float? =
        if (!duck) {
            null
        } else {
            when (facing) {
                Facing.UP -> 0.77f
                Facing.DOWN -> 0.8f
                else -> 0.75f
            }
        }

    fun rest(
        tiles: Int,
        facing: Facing,
        random: Random,
    ): List<AnimationClip> {
        val still = standing(facing)
        val parts = mutableListOf<AnimationClip>()
        when (kind) {
            AnimalKind.CAT -> {
                parts += AnimationClip(1, 0, 2, 0.45, random.nextInt(1 + tiles, 3 + 2 * tiles))
                if (random.nextBoolean()) {
                    parts += AnimationClip(3, 0, 4, 0.2)
                    parts += AnimationClip(5, 0, 4, 0.3, random.nextInt(2, 5))
                    parts += AnimationClip(4, 0, 2, 0.6, random.nextInt(2, 5))
                    if (random.nextBoolean()) parts += AnimationClip(7, 0, 3, 0.8, random.nextInt(3, 7))
                    parts += AnimationClip(3, 0, 4, 0.2, reversed = true)
                }
            }

            AnimalKind.DOG -> {
                parts += AnimationClip(2, facing.ordinal * 6, 6, 0.3, random.nextInt(1 + tiles, 3 + 2 * tiles))
                if (random.nextBoolean()) {
                    parts += AnimationClip(8, facing.ordinal * 6, 6, 0.2, random.nextInt(2, 5))
                }
            }

            else -> {
                parts += AnimationClip(still.row, still.column, 1, random.nextDouble(1.0, 2.0) * (1 + tiles))
                if (!duck && !(kind == AnimalKind.CHICKEN && baby) && random.nextBoolean()) {
                    parts +=
                        if (kind == AnimalKind.CHICKEN) {
                            AnimationClip(
                                3,
                                facing.ordinal * 2,
                                2,
                                0.6,
                                maxOf(1, (random.nextDouble(4.0, 9.0) / 1.2).toInt()),
                            )
                        } else {
                            AnimationClip(if (sheared) 7 else 3, facing.ordinal * 6, 6, 0.3, random.nextInt(2, 5))
                        }
                }
            }
        }
        return parts
    }

    companion object {
        const val STEP_DURATION = 0.5

        // Constants, so choosing a width for each animal each frame builds no list.
        private val WIDE_WHEN_GROWN = setOf("cow", "donkey")
        private val NARROW_WHEN_BABY = setOf("duck", "rabbit")

        fun pet(pet: Pet): AnimalSprites =
            if (pet.species == PetSpecies.cat) {
                AnimalSprites("Cats", pet.cat.assetName, AnimalSheet(32, 9), AnimalKind.CAT, 100f)
            } else {
                val basenji = pet.dog.breed == DogBreed.basenji
                val width = if (basenji) 96 else 144
                AnimalSprites(
                    "Dogs",
                    pet.dog.assetName,
                    AnimalSheet(width, if (basenji) 15 else 13),
                    AnimalKind.DOG,
                    width * 52f / 48,
                )
            }

        fun chicken(
            chicken: Chicken,
            now: Instant,
        ): AnimalSprites {
            val baby = !chicken.isGrown(now)
            return AnimalSprites(
                "Chickens",
                if (baby) "Chick_48x48" else chicken.kind.assetName,
                AnimalSheet(48, 4),
                AnimalKind.CHICKEN,
                if (baby) 25f else 52f,
                baby,
            )
        }

        fun livestock(
            animal: Livestock,
            now: Instant,
        ): AnimalSprites {
            val baby = !animal.isGrown(now)
            val species = animal.species
            val shearedSheet = animal.look.hasShearedPictures && !baby
            val width =
                when {
                    !baby && species.id in WIDE_WHEN_GROWN -> 144
                    baby && species.id in NARROW_WHEN_BABY -> 48
                    else -> 96
                }
            return AnimalSprites(
                species.folder,
                if (baby) animal.look.babyAssetName else animal.look.assetName,
                AnimalSheet(width, if (shearedSheet) 8 else 4),
                AnimalKind.LIVESTOCK,
                width * 52f / 48,
                baby,
                shearedSheet && animal.lastWorkedAt != null && now < animal.readyAt,
                species.id == "duck",
            )
        }
    }
}
