package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.Wind
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class RainLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.AFTER_TINT
    override val work = PerformanceWork.precipitation
    private val motion = RainMotion()
    private val clock = EffectClock()
    private val presentation = WeatherPresentation()

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        val seconds = clock.advance(now)
        val density =
            Gdx.graphics.density
                .coerceAtLeast(0.1f)
                .toDouble()
        val look =
            presentation.update(
                now,
                rain(state, now),
                state.season == Season.winter,
                Wind.pushesRight(now, state.clockOffset),
            )
        val snow = look.snow
        val drops =
            motion.update(
                seconds,
                look.amount,
                snow,
                look.pushesRight,
                camera.viewportWidth / density,
                camera.viewportHeight / density,
            )
        if (drops.isEmpty()) return
        val texture =
            if (snow) {
                textures.generated("effects/snow", 8, 8, smooth = true) { x, y ->
                    rgba(255, 255, 255, (snowAlpha(hypot(x + 0.5 - 4, y + 0.5 - 4) / 4) * 255).roundToInt())
                }
            } else {
                textures.generated("effects/rain", 3, 28) { _, y ->
                    rgba(
                        255,
                        255,
                        255,
                        (
                            y *
                                255.0 /
                                27
                        ).roundToInt(),
                    )
                }
            }
        val view = camera.view()
        val zoom = camera.zoom * density
        batch.preserve {
            for (drop in drops) {
                batch.setColor(if (snow) 1f else 0.3f, if (snow) 1f else 0.6f, 1f, drop.alpha.toFloat())
                val width = (if (snow) 8 else 3) * drop.size * zoom
                val height = (if (snow) 8 else 28) * drop.size * zoom
                batch.draw(
                    TextureRegion(texture),
                    (view.left + drop.point.x * zoom - width / 2).toFloat(),
                    (
                        view.bottom +
                            drop.point.y * zoom -
                            height / 2
                    ).toFloat(),
                    (width / 2).toFloat(),
                    (height / 2).toFloat(),
                    width.toFloat(),
                    height.toFloat(),
                    1f,
                    1f,
                    drop.rotation.toFloat(),
                )
            }
        }
    }
}

class RainTint(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.AFTER_TINT
    override val work = PerformanceWork.weather

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        val amount = Weather.eased(rain(state, now))
        if (amount <= 0) return
        val snow = state.season == Season.winter
        val deepest = if (snow) doubleArrayOf(0.9, 0.93, 1.0) else doubleArrayOf(0.6, 0.67, 0.8)
        val view = camera.view()
        batch.preserve {
            batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO)
            batch.setColor(
                (1 + (deepest[0] - 1) * amount).toFloat(),
                (1 + (deepest[1] - 1) * amount).toFloat(),
                (
                    1 +
                        (deepest[2] - 1) * amount
                ).toFloat(),
                1f,
            )
            batch.draw(
                textures.white,
                view.left.toFloat(),
                view.bottom.toFloat(),
                view.width.toFloat(),
                view.height.toFloat(),
            )
        }
    }
}

class SeasonSky(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.BACKGROUND
    override val work = PerformanceWork.weather

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        val view = camera.view()
        val color = state.season.skyRGB()
        batch.preserve {
            batch.setColor(color.red, color.green, color.blue, 1f)
            batch.draw(
                textures.white,
                view.left.toFloat(),
                view.bottom.toFloat(),
                view.width.toFloat(),
                view.height.toFloat(),
            )
            val space = textures.texture("backgrounds", "space", "jpg", smooth = true) ?: return@preserve
            val scale = max(view.width / space.width, view.height / space.height)
            val width = space.width * scale
            val height = space.height * scale
            batch.setColor(1f, 1f, 1f, darkness(state, now).toFloat())
            batch.draw(
                space,
                (camera.position.x - width / 2).toFloat(),
                (camera.position.y - height / 2).toFloat(),
                width.toFloat(),
                height.toFloat(),
            )
        }
    }
}
