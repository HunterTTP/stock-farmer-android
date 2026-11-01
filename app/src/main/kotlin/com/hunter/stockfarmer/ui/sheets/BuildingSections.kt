package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

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
                BasicText(
                    building.name,
                    style = HudType.title2.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                )
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
                    BasicText(
                        resident.name,
                        style = HudType.title2.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                    )
                    resident.pet?.let {
                        BasicText(
                            "${it.kind.name} · lives in this house",
                            style = HudType.subheadline.copy(color = Theme.textSecondary),
                        )
                    }
                }
                PillButton(
                    "Summon",
                    onClick = { act(SheetAction.Summon) },
                    modifier = Modifier.fillMaxWidth(),
                    role = PillRole.accent,
                )
            }
        }

        building.isChickenCoop || building.livestockHome != null -> {
            state.homeMessage?.let {
                SheetCard {
                    BasicText(it, style = HudType.subheadline.copy(color = Theme.textPrimary))
                }
            }
            if (building.isChickenCoop) {
                PillButton(
                    "Choose Next Chick",
                    onClick = onChick,
                    modifier = Modifier.fillMaxWidth(),
                    role = PillRole.neutral,
                )
            }
            SheetCard {
                state.homeLevel?.let {
                    BasicText(it, style = HudType.headline.copy(color = Theme.textPrimary))
                }
                if (state.upgrade == null) {
                    BasicText("Fully upgraded", style = HudType.subheadline.copy(color = Theme.textSecondary))
                } else {
                    val requiredLevel = state.upgrade.requiredLevel
                    if (requiredLevel != null) {
                        LevelRequirementNotice(requiredLevel)
                    } else {
                        PillButton(
                            title = "Upgrade",
                            onClick = onUpgrade,
                            modifier = Modifier.fillMaxWidth(),
                            role = PillRole.accent,
                            enabled = state.upgrade.affordable,
                        )
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
        Row(
            Modifier
                .fillMaxWidth()
                .glassCard(14.dp)
                .clickable(role = Role.Button, onClick = onRecipes)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Choose Recipe",
                Modifier.weight(1f),
                style = HudType.headline.copy(color = Theme.textPrimary),
            )
            if (state.choices.any { it.isNew }) NewDot()
            BasicText(
                "›",
                style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textSecondary),
            )
        }
    }
    val recipe = state.recipe
    SheetCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MenuImage(MenuPictures.stored(recipe.input), loader, Modifier.size(56.dp))
                BasicText(
                    "${recipe.inputCount} ${recipe.input.name}",
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
                )
                BasicText(
                    "${state.inputStored} in storage",
                    style = HudType.caption.copy(color = Theme.textSecondary),
                )
            }
            BasicText("→", style = HudType.title3.copy(color = Theme.textSecondary))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MenuImage(MenuPictures.stored(recipe.output), loader, Modifier.size(56.dp))
                BasicText(
                    "${recipe.outputCount} ${recipe.output.name}",
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
                )
                BasicText(
                    "${state.outputStored} in storage",
                    style = HudType.caption.copy(color = Theme.textSecondary),
                )
            }
        }
    }
    SheetCard(
        highlighted = state.status == WorkshopStatus.Ready,
    ) {
        BasicText(state.statusText, style = HudType.subheadline.copy(color = Theme.textPrimary))
    }
    PillButton(
        title = "Start",
        onClick = { act(SheetAction.StartWork) },
        modifier = Modifier.fillMaxWidth(),
        role = PillRole.accent,
        enabled = state.startRefusal == null,
    )
}

@Composable
private fun TractorSection(
    state: TractorState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    SheetCard {
        BasicText(state.farmland, style = HudType.headline.copy(color = Theme.textPrimary))
    }
    state.run?.let {
        SheetCard(highlighted = true) {
            BasicText(it, style = HudType.headline.copy(color = Theme.textPrimary))
            if (state.canCancel) {
                PillButton(
                    title = "Cancel",
                    onClick = { act(SheetAction.CancelHarvest) },
                    role = PillRole.neutral,
                )
            }
        }
    }
    if (state.crops.isEmpty()) {
        SheetCard {
            BasicText(
                "Nothing is planted or gathered yet.",
                style = HudType.subheadline.copy(color = Theme.textSecondary),
            )
        }
    } else {
        BasicText(
            "Harvest ready crops, then sell or store the load before the next trip.",
            style = HudType.subheadline.copy(color = Theme.textSecondary),
        )
    }
    state.crops.forEach { crop ->
        val summary = crop.summary
        SheetCard(highlighted = summary.readyCount > 0 || summary.gathered > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuImage(MenuPictures.stored(StorageItem.CropItem(summary.crop)), loader, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    BasicText(
                        summary.crop.name,
                        style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                    )
                    summary.remaining?.let {
                        BasicText(
                            "${summary.plantedCount} planted",
                            style = HudType.caption.copy(color = Theme.textSecondary),
                        )
                        BasicText(
                            if (it <= 0) "Ready to harvest" else "Ready in ${it.durationText}",
                            style =
                                HudType.subheadline.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (summary.readyCount > 0) Theme.accent else Theme.textSecondary,
                                ),
                        )
                    }
                    if (summary.gathered > 0) {
                        BasicText(
                            "${summary.gathered} gathered",
                            style =
                                HudType.subheadline.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Theme.textPrimary,
                                ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                title = "Store",
                                onClick = { act(SheetAction.Cargo(summary.crop.id, false)) },
                                role = PillRole.neutral,
                            )
                            PillButton(
                                title = "Sell · ${crop.sale}",
                                onClick = { act(SheetAction.Cargo(summary.crop.id, true)) },
                                role = PillRole.earn,
                            )
                        }
                    }
                    if (crop.canHarvest) {
                        PillButton(
                            title = "Harvest",
                            onClick = { act(SheetAction.Harvest(summary.crop.id)) },
                            role = PillRole.accent,
                        )
                    }
                }
            }
        }
    }
}
