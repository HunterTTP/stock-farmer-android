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
import com.hunter.stockfarmer.diagnostics.PerformanceMonitor
import com.hunter.stockfarmer.diagnostics.PerformanceWork
import com.hunter.stockfarmer.diagnostics.measureOrRun
import com.hunter.stockfarmer.farm.animals.AnimalArt
import com.hunter.stockfarmer.farm.animals.animalSources
import com.hunter.stockfarmer.farm.effects.EffectLayers
import com.hunter.stockfarmer.farm.effects.EffectPhase
import com.hunter.stockfarmer.farm.effects.FireworkArea
import com.hunter.stockfarmer.farm.effects.effectLayers
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.PlacementDrag
import com.hunter.stockfarmer.game.ViewSnapshot
import com.hunter.stockfarmer.game.WATERING_INTERVAL_SECONDS
import com.hunter.stockfarmer.game.hold
import com.hunter.stockfarmer.game.preview
import com.hunter.stockfarmer.game.touch
import com.hunter.stockfarmer.game.touchAnimalAt
import com.hunter.stockfarmer.game.waterWithSprinklers
import com.hunter.stockfarmer.ui.screens.PlaySettings
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
    private val tilePreview = TilePreview()
    private val input = FarmInput(session, camera, cameraControls, PlacementDrag(session), monitor, ::saveCamera)
    private var wateringDue = 0f

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
        val animalArt = AnimalArt(textures)
        renderer =
            FarmRenderer(
                textures,
                listOf(tractor) + animalSources(textures, session.animalGestures, animalArt) + effects.sources,
                groundEffects = { batch, state, now -> effects.draw(EffectPhase.GROUND, batch, state, now, camera) },
                weathercocks = weathercocks,
                treeLean = treeLean,
                monitor = monitor,
            )
        Gdx.input.inputProcessor = input.processor
        session.sceneFeedback.catchButterfly = ::catchButterfly
        farmPhoto = FarmPhoto(renderer, batch, textures, effects.photo, animalArt)
        // This view's own queue: a request must not land on the next view's GL thread with this view's textures.
        val app = Gdx.app
        session.photos.handler = { request, pending ->
            app.postRunnable {
                if (pending.begin()) {
                    applyGraphicsSettings()
                    // A snapshot, so the engine lock is held for the copy and not for the whole map's draw.
                    val state = request.state ?: session.read { it.copy() }
                    farmPhoto.render(state, request.pixels, request.at ?: Instant.now(), pending)
                }
            }
        }
        val graphics = Gdx.graphics
        session.farmCover.attach { covered -> graphics.isContinuousRendering = !covered }
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
            input.update(TimeUtils.millis())
            monitor.measureOrRun(PerformanceWork.tractor) { driveTractor(now) }
            session.act {
                monitor.measureOrRun(PerformanceWork.animals) {
                    session.animalGestures.advance(it, now)
                }
                if (session.isActive && !session.isSaveBlocked) session.wildGrowth.advance(it, now)
            }
            monitor.measureOrRun(PerformanceWork.watering) { waterSoil(now) }
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
            effects.sprinklers.view = renderer.view
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

    /** Moves the land the sprinklers water on, a few times a second. */
    private fun waterSoil(now: Instant) {
        wateringDue += Gdx.graphics.deltaTime
        if (wateringDue < WATERING_INTERVAL_SECONDS) return
        wateringDue = 0f
        if (session.isActive && !session.isSaveBlocked) session.waterWithSprinklers(now)
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
        session.farmCover.attach(null)
        session.view.isSceneBuilt = false
        session.view.framesDrawn = 0
        session.sceneFeedback.catchButterfly = null
        batch.dispose()
        textures.dispose()
    }

    override fun pause() {
        input.cancel()
        saveCamera()
    }
}
