package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState

internal fun drawGrid(
    batch: SpriteBatch,
    textures: TextureLibrary,
    scale: Float,
    maxScale: Float,
) {
    val old = batch.packedColor
    batch.setColor(0.12f, 0.12f, 0.12f, 0.9f * VisualTiming.gridAlpha(scale, maxScale))
    val half = scale / 2
    for (index in 0..GameState.GRID_SIZE) {
        val offset = index * FarmRenderer.TILE
        batch.draw(textures.white, offset - half, 0f, scale, FarmRenderer.WORLD)
        batch.draw(textures.white, 0f, offset - half, FarmRenderer.WORLD, scale)
    }
    batch.packedColor = old
}
