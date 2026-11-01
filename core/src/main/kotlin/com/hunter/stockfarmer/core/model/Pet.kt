@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Dog
import kotlinx.serialization.Serializable

/** What kind of animal a pet house can hold. */
@Serializable
enum class PetSpecies(
    val displayName: String,
) {
    cat("Cat"),
    dog("Dog"),
    ;

    val rawValue: String get() = name
}

/** One breed of a pet: a cat's or a dog's. */
sealed interface PetBreed {
    data class OfCat(
        val cat: Cat,
    ) : PetBreed

    data class OfDog(
        val dog: Dog,
    ) : PetBreed
}

/** The animal living in a pet house, and where it is right now. */
@Serializable
data class Pet(
    val species: PetSpecies = PetSpecies.cat,
    /** The id of a [Cat] or of a [Dog], as the species says. */
    val breed: String,
    val name: String,
    val position: GridPoint,
    val meal: AnimalMeal? = null,
) {
    val cat: Cat get() = Cat.named(breed) ?: Cat.starter

    val dog: Dog get() = Dog.named(breed) ?: Dog.starter

    val kind: PetBreed get() = if (species == PetSpecies.cat) PetBreed.OfCat(cat) else PetBreed.OfDog(dog)

    companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
