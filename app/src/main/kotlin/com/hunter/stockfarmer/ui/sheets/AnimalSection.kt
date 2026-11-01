package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

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
            containerColor = Theme.launchBackground,
            title = {
                BasicText(
                    "Give it a name",
                    style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                )
            },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(Pet.MAX_NAME_LENGTH) },
                    label = {
                        BasicText("Name", style = HudType.caption.copy(color = Theme.textSecondary))
                    },
                    singleLine = true,
                )
            },
            confirmButton = {
                PillButton(
                    title = "Save",
                    onClick = {
                        act(SheetAction.Rename(name))
                        onClose()
                    },
                    role = PillRole.accent,
                )
            },
            dismissButton = {
                PillButton("Cancel", onClick = onClose, role = PillRole.neutral)
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = Theme.launchBackground,
            title = {
                BasicText(
                    animal.name,
                    style = HudType.title3.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    animal.description.forEach {
                        BasicText(it, style = HudType.subheadline.copy(color = Theme.textSecondary))
                    }
                    message?.let {
                        BasicText(it, style = HudType.subheadline.copy(color = Theme.spend))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillButton(
                            title = "Pet",
                            onClick = { onInteraction?.invoke(AnimalInteraction.PET) },
                            modifier = Modifier.fillMaxWidth(),
                            role = PillRole.neutral,
                            enabled = onInteraction != null,
                        )
                        PillButton(
                            title = "Nudge",
                            onClick = { onInteraction?.invoke(AnimalInteraction.NUDGE) },
                            modifier = Modifier.fillMaxWidth(),
                            role = PillRole.neutral,
                            enabled = onInteraction != null,
                        )
                        PillButton(
                            title = "Name",
                            onClick = { naming = true },
                            modifier = Modifier.fillMaxWidth(),
                            role = PillRole.neutral,
                        )
                        if (animal.releasable) {
                            PillButton(
                                title = "Release",
                                onClick = { act(SheetAction.Release) },
                                modifier = Modifier.fillMaxWidth(),
                                role = PillRole.destructive,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                PillButton(
                    title = "Cancel",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    role = PillRole.neutral,
                )
            },
        )
    }
}

@Composable
internal fun PetChoiceForm(onConfirm: (PetChoiceState) -> Unit) {
    var choice by remember { mutableStateOf(PetChoiceState()) }
    SheetCard {
        ScreenIcon(SheetPictures.pet(choice.breed))
        BasicText(
            "Who will live in this house?",
            style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
        )
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
            SheetChoice("Color", choice.cat.colorName, Cat.all, { it.colorName }) {
                choice = choice.copy(cat = it)
            }
        }
        OutlinedTextField(
            choice.name,
            { choice = choice.named(it) },
            label = {
                BasicText("Name", style = HudType.caption.copy(color = Theme.textSecondary))
            },
            singleLine = true,
        )
        PillButton(
            title = "OK",
            onClick = { onConfirm(choice) },
            modifier = Modifier.fillMaxWidth(),
            role = PillRole.accent,
        )
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
        Row(
            Modifier
                .fillMaxWidth()
                .glassCard(14.dp)
                .clickable(role = Role.Button, onClick = { expanded = true })
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = HudType.caption.copy(color = Theme.textSecondary))
                BasicText(
                    value,
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
                )
            }
            BasicText("▾", style = HudType.subheadline.copy(color = Theme.textSecondary))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                val text = label(option) + if (label(option) == value) " ✓" else ""
                DropdownMenuItem(
                    text = {
                        BasicText(text, style = HudType.subheadline.copy(color = Theme.textPrimary))
                    },
                    onClick = {
                        onChoose(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
