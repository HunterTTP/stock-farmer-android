package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.OrthographicCamera
import com.hunter.stockfarmer.farm.effects.FireworkArea
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.ViewSnapshot
import com.hunter.stockfarmer.game.WORLD
import kotlin.math.min

/** The camera over the farm: scales are in logical points, while libGDX's camera covers physical pixels. */
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
            camera.position.set(WORLD / 2, WORLD / 2, 0f)
            camera.zoom = 5 * TILE / min(width, height)
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
        camera.position.x = camera.position.x.coerceIn(0f, WORLD)
        camera.position.y = camera.position.y.coerceIn(0f, WORLD)
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
