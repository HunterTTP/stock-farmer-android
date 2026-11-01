package com.hunter.stockfarmer.farm

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector3
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.catchButterfly
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.farm.animals.animalSources
import com.hunter.stockfarmer.farm.effects.EffectLayers
import com.hunter.stockfarmer.farm.effects.EffectPhase
import com.hunter.stockfarmer.farm.effects.effectLayers
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.PlacementDrag
import com.hunter.stockfarmer.game.ViewSnapshot
import com.hunter.stockfarmer.game.animalGestures
import com.hunter.stockfarmer.game.hold
import com.hunter.stockfarmer.game.preview
import com.hunter.stockfarmer.game.touch
import com.hunter.stockfarmer.game.touchAnimalAt
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import kotlinx.coroutines.flow.update
import java.time.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The farm scene: draws the session's game and turns taps and drags into play and camera movement. */
class FarmGame(
    private val session: GameSession,
    private val monitor: PerformanceMonitor? = null,
) : ApplicationAdapter() {
    private lateinit var batch: CountingSpriteBatch
    private lateinit var textures: TextureLibrary
    private lateinit var effects: EffectLayers
    private lateinit var renderer: FarmRenderer
    private lateinit var farmPhoto: FarmPhoto
    private val camera = OrthographicCamera()
    private val touch = Vector3()
    private val tilePreview = TilePreview()
    private val placementDrag = PlacementDrag(session)

    private fun <T> measure(
        work: PerformanceWork,
        action: () -> T,
    ): T = monitor?.measure(work, action) ?: action()

    fun flashPreview(
        points: List<GridPoint>,
        allowed: Boolean,
        now: Instant = Instant.now(),
    ) {
        Gdx.app.postRunnable { tilePreview.flash(points, allowed, now) }
    }

    fun revealGrowth(
        trees: Set<GridPoint>,
        rocks: Set<GridPoint>,
        now: Instant = Instant.now(),
    ) {
        Gdx.app.postRunnable { session.read { renderer.growth.reveal(trees, rocks, it.season, now) } }
    }

    override fun create() {
        batch = CountingSpriteBatch()
        textures = TextureLibrary()
        val tractor = TractorLayer(textures)
        effects = effectLayers(textures, foreground = listOf(tractor), precipitation = false, monitor = monitor)
        renderer =
            FarmRenderer(
                textures,
                listOf(tractor) + animalSources(textures, session.animalGestures) + effects.sources,
                groundEffects = { batch, state, now -> effects.draw(EffectPhase.GROUND, batch, state, now, camera) },
                monitor = monitor,
            )
        Gdx.input.inputProcessor = GestureDetector(20f, 0.4f, 0.35f, 0.15f, Gestures())
        session.sceneFeedback.catchButterfly = ::catchButterfly
        farmPhoto = FarmPhoto(renderer, batch, textures, effects.photo, foreground = tractor::drawLoad)
        session.photos.handler = { request, done ->
            Gdx.app.postRunnable {
                val png =
                    if (request.state != null) {
                        farmPhoto.render(request.state, request.pixels, request.at ?: Instant.now())
                    } else {
                        session.read { farmPhoto.render(it, request.pixels, Instant.now()) }
                    }
                done(png)
            }
        }
        placeCamera()
        session.view.framesDrawn = 0
        session.view.isSceneBuilt = true
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
        monitor?.frame()
        measure(PerformanceWork.scene) {
            Gdx.gl.glClearColor(0.05f, 0.08f, 0.12f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            camera.update()
            publishView()
            batch.projectionMatrix = camera.combined
            batch.startCount()
            batch.begin()
            val now = Instant.now()
            measure(PerformanceWork.tractor) { driveTractor(now) }
            session.act {
                measure(PerformanceWork.animals) {
                    session.animalGestures.advance(it, now)
                }
                if (session.isActive && !session.isSaveBlocked) session.wildGrowth.advance(it, now)
            }
            session.sceneFeedback.drainFlashes().forEach { tilePreview.flash(it.tiles, it.allowed, it.at) }
            session.sceneFeedback.drainInteractions().forEach { renderer.interactions.interact(it) }
            session.read { state ->
                session.wildGrowth.drain().forEach { renderer.growth.reveal(it.trees, it.rocks, state.season, it.at) }
            }
            val screenScale = camera.zoom * Gdx.graphics.density
            renderer.cameraScale = screenScale
            renderer.maxCameraScale = maxZoom() * Gdx.graphics.density
            session.read {
                effects.draw(EffectPhase.BACKGROUND, batch, it, now, camera)
                renderer.draw(batch, it, now)
                effects.draw(EffectPhase.BEFORE_TINT, batch, it, now, camera)
            }
            measure(PerformanceWork.night) { drawNightTint(now) }
            session.read { effects.draw(EffectPhase.AFTER_TINT, batch, it, now, camera) }
            celebrateBuildings(now)
            measure(PerformanceWork.preview) {
                tilePreview.draw(batch, textures, session.preview(now), now, screenScale)
            }
            batch.end()
            session.view.drawnSprites = batch.sprites
            session.view.framesDrawn++
        }
    }

    /** Sets off the fireworks the game side asked for since the last frame. */
    private fun celebrateBuildings(now: Instant) {
        val darkness = session.act { DayNightCycle.darkness(it.minuteOfDay(now)) }.coerceAtLeast(0.0)
        session.celebrations.drain { effects.fireworks.celebrateAround(it.origin, it.width, it.height, darkness) }
    }

    /** With the Bucket in hand, a tap on a flying butterfly catches it instead of using the tool. */
    private fun catchButterfly(
        tile: GridPoint,
        now: Instant,
    ): List<com.hunter.stockfarmer.core.rules.GameEvent> {
        if (!session.read { it.mode == PlayMode.harvest && it.harvestTool == HarvestTool.bucket }) return emptyList()
        val target = effects.butterflies.catchable(tile) ?: return emptyList()
        val events = session.act { it.catchButterfly(target.wings, target.releasedID, tile, now) }
        if (!events.containsRejection) effects.butterflies.remove(target)
        return events
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
        session.publish(
            session.act { engine ->
                session.tractor.advance(
                    engine,
                    Gdx.graphics.deltaTime.toDouble(),
                    session.isActive,
                    session.isSaveBlocked,
                    now,
                )
            },
        )
    }

    /** Darkens the whole view toward night by how dark the farm's clock says it is. */
    private fun drawNightTint(now: Instant) {
        val darkness = session.act { DayNightCycle.darkness(it.minuteOfDay(now)) }
        batch.drawNightTint(textures, camera, darkness)
    }

    override fun dispose() {
        session.photos.handler = null
        session.view.isSceneBuilt = false
        session.view.framesDrawn = 0
        session.sceneFeedback.catchButterfly = null
        batch.dispose()
        textures.dispose()
    }

    private inner class Gestures : GestureDetector.GestureAdapter() {
        override fun touchDown(
            x: Float,
            y: Float,
            pointer: Int,
            button: Int,
        ): Boolean {
            session.farmTouches.update { it + 1 }
            if (pointer == 0) placementDrag.begin(tileAt(x, y))
            return false
        }

        override fun tap(
            x: Float,
            y: Float,
            count: Int,
            button: Int,
        ): Boolean =
            measure(PerformanceWork.tap) {
                val tile = tileAt(x, y)
                if (!session.touchAnimalAt(touch.x, touch.y)) tile?.let { session.touch(it) }
                true
            }

        override fun longPress(
            x: Float,
            y: Float,
        ): Boolean =
            measure(PerformanceWork.tap) {
                val tile = tileAt(x, y)
                if (!session.touchAnimalAt(touch.x, touch.y)) tile?.let { session.hold(it) }
                true
            }

        private fun tileAt(
            x: Float,
            y: Float,
        ): GridPoint? {
            camera.unproject(touch.set(x, y, 0f))
            val col = floor(touch.x / FarmRenderer.TILE).toInt()
            val row = GameState.GRID_SIZE - 1 - floor(touch.y / FarmRenderer.TILE).toInt()
            return GridPoint(
                row,
                col,
            ).takeIf { col in 0 until GameState.GRID_SIZE && row in 0 until GameState.GRID_SIZE }
        }

        override fun pan(
            x: Float,
            y: Float,
            deltaX: Float,
            deltaY: Float,
        ): Boolean {
            val moved = measure(PerformanceWork.drag) { placementDrag.move(tileAt(x, y)) }
            if (moved) return true
            camera.position.add(-deltaX * camera.zoom, deltaY * camera.zoom, 0f)
            keepOnMap()
            return true
        }

        override fun panStop(
            x: Float,
            y: Float,
            pointer: Int,
            button: Int,
        ): Boolean {
            placementDrag.end()
            return true
        }

        override fun pinchStop() {
            placementDrag.end()
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
    }
}
