package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.OrthographicCamera
import com.hunter.stockfarmer.game.WORLD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Camera placement, zoom limits, pan clamping and pinch handling. */
internal class FarmCameraTest {
    private val camera = OrthographicCamera()
    private val controls = FarmCamera(camera)

    @Test
    fun `first layout centers the world and shows five tiles on the short side`() {
        controls.resize(1600, 1000, 2f)
        assertEquals(WORLD / 2, camera.position.x)
        assertEquals(WORLD / 2, camera.position.y)
        assertEquals(500f, camera.viewportHeight * camera.zoom)
        assertEquals(0.5f, camera.zoom)
    }

    @Test
    fun `zoom limits are one third through eight logical points per world unit at every density`() {
        for (density in listOf(1f, 2f, 3f)) {
            controls.resize(1600, 1000, density)
            controls.restore(1000f, 2000f, 0.01f)
            assertEquals(1f / 3, camera.zoom * density, 0.00001f)
            assertEquals(0f, controls.zoomFraction, 0.00001f)
            controls.restore(1000f, 2000f, 100f)
            assertEquals(8f, camera.zoom * density, 0.00001f)
            assertEquals(1f, controls.zoomFraction, 0.00001f)
        }
    }

    @Test
    fun `panning clamps the camera center to world boundaries and permits sky at the edges`() {
        controls.resize(1600, 1000, 1f)
        controls.pan(100_000f, -100_000f)
        assertEquals(0f, camera.position.x)
        assertEquals(0f, camera.position.y)
        controls.pan(-100_000f, 100_000f)
        assertEquals(WORLD, camera.position.x)
        assertEquals(WORLD, camera.position.y)
    }

    @Test
    fun `pan uses the current world distance per physical pixel`() {
        controls.resize(1600, 1000, 2f)
        controls.restore(1000f, 2000f, 2f)
        controls.pan(20f, 30f)
        assertEquals(980f, camera.position.x)
        assertEquals(2030f, camera.position.y)
    }

    @Test
    fun `pinching keeps the world under the fingers stationary including at a zoom limit`() {
        controls.resize(1600, 1000, 2f)
        controls.restore(1000f, 2000f, 2f)
        val focusX = 1100f
        val focusY = 250f
        val before = worldAt(focusX, focusY)
        controls.pinch(1f, 2f, focusX, focusY)
        assertEquals(before, worldAt(focusX, focusY))
        controls.pinch(1f, 100f, focusX, focusY)
        assertEquals(before.first, worldAt(focusX, focusY).first, 0.0001f)
        assertEquals(before.second, worldAt(focusX, focusY).second, 0.0001f)
    }

    @Test
    fun `pinch clamps position after preserving focus and ignores zero distance`() {
        controls.resize(1600, 1000, 1f)
        controls.restore(0f, 0f, 1f)
        controls.pinch(1f, 0.1f, 1600f, 0f)
        assertEquals(8f, camera.zoom)
        assertEquals(0f, camera.position.x)
        assertEquals(0f, camera.position.y)
        controls.pinch(8f, 0f, 0f, 0f)
        assertEquals(8f, camera.zoom)
    }

    @Test
    fun `resize preserves the chosen camera instead of recentering and logical zoom survives density changes`() {
        controls.resize(1600, 1000, 2f)
        controls.restore(1234f, 2345f, 3f)
        controls.resize(1000, 1600, 3f)
        assertEquals(1234f, camera.position.x)
        assertEquals(2345f, camera.position.y)
        assertEquals(3f, controls.logicalScale)
        assertEquals(1f, camera.zoom)
    }

    @Test
    fun `grid fades at sixty percent of fixed maximum regardless of viewport size`() {
        controls.resize(1600, 1000, 2f)
        controls.restore(1000f, 2000f, 4.8f)
        assertEquals(1f, VisualTiming.gridAlpha(controls.logicalScale, FarmCamera.MAX_SCALE), 0.00001f)
        controls.restore(1000f, 2000f, 6.4f)
        assertEquals(0.5f, VisualTiming.gridAlpha(controls.logicalScale, FarmCamera.MAX_SCALE), 0.00001f)
        controls.restore(1000f, 2000f, 8f)
        assertEquals(0f, VisualTiming.gridAlpha(controls.logicalScale, FarmCamera.MAX_SCALE))
    }

    private fun worldAt(
        x: Float,
        y: Float,
    ): Pair<Float, Float> =
        camera.position.x + (x - camera.viewportWidth / 2) * camera.zoom to
            camera.position.y - (y - camera.viewportHeight / 2) * camera.zoom
}
