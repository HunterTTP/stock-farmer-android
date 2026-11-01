package com.hunter.stockfarmer.farm

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector3
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.advanceTractor
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.farm.animals.animalSources
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.ViewSnapshot
import com.hunter.stockfarmer.game.preview
import com.hunter.stockfarmer.game.touch
import java.time.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The farm scene: draws the session's game and turns taps and drags into play and camera movement. */
class FarmGame(
    private val session: GameSession,
) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private lateinit var textures: TextureLibrary
    private lateinit var renderer: FarmRenderer
    private val camera = OrthographicCamera()
    private val touch = Vector3()

    override fun create() {
        batch = SpriteBatch()
        textures = TextureLibrary()
        renderer = FarmRenderer(textures, listOf(TractorLayer(textures)) + animalSources(textures))
        Gdx.input.inputProcessor = GestureDetector(Gestures())
        placeCamera()
    }

    override fun resize(
        width: Int,
        height: Int,
    ) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        placeCamera()
    }

    private fun placeCamera() {
        val shortSide = min(Gdx.graphics.width, Gdx.graphics.height).coerceAtLeast(1)
        camera.zoom = INITIAL_VISIBLE_TILES * FarmRenderer.TILE / shortSide
        val start = GameState.startingFarmland.first()
        camera.position.set(
            (start.col + 0.5f) * FarmRenderer.TILE,
            FarmRenderer.worldY(start.row) + FarmRenderer.TILE / 2,
            0f,
        )
        keepOnMap()
    }

    private fun keepOnMap() {
        val halfWidth = camera.viewportWidth * camera.zoom / 2
        val halfHeight = camera.viewportHeight * camera.zoom / 2
        camera.position.x =
            camera.position.x.coerceIn(
                min(halfWidth, FarmRenderer.WORLD - halfWidth),
                max(
                    halfWidth,
                    FarmRenderer.WORLD - halfWidth,
                ),
            )
        camera.position.y =
            camera.position.y.coerceIn(
                min(halfHeight, FarmRenderer.WORLD - halfHeight),
                max(
                    halfHeight,
                    FarmRenderer.WORLD - halfHeight,
                ),
            )
    }

    override fun render() {
        Gdx.gl.glClearColor(0.05f, 0.08f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        publishView()
        batch.projectionMatrix = camera.combined
        batch.begin()
        val now = Instant.now()
        driveTractor(now)
        session.read { renderer.draw(batch, it, now) }
        session.preview(now)?.let { renderer.drawPreview(batch, textures, it) }
        drawNightTint(now)
        batch.end()
    }

    /** Tells the sound side where the camera is looking. */
    private fun publishView() {
        val range = maxZoom() - MIN_ZOOM
        session.view.snapshot =
            ViewSnapshot(
                centerX = camera.position.x,
                centerY = camera.position.y,
                worldPerPixel = camera.zoom,
                widthPixels = Gdx.graphics.width,
                heightPixels = Gdx.graphics.height,
                zoomFraction = if (range > 0) ((camera.zoom - MIN_ZOOM) / range).coerceIn(0f, 1f) else 0f,
            )
    }

    private fun maxZoom() = FarmRenderer.WORLD / min(camera.viewportWidth, camera.viewportHeight)

    /** Lets a running tractor move on by the time since the last frame. */
    private fun driveTractor(now: Instant) {
        val elapsed = Gdx.graphics.deltaTime.toDouble()
        session.act { engine -> if (engine.state.tractorRun != null) engine.advanceTractor(elapsed, true, now) }
    }

    /** Darkens the whole view toward night by how dark the farm's clock says it is. */
    private fun drawNightTint(now: Instant) {
        val darkness = session.act { DayNightCycle.darkness(it.minuteOfDay(now)) }
        if (darkness <= 0.0) return
        batch.color = Color(NIGHT_RED, NIGHT_GREEN, NIGHT_BLUE, (darkness * NIGHT_STRENGTH).toFloat())
        batch.draw(
            textures.white,
            camera.position.x - camera.viewportWidth * camera.zoom / 2,
            camera.position.y - camera.viewportHeight * camera.zoom / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom,
        )
        batch.color = Color.WHITE
    }

    override fun dispose() {
        batch.dispose()
        textures.dispose()
    }

    private inner class Gestures : GestureDetector.GestureAdapter() {
        override fun tap(
            x: Float,
            y: Float,
            count: Int,
            button: Int,
        ): Boolean {
            camera.unproject(touch.set(x, y, 0f))
            val col = floor(touch.x / FarmRenderer.TILE).toInt()
            val row = GameState.GRID_SIZE - 1 - floor(touch.y / FarmRenderer.TILE).toInt()
            if (col in 0 until GameState.GRID_SIZE &&
                row in 0 until GameState.GRID_SIZE
            ) {
                session.touch(GridPoint(row, col))
            }
            return true
        }

        override fun pan(
            x: Float,
            y: Float,
            deltaX: Float,
            deltaY: Float,
        ): Boolean {
            camera.position.add(-deltaX * camera.zoom, deltaY * camera.zoom, 0f)
            keepOnMap()
            return true
        }

        private var zoomAtStart = 1f

        override fun zoom(
            initialDistance: Float,
            distance: Float,
        ): Boolean {
            if (initialDistance == distance) zoomAtStart = camera.zoom
            val wanted = zoomAtStart * initialDistance / distance
            camera.zoom = wanted.coerceIn(MIN_ZOOM, maxZoom())
            keepOnMap()
            return true
        }
    }

    private companion object {
        const val INITIAL_VISIBLE_TILES = 8f
        const val MIN_ZOOM = 0.25f
        const val NIGHT_RED = 0.04f
        const val NIGHT_GREEN = 0.06f
        const val NIGHT_BLUE = 0.22f
        const val NIGHT_STRENGTH = 0.6
    }
}
