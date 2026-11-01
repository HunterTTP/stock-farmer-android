package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.randomElement
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The few fireflies that come out of the forest on the deepest nights and work their way in toward the middle of the
 * farm. Like the night they are only a picture and never reach the rules; this holds what is worth testing about them.
 */
object Fireflies {
    const val MAX_AT_ONCE = 8

    /** How fast one drifts, in tiles a second, so a flight from the edge of the map takes the best part of a minute. */
    const val SPEED = 0.35

    /** Fireflies set off from behind things this close to the edge of the map, in tiles. */
    const val EDGE_WIDTH = 6

    /** The least its halo dims to as it pulses; the pixel itself never dims. */
    const val DIMMEST_GLOW = 0.12

    private const val SPAWN_CHANCE_AT_FULL_DARK = 0.04
    private const val DARKNESS_NEEDED = 0.7
    private val stepLength = 2.0..3.5

    /** How far a step may stray from straight toward the middle, in radians. */
    private const val STRAYING = 0.9

    /** How near the middle a flight carries on to before it turns to the nearest thing to hide behind. */
    private val arrival = 3.0..9.0
    private const val LONGEST_FLIGHT = 60

    /** A point on the map in tiles, a tile's middle being a half more than its row and column. */
    data class TilePosition(
        val row: Double,
        val col: Double,
    ) {
        fun distance(other: TilePosition): Double = hypot(other.row - row, other.col - col)

        companion object {
            fun middleOf(tile: GridPoint) = TilePosition(tile.row + 0.5, tile.col + 0.5)
        }
    }

    /** How likely one is to come out in the next second: none until it is well into the night, and rare then. */
    fun spawnChancePerSecond(darkness: Double): Double =
        max(0.0, (darkness - DARKNESS_NEEDED) / (1 - DARKNESS_NEEDED)) * SPAWN_CHANCE_AT_FULL_DARK

    /** How bright its halo is, from [DIMMEST_GLOW] to 1, [time] seconds in, as it swells and fades once every [period]. */
    fun glow(
        time: Double,
        period: Double,
    ): Double {
        val swell = 0.5 + 0.5 * sin(2 * Math.PI * time / period)
        return DIMMEST_GLOW + (1 - DIMMEST_GLOW) * swell * swell
    }

    /**
     * The tiles with something on them that is drawn over a firefly: trees (not the backdrop row), crops, rocks,
     * decorations and buildings.
     */
    fun hideaways(state: GameState): List<GridPoint> {
        val trees =
            state.trees
                .filter { it.key.row != GameState.BACKDROP_ROW }
                .flatMap { (origin, placed) -> Tree.named(placed.treeID)?.footprint(origin) ?: listOf(origin) }
        val decorations =
            state.decorations.flatMap { (origin, placed) ->
                Decoration.named(placed.decorationID)?.footprint(origin) ?: listOf(origin)
            }
        val buildings = state.placedBuildings.flatMap { it.footprint }
        return (trees + decorations + buildings + state.plots.keys + state.rocks.keys).toSet().toList()
    }

    /**
     * The points a firefly drifts through, one after another: it comes out from behind something at the edge of the
     * map, meanders in toward the middle in gentle steps and goes behind the nearest thing there. Null if there is
     * nothing to hide behind.
     */
    fun flight(
        hideaways: List<GridPoint>,
        using: RandomSource,
    ): List<TilePosition>? {
        val size = GameState.GRID_SIZE
        val middle = TilePosition(size / 2.0, size / 2.0)
        val atTheEdge =
            hideaways.filter { min(min(it.row, it.col), min(size - 1 - it.row, size - 1 - it.col)) < EDGE_WIDTH }
        val starts =
            atTheEdge.ifEmpty {
                hideaways.sortedByDescending { TilePosition.middleOf(it).distance(middle) }.take(1)
            }
        val start = starts.randomElement(using) ?: return null
        val flight = mutableListOf(TilePosition.middleOf(start))
        val arrivalDistance = using.nextDouble(arrival.start, arrival.endInclusive)
        while (flight.last().distance(middle) > arrivalDistance && flight.size < LONGEST_FLIGHT) {
            val here = flight.last()
            val towardMiddle = atan2(middle.row - here.row, middle.col - here.col)
            val angle = towardMiddle + using.nextDouble(-STRAYING, STRAYING)
            val length = min(using.nextDouble(stepLength.start, stepLength.endInclusive), here.distance(middle))
            val limit = size.toDouble()
            flight +=
                TilePosition(
                    min(max(here.row + sin(angle) * length, 0.0), limit),
                    min(max(here.col + cos(angle) * length, 0.0), limit),
                )
        }
        val last = flight.last()
        val end = hideaways.minByOrNull { TilePosition.middleOf(it).distance(last) } ?: return null
        flight += TilePosition.middleOf(end)
        return flight
    }

    /** How long a flight takes, in seconds. */
    fun duration(flight: List<TilePosition>): Double = flight.zipWithNext().sumOf { (a, b) -> a.distance(b) } / SPEED
}
