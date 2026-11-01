package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.model.GridPoint

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
