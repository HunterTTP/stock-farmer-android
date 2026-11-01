package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.presentation.TilePoint
import com.hunter.stockfarmer.core.presentation.TractorMotion
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.farm.effects.EffectOverlay
import com.hunter.stockfarmer.farm.effects.EffectPhase
import com.hunter.stockfarmer.farm.effects.preserve
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.ceil

/** The tractor: parked at its home as the building it is, or driving between the crops it is gathering. */
class TractorLayer(
    private val textures: TextureLibrary,
) : StandingSource,
    EffectOverlay {
    override val phase = EffectPhase.AFTER_TINT
    override val work = PerformanceWork.tractor
    private val regions = HashMap<Pair<Int, Int>, TextureRegion>()
    private val badges = ProduceBadge(textures)
    private val movement = TractorMovement()
    private var loadKey: String? = null
    private var loadAt = Instant.EPOCH

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        val run = state.tractorRun
        movement.advance(run, now)
        for (placed in state.placedBuildings.filter { it.building.id == TRACTOR_ID && it.origin != run?.home }) {
            val facing = TractorFacing.ofStyle(placed.building.styleID)
            val center = TractorMotion.center(TractorRules.headTile(placed.origin, facing), facing)
            add(batch, center, facing, 0, 0.0, out)
        }
        if (run != null) {
            val frame = TractorMotion.frame(run)
            add(batch, frame.position, frame.facing, movement.wheelFrame(), movement.lift(run), out)
        }
    }

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        drawLoad(batch, state, now)
    }

    fun drawLoad(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        val run = state.tractorRun
        val placed = state.placedBuildings.firstOrNull { it.building.id == TRACTOR_ID }
        val home = run?.home ?: placed?.origin
        val cargo = home?.let { state.tractorCargo[it] }
        val crop =
            cargo
                ?.keys
                ?.sorted()
                ?.firstOrNull()
                ?.let(Crop::named)
        val count = cargo?.get(crop?.id)
        if (crop == null || count == null) {
            loadKey = null
            return
        }
        val key = "${crop.id}-$count"
        if (loadKey != key) {
            loadKey = key
            loadAt = now
        }
        val center =
            run?.let { TractorMotion.frame(it).position } ?: placed?.let {
                TilePoint(it.origin.col + it.building.width / 2.0, it.origin.row + it.building.height / 2.0)
            } ?: return
        val facing = run?.let { TractorMotion.frame(it).facing } ?: TractorFacing.ofStyle(placed?.building?.styleID)
        val rise = if (facing == TractorFacing.up || facing == TractorFacing.down) 1.35 else 1.15
        val x = center.x.toFloat() * FarmRenderer.TILE
        val y =
            (GameState.GRID_SIZE - center.y + rise).toFloat() * FarmRenderer.TILE +
                VisualTiming.bob(now.secondsSince(loadAt))
        batch.preserve {
            batch.setColor(1f, 1f, 1f, 1f)
            badges.load(batch, StorageItem.CropItem(crop), count, x, y)
        }
    }

    private fun add(
        batch: SpriteBatch,
        center: TilePoint,
        facing: TractorFacing,
        column: Int,
        lift: Double,
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
        val y = (GameState.GRID_SIZE - center.y.toFloat() + lift.toFloat()) * FarmRenderer.TILE - anchorY * size
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
        const val TRACTOR_OVER = 0.7f
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
