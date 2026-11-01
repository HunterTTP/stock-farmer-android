package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.randomElement
import java.time.Instant

/** How long a pond with room for a duck waits before its first duckling and then between ducklings, sparingly. */
internal const val FIRST_DUCKLING_DELAY = 10.0 * 60

/** Ducks come to the water on their own, a baby every half hour; how many a body of water has room for is worked out from its shape. */
internal val LivestockHome.Companion.pond: LivestockHome
    get() = LivestockHome("", listOf(LivestockSpecies.duck), MOST_OF_A_KIND_ON_THE_MAP, 30.0 * 60)

/** The herds that live on ponds rather than in a building: any herd whose home is not a building made for animals. */
internal val GameEngine.pondHerdOrigins: List<GridPoint>
    get() =
        state.herds.keys
            .filter { state.building(it)?.building?.livestockHome == null }
            .sortedWith(compareBy({ it.row }, { it.col }))

/** Whether the animals of a home are ducks that live on a pond. */
internal fun GameEngine.isPondHome(home: GridPoint): Boolean =
    state.herds[home] != null && state.building(home)?.building?.livestockHome == null

internal fun GameEngine.isWaterfowl(animal: AnimalRef): Boolean {
    if (animal !is AnimalRef.Livestock) return false
    return livestock(animal.id, animal.home)?.let { it.species == LivestockSpecies.duck } ?: isPondHome(animal.home)
}

/** A new map's bodies of water each start with no ducks, or as many grown ones as they have room for. */
internal fun GameEngine.scatterPondDucks(
    now: Instant,
    using: RandomSource,
) {
    settleDucksOnPonds(now)
    val occupants = waterOccupants
    var total = occupants.ducks.size
    for (origin in pondHerdOrigins) {
        val body = state.waterLayout.bodyIndexByTile[origin] ?: continue
        repeat(using.nextInt(0..animalLimit(body))) {
            if (total >= animalLimitOnTheMap) return@repeat
            val herd = state.herds[origin] ?: return@repeat
            val tile = openWaterTiles(Waterfolk.duck, body, occupants).randomElement(using) ?: return@repeat
            val variant = LivestockSpecies.duck.variants.randomElement(using) ?: return@repeat
            val duck =
                Livestock.born(
                    herd.nextAnimalID,
                    variant,
                    LivestockSpecies.duck.name,
                    tile,
                    now.advancedBy(-LivestockSpecies.duck.growDuration),
                )
            state.herds[origin] = herd.copy(animals = herd.animals + duck, nextAnimalID = herd.nextAnimalID + 1)
            occupants.add(Waterfolk.duck, tile)
            total += 1
        }
    }
}

/** A duck whose water has been filled in, or built over, is put back on the nearest water; ducks never stand anywhere else. */
private fun GameEngine.returnStrandedDucksToTheWater() {
    for (origin in pondHerdOrigins) {
        val herd = state.herds[origin] ?: continue
        state.herds[origin] =
            herd.copy(
                animals =
                    herd.animals.map { duck ->
                        if (duck.position in
                            state.water
                        ) {
                            duck
                        } else {
                            nearestWater(duck.position)?.let { duck.copy(position = it) } ?: duck
                        }
                    },
            )
    }
}

internal fun GameEngine.nearestWater(point: GridPoint): GridPoint? =
    state.water.keys.minByOrNull {
        (it.row - point.row) * (it.row - point.row) +
            (it.col - point.col) * (it.col - point.col)
    }

/** Gives every body of water with an interior that has none a herd of ducks to come, and takes away a herd with no ducks whose water has gone. */
internal fun GameEngine.settleDucksOnPonds(now: Instant) {
    returnStrandedDucksToTheWater()
    val layout = state.waterLayout

    fun hasInterior(origin: GridPoint) =
        layout.bodyIndexByTile[origin]?.let { layout.bodies[it].interior.isNotEmpty() } ?: false
    for (origin in pondHerdOrigins) {
        if (state.herds[origin]?.animals?.isEmpty() == true && !hasInterior(origin)) state.herds.remove(origin)
    }
    val settled = pondHerdOrigins.mapNotNull { layout.bodyIndexByTile[it] }.toMutableSet()
    for ((index, body) in layout.bodies.withIndex()) {
        if (index in settled) continue
        val origin = body.interior.firstOrNull() ?: continue
        state.herds[origin] = Herd(nextBirthAt = now.advancedBy(FIRST_DUCKLING_DELAY))
        settled += index
    }
}
