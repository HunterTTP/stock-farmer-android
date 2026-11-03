package com.hunter.stockfarmer.farm

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.ScreenUtils
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.farm.animals.AnimalArt
import com.hunter.stockfarmer.farm.animals.animalSources
import com.hunter.stockfarmer.farm.effects.PhotoEffects
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * A picture of the whole farm, drawn off screen on the graphics thread and encoded as a PNG off it. It keeps
 * the season's sky, water, finished shadows, the night's darkness and the rain or snow of the moment, and leaves out the effects that
 * move with the live view. [state] must not change while drawing: pass a copy, not the live farm.
 */
internal class FarmPhoto(
    private val renderer: FarmRenderer,
    private val batch: SpriteBatch,
    private val textures: TextureLibrary,
    private val effects: PhotoEffects,
    private val animalArt: AnimalArt,
) {
    private val encoder = PhotoEncoder(::encodePng, Pixmap::dispose)

    /**
     * Draws now and answers later, on another thread: [done] gets the PNG of the map with a margin of sky around it,
     * [pixels] on a side, or null if the device ran out of memory.
     */
    fun render(
        state: GameState,
        pixels: Int,
        now: Instant,
        done: (ByteArray?) -> Unit,
    ) {
        val picture =
            try {
                draw(state, pixels, now)
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: GdxRuntimeException) {
                null
            }
        if (picture == null) done(null) else encoder.submit(picture, done)
    }

    private fun draw(
        state: GameState,
        pixels: Int,
        now: Instant,
    ): Pixmap {
        val layout = FarmPhotoLayout(pixels)
        val side = layout.worldSide
        val camera =
            OrthographicCamera(side, side).apply {
                position.set(FarmRenderer.WORLD / 2, FarmRenderer.WORLD / 2, 0f)
                update()
            }
        val target = FrameBuffer(Pixmap.Format.RGBA8888, pixels, pixels, false)
        val previousProjection = batch.projectionMatrix.cpy()
        // A photo starts its own scene, so it cannot consume gestures or advance live animal, growth or insect clocks.
        val tractor = TractorLayer(textures)
        val photoRenderer =
            FarmRenderer(textures, listOf(tractor) + animalSources(textures, art = animalArt)).apply {
                gridShown = renderer.gridShown
                cameraScale = layout.worldPerPixel
                maxCameraScale = Float.MAX_VALUE
                treesLean = false
            }
        val photoEffects = effects.fresh()
        var bound = false
        try {
            target.begin()
            bound = true
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            batch.projectionMatrix = camera.combined
            batch.begin()
            photoEffects.drawSky(batch, state, now, camera)
            photoRenderer.draw(batch, state, now) { b, s, t -> photoEffects.drawGround(b, s, t, camera) }
            batch.drawNightTint(textures, camera, DayNightCycle.darkness(now, state.clockOffset))
            photoEffects.drawWeather(batch, state, now, camera)
            tractor.drawLoad(batch, state, now)
            batch.end()
            return ScreenUtils.getFrameBufferPixmap(0, 0, pixels, pixels)
        } finally {
            if (batch.isDrawing) batch.end()
            if (bound) target.end()
            batch.projectionMatrix = previousProjection
            target.dispose()
        }
    }

    private fun encodePng(picture: Pixmap): ByteArray {
        val bytes = ByteArrayOutputStream()
        PixmapIO.PNG(picture.width * picture.height * 4).also {
            it.setFlipY(true)
            it.write(bytes, picture)
            it.dispose()
        }
        return bytes.toByteArray()
    }
}

/** FarmScene.photoScene gives the square map two tiles of sky on every side, at either export resolution. */
internal class FarmPhotoLayout(
    pixels: Int,
) {
    val worldSide = FarmRenderer.WORLD + 4 * FarmRenderer.TILE
    val worldPerPixel = worldSide / pixels
}
