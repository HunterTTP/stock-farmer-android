package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.fishDirection
import com.hunter.stockfarmer.core.rules.moveFish
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import java.time.Instant

/**
 * Makes the fish in the ponds swim: each rests for a second or four, picks a direction and swims that way for one to
 * three tiles, one tile every [STEP_SECONDS], then rests again.
 */
internal class FishSwimming(
    private val random: RandomSource,
) {
    private class Swim(
        var nextAt: Instant,
        var direction: GridPoint? = null,
        var tilesLeft: Int = 0,
    )

    private val swims = mutableMapOf<GridPoint, Swim>()

    fun forget() = swims.clear()

    fun advance(
        engine: GameEngine,
        now: Instant,
    ) {
        val anchors =
            engine.state.fishPonds
                .filterValues { it.fish != null }
                .keys
        swims.keys.retainAll(anchors)
        for (anchor in anchors.sortedWith(compareBy({ it.row }, { it.col }))) {
            val swim = swims.getOrPut(anchor) { Swim(now.advancedBy(rest(0.0))) }
            if (now < swim.nextAt) continue
            val direction = swim.direction ?: startSwim(engine, anchor, swim)
            if (direction == null) {
                restAfter(swim, now)
                continue
            }
            if (engine.moveFish(anchor, direction) == null) {
                restAfter(swim, now)
                continue
            }
            swim.tilesLeft -= 1
            swim.nextAt = now.advancedBy(STEP_SECONDS)
            if (swim.tilesLeft == 0) restAfter(swim, swim.nextAt)
        }
    }

    private fun startSwim(
        engine: GameEngine,
        anchor: GridPoint,
        swim: Swim,
    ): GridPoint? {
        val direction = engine.fishDirection(anchor, random) ?: return null
        swim.direction = direction
        swim.tilesLeft = random.nextInt(FEWEST_TILES..MOST_TILES)
        return direction
    }

    private fun restAfter(
        swim: Swim,
        from: Instant,
    ) {
        swim.direction = null
        swim.tilesLeft = 0
        swim.nextAt = from.advancedBy(rest(FEWEST_REST))
    }

    private fun rest(fewest: Double): Double = random.nextDouble(fewest, MOST_REST)

    companion object {
        const val STEP_SECONDS = 1.8
        private const val FEWEST_REST = 1.0
        private const val MOST_REST = 4.0
        private const val FEWEST_TILES = 1
        private const val MOST_TILES = 3
    }
}
