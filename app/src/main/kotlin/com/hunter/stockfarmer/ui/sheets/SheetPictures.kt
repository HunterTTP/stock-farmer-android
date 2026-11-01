package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.ui.screens.ScreenArt

internal object SheetPictures {
    fun resident(resident: Pet): ScreenArt =
        if (resident.species == PetSpecies.dog) {
            ScreenArt(
                "images/animals/Dogs/Dogs_Doghouse_Sleeping_48x48.png",
                columns = 8,
                rows = 9,
                row = resident.dog.doghouseRow,
            )
        } else {
            pet(resident.kind)
        }

    fun pet(breed: PetBreed): ScreenArt =
        when (breed) {
            is PetBreed.cat -> {
                ScreenArt(
                    "images/animals/Cats/${breed.cat.assetName}.png",
                    frameWidth = 32,
                    frameHeight = 32,
                    row = 1,
                )
            }

            is PetBreed.dog -> {
                ScreenArt(
                    "images/animals/Dogs/${breed.dog.assetName}.png",
                    columns = 24,
                    rows = if (breed.dog.breed == DogBreed.basenji) 15 else 13,
                    row = 2,
                )
            }
        }

    fun chicken(variant: ChickenVariant) =
        ScreenArt(
            "images/animals/Chickens/${variant.assetName}.png",
            frameWidth = 48,
            rows = 4,
            column = 3,
        )

    fun animal(animal: AnimalState): ScreenArt? {
        animal.pet?.let { return pet(it.kind) }
        animal.chicken?.let { return chicken(it) }
        val (species, variant) = animal.livestockVariant?.let(LivestockSpecies::variant) ?: return null
        return ScreenArt(
            "images/animals/${species.folder}/${variant.assetName}.png",
            frameWidth = if (species.id in listOf("cow", "donkey")) 144 else 96,
            rows = if (variant.hasShearedPictures) 8 else 4,
        )
    }
}
