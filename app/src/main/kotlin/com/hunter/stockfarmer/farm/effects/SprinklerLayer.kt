package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.farm.ViewBounds
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant

/**
 * Draws the sprinklers' sprays: a blue mist as wide as the reach hangs behind the drops, and each drop arcs from a tip of
 * the sprinkler into a tile it waters. Each is drawn just in front of its sprinkler, in the back to front pass.
 */
class SprinklerLayer(
    private val textures: TextureLibrary,
) : StandingSource {
    override val work = PerformanceWork.weather
    internal val sprays = SprinklerSprays()

    /** What the camera shows; a spray wholly outside it is not drawn, though it carries on. */
    internal var view = ViewBounds.EVERYTHING
    private var batch: SpriteBatch? = null
    private var seconds = 0.0

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        sprays.sync(state.decorations, now)
        if (sprays.sprinklers.isEmpty()) return
        sprays.tick(now, rain(state, now) > 0, state.sprinklersRunConstantly)
        this.batch = batch
        seconds = SprinklerSprays.seconds(now)
        for (index in sprays.sprinklers.indices) {
            val sprinkler = sprays.sprinklers[index]
            if (sprinkler.sprays.all { it.isOver(seconds) }) continue
            val extent = sprinkler.extent
            if (!view.sees(sprinkler.centerX - extent, sprinkler.centerY - extent, 2 * extent, 2 * extent)) continue
            out +=
                sprinkler.standing
                    ?: Standing(sprinkler.bottomEdge + IN_FRONT) { draw(sprinkler) }.also { sprinkler.standing = it }
        }
    }

    private fun draw(sprinkler: Sprinkler) {
        val batch = batch ?: return
        val mist = textures.mist()
        val old = batch.packedColor
        try {
            val side = (2 * sprinkler.reach + 1) * TILE.toFloat()
            for (spray in sprinkler.sprays) {
                if (spray.isOver(seconds)) continue
                batch.setColor(MIST_RED, MIST_GREEN, MIST_BLUE, spray.mistAlpha(seconds))
                batch.draw(mist, sprinkler.centerX - side / 2, sprinkler.centerY - side / 2, side, side)
            }
            val rgb = WaterDrops.colorsRgb
            val size = WaterDrops.SIZE.toFloat()
            for (spray in sprinkler.sprays) {
                if (spray.isOver(seconds)) continue
                val elapsed = (seconds - spray.startedAt).toFloat()
                for (drop in 0 until spray.count) {
                    val age = elapsed - spray.delay[drop]
                    if (age < 0 || age >= spray.flight[drop]) continue
                    val progress = spray.progress(drop, age)
                    val tint = spray.tint[drop] * 3
                    batch.setColor(rgb[tint], rgb[tint + 1], rgb[tint + 2], spray.alpha(drop, age))
                    batch.draw(
                        textures.white,
                        sprinkler.centerX + spray.x(drop, progress) - size / 2,
                        sprinkler.centerY + spray.y(drop, progress) - size / 2,
                        size,
                        size,
                    )
                }
            }
        } finally {
            batch.packedColor = old
        }
    }

    /** A round cloud of one color, solid in the middle and fading evenly to nothing at its edge. */
    private fun TextureLibrary.mist() =
        generated("effects/sprinklerMist", MIST_SIDE, MIST_SIDE, smooth = true) { x, y ->
            val radius = kotlin.math.hypot(x - (MIST_SIDE - 1) / 2.0, y - (MIST_SIDE - 1) / 2.0) / (MIST_SIDE / 2.0)
            rgba(255, 255, 255, ((1 - radius).coerceIn(0.0, 1.0) * 255).toInt())
        }

    private companion object {
        /** In front of the sprinkler, which stands at the bottom edge of its ground. */
        const val IN_FRONT = 0.0005f
        const val MIST_SIDE = 128
        const val MIST_RED = 0.35f
        const val MIST_GREEN = 0.65f
        const val MIST_BLUE = 0.92f
    }
}
