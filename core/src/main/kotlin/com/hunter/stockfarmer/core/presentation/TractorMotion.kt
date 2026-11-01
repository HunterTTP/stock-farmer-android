package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A point on the map in tiles, with rows increasing downwards. */
data class TilePoint(
    val x: Double,
    val y: Double,
)

object TractorMotion {
    data class Frame(
        val position: TilePoint,
        val facing: TractorFacing,
    )

    /** Coordinates are measured in tiles, with rows increasing downwards. */
    fun center(
        head: GridPoint,
        facing: TractorFacing,
    ): TilePoint {
        val origin = TractorRules.origin(head, facing)
        val horizontal = facing.direction.col != 0
        return TilePoint(
            x = origin.col + if (horizontal) 1.0 else 0.5,
            y = origin.row + if (horizontal) 0.5 else 1.0,
        )
    }

    fun duration(run: TractorRun): Double {
        val next = run.nextTile ?: return 0.0
        val start = center(run.position, run.facing)
        val end = center(next, run.destinationFacing)
        return (abs(end.x - start.x) + abs(end.y - start.y)) * TractorRules.SECONDS_PER_TILE
    }

    fun frame(run: TractorRun): Frame {
        val start = center(run.position, run.facing)
        val next = run.nextTile
        val heading = run.nextFacing
        if (next == null || heading == null) return Frame(start, run.facing)
        val end = center(next, run.destinationFacing)
        val horizontal = heading.direction.col != 0
        val reversesStep = run.nextPoseFacing != null
        val corner = if (horizontal != reversesStep) TilePoint(start.x, end.y) else TilePoint(end.x, start.y)
        val firstLength = abs(corner.x - start.x) + abs(corner.y - start.y)
        val lastLength = abs(end.x - corner.x) + abs(end.y - corner.y)
        val distance = (firstLength + lastLength) * run.progress
        if (distance < firstLength) return frame(start, corner, distance / firstLength)
        if (lastLength <= 0) return Frame(end, if (firstLength > 0) facing(start, corner) else heading)
        return frame(corner, end, (distance - firstLength) / lastLength)
    }

    private fun frame(
        start: TilePoint,
        end: TilePoint,
        progress: Double,
    ) = Frame(
        TilePoint(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress),
        facing(start, end),
    )

    private fun facing(
        start: TilePoint,
        end: TilePoint,
    ): TractorFacing {
        if (end.x != start.x) return if (end.x > start.x) TractorFacing.right else TractorFacing.left
        return if (end.y > start.y) TractorFacing.down else TractorFacing.up
    }
}

class TractorBobbing(
    height: Double = DEFAULT_HEIGHT,
    speed: Double = DEFAULT_SPEED,
) {
    val height: Double =
        if (height.isFinite()) {
            min(
                max(height, HEIGHT_RANGE.start),
                HEIGHT_RANGE.endInclusive,
            )
        } else {
            DEFAULT_HEIGHT
        }
    val speed: Double =
        if (speed.isFinite()) {
            min(
                max(speed, SPEED_RANGE.start),
                SPEED_RANGE.endInclusive,
            )
        } else {
            DEFAULT_SPEED
        }

    fun offset(movementTime: Double): Double =
        height * (1 - cos(movementTime / TractorRules.SECONDS_PER_TILE * speed * Math.PI * 2)) / 2

    override fun equals(other: Any?) = other is TractorBobbing && other.height == height && other.speed == speed

    override fun hashCode() = 31 * height.hashCode() + speed.hashCode()

    companion object {
        val HEIGHT_RANGE = 0.0..0.1
        val SPEED_RANGE = 0.25..4.0
        private const val DEFAULT_HEIGHT = 0.005
        private const val DEFAULT_SPEED = 2.0
    }
}
