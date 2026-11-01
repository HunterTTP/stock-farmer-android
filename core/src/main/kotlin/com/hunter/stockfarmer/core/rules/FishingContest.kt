package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The moment a fish bites: the fish swims about the water on its own and the player holds the fishing rod over it to fill
 * a bar. When the bar is full the fish is landed. While the rod is away the bar slowly drains to zero, without losing the
 * fish. The rarer the fish, the quicker it swims, the more it changes its mind about where to go, and the slower the bar
 * fills, so rarer fish demand more careful tracking. And the fuller the bar, the harder the fish fights: it swims quicker
 * and changes its mind more often and more sharply, up to twice as fast and as restless as it began.
 * Positions are in points of the screen, with the origin at the top left and `y` growing downward, times in seconds.
 */
class FishingContest(
    rarity: Rarity,
    private val bounds: Bounds,
    private val reachFactor: Double = 1.0,
    using: RandomSource,
) {
    data class Position(
        val x: Double,
        val y: Double,
    ) {
        fun distance(other: Position): Double = hypot(other.x - x, other.y - y)
    }

    /** The part of the screen the fish keeps to. */
    class Bounds(
        val minX: Double,
        maxX: Double,
        val minY: Double,
        maxY: Double,
    ) {
        val maxX: Double = max(maxX, minX + 1)
        val maxY: Double = max(maxY, minY + 1)
    }

    /** How a fish behaves for its species rarity. */
    private class Difficulty(
        /** Points a second. */
        val speed: Double,
        /** How long the rod must be held over it to fill the bar, in seconds. */
        val fillSeconds: Double,
        /** How long it keeps a heading before picking another, and by how much at most it turns, and how quickly it turns. */
        val turnEvery: ClosedFloatingPointRange<Double>,
        val widestTurn: Double,
        val turnRate: Double,
    ) {
        companion object {
            fun of(rarity: Rarity): Difficulty =
                when (rarity) {
                    Rarity.common -> Difficulty(24.0, 1.6, 3.0..6.0, 0.5, 0.8)
                    Rarity.uncommon -> Difficulty(55.0, 3.0, 1.2..2.4, 1.2, 2.2)
                    Rarity.rare -> Difficulty(85.0, 4.4, 0.7..1.5, 2.0, 4.0)
                    Rarity.epic -> Difficulty(108.0, 5.8, 0.5..1.2, 2.6, 5.5)
                    Rarity.legendary -> Difficulty(130.0, 7.0, 0.35..1.0, PI, 7.0)
                }
        }
    }

    private val difficulty = Difficulty.of(rarity)
    val speed: Double get() = difficulty.speed

    /** How long the rod must be held over the fish to fill the bar. */
    val fillSeconds: Double get() = difficulty.fillSeconds

    /** How far from the fish the rod still fills the bar, which a better rod widens. */
    val reach: Double get() = REACH * reachFactor

    var fish: Position =
        Position(using.nextDouble(bounds.minX, bounds.maxX), using.nextDouble(bounds.minY, bounds.maxY))
        private set

    /** The way the fish is swimming, in radians from straight right, turning toward down. */
    var heading: Double = using.nextDouble(-PI, PI)
        private set

    /** How full the bar is, from 0 to 1. */
    var progress: Double = 0.0
        private set

    /** How many times the fish has picked a new way to go. */
    var turns: Int = 0
        private set
    private var wantedHeading: Double = heading
    private var untilTurn: Double = using.nextDouble(difficulty.turnEvery.start, difficulty.turnEvery.endInclusive)

    /** Whether a rod at this position is close enough to the fish for the bar to fill. */
    fun isOverFish(rod: Position): Boolean = rod.distance(fish) <= reach

    /** Moves the fish on by a moment, and fills the bar while the rod is held over it. Says whether the bar is full. */
    fun advance(
        seconds: Double,
        rod: Position?,
        using: RandomSource,
    ): Boolean {
        if (progress >= 1) return true
        if (seconds <= 0) return false
        swim(seconds, using)
        progress =
            if (rod != null && isOverFish(rod)) {
                min(1.0, progress + seconds / fillSeconds)
            } else {
                max(0.0, progress - seconds * DRAIN_PER_SECOND)
            }
        return progress >= 1
    }

    /** How hard the fish is fighting now: 1 at first, and more as the bar fills. */
    val fight: Double get() = 1 + FIGHTING_HARDER * progress

    /** How quickly it swims now. */
    val currentSpeed: Double get() = speed * fight

    private fun swim(
        seconds: Double,
        using: RandomSource,
    ) {
        untilTurn -= seconds
        if (untilTurn <= 0) {
            val widest = difficulty.widestTurn + (PI - difficulty.widestTurn) * SHARPER_TURNS * progress
            wantedHeading = normalized(heading + using.nextDouble(-widest, widest))
            untilTurn = using.nextDouble(difficulty.turnEvery.start, difficulty.turnEvery.endInclusive) / fight
            turns += 1
        }
        val step = difficulty.turnRate * fight * seconds
        heading = normalized(heading + min(max(normalized(wantedHeading - heading), -step), step))
        var x = fish.x + cos(heading) * currentSpeed * seconds
        var y = fish.y + sin(heading) * currentSpeed * seconds
        if (x < bounds.minX || x > bounds.maxX) {
            x = min(max(x, bounds.minX), bounds.maxX)
            heading = normalized(PI - heading)
            wantedHeading = normalized(PI - wantedHeading)
        }
        if (y < bounds.minY || y > bounds.maxY) {
            y = min(max(y, bounds.minY), bounds.maxY)
            heading = normalized(-heading)
            wantedHeading = normalized(-wantedHeading)
        }
        fish = Position(x, y)
    }

    /** For tests: puts the fish where it is wanted. */
    internal fun place(position: Position) {
        fish = position
    }

    /** For tests: puts the bar as full as it is wanted. */
    internal fun fill(to: Double) {
        progress = to
    }

    companion object {
        /** How near the rod must be to the fish, in points, for the bar to fill. */
        const val REACH = 44.0
        private const val DRAIN_PER_SECOND = 0.04

        /**
         * How much more the fish fights, as a share of how it began, when the bar is nearly full: it swims and turns this
         * many times as fast, and turns by this share of the way from its usual widest turn to a full turn about.
         */
        private const val FIGHTING_HARDER = 1.0
        private const val SHARPER_TURNS = 0.6

        /** An angle brought into the range from minus pi to pi. */
        private fun normalized(angle: Double): Double {
            var result = angle % (2 * PI)
            if (result > PI) {
                result -= 2 * PI
            } else if (result < -PI) {
                result += 2 * PI
            }
            return result
        }
    }
}
