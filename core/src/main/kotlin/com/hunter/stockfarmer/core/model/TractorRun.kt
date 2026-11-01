@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class TractorFacing {
    right,
    up,
    left,
    down,
    ;

    val rawValue: String get() = name

    val direction: GridPoint
        get() =
            when (this) {
                right -> GridPoint(0, 1)
                up -> GridPoint(-1, 0)
                left -> GridPoint(0, -1)
                down -> GridPoint(1, 0)
            }

    val styleID: String
        get() =
            when (this) {
                right -> "right"
                up -> "back"
                left -> "left"
                down -> "front"
            }

    companion object {
        fun ofStyle(styleID: String?): TractorFacing =
            when (styleID) {
                "left" -> left
                "back" -> up
                "front" -> down
                else -> right
            }

        fun ofDirection(direction: GridPoint): TractorFacing =
            when {
                direction.col > 0 -> right
                direction.col < 0 -> left
                direction.row < 0 -> up
                else -> down
            }
    }
}

@Serializable(with = TractorRunSerializer::class)
data class TractorRun(
    val home: GridPoint,
    val cropID: String,
    /** The tile under the front of the Tractor, where one crop is collected. */
    val position: GridPoint,
    val nextTile: GridPoint? = null,
    val nextPoseFacing: TractorFacing? = null,
    val facing: TractorFacing,
    val progress: Double = 0.0,
    val returning: Boolean = false,
    val remaining: Set<GridPoint> = emptySet(),
) {
    val nextFacing: TractorFacing?
        get() = nextTile?.let { TractorFacing.ofDirection(GridPoint(it.row - position.row, it.col - position.col)) }

    val destinationFacing: TractorFacing get() = nextPoseFacing ?: nextFacing ?: facing
}

object TractorRules {
    const val SECONDS_PER_TILE = 1.2

    fun footprint(
        head: GridPoint,
        facing: TractorFacing,
    ): List<GridPoint> {
        val horizontal = facing.direction.col != 0
        return origin(head, facing).rectangle(rows = if (horizontal) 1 else 2, cols = if (horizontal) 2 else 1)
    }

    internal fun nextHead(
        head: GridPoint,
        heading: TractorFacing,
    ) = GridPoint(head.row + heading.direction.row, head.col + heading.direction.col)

    fun headTile(
        origin: GridPoint,
        facing: TractorFacing,
    ) = GridPoint(origin.row + max(0, facing.direction.row), origin.col + max(0, facing.direction.col))

    internal fun origin(
        head: GridPoint,
        facing: TractorFacing,
    ) = GridPoint(head.row - max(0, facing.direction.row), head.col - max(0, facing.direction.col))
}

/** Both ends of a driving step stay reserved until the Tractor arrives. */
val GameState.tractorOccupiedTiles: Set<GridPoint>
    get() {
        val run = tractorRun ?: return emptySet()
        return (
            TractorRules.footprint(run.position, run.facing) +
                (run.nextTile?.let { TractorRules.footprint(it, run.destinationFacing) } ?: emptyList())
        ).toSet()
    }
