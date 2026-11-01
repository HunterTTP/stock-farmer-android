package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.random.Random

class BuildingGlowLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.AFTER_TINT
    override val work = PerformanceWork.night
    private val clock = EffectClock()

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        clock.advance(now)
        val dark = maxOf(darkness(state, now), rain(state, now))
        if (dark == 0.0) return
        batch.preserve {
            batch.additive()
            for (torch in NightLights.torches(state, clock.time, dark)) {
                batch.setColor(1f, 0.58f, 0.2f, torch.alpha.toFloat())
                batch.glow(textures, torch.point, torch.reach)
            }
        }
    }
}

class NightPulseLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.GROUND
    override val work = PerformanceWork.night
    private val motion = PulseMotion()
    private val patterns = List(6) { pattern -> (0 until 144).shuffled(Random(pattern)).take(7).toSet() }

    fun sync(
        night: Int?,
        revision: Long,
    ) = motion.sync(night, revision)

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        val tiles = motion.tiles(state, now, Point(camera.position.x / TILE, (WORLD - camera.position.y) / TILE))
        val color = NightLights.pulseColor(state.season)
        val view = camera.view()
        batch.preserve {
            batch.additive()
            for (tile in tiles) {
                val x = tile.tile.col * TILE
                val y = FarmRenderer.worldY(tile.tile.row).toDouble()
                if (x + TILE < view.left || x > view.right || y + TILE < view.bottom || y > view.top) continue
                batch.setColor(
                    color.red / 255f,
                    color.green / 255f,
                    color.blue / 255f,
                    (tile.intensity * 0.4).toFloat(),
                )
                batch.glow(textures, Point(x + TILE / 2, y + TILE / 2), TILE)
                val texture =
                    textures.generated("effects/pulse/${tile.pattern}", 12, 12) { col, row ->
                        if (row * 12 +
                            col in
                            patterns[tile.pattern]
                        ) {
                            -1
                        } else {
                            0
                        }
                    }
                batch.setColor(
                    color.red / 255f,
                    color.green / 255f,
                    color.blue / 255f,
                    (tile.intensity * tile.twinkle).toFloat(),
                )
                batch.draw(texture, x.toFloat(), y.toFloat(), TILE.toFloat(), TILE.toFloat())
            }
        }
    }
}
