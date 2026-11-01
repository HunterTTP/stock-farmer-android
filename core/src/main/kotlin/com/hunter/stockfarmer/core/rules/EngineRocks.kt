package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.OreKind
import com.hunter.stockfarmer.core.catalog.OreVein
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextBoolean
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.randomElement
import kotlin.math.max

private const val ROCK_TRIES = 200

/**
 * Gives a map its rocks if it has none yet: a new game. They lie thinly and evenly over the whole map on bare grass, away
 * from the starting farmland.
 */
fun GameEngine.scatterRocksIfNeeded(using: RandomSource) {
    if (state.hasRocks) return
    state.hasRocks = true
    repeat(using.nextInt(Rock.onANewMap)) { placeOneRock(using = using) }
}

/** A farm receives a small ore population once. */
fun GameEngine.scatterOreVeinsIfNeeded(using: RandomSource) {
    if (state.hasOreVeins) return
    state.hasOreVeins = true
    val wanted = using.nextInt(OreVein.onANewMap)
    repeat(max(oreVeinCount, wanted) - oreVeinCount) { placeOneOreVein(using) }
}

internal val GameEngine.oreVeinCount: Int get() = state.rocks.values.count { it.ore != null }

private fun GameEngine.placeOneOreVein(using: RandomSource) {
    val size = randomOreSize(using) ?: return
    placeOneRock(size, using)
}

internal fun randomOreSize(using: RandomSource): RockSize? {
    val ore = OreKind.entries.randomElement(using) ?: return null
    return if (ore == OreKind.silver && using.nextBoolean()) RockSize.smallSilver else ore.rock
}

/** Bare grass away from the start and the front of a home, where a rock may stand. */
internal fun GameEngine.isFreeForRock(point: GridPoint): Boolean =
    GameState.isClearOfStart(point) && isBareGround(point) && point !in frontTilesOfHomes

private fun GameEngine.placeOneRock(
    size: RockSize? = null,
    using: RandomSource,
) {
    val fronts = frontTilesOfHomes
    repeat(ROCK_TRIES) {
        val point = GridPoint(using.nextInt(0 until GameState.GRID_SIZE), using.nextInt(0 until GameState.GRID_SIZE))
        if (!GameState.isClearOfStart(point) || !isBareGround(point) || point in fronts) return@repeat
        state.rocks[point] = size ?: randomSize(using)
        return
    }
}

internal fun randomSize(using: RandomSource): RockSize {
    var roll = using.nextUnit()
    for (size in RockSize.stoneSizes) {
        roll -= size.weight
        if (roll < 0) return size
    }
    return RockSize.small
}
