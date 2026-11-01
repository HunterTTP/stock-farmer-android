@file:Suppress("ktlint:standard:class-naming", "ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.PetSpecies

enum class DogBreed(
    val displayName: String,
) {
    labrador("Labrador"),
    germanShepherd("German Shepherd"),
    basenji("Basenji"),
    ;

    val rawValue: String get() = name
    val dogs: List<Dog> get() = Dog.all.filter { it.breed == this }
}

data class Dog(
    val id: String,
    val breed: DogBreed,
    val colorName: String,
    val assetName: String,
    val doghouseRow: Int,
) {
    val name: String get() = "$colorName " + breed.displayName

    companion object {
        val all =
            listOf(
                Dog(
                    "labrador-brown",
                    DogBreed.labrador,
                    "Brown",
                    "Dog_Labrador_Brown_48x48",
                    0,
                ),
                Dog(
                    "labrador-white",
                    DogBreed.labrador,
                    "White",
                    "Dog_Labrador_White_48x48",
                    1,
                ),
                Dog(
                    "labrador-darkBrown",
                    DogBreed.labrador,
                    "Dark Brown",
                    "Dog_Labrador_Dark_Brown_48x48",
                    2,
                ),
                Dog(
                    "basenji-orange",
                    DogBreed.basenji,
                    "Orange",
                    "Dog_Basenji_Orange_48x48",
                    3,
                ),
                Dog(
                    "basenji-brown",
                    DogBreed.basenji,
                    "Brown",
                    "Dog_Basenji_Brown_48x48",
                    5,
                ),
                Dog(
                    "basenji-gray",
                    DogBreed.basenji,
                    "Gray",
                    "Dog_Basenji_Gray_48x48",
                    4,
                ),
                Dog(
                    "germanShepherd-brown",
                    DogBreed.germanShepherd,
                    "Brown",
                    "Dog_German_Shepherd_Brown_48x48",
                    6,
                ),
                Dog(
                    "germanShepherd-gray",
                    DogBreed.germanShepherd,
                    "Gray",
                    "Dog_German_Shepherd_Gray_48x48",
                    7,
                ),
                Dog(
                    "germanShepherd-darkBrown",
                    DogBreed.germanShepherd,
                    "Dark Brown",
                    "Dog_German_Shepherd_Dark_Brown_48x48",
                    8,
                ),
            )
        val starter = all[0]

        fun named(id: String): Dog? = all.firstOrNull { it.id == id }
    }
}

sealed interface PetBreed {
    data class cat(
        val cat: Cat,
    ) : PetBreed

    data class dog(
        val dog: Dog,
    ) : PetBreed

    val species: PetSpecies get() =
        when (this) {
            is cat -> PetSpecies.cat
            is dog -> PetSpecies.dog
        }
    val id: String get() =
        when (this) {
            is cat -> cat.id
            is dog -> dog.id
        }
    val name: String get() =
        when (this) {
            is cat -> cat.name
            is dog -> dog.name
        }
}
