package com.hunter.stockfarmer.farm

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import com.hunter.stockfarmer.core.model.GameState
import java.io.ByteArrayOutputStream
import java.time.Instant

/** A picture of the whole farm, drawn off screen and encoded as a PNG. Call on the graphics thread. */
class FarmPhoto(
    private val renderer: FarmRenderer,
    private val batch: SpriteBatch,
) {
    /** The PNG of the map with a margin of sky around it, [pixels] on a side, or null if the device ran out of memory. */
    fun render(
        state: GameState,
        pixels: Int,
        now: Instant,
    ): ByteArray? =
        try {
            draw(state, pixels, now)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: com.badlogic.gdx.utils.GdxRuntimeException) {
            null
        }

    private fun draw(
        state: GameState,
        pixels: Int,
        now: Instant,
    ): ByteArray {
        val side = FarmRenderer.WORLD + 2 * MARGIN_TILES * FarmRenderer.TILE
        val camera =
            OrthographicCamera(side, side).apply {
                position.set(FarmRenderer.WORLD / 2, FarmRenderer.WORLD / 2, 0f)
                update()
            }
        val target = FrameBuffer(Pixmap.Format.RGBA8888, pixels, pixels, false)
        try {
            target.begin()
            Gdx.gl.glClearColor(SKY_RED, SKY_GREEN, SKY_BLUE, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            batch.projectionMatrix = camera.combined
            batch.begin()
            renderer.draw(batch, state, now)
            batch.end()
            val picture = ScreenUtils.getFrameBufferPixmap(0, 0, pixels, pixels)
            target.end()
            try {
                val bytes = ByteArrayOutputStream()
                PixmapIO.PNG(pixels * pixels * 4).also {
                    it.setFlipY(true)
                    it.write(bytes, picture)
                    it.dispose()
                }
                return bytes.toByteArray()
            } finally {
                picture.dispose()
            }
        } finally {
            target.dispose()
        }
    }

    private companion object {
        const val MARGIN_TILES = 2
        const val SKY_RED = 0.42f
        const val SKY_GREEN = 0.68f
        const val SKY_BLUE = 0.85f
    }
}
