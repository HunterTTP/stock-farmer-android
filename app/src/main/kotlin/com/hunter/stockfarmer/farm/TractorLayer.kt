package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.presentation.TilePoint
import com.hunter.stockfarmer.core.presentation.TractorMotion
import java.time.Instant
import kotlin.math.ceil

/** The tractor: parked at its home as the building it is, or driving between the crops it is gathering. */
class TractorLayer(
    private val textures: TextureLibrary,
) : StandingSource {
    private val regions = HashMap<Pair<Int, Int>, TextureRegion>()

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        val run = state.tractorRun
        for (placed in state.placedBuildings.filter { it.building.id == TRACTOR_ID && it.origin != run?.home }) {
            val facing = TractorFacing.ofStyle(placed.building.styleID)
            val center = TractorMotion.center(TractorRules.headTile(placed.origin, facing), facing)
            add(batch, center, facing, 0, out)
        }
        if (run != null) {
            val frame = TractorMotion.frame(run)
            val wheelFrame = (now.toEpochMilli() / WHEEL_MILLIS % FRAME_COUNT).toInt()
            add(batch, frame.position, frame.facing, wheelFrame, out)
        }
    }

    private fun add(
        batch: SpriteBatch,
        center: TilePoint,
        facing: TractorFacing,
        column: Int,
        out: MutableList<Standing>,
    ) {
        val region = region(facing, column) ?: return
        val sideways = facing == TractorFacing.left || facing == TractorFacing.right
        val scale = 2 * FarmRenderer.TILE / BODY_LENGTH * if (sideways) 0.9f else 0.8f
        val size = FRAME_SIZE * scale
        val bodyX = BODY_CENTER_X.getValue(facing)
        val bodyY = BODY_CENTER_Y.getValue(facing)
        val anchorX = bodyX / FRAME_SIZE
        // Sideways artwork reaches the frame's bottom, so it sits half a tile below the footprint center.
        val anchorY = if (sideways) FarmRenderer.TILE / 2 / size else 1 - bodyY / FRAME_SIZE
        val x = center.x.toFloat() * FarmRenderer.TILE - anchorX * size
        val y = (GameState.GRID_SIZE - center.y.toFloat()) * FarmRenderer.TILE - anchorY * size
        val bottomOffset = if (sideways) 0.5 else 0.8
        out += Standing(ceil(center.y + bottomOffset).toFloat() + TRACTOR_OVER) { batch.draw(region, x, y, size, size) }
    }

    private fun region(
        facing: TractorFacing,
        column: Int,
    ): TextureRegion? {
        val sheet = textures.texture("vehicles", "Tractor_48x48") ?: return null
        return regions.getOrPut(facing.ordinal to column) {
            val frameWidth = sheet.width / FRAME_COUNT
            val frameHeight = sheet.height / ROWS
            TextureRegion(sheet, column * frameWidth, (facing.ordinal + 1) * frameHeight, frameWidth, frameHeight)
        }
    }

    private companion object {
        const val TRACTOR_ID = "tractor1"
        const val FRAME_COUNT = 6
        const val ROWS = 5
        const val FRAME_SIZE = 96f
        const val BODY_LENGTH = 82f
        const val WHEEL_MILLIS = 90L
        const val TRACTOR_OVER = 0.0007f
        val BODY_CENTER_X =
            mapOf(
                TractorFacing.right to 49f,
                TractorFacing.up to 49f,
                TractorFacing.left to 50f,
                TractorFacing.down to 48.5f,
            )
        val BODY_CENTER_Y =
            mapOf(
                TractorFacing.right to 67.5f,
                TractorFacing.up to 46f,
                TractorFacing.left to 67.5f,
                TractorFacing.down to 50f,
            )
    }
}
