package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant

class WaterLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.GROUND
    override val work = PerformanceWork.weather
    private val clock = EffectClock()

    private data class Frame(
        val style: WaterStyle,
        val index: Int,
        val glow: Boolean,
    )

    private val frames = mutableMapOf<Frame, IntArray>()

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        clock.advance(now)
        val view = camera.view()
        val dark = darkness(state, now)
        batch.preserve {
            for ((point, style) in state.water) {
                val x = point.col * TILE
                val y = FarmRenderer.worldY(point.row).toDouble()
                if (x + TILE < view.left || x > view.right || y + TILE < view.bottom || y > view.top) continue
                val hash = (point.row * 73856093) xor (point.col * 19349663)
                val frame = WaterAnimation.frame(clock.time, (hash.toLong().let { kotlin.math.abs(it) } % 6).toInt())
                for (glow in listOf(false, true)) {
                    if (glow && dark == 0.0) continue
                    val pixels =
                        frames.getOrPut(Frame(style, frame, glow)) {
                            WaterAnimation.pixels(style, frame, glow)
                        }
                    val texture =
                        textures.generated("effects/water/$style/$frame/$glow", 16, 16) { col, row ->
                            pixels[row * 16 + col]
                        }
                    if (glow) batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE)
                    val light = if (glow) dark.toFloat() else 1f
                    batch.setColor(light, light, light, light)
                    batch.draw(texture, x.toFloat(), y.toFloat(), TILE.toFloat(), TILE.toFloat())
                }
                batch.setBlendFunction(
                    GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA,
                )
            }
        }
    }
}
