package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import java.time.Instant
import java.util.UUID

class FireflyLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.GROUND
    private val clock = EffectClock()
    private val population = FireflyPopulation()
    var onSighting: (() -> Unit)?
        get() = population.onSighting
        set(value) {
            population.onSighting = value
        }

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        population.update(state, clock.advance(now), darkness(state, now))
        drawFlies(batch, state, now, halos = false)
    }

    val halos: EffectOverlay =
        object : EffectOverlay {
            override val phase = EffectPhase.AFTER_TINT

            override fun draw(
                batch: SpriteBatch,
                state: GameState,
                now: Instant,
                camera: OrthographicCamera,
            ) {
                drawFlies(batch, state, now, halos = true)
            }
        }

    private fun drawFlies(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        halos: Boolean,
    ) {
        val visibility = population.visibility(darkness(state, now))
        batch.preserve {
            batch.additive()
            for (fly in population.flies) {
                val alpha = visibility * if (halos) fly.haloAlpha else fly.pixelAlpha
                batch.setColor(1f, 0.58f, 0.2f, alpha.toFloat())
                if (halos) batch.glow(textures, fly.point, 64.0) else batch.pixel(textures, fly.point, 6.0)
            }
        }
    }
}

class ButterflyLayer(
    private val textures: TextureLibrary,
) : StandingSource {
    class Catchable internal constructor(
        val wings: Butterflies.Wings,
        val releasedID: UUID?,
        internal val motion: ButterflyMotion,
    )

    private val clock = EffectClock()
    private val population = ButterflyPopulation()
    private val color = Color()

    fun catchable(tile: GridPoint): Catchable? =
        population.flies.firstOrNull { it.alpha > 0.4 && it.tile == tile }?.let {
            Catchable(
                it.wings,
                it.releasedID,
                it,
            )
        }

    fun remove(catchable: Catchable) {
        population.flies.remove(catchable.motion)
    }

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        population.update(state, clock.advance(now), darkness(state, now), rain(state, now))
        for (fly in population.flies) {
            out +=
                Standing(Float.MAX_VALUE) {
                    batch.preserve {
                        when (val wings = fly.wings) {
                            is Butterflies.Wings.Color -> {
                                val rgb = wings.rarity.color
                                val alpha = fly.alpha.toFloat()
                                color.set(rgb.red.toFloat(), rgb.green.toFloat(), rgb.blue.toFloat(), alpha)
                            }

                            Butterflies.Wings.Shifting -> {
                                color.fromHsv(((fly.hue + fly.age / 20) % 1 * 360).toFloat(), 0.95f, 1f)
                                color.a = fly.alpha.toFloat()
                            }
                        }
                        val rows = ButterflyArt.frames[fly.frame]
                        val texture =
                            textures.generated("effects/butterfly/${fly.frame}", 7, 5) { x, y ->
                                when (rows[y][x]) {
                                    'W' -> rgba(255, 255, 255)
                                    'B' -> rgba(10, 10, 13)
                                    else -> 0
                                }
                            }
                        batch.color = color
                        val x = (fly.point.x - 10.5).toFloat()
                        val y = (fly.point.y - 7.5).toFloat()
                        batch.draw(TextureRegion(texture), x, y, 10.5f, 7.5f, 21f, 15f, 1f, 1f, fly.facing.toFloat())
                    }
                }
        }
    }
}

internal object ButterflyArt {
    val frames =
        listOf(
            listOf("WW...WW", "WWW.WWW", "WWWBWWW", ".WWBWW.", "..WBW.."),
            listOf("..W.W..", ".WW.WW.", ".WWBWW.", "..WBW..", "...B..."),
            listOf(".......", "..W.W..", "..WBW..", "...B...", "...B..."),
        )
}
