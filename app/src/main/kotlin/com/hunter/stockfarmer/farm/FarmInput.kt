package com.hunter.stockfarmer.farm

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.PlacementDrag
import com.hunter.stockfarmer.game.gridTileAt
import com.hunter.stockfarmer.game.hold
import com.hunter.stockfarmer.game.touch
import com.hunter.stockfarmer.game.touchAnimalAt
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import com.hunter.stockfarmer.ui.screens.measureOrRun
import kotlinx.coroutines.flow.update
import kotlin.math.hypot

/**
 * What the player's fingers do on the farm: libGDX touches go to a recognizer that alone decides between pan and
 * pinch; a placement under the finger is dragged, otherwise the camera moves, and taps use the tool.
 */
internal class FarmInput(
    private val session: GameSession,
    private val camera: OrthographicCamera,
    private val cameraControls: FarmCamera,
    private val placementDrag: PlacementDrag,
    private val monitor: PerformanceMonitor?,
    private val onCameraMoved: () -> Unit,
) {
    private val world = Vector3()
    private val recognizer = FarmGestureRecognizer(Gestures())

    val processor: InputAdapter = FarmTouchProcessor()

    /** Lets a held finger turn into a long press once enough time has passed. */
    fun update(nowMillis: Long) = recognizer.update(nowMillis)

    fun cancel() = recognizer.cancel()

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

    /** The screen point in world units; the returned vector is reused by the next call. */
    private fun worldAt(
        x: Float,
        y: Float,
    ): Vector3 = camera.unproject(world.set(x, y, 0f))

    private fun tileAt(at: Vector3): GridPoint? = gridTileAt(at.x, at.y, FarmRenderer.TILE, GameState.GRID_SIZE)

    private inner class Gestures : FarmGestureListener {
        override fun touchDown(
            x: Float,
            y: Float,
            isFirst: Boolean,
        ) {
            session.farmTouches.update { it + 1 }
            if (isFirst) placementDrag.begin(tileAt(worldAt(x, y)))
        }

        override fun tap(
            x: Float,
            y: Float,
        ) {
            monitor.measureOrRun(PerformanceWork.tap) {
                val at = worldAt(x, y)
                if (!session.touchAnimalAt(at.x, at.y)) tileAt(at)?.let { session.touch(it) }
            }
        }

        override fun longPress(
            x: Float,
            y: Float,
        ) {
            monitor.measureOrRun(PerformanceWork.tap) {
                val at = worldAt(x, y)
                if (!session.touchAnimalAt(at.x, at.y)) tileAt(at)?.let { session.hold(it) }
            }
        }

        override fun pan(
            x: Float,
            y: Float,
            deltaX: Float,
            deltaY: Float,
        ) {
            val moved = monitor.measureOrRun(PerformanceWork.drag) { placementDrag.move(tileAt(worldAt(x, y))) }
            if (!moved) cameraControls.pan(deltaX, deltaY)
        }

        override fun panStop() {
            placementDrag.end()
            onCameraMoved()
        }

        override fun pinchStop() {
            placementDrag.end()
            zoomAtStart = null
            onCameraMoved()
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
}
