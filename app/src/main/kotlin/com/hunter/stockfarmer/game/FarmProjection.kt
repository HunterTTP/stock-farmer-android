package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint

/** A tile's side in world units; the map is [GameState.GRID_SIZE] tiles square. */
const val TILE = 100f

/** The side of the whole map in world units. */
const val WORLD = GameState.GRID_SIZE * TILE

data class FarmScreenPoint(
    val x: Float,
    val y: Float,
)

/** Inverse of FarmView.tileAt, using tile centers and the farm's Y-up world coordinates. */
fun ViewSnapshot.screenPosition(
    tile: GridPoint,
    tileSize: Float,
    gridSize: Int,
): FarmScreenPoint? {
    if (worldPerPixel <= 0f || !worldPerPixel.isFinite() || widthPixels <= 0 || heightPixels <= 0) return null
    val worldX = (tile.col + 0.5f) * tileSize
    val worldY = (gridSize - tile.row - 0.5f) * tileSize
    return FarmScreenPoint(
        widthPixels / 2f + (worldX - centerX) / worldPerPixel,
        heightPixels / 2f - (worldY - centerY) / worldPerPixel,
    )
}

fun ViewSnapshot.isOnScreen(point: FarmScreenPoint): Boolean =
    point.x >= 0f && point.x < widthPixels && point.y >= 0f && point.y < heightPixels
