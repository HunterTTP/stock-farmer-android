package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.isChopping
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.storageCapacity
import com.hunter.stockfarmer.core.rules.storedCount
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.canDestroyWhilePlacing
import com.hunter.stockfarmer.ui.menus.MenuPicture
import com.hunter.stockfarmer.ui.menus.UnlockCategory
import com.hunter.stockfarmer.ui.menus.hasNewItems
import com.hunter.stockfarmer.ui.screens.LessonGuideState

/** What the heads-up display shows, read from the game a few times a second. */
internal data class HudState(
    val money: Double,
    val xp: Int,
    val isPlacing: Boolean,
    /** What Place will charge for a new building, or null when it is free or something already standing is being moved. */
    val placementCost: Double?,
    /** Whether what is being placed already stands on the farm, so Destroy is offered. */
    val canDestroy: Boolean,
    val tool: HudTool,
    val toolPicture: MenuPicture,
    val circleTool: ToolCircleTool,
    val dropdowns: List<ToolDropdown>,
    val newTools: Set<HudTool>,
    val storage: String,
    val lesson: LessonGuideState,
)

internal const val HUD_REFRESH_MILLIS = 250L

internal fun GameSession.hud(reach: Float = ToolCircleMetrics.DEFAULT_REACH): HudState =
    act { engine ->
        val state = engine.state
        val tool = engine.currentTool
        val placement = engine.buildingPlacement
        val dropdowns = ToolPalette.dropdowns(state, tool)
        HudState(
            money = state.money,
            xp = state.xp,
            isPlacing = engine.isPlacing,
            canDestroy = engine.canDestroyWhilePlacing,
            placementCost = placement?.takeIf { it.replacing == null && it.building.cost > 0 }?.building?.cost,
            tool = tool,
            toolPicture = ToolPalette.picture(state, tool, dropdowns),
            circleTool =
                ToolCircleTool(
                    activationDelayMillis = engine.activationDelayMillis,
                    activatesImmediately = engine.activatesImmediately,
                    isChopping = engine.isChopping,
                    reach = reach,
                ),
            dropdowns = dropdowns,
            newTools =
                buildSet {
                    if (engine.hasNewItems(UnlockCategory.PLANT)) add(HudTool.Planting)
                    if (engine.hasNewItems(UnlockCategory.FERTILIZE)) add(HudTool.Fertilizing)
                    if (engine.hasNewItems(UnlockCategory.TERRAIN)) add(HudTool.Terrain)
                    if (engine.hasNewItems(UnlockCategory.BUILD)) add(HudTool.Building)
                    if (engine.hasNewItems(UnlockCategory.DECORATE)) add(HudTool.Decorating)
                },
            storage = "${StatusText.count(engine.storedCount)} of ${StatusText.count(engine.storageCapacity)}",
            lesson = LessonGuideState.from(state),
        )
    }
