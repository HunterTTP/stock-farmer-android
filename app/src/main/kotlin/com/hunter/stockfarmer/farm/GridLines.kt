package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.WORLD

internal fun drawGrid(
    batch: SpriteBatch,
    textures: TextureLibrary,
    scale: Float,
    maxScale: Float,
    view: ViewBounds = ViewBounds.EVERYTHING,
) {
    val alpha = VisualTiming.gridAlpha(scale, maxScale)
    if (alpha <= 0f) return
    val old = batch.packedColor
    batch.setColor(0.12f, 0.12f, 0.12f, 0.9f * alpha)
    val half = scale / 2
    for (index in 0..GameState.GRID_SIZE) {
        val offset = index * TILE
        if (view.sees(offset - half, 0f, scale, WORLD)) {
            batch.draw(textures.white, offset - half, 0f, scale, WORLD)
        }
        if (view.sees(0f, offset - half, WORLD, scale)) {
            batch.draw(textures.white, 0f, offset - half, WORLD, scale)
        }
    }
    batch.packedColor = old
}
