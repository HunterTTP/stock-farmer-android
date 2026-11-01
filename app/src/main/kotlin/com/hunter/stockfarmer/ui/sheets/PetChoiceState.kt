package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies

internal data class PetChoiceState(
    val species: PetSpecies = PetSpecies.cat,
    val cat: Cat = Cat.starter,
    val dog: Dog = Dog.starter,
    val name: String = "",
) {
    val breed: PetBreed get() = if (species == PetSpecies.cat) PetBreed.cat(cat) else PetBreed.dog(dog)

    fun chooseBreed(breed: DogBreed): PetChoiceState = copy(dog = breed.dogs.first())

    fun named(name: String): PetChoiceState = copy(name = name.take(Pet.MAX_NAME_LENGTH))
}
