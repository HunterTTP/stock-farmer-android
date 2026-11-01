package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.core.rules.building
import com.hunter.stockfarmer.core.rules.destroyDecoration
import com.hunter.stockfarmer.core.rules.destroyFence
import com.hunter.stockfarmer.core.rules.isChopping
import com.hunter.stockfarmer.core.rules.isMining
import com.hunter.stockfarmer.core.rules.refund
import com.hunter.stockfarmer.core.rules.setSprinklersRunConstantly
import com.hunter.stockfarmer.core.rules.workshopStatus
import com.hunter.stockfarmer.ui.sheets.SheetTarget
import java.time.Instant
import java.util.Locale

internal fun GameSession.openBuilding(
    engine: GameEngine,
    origin: GridPoint,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val placed = engine.building(point) ?: return emptyList()
    val tool = engine.state.harvestTool
    val takesGoods =
        engine.state.mode == PlayMode.harvest &&
            (
                (tool == HarvestTool.bucket && engine.workshopStatus(origin, now) == WorkshopStatus.Ready) ||
                    (tool == HarvestTool.scythe && !engine.state.tractorCargo[origin].isNullOrEmpty())
            )
    if (takesGoods) return flash(placed.footprint, engine.useTool(point, now), now)
    sceneFeedback.interact(origin, building = true, now)
    sceneFeedback.flash(placed.footprint, false, now)
    haptic(HapticCue.Light)
    sheets.value = SheetTarget.Building(origin)
    return emptyList()
}

internal fun GameSession.askAboutLandscape(
    engine: GameEngine,
    point: GridPoint,
    now: Instant,
): Boolean {
    if (engine.isChopping) return false
    if (point in engine.state.fences) {
        sceneFeedback.flash(listOf(point), false, now)
        ask(
            Confirmation("Sell fence?", "", "Sell · ${FenceColor.price.dollars}") {
                publish(act { it.destroyFence(point) })
            },
        )
        return true
    }
    val site = engine.state.decoration(point) ?: return false
    val (origin, placed) = site
    val decoration = placed.decoration
    sceneFeedback.interact(origin, building = false, now)
    sceneFeedback.flash(decoration.footprint(origin), false, now)
    val alternative =
        if (decoration.isSprinkler) {
            val runs = engine.state.sprinklersRunConstantly
            Confirmation.Alternative(if (runs) "Turn Always On off" else "Turn Always On") {
                act { it.setSprinklersRunConstantly(!runs) }
            }
        } else {
            null
        }
    ask(
        Confirmation(
            "Destroy ${decoration.name.lowercase(Locale.US)}?",
            sprayMessage(placed, engine.state.sprinklersRunConstantly, now, engine.state.clockOffset),
            "Destroy · ${engine.refund(decoration).dollars}",
            alternative,
        ) { publish(act { it.destroyDecoration(point, Instant.now()) }) },
    )
    return true
}

internal fun sprayMessage(
    placed: PlacedDecoration,
    alwaysOn: Boolean,
    now: Instant,
    clockOffset: Double,
): String {
    if (!placed.decoration.isSprinkler) return ""
    if (alwaysOn) return "Always On: every sprinkler sprays all the time"
    val line = "Sprays again in ${Decoration.timeUntilNextSpray(now).durationText}"
    return if (Weather.rainAmount(now, clockOffset) > 0) "$line\nNot while it is raining" else line
}

internal fun GameSession.toolGuidance(
    engine: GameEngine,
    point: GridPoint,
    now: Instant,
): List<GameEvent>? {
    val tree = engine.state.tree(point)
    if (tree != null && !engine.isChopping) {
        return flash(tree.second.tree.footprint(tree.first), listOf(GameEvent.Rejected(Rejection.UseTheAxe)), now)
    }
    if (point in engine.state.rocks && !engine.isMining) {
        return flash(listOf(point), listOf(GameEvent.Rejected(Rejection.UseThePickaxe)), now)
    }
    return null
}
