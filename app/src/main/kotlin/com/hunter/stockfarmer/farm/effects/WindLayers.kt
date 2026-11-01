package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.Wind
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.floor

class WindLayer(
    private val textures: TextureLibrary,
) : EffectOverlay,
    StandingSource {
    override val phase = EffectPhase.GROUND
    override val work = PerformanceWork.weather
    internal val motion = WindMotion()
    private val clock = EffectClock()

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        motion.update(
            clock.advance(now),
            state.season,
            rain(state, now) > 0,
            Wind.pushesRight(now, state.clockOffset),
            camera.view(),
            View(0.0, 0.0, camera.viewportWidth.toDouble(), camera.viewportHeight.toDouble()),
        )
    }

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        for (wisp in motion.wisps) {
            val age = motion.time - wisp.born
            if (age < 0) continue
            val row = floor((WORLD - wisp.start.y) / TILE).toFloat()
            out +=
                Standing(row + 1 + 0.9f) {
                    batch.preserve {
                        for (index in wisp.points.indices) {
                            batch.setColor(1f, 1f, 1f, WindWisp.alpha(age, index, wisp.strength).toFloat())
                            batch.pixel(textures, wisp.point(index, age), 4.0)
                        }
                    }
                }
        }
    }
}

class LeafLayer internal constructor(
    private val textures: TextureLibrary,
    private val motion: WindMotion,
) : EffectOverlay {
    override val phase = EffectPhase.BEFORE_TINT
    override val work = PerformanceWork.weather

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        batch.preserve {
            batch.setColor(1f, 1f, 1f, 1f)
            for (leaf in motion.leaves) {
                val age = motion.time - leaf.born
                if (age < 0) continue
                val texture = textures.texture("seasons/fall/leaves", "leaf_${leaf.color}") ?: continue
                val count = texture.width / 48
                if (count == 0) continue
                val frame = (age / leaf.frameTime).toInt() % count
                val region = TextureRegion(texture, frame * 48, 0, 48, minOf(48, texture.height))
                val point = leaf.point(age, camera.viewportWidth.toDouble())
                val side = leaf.side * camera.zoom
                batch.draw(
                    region,
                    (camera.position.x + point.x * camera.zoom - side / 2).toFloat(),
                    (
                        camera.position.y +
                            point.y * camera.zoom -
                            side / 2
                    ).toFloat(),
                    side.toFloat(),
                    side.toFloat(),
                )
            }
        }
    }
}
