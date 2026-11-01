package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.epochSecondsDouble
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** Matches the numbered sprite files, e.g. `wheat-phase-3`. */
@Suppress("ktlint:standard:enum-entry-name-case")
enum class GrowthPhase(
    val spriteNumber: Int,
) {
    sprout(2),
    growing(3),
    ready(4),
}

/** How a fruit tree looks: bare until it is done growing, then with ripe fruit ready to pick. */
@Suppress("ktlint:standard:enum-entry-name-case")
enum class FruitTreePhase {
    fruitless,
    ripe,
    ;

    companion object {
        fun ofProgress(progress: Double): FruitTreePhase = if (progress >= 1) ripe else fruitless
    }
}

object GrowthRules {
    fun progress(
        plot: Plot,
        crop: Crop,
        speedMultiplier: Double = 1.0,
        now: Instant,
    ): Double {
        val duration = crop.growDuration / speedMultiplier
        if (duration <= 0 || now >= plot.plantedAt.advancedBy(duration)) return 1.0
        return min(1.0, max(0.0, now.secondsSince(plot.plantedAt) / duration))
    }
}

/** When each of a tile's four sprites changes form, worked out once for a planting; every planting looks a little different. */
class GrowthTimelines(
    plot: Plot,
    point: GridPoint,
    crop: Crop,
) {
    private val timelines: List<StageTimeline>

    init {
        val millis = (plot.plantedAt.epochSecondsDouble * 1000).toLong()
        timelines = StageTimeline.quadrants("${point.row},${point.col}|${crop.id}|$millis")
    }

    /** Each of a tile's four sprites advances at its own moment, with the last reaching its final form exactly at completion. */
    fun phase(
        quadrant: Int,
        progress: Double,
    ): GrowthPhase {
        if (progress >= 1) return GrowthPhase.ready
        val timeline = timelines[quadrant]
        if (progress >= timeline.finalStart) return GrowthPhase.ready
        return if (progress >= timeline.growingStart) GrowthPhase.growing else GrowthPhase.sprout
    }
}

/** Per-tile growth speed: soil near water is saturated and grows a little faster. */
class GrowthConditions(
    state: GameState,
) {
    val saturatedTiles: Set<GridPoint> = state.saturatedFarmland
    val fertilizedTiles: Set<GridPoint> = state.fertilized.toSet()
    val guardedTiles: Set<GridPoint> = state.guardedFarmland

    fun speedMultiplier(point: GridPoint): Double =
        (if (point in saturatedTiles) SATURATION_MULTIPLIER else 1.0) *
            (if (point in fertilizedTiles) Fertilizer.growthMultiplier else 1.0) *
            (if (point in guardedTiles) Decoration.GUARD_MULTIPLIER else 1.0)

    fun progress(
        plot: Plot,
        crop: Crop,
        point: GridPoint,
        now: Instant,
    ): Double = GrowthRules.progress(plot, crop, speedMultiplier(point), now)

    fun isReady(
        plot: Plot,
        crop: Crop,
        point: GridPoint,
        now: Instant,
    ): Boolean = progress(plot, crop, point, now) >= 1

    /** Whether the crop planted in a plot is fully grown; a plot of a crop that no longer exists never is. */
    fun isReady(
        plot: Plot,
        point: GridPoint,
        now: Instant,
    ): Boolean = Crop.named(plot.cropID)?.let { isReady(plot, it, point, now) } ?: false

    fun remainingTime(
        plot: Plot,
        crop: Crop,
        point: GridPoint,
        now: Instant,
    ): Double {
        val duration = crop.growDuration / speedMultiplier(point)
        if (now >= plot.plantedAt.advancedBy(duration)) return 0.0
        return max(0.0, duration - now.secondsSince(plot.plantedAt))
    }

    companion object {
        const val SATURATION_MULTIPLIER = 1.05
    }
}

/** When one sprite leaves its sprout form and when it reaches its final form, as fractions of the growth. */
internal class StageTimeline(
    val growingStart: Double,
    val finalStart: Double,
) {
    companion object {
        /** The other three sprites finish around these points, so growth looks continuous right up to completion. */
        private val earlyFinishes = listOf(0.3, 0.55, 0.8)
        private const val JITTER = 0.12

        /** Timelines for a tile's four sprites; exactly one finishes at 1, and which one varies per tile. */
        fun quadrants(seed: String): List<StageTimeline> {
            val random = StageRandom(seed)
            val order = mutableListOf(0, 1, 2, 3)
            for (index in 3 downTo 1) {
                val other = (random.next() * (index + 1)).toInt()
                order[index] = order[other].also { order[other] = order[index] }
            }
            val finishes = MutableList(4) { 1.0 }
            for ((rank, quadrant) in order.take(3).withIndex()) {
                finishes[quadrant] = earlyFinishes[rank] + (random.next() - 0.5) * JITTER
            }
            return finishes.map { finalStart ->
                StageTimeline(finalStart * (0.3 + 0.4 * random.next()), finalStart)
            }
        }
    }
}

/** A tiny seeded generator (a string hash feeding mulberry32) so every planting looks the same each time it is drawn. */
internal class StageRandom(
    seed: String,
) {
    private var state: UInt =
        seed.toByteArray(Charsets.UTF_8).fold(0x811c9dc5u) { hash, byte ->
            (hash xor (byte.toInt() and 0xFF).toUInt()) * 0x01000193u
        }

    fun next(): Double {
        state += 0x6d2b79f5u
        var r = (state xor (state shr 15)) * (1u or state)
        r = r xor (r + (r xor (r shr 7)) * (61u or r))
        return (r xor (r shr 14)).toDouble() / 4_294_967_296.0
    }
}
