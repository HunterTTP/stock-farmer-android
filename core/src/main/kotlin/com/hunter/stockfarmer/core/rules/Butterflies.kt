package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Fireflies.TilePosition
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.randomElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The few butterflies that come in from the edge of the map by day, flutter from one crop, rock or decoration to the
 * next and leave again. Like the fireflies they are only a picture and never reach the rules; this holds what is worth
 * testing about them.
 */
object Butterflies {
    const val MAX_AT_ONCE = 6

    /** How fast one flutters, in tiles a second: livelier than a firefly's drift. */
    const val SPEED = 1.1
    val stepLength = 1.5..3.0
    const val LONGEST_FLIGHT = 80

    private const val SPAWN_CHANCE = 0.1
    private const val BRIGHT_ENOUGH = 0.05
    private const val DRY_ENOUGH = 0.05

    /** How many things it visits, how near it comes to each, and how far it hops about each once there. */
    private const val VISITS = 2
    private const val ARRIVAL_DISTANCE = 1.2
    private const val HOPS = 2
    private val hopLength = 0.5..1.2

    /** How far a step may stray from straight toward where it is heading, in radians. */
    private const val STRAYING = 0.7

    /** With nothing to visit, it crosses the farm between points this far inside the edge. */
    private const val WANDER_MARGIN = 8.0

    /** How many the player may have released on the farm at once; they stay until caught and are not counted against the wild ones. */
    const val MOST_RELEASED = 10

    /** How likely a butterfly is to have wings that change color. */
    internal const val SHIFTING_CHANCE = 0.01

    /** What a butterfly's wings are: one of the rarity colors, each equally likely, or, very rarely, a color that slowly changes. */
    @Serializable(with = WingsSerializer::class)
    sealed interface Wings {
        /** What saves and storage keep it under: its rarity's name, or "shifting". */
        val id: String

        data class Color(
            val rarity: Rarity,
        ) : Wings {
            override val id: String get() = rarity.rawValue
        }

        data object Shifting : Wings {
            override val id: String get() = "shifting"
        }

        companion object {
            val all: List<Wings> by lazy { Rarity.entries.map { Color(it) } + Shifting }

            fun of(id: String): Wings? = all.firstOrNull { it.id == id }
        }
    }

    internal object WingsSerializer : KSerializer<Wings> {
        override val descriptor = PrimitiveSerialDescriptor("Wings", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: Wings,
        ) = encoder.encodeString(value.id)

        override fun deserialize(decoder: Decoder): Wings {
            val id = decoder.decodeString()
            return Wings.of(id) ?: throw kotlinx.serialization.SerializationException("No wings called $id")
        }
    }

    fun wings(using: RandomSource): Wings {
        if (using.nextUnit() < SHIFTING_CHANCE) return Wings.Shifting
        return Wings.Color(Rarity.entries.randomElement(using) ?: Rarity.common)
    }

    /** How likely one is to come out in the next second: only in full daylight and dry weather, and never in winter. */
    fun spawnChancePerSecond(
        darkness: Double,
        rain: Double,
        season: Season,
    ): Double = if (darkness < BRIGHT_ENOUGH && rain < DRY_ENOUGH && season != Season.winter) SPAWN_CHANCE else 0.0

    class Attraction(
        val tile: GridPoint,
        isCrop: Boolean = false,
    ) {
        val landingPoints: List<TilePosition> =
            if (isCrop) {
                listOf(0.25, 0.75).flatMap { row ->
                    listOf(0.25, 0.75).map { col -> TilePosition(tile.row + row, tile.col + col) }
                }
            } else {
                listOf(TilePosition.middleOf(tile))
            }
    }

    /** The things worth landing on, with a point for each crop sprite or the center of other tiles. */
    fun attractions(state: GameState): List<Attraction> =
        (state.plots.keys + state.rocks.keys + state.decorations.keys)
            .toSet()
            .sortedWith(compareBy({ it.row }, { it.col }))
            .map { tile ->
                val crop = state.plots[tile]?.let { Crop.named(it.cropID) }
                Attraction(tile, isCrop = crop?.isFruitTree == false)
            }

    /**
     * The points a butterfly flutters through, one after another: in from a point at the edge, near a couple of the
     * attractions with a hop or two about each, and out by the edge again.
     */
    fun flight(
        attractions: List<Attraction>,
        using: RandomSource,
    ): List<TilePosition> {
        val flight = mutableListOf(edgePoint(using))
        visitSome(flight, attractions, using)
        val exit = edgePoint(using)
        walk(flight, exit, using)
        flight += exit
        return flight
    }

    /**
     * How a butterfly the player let go goes on: from where it is, near a couple of the attractions with a hop or two
     * about each, and never off the map.
     */
    fun wander(
        start: TilePosition,
        attractions: List<Attraction>,
        using: RandomSource,
    ): List<TilePosition> {
        val flight = mutableListOf(start)
        visitSome(flight, attractions, using)
        return flight
    }

    /** Where a butterfly sits out the night or the rain: the landing place nearest it, or where it already is with none about. */
    fun restingSpot(
        position: TilePosition,
        attractions: List<Attraction>,
    ): TilePosition = attractions.flatMap { it.landingPoints }.minByOrNull { position.distance(it) } ?: position

    private fun visitSome(
        flight: MutableList<TilePosition>,
        attractions: List<Attraction>,
        using: RandomSource,
    ) {
        repeat(VISITS) {
            val target =
                attractions.randomElement(using)?.landingPoints?.randomElement(using) ?: wanderPoint(using)
            walk(flight, target, using)
            if (flight.size < LONGEST_FLIGHT - 1 && flight.last() != target) flight += target
            repeat(HOPS) { hop(flight, target, using) }
        }
    }

    /** How long a flight takes, in seconds. */
    fun duration(flight: List<TilePosition>): Double = flight.zipWithNext().sumOf { (a, b) -> a.distance(b) } / SPEED

    private fun edgePoint(using: RandomSource): TilePosition {
        val limit = GameState.GRID_SIZE.toDouble()
        val along = using.nextDouble(0.0, limit)
        return when (using.nextInt(0..3)) {
            0 -> TilePosition(0.0, along)
            1 -> TilePosition(limit, along)
            2 -> TilePosition(along, 0.0)
            else -> TilePosition(along, limit)
        }
    }

    private fun wanderPoint(using: RandomSource): TilePosition {
        val upper = GameState.GRID_SIZE - WANDER_MARGIN
        return TilePosition(using.nextDouble(WANDER_MARGIN, upper), using.nextDouble(WANDER_MARGIN, upper))
    }

    /** Steps toward a point, straying a little each step, until it is near it, or the flight is as long as it may be. */
    private fun walk(
        flight: MutableList<TilePosition>,
        target: TilePosition,
        using: RandomSource,
    ) {
        while (flight.last().distance(target) > ARRIVAL_DISTANCE && flight.size < LONGEST_FLIGHT - 1) {
            val here = flight.last()
            val angle = atan2(target.row - here.row, target.col - here.col) + using.nextDouble(-STRAYING, STRAYING)
            val length = min(using.nextDouble(stepLength.start, stepLength.endInclusive), here.distance(target))
            flight += clamped(here.row + sin(angle) * length, here.col + cos(angle) * length)
        }
    }

    private fun hop(
        flight: MutableList<TilePosition>,
        target: TilePosition,
        using: RandomSource,
    ) {
        if (flight.size >= LONGEST_FLIGHT - 1) return
        val angle = using.nextDouble(0.0, 2 * Math.PI)
        val length = using.nextDouble(hopLength.start, hopLength.endInclusive)
        flight += clamped(target.row + sin(angle) * length, target.col + cos(angle) * length)
    }

    private fun clamped(
        row: Double,
        col: Double,
    ): TilePosition {
        val limit = GameState.GRID_SIZE.toDouble()
        return TilePosition(min(max(row, 0.0), limit), min(max(col, 0.0), limit))
    }
}
