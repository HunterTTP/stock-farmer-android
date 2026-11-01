package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.game.PlacementPreview
import java.time.Instant

/** Persistent placement feedback, or a tool flash that disappears after 0.7 seconds. */
class TilePreview {
    private var flash: PlacementPreview? = null
    private var flashedAt = Instant.EPOCH

    fun flash(
        points: List<GridPoint>,
        allowed: Boolean,
        now: Instant,
    ) {
        flash = PlacementPreview(points, allowed, null)
        flashedAt = now
    }

    fun draw(
        batch: SpriteBatch,
        textures: TextureLibrary,
        preview: PlacementPreview?,
        now: Instant,
        scale: Float,
    ) {
        val alpha = if (preview != null) 1f else VisualTiming.previewAlpha(now.secondsSince(flashedAt))
        val shown = preview ?: flash ?: return
        if (alpha <= 0) {
            flash = null
            return
        }
        drawTiles(batch, textures, shown, 3 * scale, alpha)
    }
}

internal fun drawTiles(
    batch: SpriteBatch,
    textures: TextureLibrary,
    preview: PlacementPreview,
    lineWidth: Float,
    alpha: Float = 1f,
) {
    val old = batch.packedColor
    val red = if (preview.canPlace) 0.13f else 0.94f
    val green = if (preview.canPlace) 0.77f else 0.27f
    val blue = if (preview.canPlace) 0.37f else 0.27f
    for (tile in preview.footprint) {
        val x = tile.col * FarmRenderer.TILE
        val y = FarmRenderer.worldY(tile.row)
        val side = FarmRenderer.TILE
        batch.setColor(red, green, blue, 0.35f * alpha)
        batch.draw(textures.white, x, y, side, side)
        batch.setColor(red, green, blue, 0.9f * alpha)
        val half = lineWidth / 2
        batch.draw(textures.white, x - half, y - half, side + lineWidth, lineWidth)
        batch.draw(textures.white, x - half, y + side - half, side + lineWidth, lineWidth)
        batch.draw(textures.white, x - half, y + half, lineWidth, side - lineWidth)
        batch.draw(textures.white, x + side - half, y + half, lineWidth, side - lineWidth)
    }
    batch.packedColor = old
}
