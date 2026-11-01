package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GridPoint(
    val row: Int,
    val col: Int,
) {
    /** The tiles of a rectangle whose top left is this tile, row by row. */
    fun rectangle(
        rows: Int,
        cols: Int,
    ): List<GridPoint> =
        (0 until rows).flatMap { rowOffset -> (0 until cols).map { GridPoint(row + rowOffset, col + it) } }

    /** The top left of a square of [side] tiles standing on this tile: its bottom row, in the middle. */
    fun topLeftOfSquare(side: Int): GridPoint = GridPoint(row - (side - 1), col - (side - 1) / 2)
}

/** The tiles on the lowest row of the map among them: what a picture standing on this ground really takes up. */
val Collection<GridPoint>.bottomRow: List<GridPoint>
    get() {
        val lowest = maxOfOrNull { it.row } ?: return emptyList()
        return filter { it.row == lowest }
    }
