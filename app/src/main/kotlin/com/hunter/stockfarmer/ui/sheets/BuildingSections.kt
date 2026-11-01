package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.MarketQuestSection
import com.hunter.stockfarmer.ui.screens.ScreenIcon

@Composable
internal fun BuildingSections(
    session: GameSession,
    state: BuildingState,
    act: (SheetAction) -> Unit,
    onAnimal: (AnimalRef) -> Unit,
    onStocks: () -> Unit,
    onDemolish: () -> Unit,
    onEvents: (List<GameEvent>) -> Unit,
) {
    val building = state.placed.building
    val loader = rememberMenuAssetLoader()
    SheetCard {
        MenuImage(MenuPictures.building(building), loader)
        Text(building.name, style = MaterialTheme.typography.titleLarge)
        BuildingDetails.lines(building).forEach { Text(it) }
    }
    if (building.isMarketStand) MarketQuestSection(session, state.placed.origin, onEvents)
    state.workshop?.let { WorkshopSection(it, act) }
    if (building.isPetHouse) {
        val resident = state.resident
        if (resident == null) {
            PetChoiceForm { act(SheetAction.MovePetIn(it.breed, it.name)) }
        } else {
            SheetCard {
                ScreenIcon(resident.pet?.let(SheetPictures::resident))
                Text(resident.name, style = MaterialTheme.typography.titleMedium)
                resident.description.forEach { Text(it) }
                SheetButton("Summon") { act(SheetAction.Summon) }
                SheetButton("Name and animal menu") { onAnimal(resident.ref) }
            }
        }
    }
    state.homeMessage?.let { SheetCard { Text(it) } }
    if (building.isChickenCoop) ChickAppearanceSection(state.nextChick, act)
    state.homeLevel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    if (state.homeLevel != null && state.upgrade == null) Text("Fully upgraded")
    if (!building.isPetHouse) {
        state.animals.forEach { animal ->
            SheetCard {
                ScreenIcon(SheetPictures.animal(animal))
                Text(animal.name, style = MaterialTheme.typography.titleMedium)
                animal.description.forEach { Text(it) }
                SheetButton("Animal menu") { onAnimal(animal.ref) }
            }
        }
    }
    state.upgrade?.let { upgrade ->
        SheetCard {
            Text(upgrade.detail)
            upgrade.refusal?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SheetButton(upgrade.title) { act(SheetAction.Upgrade) }
        }
    }
    state.stocks?.let {
        SheetCard {
            Text(it)
            if (building.isUpgradedHouse) Text("Fully upgraded")
            SheetButton("Stock Market", onStocks)
        }
    }
    state.tractor?.let { TractorSection(it, act) }
    state.storage?.let {
        SheetCard { Text(it) }
        if (state.stored.isEmpty()) Text("Storage is empty")
        state.stored.forEach { item -> StorageSection(item, act) }
    }
    SheetCard {
        SheetButton("Move") { act(SheetAction.Move) }
        if (state.moving) Text("The Tractor must finish its trip before it can be moved.")
        Text(state.refund)
        state.demolishRefusal?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = onDemolish) { Text("Demolish", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun WorkshopSection(
    state: WorkshopState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    SheetCard {
        Text("Recipe", style = MaterialTheme.typography.titleMedium)
        SheetChoice(
            "Make",
            state.recipe.output.name,
            state.choices,
            label = { "${it.recipe.output.name}${it.requiredLevel?.let { level -> " · Level $level" }.orEmpty()}" },
            onChoose = { act(SheetAction.Recipe(it.recipe.id)) },
        )
        MenuImage(MenuPictures.stored(state.recipe.input), loader)
        val recipe = state.recipe
        Text("${recipe.inputCount} ${recipe.input.name} → ${recipe.outputCount} ${recipe.output.name}")
        Text("${state.inputStored} ${state.recipe.input.name} in storage")
        MenuImage(MenuPictures.stored(state.recipe.output), loader)
        Text("${state.outputStored} ${state.recipe.output.name} in storage")
        Text(state.statusText)
        state.startRefusal?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SheetButton("Start") { act(SheetAction.StartWork) }
        if (state.status == WorkshopStatus.Ready) SheetButton("Collect with Bucket") { act(SheetAction.CollectGoods) }
    }
}

@Composable
private fun ChickAppearanceSection(
    selected: String?,
    act: (SheetAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SheetCard {
        Text("Next chick: ${selected?.let(ChickenVariant::named)?.name ?: "Random Hen"}")
        TextButton(onClick = { expanded = !expanded }) { Text("Choose Next Chick") }
        if (expanded) {
            Text(
                "Choose what future chicks grow into. Existing chickens keep their appearance. " +
                    "Roosters are ornamental and lay no eggs.",
            )
            SheetButton("Random Hen") {
                act(SheetAction.NextChick(null))
                expanded = false
            }
            ChickenVariant.available.forEach { variant ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ScreenIcon(SheetPictures.chicken(variant))
                    TextButton(onClick = {
                        act(SheetAction.NextChick(variant.id))
                        expanded = false
                    }) {
                        Text(variant.name + if (selected == variant.id) " · Selected" else "")
                    }
                }
            }
        }
    }
}

@Composable
private fun TractorSection(
    state: TractorState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    SheetCard {
        Text(state.farmland)
        state.run?.let { Text(it) }
        if (state.canCancel) SheetButton("Cancel harvest") { act(SheetAction.CancelHarvest) }
    }
    if (state.crops.isEmpty()) {
        Text("Nothing is planted or gathered yet.")
    } else {
        Text("Harvest ready crops, then sell or store the load before the next trip.")
    }
    state.crops.forEach { crop ->
        SheetCard {
            val summary = crop.summary
            MenuImage(MenuPictures.stored(StorageItem.CropItem(summary.crop)), loader)
            Text(summary.crop.name, style = MaterialTheme.typography.titleMedium)
            summary.remaining?.let {
                Text("${summary.plantedCount} planted · ${summary.readyCount} ready")
                Text(if (it <= 0) "Ready to harvest" else "Ready in ${it.durationText}")
            }
            if (summary.gathered > 0) {
                Text("${summary.gathered} gathered")
                SheetButton("Store") { act(SheetAction.Cargo(summary.crop.id, false)) }
                SheetButton("Sell · ${crop.sale}") { act(SheetAction.Cargo(summary.crop.id, true)) }
            }
            if (crop.canHarvest) SheetButton("Harvest") { act(SheetAction.Harvest(summary.crop.id)) }
        }
    }
}

@Composable
private fun StorageSection(
    stored: StoredState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    var quantity by remember(stored.item.id, stored.count) { mutableStateOf(stored.count) }
    var trash by remember { mutableStateOf(false) }
    SheetCard {
        MenuImage(MenuPictures.stored(stored.item), loader)
        Text("${stored.count} ${stored.item.name}")
        if (stored.item.isSellable) Text("All sell for ${stored.sale}")
        Row {
            TextButton(onClick = { quantity = (quantity - 1).coerceAtLeast(1) }) { Text("−") }
            Text("$quantity")
            TextButton(onClick = { quantity = (quantity + 1).coerceAtMost(stored.count) }) { Text("+") }
            TextButton(onClick = { quantity = stored.count }) { Text("All") }
        }
        if (stored.item.isSellable) {
            SheetButton("Sell $quantity") {
                act(SheetAction.Stored(stored.item, quantity, true))
            }
        }
        TextButton(onClick = { trash = true }) { Text("${stored.item.discardedTitle} $quantity") }
    }
    if (trash) {
        SheetConfirmation(
            "${stored.item.discardedTitle} $quantity ${stored.item.name}?",
            "These items will leave storage for nothing.",
            { trash = false },
            {
                trash = false
                act(SheetAction.Stored(stored.item, quantity, false))
            },
        )
    }
}
