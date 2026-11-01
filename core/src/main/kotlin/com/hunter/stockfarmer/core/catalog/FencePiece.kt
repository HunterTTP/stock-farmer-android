@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint

enum class FencePiece(
    val rawValue: String,
) {
    unconnectedPost("unconnected-post"),
    leftEndcap("left-endcap"),
    rightEndcap("right-endcap"),
    topEndcap("top-endcap"),
    bottomEndcap("bottom-endcap"),
    middleHorizontal("middle-horizontal"),
    middleVertical("middle-vertical"),
    topLeftCorner("top-left-corner"),
    topRightCorner("top-right-corner"),
    bottomLeftCorner("bottom-left-corner"),
    bottomRightCorner("bottom-right-corner"),
}

data class FencePlacement(
    val piece: FencePiece,
    val column: Int,
    val row: Int,
)

data class FenceConnections(
    val north: Boolean,
    val east: Boolean,
    val south: Boolean,
    val west: Boolean,
) {
    constructor(around: GridPoint, fences: Map<GridPoint, String>) : this(
        GridPoint(around.row - 1, around.col) in fences,
        GridPoint(around.row, around.col + 1) in fences,
        GridPoint(around.row + 1, around.col) in fences,
        GridPoint(around.row, around.col - 1) in fences,
    )

    /** Corners and ends have their own picture; junctions layer horizontal and vertical pieces. */
    val centerPieces: List<FencePiece> get() =
        when {
            !north && !east && !south && !west -> listOf(FencePiece.unconnectedPost)
            !north && east && south && !west -> listOf(FencePiece.topLeftCorner)
            !north && !east && south && west -> listOf(FencePiece.topRightCorner)
            north && east && !south && !west -> listOf(FencePiece.bottomLeftCorner)
            north && !east && !south && west -> listOf(FencePiece.bottomRightCorner)
            else -> listOfNotNull(horizontalPiece, verticalPiece)
        }

    val arms: List<FencePlacement> get() =
        listOfNotNull(
            if (west) FencePlacement(FencePiece.middleHorizontal, -1, 0) else null,
            if (east) FencePlacement(FencePiece.middleHorizontal, 1, 0) else null,
            if (north) FencePlacement(FencePiece.middleVertical, 0, -1) else null,
            if (south) FencePlacement(FencePiece.middleVertical, 0, 1) else null,
        )

    private val horizontalPiece: FencePiece? get() =
        when {
            east && west -> FencePiece.middleHorizontal
            east -> FencePiece.leftEndcap
            west -> FencePiece.rightEndcap
            else -> null
        }
    private val verticalPiece: FencePiece? get() =
        when {
            north && south -> FencePiece.middleVertical
            north -> FencePiece.bottomEndcap
            south -> FencePiece.topEndcap
            else -> null
        }
}
