package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.isChopping
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.storageCapacity
import com.hunter.stockfarmer.core.rules.storedCount
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.MenuPicture
import com.hunter.stockfarmer.ui.screens.LessonGuideState

/** What the heads-up display shows, read from the game a few times a second. */
internal data class HudState(
    val money: Double,
    val xp: Int,
    val isPlacing: Boolean,
    /** What Place will charge for a new building, or null when it is free or something already standing is being moved. */
    val placementCost: Double?,
    val tool: HudTool,
    val toolPicture: MenuPicture,
    val circleTool: ToolCircleTool,
    val dropdowns: List<ToolDropdown>,
    val storage: String,
    val lesson: LessonGuideState,
)

internal const val HUD_REFRESH_MILLIS = 250L

internal fun GameSession.hud(reach: Float = ToolCircleMetrics.DEFAULT_REACH): HudState =
    act { engine ->
        val state = engine.state
        val tool = engine.currentTool
        val placement = engine.buildingPlacement
        HudState(
            money = state.money,
            xp = state.xp,
            isPlacing = engine.isPlacing,
            placementCost = placement?.takeIf { it.replacing == null && it.building.cost > 0 }?.building?.cost,
            tool = tool,
            toolPicture = ToolPalette.picture(state, tool),
            circleTool =
                ToolCircleTool(
                    activationDelayMillis = engine.activationDelayMillis,
                    activatesImmediately = engine.activatesImmediately,
                    isChopping = engine.isChopping,
                    reach = reach,
                ),
            dropdowns = ToolPalette.dropdowns(state, tool),
            storage = "${StatusText.count(engine.storedCount)} of ${StatusText.count(engine.storageCapacity)}",
            lesson = LessonGuideState.from(state),
        )
    }
