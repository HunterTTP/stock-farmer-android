package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.FactsCards
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.MarketQuestSection
import com.hunter.stockfarmer.ui.screens.ScreenFormat
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.LevelRequirementNotice
import com.hunter.stockfarmer.ui.theme.NewDot
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
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
            FactsCards(GameEngine.facts(building))
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .glassCard(14.dp)
                        .clickable(role = Role.Button) { act(SheetAction.Summon) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("↓", style = HudType.subheadline.copy(color = Theme.accent))
                    BasicText(
                        "Summon",
                        style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.accent),
                    )
                }
            }
        }

        building.isChickenCoop || building.livestockHome != null -> {
            state.homeMessage?.let {
                SheetCard {
                    BasicText(
                        it,
                        style = HudType.subheadline.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
                    )
                }
            }
            if (building.isChickenCoop) {
                Row(
                    Modifier.fillMaxWidth().categoryCard(false).clickable(role = Role.Button, onClick = onChick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        "Choose Next Chick",
                        Modifier.weight(1f),
                        style = HudType.headline.copy(color = Theme.textPrimary),
                    )
                    HudIcon(HudIcons.chevronRight, Theme.textSecondary, size = 16.dp)
                }
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
                            modifier = Modifier.fillMaxWidth().alpha(if (state.upgrade.affordable) 1f else 0.4f),
                            fill = Theme.xp,
                            text = Color.White,
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
            HudIcon(HudIcons.chevronRight, Theme.textSecondary, size = 16.dp)
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
                    "${ScreenFormat.number(state.inputStored.toLong())} in storage",
                    style = HudType.caption.copy(color = Theme.textSecondary),
                )
            }
            BasicText("→", style = HudType.title3.copy(fontWeight = FontWeight.Bold, color = Theme.textSecondary))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MenuImage(MenuPictures.stored(recipe.output), loader, Modifier.size(56.dp))
                BasicText(
                    "${recipe.outputCount} ${recipe.output.name}",
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
                )
                BasicText(
                    "${ScreenFormat.number(state.outputStored.toLong())} in storage",
                    style = HudType.caption.copy(color = Theme.textSecondary),
                )
            }
        }
    }
    SheetCard(
        highlighted = state.status == WorkshopStatus.Ready,
    ) {
        BasicText(
            state.statusText,
            style = HudType.subheadline.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
        )
    }
    PillButton(
        title = "Start",
        onClick = { act(SheetAction.StartWork) },
        modifier = Modifier.fillMaxWidth().alpha(if (state.startRefusal == null) 1f else 0.4f),
        fill = Theme.accent,
        text = Theme.onAccent,
    )
}

@Composable
private fun TractorSection(
    state: TractorState,
    act: (SheetAction) -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    SheetCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Farmland tiles used",
                Modifier.weight(1f),
                style = HudType.body.copy(color = Theme.textSecondary),
            )
            BasicText(
                state.farmland.substringAfter(": ").split(" / ").joinToString(" / ") {
                    it.toLongOrNull()?.let(ScreenFormat::number) ?: it
                },
                style = HudType.headline.copy(color = Theme.textPrimary),
            )
        }
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
                            "${ScreenFormat.number(summary.plantedCount.toLong())} planted",
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
                            "${ScreenFormat.number(summary.gathered.toLong())} gathered",
                            style =
                                HudType.subheadline.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Theme.textPrimary,
                                ),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PillButton(
                                title = "Store",
                                onClick = { act(SheetAction.Cargo(summary.crop.id, false)) },
                                role = PillRole.accent,
                                fillWidth = false,
                            )
                            PillButton(
                                title = "Sell · ${crop.sale}",
                                onClick = { act(SheetAction.Cargo(summary.crop.id, true)) },
                                role = PillRole.earn,
                                fillWidth = false,
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
