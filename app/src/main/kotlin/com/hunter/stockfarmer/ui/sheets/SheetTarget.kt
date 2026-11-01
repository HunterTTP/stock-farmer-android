package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.animalStandingAt
import com.hunter.stockfarmer.core.rules.building
import com.hunter.stockfarmer.game.GameSession

/** Stable identities, so a moving animal's menu follows the animal rather than its old tile. */
sealed interface SheetTarget {
    data class Building(
        val origin: GridPoint,
    ) : SheetTarget

    data class Animal(
        val animal: AnimalRef,
    ) : SheetTarget

    data class Fish(
        val pond: GridPoint,
    ) : SheetTarget
}

fun GameSession.targetAt(tile: GridPoint): SheetTarget? = act { SheetTargets.at(it, tile) }

internal object SheetTargets {
    fun at(
        engine: GameEngine,
        tile: GridPoint,
    ): SheetTarget? {
        engine.building(tile)?.let { return SheetTarget.Building(it.origin) }
        engine.animalStandingAt(tile)?.let { return SheetTarget.Animal(it) }
        return engine.state.fishPonds.entries
            .firstOrNull { it.value.fish?.position == tile }
            ?.let { SheetTarget.Fish(it.key) }
    }
}
