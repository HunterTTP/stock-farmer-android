package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant

class FireworkLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.AFTER_TINT
    override val work = PerformanceWork.night
    private val motion = FireworkMotion()
    private val clock = EffectClock()

    fun reset() = motion.reset()

    fun celebrateAround(
        origin: GridPoint,
        width: Int,
        height: Int,
        darkness: Double,
    ) {
        val area =
            FireworkArea(
                (origin.col - 1.5) * TILE,
                WORLD - (origin.row + height + 1.5) * TILE,
                (width + 3) * TILE,
                (height + 3) * TILE,
            )
        motion.launch(area, 4, darkness)
    }

    fun celebrateAcross(
        ground: FireworkArea,
        darkness: Double,
    ) = motion.launch(ground, 9, darkness)

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        val pixels = motion.advance(clock.advance(now))
        batch.preserve {
            for (pixel in pixels) {
                val color = pixel.color
                batch.setColor(color.red / 255f, color.green / 255f, color.blue / 255f, pixel.alpha.toFloat())
                if (pixel.size > 0) batch.pixel(textures, pixel.point, pixel.size)
            }
            batch.additive()
            for (pixel in pixels) {
                if (pixel.glowSize <= 0) continue
                val color = pixel.color
                batch.setColor(color.red / 255f, color.green / 255f, color.blue / 255f, pixel.alpha.toFloat())
                batch.glow(textures, pixel.point, pixel.glowSize)
            }
        }
    }
}
