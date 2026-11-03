package com.hunter.stockfarmer.farm

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
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
import com.hunter.stockfarmer.farm.effects.FireworkArea
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
import com.hunter.stockfarmer.ui.screens.PlaySettings
import com.hunter.stockfarmer.ui.screens.measureOrRun
import kotlinx.coroutines.flow.update
import java.time.Instant
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/** The farm scene: draws the session's game and turns taps and drags into play and camera movement. */
class FarmGame(
    private val session: GameSession,
    private val play: PlaySettings,
    private val monitor: PerformanceMonitor? = null,
) : ApplicationAdapter() {
    private lateinit var batch: CountingSpriteBatch
    private lateinit var textures: TextureLibrary
    private lateinit var effects: EffectLayers
    private lateinit var renderer: FarmRenderer
    private lateinit var farmPhoto: FarmPhoto
    private val camera = OrthographicCamera()
    private val cameraControls = FarmCamera(camera)
    private val cameraPreferences by lazy { Gdx.app.getPreferences("camera") }
    private val touch = Vector3()
    private val tilePreview = TilePreview()
    private val placementDrag = PlacementDrag(session)

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
        val weathercocks = WeathercockTurn()
        val treeLean = TreeLean()
        effects =
            effectLayers(
                textures,
                foreground = listOf(tractor),
                precipitation = false,
                onGust = { pushesRight, at ->
                    weathercocks.gust(pushesRight, at)
                    treeLean.gust(pushesRight, at)
                },
                monitor = monitor,
            )
        renderer =
            FarmRenderer(
                textures,
                listOf(tractor) + animalSources(textures, session.animalGestures) + effects.sources,
                groundEffects = { batch, state, now -> effects.draw(EffectPhase.GROUND, batch, state, now, camera) },
                weathercocks = weathercocks,
                treeLean = treeLean,
                monitor = monitor,
            )
        Gdx.input.inputProcessor = FarmTouchProcessor()
        session.sceneFeedback.catchButterfly = ::catchButterfly
        farmPhoto = FarmPhoto(renderer, batch, textures, effects.photo)
        session.photos.handler = { request, done ->
            Gdx.app.postRunnable {
                applyGraphicsSettings()
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
        placeCamera(width, height)
    }

    private fun placeCamera(
        width: Int = Gdx.graphics.width,
        height: Int = Gdx.graphics.height,
    ) {
        val firstLayout = !cameraControls.isPlaced
        cameraControls.resize(width, height, Gdx.graphics.density)
        if (firstLayout && cameraControls.isPlaced && cameraPreferences.contains("cameraScale")) {
            cameraControls.restore(
                cameraPreferences.getFloat("cameraX"),
                cameraPreferences.getFloat("cameraY"),
                cameraPreferences.getFloat("cameraScale"),
            )
        }
    }

    private fun saveCamera() {
        if (!cameraControls.isPlaced) return
        cameraPreferences.putFloat("cameraX", camera.position.x)
        cameraPreferences.putFloat("cameraY", camera.position.y)
        cameraPreferences.putFloat("cameraScale", cameraControls.logicalScale)
        cameraPreferences.flush()
    }

    override fun render() {
        monitor?.frame()
        monitor.measureOrRun(PerformanceWork.scene) {
            applyGraphicsSettings()
            Gdx.gl.glClearColor(0.05f, 0.08f, 0.12f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            camera.update()
            publishView()
            batch.projectionMatrix = camera.combined
            batch.startCount()
            batch.begin()
            val now = Instant.now()
            recognizer.update(TimeUtils.millis())
            monitor.measureOrRun(PerformanceWork.tractor) { driveTractor(now) }
            session.act {
                monitor.measureOrRun(PerformanceWork.animals) {
                    session.animalGestures.advance(it, now)
                }
                if (session.isActive && !session.isSaveBlocked) session.wildGrowth.advance(it, now)
            }
            session.sceneFeedback.drainFlashes().forEach { tilePreview.flash(it.tiles, it.allowed, it.at) }
            session.sceneFeedback.drainInteractions().forEach { renderer.interactions.interact(it) }
            session.read { state ->
                session.wildGrowth.drain().forEach { renderer.growth.reveal(it.trees, it.rocks, state.season, it.at) }
            }
            val moving = session.act { MovingOrigins.of(it) }
            renderer.moving = moving
            effects.shadows.moving = moving
            val screenScale = camera.zoom * Gdx.graphics.density
            renderer.cameraScale = screenScale
            renderer.maxCameraScale = FarmCamera.MAX_SCALE
            renderer.view = visibleBounds()
            session.read {
                effects.nightPulse.sync(session.nightPulse, session.nightPulseRevision)
                effects.draw(EffectPhase.BACKGROUND, batch, it, now, camera)
                renderer.draw(batch, it, now)
                effects.draw(EffectPhase.BEFORE_TINT, batch, it, now, camera)
            }
            monitor.measureOrRun(PerformanceWork.night) { drawNightTint(now) }
            session.read { effects.draw(EffectPhase.AFTER_TINT, batch, it, now, camera) }
            celebrate(now)
            monitor.measureOrRun(PerformanceWork.preview) {
                tilePreview.draw(batch, textures, session.preview(now), now, screenScale)
            }
            batch.end()
            session.view.drawnSprites = batch.sprites
            session.view.framesDrawn++
        }
    }

    /** Read on the GL thread so settings changes reach both the next frame and any photo requested before it. */
    private fun applyGraphicsSettings() {
        renderer.gridShown = play.isGridShown
        effects.shadows.enabled = play.isDynamicShadowsEnabled
        effects.shadows.intensity = play.shadowIntensity.toDouble()
    }

    /** Sets off the fireworks the game side asked for since the last frame. */
    private fun celebrate(now: Instant) {
        val darkness = session.act { DayNightCycle.darkness(it.minuteOfDay(now)) }.coerceAtLeast(0.0)
        session.celebrations.drain { effects.fireworks.celebrateAround(it.origin, it.width, it.height, darkness) }
        val ground = session.view.snapshot?.fireworkGround() ?: return
        session.celebrations.drainAcross { effects.fireworks.celebrateAcross(ground, darkness) }
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

    /** What the camera shows plus a tile all round, so nothing pops at the edges. */
    private fun visibleBounds() =
        ViewBounds
            .around(
                camera.position.x,
                camera.position.y,
                camera.viewportWidth * camera.zoom,
                camera.viewportHeight * camera.zoom,
            ).padded(FarmRenderer.TILE)

    /** Tells the sound side where the camera is looking. */
    private fun publishView() {
        session.view.snapshot =
            ViewSnapshot(
                centerX = camera.position.x,
                centerY = camera.position.y,
                worldPerPixel = camera.zoom,
                widthPixels = Gdx.graphics.width,
                heightPixels = Gdx.graphics.height,
                zoomFraction = cameraControls.zoomFraction,
            )
    }

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
        saveCamera()
        session.photos.handler = null
        session.view.isSceneBuilt = false
        session.view.framesDrawn = 0
        session.sceneFeedback.catchButterfly = null
        batch.dispose()
        textures.dispose()
    }

    /** Routes libGDX touches into the recognizer, which alone decides between pan and pinch. */
    private inner class FarmTouchProcessor : InputAdapter() {
        override fun touchDown(
            screenX: Int,
            screenY: Int,
            pointer: Int,
            button: Int,
        ): Boolean {
            recognizer.touchDown(pointer, screenX.toFloat(), screenY.toFloat(), TimeUtils.millis())
            return true
        }

        override fun touchDragged(
            screenX: Int,
            screenY: Int,
            pointer: Int,
        ): Boolean {
            recognizer.touchDragged(pointer, screenX.toFloat(), screenY.toFloat())
            return true
        }

        override fun touchUp(
            screenX: Int,
            screenY: Int,
            pointer: Int,
            button: Int,
        ): Boolean {
            recognizer.touchUp(pointer, screenX.toFloat(), screenY.toFloat())
            return true
        }

        override fun touchCancelled(
            screenX: Int,
            screenY: Int,
            pointer: Int,
            button: Int,
        ): Boolean {
            recognizer.cancel()
            return true
        }
    }

    private val recognizer = FarmGestureRecognizer(Gestures())

    private inner class Gestures : FarmGestureListener {
        override fun touchDown(
            x: Float,
            y: Float,
            isFirst: Boolean,
        ) {
            session.farmTouches.update { it + 1 }
            if (isFirst) placementDrag.begin(tileAt(x, y))
        }

        override fun tap(
            x: Float,
            y: Float,
        ) {
            monitor.measureOrRun(PerformanceWork.tap) {
                val tile = tileAt(x, y)
                if (!session.touchAnimalAt(touch.x, touch.y)) tile?.let { session.touch(it) }
            }
        }

        override fun longPress(
            x: Float,
            y: Float,
        ) {
            monitor.measureOrRun(PerformanceWork.tap) {
                val tile = tileAt(x, y)
                if (!session.touchAnimalAt(touch.x, touch.y)) tile?.let { session.hold(it) }
            }
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
        ) {
            val moved = monitor.measureOrRun(PerformanceWork.drag) { placementDrag.move(tileAt(x, y)) }
            if (!moved) cameraControls.pan(deltaX, deltaY)
        }

        override fun panStop() {
            placementDrag.end()
            saveCamera()
        }

        override fun pinchStop() {
            placementDrag.end()
            zoomAtStart = null
            saveCamera()
        }

        private var zoomAtStart: Float? = null

        override fun pinch(
            initial1: FingerPoint,
            initial2: FingerPoint,
            current1: FingerPoint,
            current2: FingerPoint,
        ) {
            val initialDistance = distance(initial1, initial2)
            if (initialDistance <= 0f) return
            val start = zoomAtStart ?: camera.zoom.also { zoomAtStart = it }
            cameraControls.pinch(
                start,
                distance(current1, current2) / initialDistance,
                (current1.x + current2.x) / 2,
                (current1.y + current2.y) / 2,
            )
        }

        private fun distance(
            a: FingerPoint,
            b: FingerPoint,
        ) = hypot(a.x - b.x, a.y - b.y)
    }

    override fun pause() {
        recognizer.cancel()
        saveCamera()
    }
}

/** SpriteKit scales are in logical points; libGDX's camera covers physical pixels. */
internal class FarmCamera(
    private val camera: OrthographicCamera,
) {
    private var density = 1f
    var isPlaced = false
        private set
    val logicalScale: Float get() = camera.zoom * density
    val zoomFraction: Float get() = ((logicalScale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)).coerceIn(0f, 1f)

    fun resize(
        width: Int,
        height: Int,
        density: Float,
    ) {
        if (width <= 0 || height <= 0) return
        val previousScale = logicalScale
        this.density = density.coerceAtLeast(0.1f)
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        if (!isPlaced) {
            camera.position.set(FarmRenderer.WORLD / 2, FarmRenderer.WORLD / 2, 0f)
            camera.zoom = 5 * FarmRenderer.TILE / min(width, height)
            isPlaced = true
        } else {
            camera.zoom = previousScale / this.density
        }
        applyScale(camera.zoom)
    }

    fun restore(
        x: Float,
        y: Float,
        logicalScale: Float,
    ) {
        camera.position.set(x, y, 0f)
        applyScale(logicalScale / density)
    }

    fun pan(
        deltaX: Float,
        deltaY: Float,
    ) {
        camera.position.add(-deltaX * camera.zoom, deltaY * camera.zoom, 0f)
        clampPosition()
    }

    fun pinch(
        startScale: Float,
        magnification: Float,
        focusX: Float,
        focusY: Float,
    ) {
        if (magnification <= 0f || !magnification.isFinite()) return
        val before = camera.zoom
        applyScale(startScale / magnification)
        camera.position.add(
            (focusX - camera.viewportWidth / 2) * (before - camera.zoom),
            -(focusY - camera.viewportHeight / 2) * (before - camera.zoom),
            0f,
        )
        clampPosition()
    }

    private fun applyScale(scale: Float) {
        camera.zoom = scale.coerceIn(MIN_SCALE / density, MAX_SCALE / density)
        clampPosition()
    }

    private fun clampPosition() {
        camera.position.x = camera.position.x.coerceIn(0f, FarmRenderer.WORLD)
        camera.position.y = camera.position.y.coerceIn(0f, FarmRenderer.WORLD)
    }

    companion object {
        const val MIN_SCALE = 1f / 3
        const val MAX_SCALE = 8f
    }
}

/** The lower sixty percent of the camera view leaves room for the bursts overhead. */
internal fun ViewSnapshot.fireworkGround(): FireworkArea {
    val width = widthPixels * worldPerPixel.toDouble()
    val height = heightPixels * worldPerPixel.toDouble()
    return FireworkArea(centerX - width / 2, centerY - height / 2, width, height * 0.6)
}
