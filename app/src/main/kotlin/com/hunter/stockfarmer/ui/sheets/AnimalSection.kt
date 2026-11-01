package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.ScreenIcon

@Composable
internal fun AnimalSection(
    animal: AnimalState,
    act: (SheetAction) -> Unit,
    onRelease: () -> Unit,
    onInteraction: ((AnimalInteraction) -> Unit)?,
) {
    var name by remember(animal.ref) { mutableStateOf(animal.name) }
    val loader = rememberMenuAssetLoader()
    SheetCard {
        ScreenIcon(SheetPictures.animal(animal))
        Text(animal.name, style = MaterialTheme.typography.titleLarge)
        animal.description.forEach { Text(it) }
        Button(onClick = { onInteraction?.invoke(AnimalInteraction.PET) }, enabled = onInteraction != null) {
            Text("Pet")
        }
        Button(onClick = { onInteraction?.invoke(AnimalInteraction.NUDGE) }, enabled = onInteraction != null) {
            Text("Nudge")
        }
        OutlinedTextField(name, { name = it.take(Pet.MAX_NAME_LENGTH) }, label = { Text("Name") }, singleLine = true)
        SheetButton("Save name") { act(SheetAction.Rename(name)) }
        if (animal.releasable) {
            TextButton(onClick = onRelease) {
                Text("Release", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (animal.foods.isNotEmpty()) {
        SheetCard {
            Text("Feed from storage", style = MaterialTheme.typography.titleMedium)
            animal.foods.forEach { food ->
                MenuImage(MenuPictures.stored(food.item), loader)
                SheetButton("Feed ${food.item.name} · ${food.count} in storage") { act(SheetAction.Feed(food.item)) }
            }
        }
    }
}

@Composable
internal fun PetChoiceForm(onConfirm: (PetChoiceState) -> Unit) {
    var choice by remember { mutableStateOf(PetChoiceState()) }
    SheetCard {
        ScreenIcon(SheetPictures.pet(choice.breed))
        Text("Who will live in this house?", style = MaterialTheme.typography.titleMedium)
        SheetChoice("Pet", choice.species.displayName, PetSpecies.entries, { it.displayName }) {
            choice = choice.copy(species = it)
        }
        if (choice.species == PetSpecies.dog) {
            SheetChoice("Breed", choice.dog.breed.displayName, DogBreed.entries, { it.displayName }) {
                choice = choice.chooseBreed(it)
            }
            SheetChoice("Color", choice.dog.colorName, choice.dog.breed.dogs, { it.colorName }) {
                choice = choice.copy(dog = it)
            }
        } else {
            SheetChoice("Color", choice.cat.colorName, Cat.all, { it.colorName }) { choice = choice.copy(cat = it) }
        }
        OutlinedTextField(choice.name, { choice = choice.named(it) }, label = { Text("Name") }, singleLine = true)
        SheetButton("OK") { onConfirm(choice) }
    }
}

@Composable
internal fun <T> SheetChoice(
    title: String,
    value: String,
    options: List<T>,
    label: (T) -> String,
    onChoose: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text("$title: $value ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    onChoose(option)
                    expanded = false
                })
            }
        }
    }
}
