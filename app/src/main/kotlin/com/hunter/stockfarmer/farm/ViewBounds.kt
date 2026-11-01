package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import kotlin.math.floor

/** The part of the map the camera shows, in world units, so the renderer can skip what lies wholly outside it. */
internal class ViewBounds(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
) {
    /** This view grown by [amount] on every side. */
    fun padded(amount: Float) = ViewBounds(left - amount, bottom - amount, right + amount, top + amount)

    /** Whether a picture at [x], [y] of [width] by [height] reaches into the view, counting [reach] of overhang on every side. */
    fun sees(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        reach: Float = 0f,
    ): Boolean = x + width + reach >= left && x - reach <= right && y + height + reach >= bottom && y - reach <= top

    /** Whether any part of the tile at [row] and [col] is in the view. */
    fun seesTile(
        row: Int,
        col: Int,
    ): Boolean = sees(col * TILE, FarmRenderer.worldY(row), TILE, TILE)

    /** The columns of tiles in the view. */
    val columns: IntRange
        get() = tileIndex(left).coerceAtLeast(0)..tileIndex(right).coerceAtMost(LAST)

    /** The rows of tiles in the view; rows count down the map while world y counts up. */
    val rows: IntRange
        get() = (LAST - tileIndex(top)).coerceAtLeast(0)..(LAST - tileIndex(bottom)).coerceAtMost(LAST)

    private fun tileIndex(world: Float): Int = floor((world / TILE).coerceIn(-1f, GameState.GRID_SIZE + 1f)).toInt()

    companion object {
        private const val TILE = FarmRenderer.TILE
        private const val LAST = GameState.GRID_SIZE - 1

        /** No culling: for a photo of the whole map. */
        val EVERYTHING =
            ViewBounds(
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
            )

        /** The area of a camera centered at ([x], [y]) showing [width] by [height] world units. */
        fun around(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) = ViewBounds(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
    }
}
