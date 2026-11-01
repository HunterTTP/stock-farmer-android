package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.beginMoving
import com.hunter.stockfarmer.core.rules.beginMovingDecoration
import com.hunter.stockfarmer.core.rules.beginMovingFruitTree
import com.hunter.stockfarmer.core.rules.beginMovingTree
import com.hunter.stockfarmer.core.rules.cropsToDestroy
import com.hunter.stockfarmer.core.rules.destroyCrops
import com.hunter.stockfarmer.core.rules.destroyFertilizer
import com.hunter.stockfarmer.core.rules.fertilizerToDestroy
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.plantCount
import com.hunter.stockfarmer.core.rules.remainingGrowTime
import java.time.Instant

/** A question the player must answer before something is lost. */
class Confirmation(
    val title: String,
    val message: String,
    val confirmTitle: String,
    val alternative: Alternative? = null,
    val action: () -> Unit,
) {
    class Alternative(
        val title: String,
        val action: () -> Unit,
    )
}

/**
 * Holding a finger on a building, a fruit tree, a decoration or a tree picks it up to be moved; holding one on crops
 * or fertilizer asks whether to destroy them.
 */
fun GameSession.hold(
    point: GridPoint,
    now: Instant = Instant.now(),
): List<GameEvent> =
    publish(
        act { engine ->
            when {
                engine.isPlacing -> emptyList()
                else -> engine.pickUp(point) ?: askToDestroy(engine, point, now)
            }
        },
    )

/** Picks up what stands on the point to move it, or says why it cannot be; null when there is nothing to pick up. */
private fun GameEngine.pickUp(point: GridPoint): List<GameEvent>? {
    state.building(point)?.let { placed ->
        if (state.tractorRun?.home == placed.origin) return refusal(Rejection.TractorBusy)
        beginMoving(placed)
        return emptyList()
    }
    state.plant(point)?.let { (_, plot) ->
        if (Crop.named(plot.cropID)?.isFruitTree == true) {
            beginMovingFruitTree(point)
            return emptyList()
        }
    }
    if (state.decoration(point) != null) {
        beginMovingDecoration(point)
        return emptyList()
    }
    state.tree(point)?.let { (_, tree) ->
        if (tree.isNaturallyGenerated) return refusal(Rejection.UseTheAxe)
        beginMovingTree(point)
        return emptyList()
    }
    if (state.rocks[point] != null) return refusal(Rejection.UseThePickaxe)
    return null
}

private fun GameSession.askToDestroy(
    engine: GameEngine,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val crops = engine.cropsToDestroy(point)
    if (crops.isNotEmpty()) {
        sceneFeedback.flash(crops, false, now)
        val plants = engine.plantCount(crops)
        val remaining = crops.mapNotNull { engine.remainingGrowTime(it, now) }.maxOrNull() ?: 0.0
        val label = if (plants == 1) "Grow time remaining" else "Longest grow time remaining"
        val timing = if (remaining > 0) "$label: ${remaining.durationText}" else "Ready to harvest"
        confirmations.value =
            Confirmation(
                "Destroy $plants ${if (plants == 1) "crop" else "crops"}?",
                "No money will be earned.\n$timing",
                "Destroy",
            ) { publish(act { it.destroyCrops(crops) }) }
    } else {
        engine.fertilizerToDestroy(point)?.let { tile ->
            sceneFeedback.flash(listOf(tile), false, now)
            confirmations.value =
                Confirmation("Destroy fertilizer?", "No money will be returned.", "Destroy") {
                    publish(act { it.destroyFertilizer(tile) })
                }
        }
    }
    return emptyList()
}

private fun refusal(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))
