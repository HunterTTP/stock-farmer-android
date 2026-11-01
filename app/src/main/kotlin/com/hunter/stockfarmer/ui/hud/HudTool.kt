package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.selectDecorating
import com.hunter.stockfarmer.core.rules.selectTerrain
import com.hunter.stockfarmer.core.rules.selectTilling
import com.hunter.stockfarmer.core.rules.selectedDecoration

/** What the player is holding: each tool is dragged over the farm and owns a group of things to choose from (iOS `Tool`). */
enum class HudTool(
    val title: String,
) {
    Planting("Plant"),
    Harvest("Harvest"),
    Tilling("Fertilize"),
    Terrain("Terrain"),
    Building("Build"),
    Decorating("Decorate"),
}

/** The tool the game is in right now; a sprinkler in hand counts as Fertilize. */
val GameEngine.currentTool: HudTool
    get() =
        when (state.mode) {
            PlayMode.plant -> HudTool.Planting
            PlayMode.fertilize -> HudTool.Tilling
            PlayMode.harvest -> HudTool.Harvest
            PlayMode.landscape -> HudTool.Terrain
            PlayMode.build -> HudTool.Building
            PlayMode.decorate -> if (selectedDecoration?.isSprinkler == true) HudTool.Tilling else HudTool.Decorating
        }

/**
 * How long a dragged tool must rest before it starts working: quick for crops, slower for land that is costly to mis-place.
 * Harvesting has no additional pause.
 */
val GameEngine.activationDelayMillis: Long?
    get() =
        when (state.mode) {
            PlayMode.harvest -> null
            PlayMode.plant -> 40
            PlayMode.fertilize -> 70
            PlayMode.landscape -> if (state.selectedLandscape == LandscapeTool.farmland) 250 else 150
            PlayMode.build, PlayMode.decorate -> 120
        }

/** The Scythe and Bucket also bypass the drag distance. */
val GameEngine.activatesImmediately: Boolean
    get() =
        state.mode == PlayMode.harvest &&
            (state.harvestTool == HarvestTool.scythe || state.harvestTool == HarvestTool.bucket)

/** Picks up a tool, holding whatever it was last set to. */
fun GameEngine.pickUp(tool: HudTool) {
    when (tool) {
        HudTool.Planting -> setMode(PlayMode.plant)
        HudTool.Tilling -> selectTilling()
        HudTool.Harvest -> setMode(PlayMode.harvest)
        HudTool.Terrain -> selectTerrain()
        HudTool.Building -> setMode(PlayMode.build)
        HudTool.Decorating -> selectDecorating()
    }
}
