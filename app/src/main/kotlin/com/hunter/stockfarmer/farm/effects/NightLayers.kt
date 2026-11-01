package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
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
    private var patterns: Array<TextureRegion>? = null

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
        val tiles =
            motion.tiles(state, now, Point(camera.position.x / TILE, (WORLD - camera.position.y) / TILE), camera.view())
        if (tiles.size == 0) return
        val color = NightLights.pulseColor(state.season)
        val red = color.red / 255f
        val green = color.green / 255f
        val blue = color.blue / 255f
        val size = FarmRenderer.TILE
        val glow = textures.glow()
        val patterns = patterns ?: PulsePatterns.regions(textures).also { patterns = it }
        batch.preserve {
            batch.additive()
            // Added light sums the same in any order, so every glow goes before every pattern. Each change of texture
            // flushes the batch into a draw call, and this makes two a frame instead of two for every glowing tile.
            for (index in 0 until tiles.size) {
                batch.setColor(red, green, blue, (tiles.intensity(index) * 0.4).toFloat())
                batch.draw(glow, tiles.col(index) * size, FarmRenderer.worldY(tiles.row(index)), size, size)
            }
            for (index in 0 until tiles.size) {
                batch.setColor(red, green, blue, (tiles.intensity(index) * tiles.twinkle(index)).toFloat())
                batch.draw(
                    patterns[tiles.pattern(index)],
                    tiles.col(index) * size,
                    FarmRenderer.worldY(tiles.row(index)),
                    size,
                    size,
                )
            }
        }
    }
}

/** The sparkle patterns of the wave, a few lit pixels each, side by side in one texture with a clear column between. */
internal object PulsePatterns {
    const val COUNT = 6
    const val SIZE = 12
    private const val LIT_PIXELS = 7
    private const val STRIDE = SIZE + 1
    const val STRIP_WIDTH = COUNT * STRIDE

    /** The pixels lit in each pattern, numbered row by row. */
    private val lit: List<Set<Int>> =
        List(COUNT) { pattern -> (0 until SIZE * SIZE).shuffled(Random(pattern)).take(LIT_PIXELS).toSet() }

    /** The color of a pixel of the strip: white where a pattern is lit, clear elsewhere. */
    fun stripPixel(
        x: Int,
        y: Int,
    ): Int {
        val col = x % STRIDE
        return if (col < SIZE && y * SIZE + col in lit[x / STRIDE]) -1 else 0
    }

    /** Where in the strip each pattern lies, in pixels from its left edge. */
    fun left(pattern: Int) = pattern * STRIDE

    fun regions(textures: TextureLibrary): Array<TextureRegion> {
        val strip = textures.generated("effects/pulse", STRIP_WIDTH, SIZE, pixel = ::stripPixel)
        return Array(COUNT) { TextureRegion(strip, left(it), 0, SIZE, SIZE) }
    }
}
