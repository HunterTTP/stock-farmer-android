package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.animalStandingAt
import com.hunter.stockfarmer.core.rules.building

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
