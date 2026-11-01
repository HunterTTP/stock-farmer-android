package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.ItemInfo
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.MarketQuestSection
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.EffectsCard
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.LevelRequirementNotice
import com.hunter.stockfarmer.ui.theme.NewDot
import com.hunter.stockfarmer.ui.theme.Theme

@Composable
internal fun BuildingSections(
    session: GameSession,
    state: BuildingState,
    act: (SheetAction) -> Unit,
    onChick: () -> Unit,
    onRecipes: () -> Unit,
    onUpgrade: () -> Unit,
    onEvents: (List<GameEvent>) -> Unit,
) {
    val building = state.placed.building
    val loader = rememberMenuAssetLoader()
    when {
        state.tractor != null -> {
            TractorSection(state.tractor, act)
        }

        building.isMarketStand -> {
            MarketQuestSection(session, state.placed.origin, onEvents)
        }

        else -> {
            SheetCard {
                MenuImage(MenuPictures.building(building), loader, Modifier.size(120.dp))
                Text(building.name, style = HudType.title2)
            }
            EffectsCard(ItemInfo.descriptionLines(building))
        }
    }
    when {
        building.isPetHouse -> {
            val resident = state.resident
            if (resident == null) {
                PetChoiceForm { act(SheetAction.MovePetIn(it.breed, it.name)) }
            } else {
                SheetCard(highlighted = true) {
                    ScreenIcon(resident.pet?.let(SheetPictures::resident), size = 96.dp)
                    Text(resident.name, style = HudType.title2)
                    resident.pet?.let {
                        Text(
                            "${it.kind.name} · lives in this house",
                            style = HudType.subheadline,
                            color = Theme.textSecondary,
                        )
                    }
                }
                SheetButton("Summon") { act(SheetAction.Summon) }
            }
        }

        building.isChickenCoop || building.livestockHome != null -> {
            state.homeMessage?.let { SheetCard { Text(it, style = HudType.subheadline) } }
            if (building.isChickenCoop) SheetButton("Choose Next Chick", onChick)
            SheetCard {
                state.homeLevel?.let { Text(it, style = HudType.headline) }
                if (state.upgrade == null) {
                    Text("Fully upgraded", color = Theme.textSecondary)
                } else {
                    val requiredLevel = state.upgrade.requiredLevel
                    if (requiredLevel != null) {
                        LevelRequirementNotice(requiredLevel)
                    } else {
                        Button(
                            onUpgrade,
                            Modifier.fillMaxWidth().alpha(if (state.upgrade.affordable) 1f else 0.4f),
                            colors = ButtonDefaults.buttonColors(containerColor = Theme.xp),
                        ) { Text("Upgrade") }
                    }
                }
            }
        }

        state.workshop != null -> {
            WorkshopSection(state.workshop, act, onRecipes)
        }
    }
}

@Composable
private fun WorkshopSection(
    state: WorkshopState,
    act: (SheetAction) -> Unit,
    onRecipes: () -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    if (state.choices.size > 1) {
        TextButton(onRecipes, Modifier.fillMaxWidth()) {
            Text("Choose Recipe", Modifier.weight(1f), style = HudType.headline)
            if (state.choices.any { it.isNew }) NewDot()
            Text("›")
        }
    }
    val recipe = state.recipe
    SheetCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MenuImage(MenuPictures.stored(recipe.input), loader, Modifier.size(56.dp))
                Text("${recipe.inputCount} ${recipe.input.name}", style = HudType.subheadline)
                Text("${state.inputStored} in storage", style = HudType.caption, color = Theme.textSecondary)
            }
            Text("→", style = HudType.title3)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MenuImage(MenuPictures.stored(recipe.output), loader, Modifier.size(56.dp))
                Text("${recipe.outputCount} ${recipe.output.name}", style = HudType.subheadline)
                Text("${state.outputStored} in storage", style = HudType.caption, color = Theme.textSecondary)
            }
        }
    }
    SheetCard(
        highlighted = state.status == WorkshopStatus.Ready,
    ) { Text(state.statusText, style = HudType.subheadline) }
    Button(
        onClick = { act(SheetAction.StartWork) },
        modifier = Modifier.fillMaxWidth().alpha(if (state.startRefusal == null) 1f else 0.4f),
    ) { Text("Start") }
}

@Composable
private fun TractorSection(
    state: TractorState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    SheetCard { Text(state.farmland, style = HudType.headline) }
    state.run?.let {
        SheetCard(highlighted = true) {
            Text(it, style = HudType.headline)
            if (state.canCancel) TextButton({ act(SheetAction.CancelHarvest) }) { Text("Cancel") }
        }
    }
    if (state.crops.isEmpty()) {
        SheetCard { Text("Nothing is planted or gathered yet.") }
    } else {
        Text(
            "Harvest ready crops, then sell or store the load before the next trip.",
            style = HudType.subheadline,
            color = Theme.textSecondary,
        )
    }
    state.crops.forEach { crop ->
        val summary = crop.summary
        SheetCard(highlighted = summary.readyCount > 0 || summary.gathered > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuImage(MenuPictures.stored(StorageItem.CropItem(summary.crop)), loader, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(summary.crop.name, style = HudType.headline)
                    summary.remaining?.let {
                        Text("${summary.plantedCount} planted", style = HudType.caption, color = Theme.textSecondary)
                        Text(
                            if (it <= 0) "Ready to harvest" else "Ready in ${it.durationText}",
                            style = HudType.subheadline,
                            color = if (summary.readyCount > 0) Theme.accent else Theme.textSecondary,
                        )
                    }
                    if (summary.gathered > 0) {
                        Text("${summary.gathered} gathered", style = HudType.subheadline)
                        Row {
                            Button({ act(SheetAction.Cargo(summary.crop.id, false)) }) { Text("Store") }
                            Button({ act(SheetAction.Cargo(summary.crop.id, true)) }) { Text("Sell · ${crop.sale}") }
                        }
                    }
                    if (crop.canHarvest) Button({ act(SheetAction.Harvest(summary.crop.id)) }) { Text("Harvest") }
                }
            }
        }
    }
}
