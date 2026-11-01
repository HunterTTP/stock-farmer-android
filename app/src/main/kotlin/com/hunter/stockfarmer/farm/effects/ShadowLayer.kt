package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.Sun
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.roundToInt

class ShadowLayer(
    private val textures: TextureLibrary,
) : EffectOverlay {
    override val phase = EffectPhase.GROUND
    override val work = PerformanceWork.shadows
    var enabled = true
    var intensity = 0.5
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    private data class Made(
        val key: String,
        val image: ShadowImage,
        val texture: Texture,
    )

    private val casters = ShadowCasters(textures)
    private val masks = mutableMapOf<String, AlphaMask>()
    private val made = mutableMapOf<ShadowShape, Made>()
    private val waiting = linkedSetOf<ShadowShape>()
    private var step: Int? = null
    private var softness: Int? = null
    private var serial = 0L

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) = draw(batch, state, now, camera, finishAll = false)

    /** Draws with every shadow already cast, for a still picture. */
    fun drawFinished(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) = draw(batch, state, now, camera, finishAll = true)

    private fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
        finishAll: Boolean,
    ) {
        val minute = DayNightCycle.minuteOfDay(now, state.clockOffset)
        val sun = Sun.shadow(minute)
        if (!enabled || intensity == 0.0 || sun.strength <= 0) return
        val wanted = casters.collect(state)
        val shapes = wanted.map { it.shape }.toSet()
        val sunStep = Sun.step(minute)
        val softnessStep = (intensity * 4).roundToInt()
        if (step != sunStep || softness != softnessStep) {
            step = sunStep
            softness = softnessStep
            waiting.clear()
            waiting += shapes
        }
        for (old in made.keys.toList()) {
            if (old !in shapes) textures.releaseGenerated(made.remove(old)!!.key)
        }
        waiting.retainAll(shapes)
        waiting += shapes.filter { it !in made }
        while (true) {
            val next = waiting.firstOrNull() ?: break
            val caster = wanted.first { it.shape == next }
            val mask = caster.texture?.let { texture -> masks.getOrPut(next.image!!) { alphaMask(texture) } }
            val image = ShadowRaster.render(next, Sun.shadow(sunStep * Sun.stepMinutes), intensity, mask)
            if (image != null) {
                val key = "effects/shadow/${hashCode()}/${serial++}"
                val texture =
                    textures.generated(key, image.width, image.height, smooth = true) { x, y ->
                        rgba(
                            0,
                            0,
                            0,
                            (
                                image.alpha[
                                    y *
                                        image.width +
                                        x,
                                ] *
                                    255
                            ).roundToInt(),
                        )
                    }
                made.remove(next)?.let { textures.releaseGenerated(it.key) }
                made[next] = Made(key, image, texture)
            }
            waiting.remove(next)
            if (!finishAll) break
        }
        val view = camera.view()
        batch.preserve {
            for (caster in wanted) {
                val entry = made[caster.shape] ?: continue
                val image = entry.image
                val x = caster.foot.x + image.left
                val y = caster.foot.y + image.bottom
                val width = image.width / ShadowRaster.RESOLUTION
                val height = image.height / ShadowRaster.RESOLUTION
                if (x + width < view.left || x > view.right || y + height < view.bottom || y > view.top) continue
                batch.setColor(1f, 1f, 1f, (caster.shape.kind.opacity * sun.strength * intensity * 2).toFloat())
                batch.draw(entry.texture, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
            }
        }
    }

    private fun alphaMask(texture: Texture): AlphaMask {
        val data = texture.textureData
        if (!data.isPrepared) data.prepare()
        val pixmap = data.consumePixmap()
        return try {
            AlphaMask(
                pixmap.width,
                pixmap.height,
                ByteArray(pixmap.width * pixmap.height) { index ->
                    (
                        pixmap.getPixel(index % pixmap.width, index / pixmap.width) and
                            255
                    ).toByte()
                },
            )
        } finally {
            if (data.disposePixmap()) pixmap.dispose()
        }
    }
}
