package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.smoothstep
import com.hunter.stockfarmer.farm.Standing
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * The drops one spray throws, one slot of each array to a drop, so a later spray reuses the memory of the one before it.
 * Offsets are world units from the sprinkler's middle, with y up the screen.
 */
internal class Spray(
    capacity: Int,
) {
    var startedAt = 0.0
    var duration = 0.0
    var count = 0
    val delay = FloatArray(capacity)
    val flight = FloatArray(capacity)
    val startX = FloatArray(capacity)
    val startY = FloatArray(capacity)
    val endX = FloatArray(capacity)
    val endY = FloatArray(capacity)
    val controlY = FloatArray(capacity)
    val tint = IntArray(capacity)

    fun isOver(seconds: Double) = count == 0 || seconds - startedAt >= duration

    /** The drop's place along its arc, which is a quadratic curve eased in and out over its flight. */
    fun progress(
        drop: Int,
        age: Float,
    ): Float = smoothstep((age / flight[drop]).toDouble()).toFloat()

    fun x(
        drop: Int,
        progress: Float,
    ): Float = startX[drop] + endX[drop] * progress

    fun y(
        drop: Int,
        progress: Float,
    ): Float {
        val rest = 1 - progress
        return startY[drop] + 2 * rest * progress * controlY[drop] + progress * progress * endY[drop]
    }

    /** A drop fades in over 0.15 seconds, stays, and fades out over the last 0.25 seconds of its flight. */
    fun alpha(
        drop: Int,
        age: Float,
    ): Float = minOf(1f, age / FADE_IN, (flight[drop] - age) / FADE_OUT).coerceAtLeast(0f)

    /** The mist fades in over 1.5 seconds to 0.3, holds, and fades out over the last 1.5 seconds. */
    fun mistAlpha(seconds: Double): Float {
        val age = seconds - startedAt
        return (MIST_ALPHA * minOf(1.0, age / MIST_FADE, (duration - age) / MIST_FADE).coerceAtLeast(0.0)).toFloat()
    }

    companion object {
        const val FADE_IN = 0.15f
        const val FADE_OUT = 0.25f
        const val MIST_ALPHA = 0.3
        const val MIST_FADE = 1.5
    }
}

/** A sprinkler standing on the farm and the sprays it is throwing; at most two at once, for a fountain's overlapping ones. */
internal class Sprinkler(
    val decoration: Decoration,
    var placed: PlacedDecoration,
    val centerX: Float,
    val centerY: Float,
    val bottomEdge: Float,
) {
    val reach = decoration.waterReach ?: 0
    val sprays = Array(2) { Spray(((2 * reach + 1) * (2 * reach + 1) - 1) * SprinklerSprays.DROPS_PER_TILE) }

    /** The latest of the sprays all sprinklers share that this one has had, and when it last sprayed as a fountain. */
    var sprayCount = 0
    var fountainAt = Double.NEGATIVE_INFINITY
    var seenIn = -1L

    /** Drawn in its turn each frame; made once so a frame allocates nothing for it. */
    var standing: Standing? = null

    /** How far the mist and the drops reach from the middle. */
    val extent get() = (reach + 1) * TILE.toFloat()
}

/**
 * Sprinklers spray every minute together by the clock on the wall, so it is the same however the game was opened or put
 * away, and never while it is raining; fountains spray all the time, rain or not. Port of iOS `DecorationLayer`.
 */
internal class SprinklerSprays(
    private val random: Random = Random.Default,
) {
    private val byOrigin = HashMap<GridPoint, Sprinkler>()
    private var frame = 0L
    private var fingerprint = 0
    private var knownSize = -1

    /** The sprinklers standing now, as a list so a frame can walk it without allocating. */
    val sprinklers = ArrayList<Sprinkler>()

    /**
     * Notices sprinklers set down, moved or taken away. The decorations are walked only when they have changed, which
     * is read as a hash and a size, as for the growth conditions.
     */
    fun sync(
        decorations: Map<GridPoint, PlacedDecoration>,
        now: Instant,
    ) {
        val hash = decorations.hashCode()
        if (hash == fingerprint && decorations.size == knownSize) return
        fingerprint = hash
        knownSize = decorations.size
        frame++
        var seen = 0
        for ((origin, placed) in decorations) {
            val decoration = Decoration.named(placed.decorationID)
            if (decoration == null || !decoration.isSprinkler) continue
            seen++
            var sprinkler = byOrigin[origin]
            if (sprinkler != null && !looksTheSame(sprinkler.placed, placed)) sprinkler = null
            if (sprinkler == null) {
                sprinkler = place(origin, decoration, placed, now)
                byOrigin[origin] = sprinkler
            } else if (placed.placedAt != sprinkler.placed.placedAt) {
                sprinkler.sprayCount = Decoration.spraysDone(now) - 1
            }
            sprinkler.placed = placed
            sprinkler.seenIn = frame
        }
        if (seen != byOrigin.size) byOrigin.values.removeAll { it.seenIn != frame }
        sprinklers.clear()
        sprinklers.addAll(byOrigin.values)
    }

    private fun place(
        origin: GridPoint,
        decoration: Decoration,
        placed: PlacedDecoration,
        now: Instant,
    ): Sprinkler {
        val sprinkler =
            Sprinkler(
                decoration,
                placed,
                (origin.col + decoration.width / 2f) * TILE.toFloat(),
                (WORLD - (origin.row + decoration.height / 2.0) * TILE).toFloat(),
                (origin.row + decoration.height).toFloat(),
            )
        // One just set down has not had its first spray yet, so it sprays at once.
        val isNew = placed.placedAt?.let { seconds(now) - seconds(it) < 1 } ?: false
        sprinkler.sprayCount = if (isNew) -1 else Decoration.spraysDone(now)
        return sprinkler
    }

    private fun looksTheSame(
        drawn: PlacedDecoration,
        placed: PlacedDecoration,
    ) = drawn.decorationID == placed.decorationID && drawn.size == placed.size

    /** Sprays any sprinkler that has come to its time. */
    fun tick(
        now: Instant,
        isRaining: Boolean,
        runsConstantly: Boolean,
    ) {
        val seconds = seconds(now)
        for (index in sprinklers.indices) {
            val sprinkler = sprinklers[index]
            if (runsConstantly) {
                if (seconds - sprinkler.fountainAt < FOUNTAIN_INTERVAL) continue
                sprinkler.fountainAt = seconds
                spray(sprinkler, seconds, FOUNTAIN_DURATION, FOUNTAIN_DROPS_PER_TILE)
                continue
            }
            val latest = Decoration.spraysDone(now)
            if (latest <= sprinkler.sprayCount) continue
            sprinkler.sprayCount = latest
            if (!isRaining) spray(sprinkler, seconds, Decoration.SPRAY_DURATION, DROPS_PER_TILE)
        }
    }

    /** Throws drops out of the tips of a sprinkler that arc up and fall into every tile it waters, at random moments of the spray. */
    private fun spray(
        sprinkler: Sprinkler,
        seconds: Double,
        duration: Double,
        dropsPerTile: Int,
    ) {
        val spray = sprinkler.sprays.firstOrNull { it.isOver(seconds) } ?: sprinkler.sprays.minBy { it.startedAt }
        spray.startedAt = seconds
        spray.duration = duration
        var drops = 0
        val reach = sprinkler.reach
        for (rowOffset in -reach..reach) {
            for (colOffset in -reach..reach) {
                if (rowOffset == 0 && colOffset == 0) continue
                val sideways =
                    if (abs(colOffset) ==
                        abs(rowOffset)
                    ) {
                        random.nextBoolean()
                    } else {
                        abs(colOffset) > abs(rowOffset)
                    }
                val tipX = if (sideways) (if (colOffset < 0) -SIDE_TIP else SIDE_TIP) else 0f
                val tipY =
                    if (sideways) {
                        SIDE_TIP_Y
                    } else if (rowOffset < 0) {
                        UP_TIP
                    } else {
                        -UP_TIP
                    }
                val distance = max(abs(rowOffset), abs(colOffset))
                val flight = NEAREST_FLIGHT + FLIGHT_PER_TILE * (distance - 1)
                repeat(dropsPerTile) {
                    // Rows count down the map and y counts up it.
                    val endX = (colOffset + random.nextFloat() * 0.7f - 0.35f - tipX) * TILE.toFloat()
                    val endY = (-rowOffset + random.nextFloat() * 0.7f - 0.35f - tipY) * TILE.toFloat()
                    val peak = max(0f, endY) + (NEAREST_RISE + RISE_PER_TILE * (distance - 1)) * TILE.toFloat()
                    spray.flight[drops] = flight
                    spray.delay[drops] = random.nextFloat() * (duration.toFloat() - flight)
                    spray.startX[drops] = tipX * TILE.toFloat()
                    spray.startY[drops] = tipY * TILE.toFloat()
                    spray.endX[drops] = endX
                    spray.endY[drops] = endY
                    spray.controlY[drops] = 2 * peak - endY / 2
                    spray.tint[drops] = random.nextInt(WaterDrops.colors.size)
                    drops++
                }
            }
        }
        spray.count = drops
    }

    companion object {
        const val DROPS_PER_TILE = 14
        const val FOUNTAIN_DROPS_PER_TILE = 2
        const val FOUNTAIN_DURATION = 4.0
        const val FOUNTAIN_INTERVAL = FOUNTAIN_DURATION - Spray.MIST_FADE

        /** Where a drop leaves the sprinkler, as fractions of a tile from its middle: points up and down, arms left and right. */
        private const val UP_TIP = 0.25f
        private const val SIDE_TIP = 0.25f
        private const val SIDE_TIP_Y = 0.04f
        private const val NEAREST_FLIGHT = 0.9f
        private const val FLIGHT_PER_TILE = 0.2f
        private const val NEAREST_RISE = 0.1f
        private const val RISE_PER_TILE = 0.04f

        fun seconds(at: Instant): Double = at.epochSecond + at.nano / 1e9
    }
}
