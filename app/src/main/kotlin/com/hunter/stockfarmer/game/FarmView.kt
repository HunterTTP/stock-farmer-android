package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.model.GridPoint
import kotlin.math.floor

/** What the camera is looking at, as the farm last drew it. */
data class ViewSnapshot(
    val centerX: Float,
    val centerY: Float,
    /** World units a screen pixel covers. */
    val worldPerPixel: Float,
    val widthPixels: Int,
    val heightPixels: Int,
    /** 0 at the closest zoom and 1 at the furthest. */
    val zoomFraction: Float,
)

/** Shared between the farm, which draws on its own thread, and whoever turns game events into sound. */
class FarmView {
    @Volatile
    var snapshot: ViewSnapshot? = null

    /** Sprites drawn in the last frame, for the performance screen's node count. */
    @Volatile
    var drawnSprites: Int = 0

    /** What the dragged tool would do under the finger, drawn on the farm like a placement. */
    @Volatile
    var toolPreview: PlacementPreview? = null

    /** The tile under a point on the screen, or null when it is off the map or the farm has not been drawn yet. */
    fun tileAt(
        screenX: Float,
        screenY: Float,
        tileSize: Float,
        gridSize: Int,
    ): GridPoint? {
        val view = snapshot ?: return null
        val worldX = view.centerX + (screenX - view.widthPixels / 2f) * view.worldPerPixel
        val worldY = view.centerY - (screenY - view.heightPixels / 2f) * view.worldPerPixel
        val col = floor(worldX / tileSize).toInt()
        val row = gridSize - 1 - floor(worldY / tileSize).toInt()
        return if (col in 0 until gridSize && row in 0 until gridSize) GridPoint(row, col) else null
    }

    /** How a sound from a tile is heard: quieter and to one side when it is off to one side or far from the view. */
    fun placement(
        tile: GridPoint,
        tileSize: Float,
        gridSize: Int,
    ): SoundPlacement {
        val view = snapshot ?: return SoundPlacement.centered
        val worldX = (tile.col + 0.5f) * tileSize
        val worldY = (gridSize - tile.row - 0.5f) * tileSize
        return SoundPlacement(
            zoomFraction = view.zoomFraction.toDouble(),
            horizontalOffset = ((worldX - view.centerX) / view.worldPerPixel).toDouble(),
            verticalOffset = ((worldY - view.centerY) / view.worldPerPixel).toDouble(),
            viewWidth = view.widthPixels.toDouble(),
            viewHeight = view.heightPixels.toDouble(),
        )
    }
}
