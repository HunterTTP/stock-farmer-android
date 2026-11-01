package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard

@Composable
internal fun AnimalMenu(
    animal: AnimalState,
    act: (SheetAction) -> Unit,
    onClose: () -> Unit,
    message: String?,
    onInteraction: ((AnimalInteraction) -> Unit)?,
) {
    var name by remember(animal.ref) { mutableStateOf(animal.name) }
    var naming by remember(animal.ref) { mutableStateOf(false) }
    if (naming) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Give it a name") },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(Pet.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton({
                    act(SheetAction.Rename(name))
                    onClose()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClose) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(animal.name) },
            text = {
                androidx.compose.foundation.layout.Column {
                    animal.description.forEach { Text(it) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton({ onInteraction?.invoke(AnimalInteraction.PET) }, enabled = onInteraction != null) {
                        Text("Pet")
                    }
                    TextButton({ onInteraction?.invoke(AnimalInteraction.NUDGE) }, enabled = onInteraction != null) {
                        Text("Nudge")
                    }
                    TextButton({ naming = true }) { Text("Name") }
                    if (animal.releasable) {
                        TextButton({ act(SheetAction.Release) }) {
                            Text("Release", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClose) { Text("Cancel") } },
        )
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
        TextButton(onClick = { expanded = true }, Modifier.fillMaxWidth().categoryCard(false)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = HudType.caption, color = Theme.textSecondary)
                    Text(value, style = HudType.subheadline, color = Theme.textPrimary)
                }
                Text("⌃⌄", color = Theme.textSecondary)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                val text = label(option) + if (label(option) == value) " ✓" else ""
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onChoose(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
