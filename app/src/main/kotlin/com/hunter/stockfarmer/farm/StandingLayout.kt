package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.catalog.FencePlacement
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.game.TILE
import kotlin.math.min

/** Where a picture is drawn: its lower left corner and size in world units. */
internal data class Box(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** The geometry of what stands on the farm, from the reference's layers: sizes, anchors and the order fences are drawn in. */
internal object StandingLayout {
    /** A tile is this many pixels wide in the decoration, crop and building pictures. */
    const val PIXELS_PER_TILE = 48f

    /** A tile is this many pixels wide in the tree and rock pictures. */
    const val LARGE_PIXELS_PER_TILE = 96f

    private const val FIT_IN_SQUARE = 0.95f
    private const val FENCE_STRETCH = 1.5f

    /** The picture's size shrunk to fit inside [width] by [height], keeping its proportions. */
    fun fit(
        pictureWidth: Float,
        pictureHeight: Float,
        width: Float,
        height: Float,
    ): Pair<Float, Float> {
        val scale = min(width / pictureWidth, height / pictureHeight)
        return pictureWidth * scale to pictureHeight * scale
    }

    /** A building's picture fitted inside its footprint and centered on it. */
    fun building(
        origin: GridPoint,
        columns: Int,
        rows: Int,
        pictureWidth: Float,
        pictureHeight: Float,
    ): Box {
        val (width, height) = fit(pictureWidth, pictureHeight, columns * TILE, rows * TILE)
        return Box(
            origin.col * TILE + (columns * TILE - width) / 2,
            FarmRenderer.worldY(origin.row) + TILE - rows * TILE + (rows * TILE - height) / 2,
            width,
            height,
        )
    }

    /** A picture standing at the middle of the bottom edge of its ground, which is [columns] wide and [rows] tall. */
    fun standing(
        origin: GridPoint,
        columns: Int,
        rows: Int,
        pictureWidth: Float,
        pictureHeight: Float,
        pixelsPerTile: Float,
        drawScale: Float = 1f,
    ): Box {
        val scale = TILE / pixelsPerTile * drawScale
        val width = pictureWidth * scale
        return Box(
            (origin.col + columns / 2f) * TILE - width / 2,
            FarmRenderer.worldY(origin.row + rows - 1),
            width,
            pictureHeight * scale,
        )
    }

    /** A picture lying on its ground, such as a sprinkler, centered on it. */
    fun lying(
        origin: GridPoint,
        columns: Int,
        rows: Int,
        pictureWidth: Float,
        pictureHeight: Float,
        drawScale: Float,
    ): Box {
        val box = standing(origin, columns, rows, pictureWidth, pictureHeight, PIXELS_PER_TILE, drawScale)
        return box.copy(y = box.y + rows * TILE / 2 - box.height / 2)
    }

    /** A fruit tree fitted, with a little room to spare, inside its square of tiles and resting on its bottom edge. */
    fun fruitTree(
        origin: GridPoint,
        side: Int,
        pictureWidth: Float,
        pictureHeight: Float,
    ): Box {
        val square = side * TILE * FIT_IN_SQUARE
        val (width, height) = fit(pictureWidth, pictureHeight, square, square)
        return Box(
            (origin.col + side / 2f) * TILE - width / 2,
            FarmRenderer.worldY(origin.row + side - 1),
            width,
            height,
        )
    }

    /** One of a crop's four plants, numbered from the top left across and then down; each is half a tile square. */
    fun quadrant(
        point: GridPoint,
        quadrant: Int,
    ): Box {
        val half = TILE / 2
        return Box(
            point.col * TILE + (quadrant % 2) * half,
            FarmRenderer.worldY(point.row) + (1 - quadrant / 2) * half,
            half,
            half,
        )
    }

    /** The side of one of a fence tile's pictures, which overlap their neighbors a little. */
    val fencePictureSize: Float get() = TILE / 3 * FENCE_STRETCH

    /** Lower pictures are drawn in front of the ones above them, and the middle in front of its arms on the same line. */
    fun fenceDrawOrder(connections: FenceConnections): List<FencePlacement> {
        val center = connections.centerPieces.map { FencePlacement(it, 0, 0) }
        return (connections.arms + center).sortedBy { 2 * it.row + if (it.column == 0 && it.row == 0) 1 else 0 }
    }
}
