package com.hunter.stockfarmer.ui.fishing

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.fishBiting
import com.hunter.stockfarmer.core.rules.fishSaleValue
import com.hunter.stockfarmer.core.rules.fishWeight
import com.hunter.stockfarmer.core.rules.fishingReachFactor
import com.hunter.stockfarmer.core.rules.hookedPond
import com.hunter.stockfarmer.core.rules.markFishingHintSeen

/** Immutable details of the fish on the line; the pond identifies it even after it swims off the tapped tile. */
@ConsistentCopyVisibility
data class FishingCast private constructor(
    val point: GridPoint,
    val pond: GridPoint,
    val kind: FishKind,
    val weight: Double,
    val sellPrice: Double,
    val waterStyle: WaterStyle,
    val reachFactor: Double,
    val showsHint: Boolean,
) {
    val sizeFactor: Double get() = kind.sizeFactor(weight)

    /** The toast shown when the player gives the fish up. */
    val swamAwayMessage: String get() = "The ${kind.name} swam away"
    val picturePath: String get() = "images/animals/Fish/${kind.assetName}.png"

    companion object {
        /** Call inside GameSession.act, once per hook, while no other fishing overlay is open. */
        fun start(
            engine: GameEngine,
            events: List<GameEvent>,
        ): FishingCast? {
            val point = events.filterIsInstance<GameEvent.FishHooked>().firstOrNull()?.point ?: return null
            val kind = engine.fishBiting(point) ?: return null
            val pond = engine.hookedPond(point) ?: return null
            val weight = engine.fishWeight(pond) ?: return null
            val price = engine.fishSaleValue(pond) ?: return null
            return FishingCast(
                point,
                pond,
                kind,
                weight,
                price,
                engine.state.water[point] ?: WaterStyle.medium,
                engine.fishingReachFactor,
                !engine.state.hasSeenFishingHint,
            ).also { engine.markFishingHintSeen() }
        }
    }
}
