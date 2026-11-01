package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
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

    private val styles = WaterStyle.entries
    private val frameCount = WaterAnimation.FRAMES
    private val bucketCount = styles.size * frameCount
    private val textureCache = arrayOfNulls<Texture>(bucketCount * 2)

    // Visible tiles grouped by picture, so tiles sharing one are drawn together and the batch is not flushed per tile.
    private val buckets = arrayOfNulls<FloatArray>(bucketCount)
    private val bucketSizes = IntArray(bucketCount)
    private val usedBuckets = IntArray(bucketCount)
    private var usedCount = 0

    private fun texture(
        bucket: Int,
        glow: Boolean,
    ): Texture {
        val key = bucket * 2 + if (glow) 1 else 0
        textureCache[key]?.let { return it }
        val style = styles[bucket / frameCount]
        val frame = bucket % frameCount
        val pixels = WaterAnimation.pixels(style, frame, glow)
        return textures
            .generated("effects/water/$style/$frame/$glow", 16, 16) { col, row -> pixels[row * 16 + col] }
            .also { textureCache[key] = it }
    }

    private fun add(
        bucket: Int,
        x: Float,
        y: Float,
    ) {
        var xy = buckets[bucket] ?: FloatArray(16).also { buckets[bucket] = it }
        val size = bucketSizes[bucket]
        if (size == 0) usedBuckets[usedCount++] = bucket
        if (size + 2 > xy.size) {
            xy = xy.copyOf(xy.size * 2)
            buckets[bucket] = xy
        }
        xy[size] = x
        xy[size + 1] = y
        bucketSizes[bucket] = size + 2
    }

    private fun drawBuckets(
        batch: SpriteBatch,
        glow: Boolean,
        light: Float,
    ) {
        batch.setColor(light, light, light, light)
        for (used in 0 until usedCount) {
            val bucket = usedBuckets[used]
            val texture = texture(bucket, glow)
            val xy = buckets[bucket] ?: continue
            for (index in 0 until bucketSizes[bucket] step 2) {
                batch.draw(texture, xy[index], xy[index + 1], TILE.toFloat(), TILE.toFloat())
            }
        }
    }

    override fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        clock.advance(now)
        if (state.water.isEmpty()) return
        val view = camera.view()
        val dark = darkness(state, now)
        usedCount = 0
        for ((point, style) in state.water) {
            val x = point.col * TILE
            val y = FarmRenderer.worldY(point.row).toDouble()
            if (x + TILE < view.left || x > view.right || y + TILE < view.bottom || y > view.top) continue
            val hash = (point.row * 73856093) xor (point.col * 19349663)
            val frame = WaterAnimation.frame(clock.time, (hash.toLong().let { kotlin.math.abs(it) } % 6).toInt())
            add(style.ordinal * frameCount + frame, x.toFloat(), y.toFloat())
        }
        batch.preserve {
            // Tiles do not overlap, so all the plain ones and then all the glowing ones look the same as tile by tile.
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            drawBuckets(batch, glow = false, light = 1f)
            if (dark != 0.0) {
                batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE)
                drawBuckets(batch, glow = true, light = dark.toFloat())
            }
        }
        for (used in 0 until usedCount) bucketSizes[usedBuckets[used]] = 0
        usedCount = 0
    }
}
