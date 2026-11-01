package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.hypot
import kotlin.math.roundToInt

/** GROUND goes between drawGround and collecting standing objects; other phases surround renderer.draw and the tint. */
enum class EffectPhase { BACKGROUND, GROUND, BEFORE_TINT, AFTER_TINT }

interface EffectOverlay {
    val phase: EffectPhase
    val work: PerformanceWork get() = PerformanceWork.weather

    fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    )
}

class EffectLayers internal constructor(
    val sources: List<StandingSource>,
    val overlays: List<EffectOverlay>,
    val butterflies: ButterflyLayer,
    val fireworks: FireworkLayer,
    val nightPulse: NightPulseLayer,
    val shadows: ShadowLayer,
    val photo: PhotoEffects,
    private val monitor: PerformanceMonitor? = null,
) {
    private val overlaysByPhase = EffectPhase.entries.map { phase -> overlays.filter { it.phase == phase } }

    fun draw(
        phase: EffectPhase,
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        overlaysByPhase[phase.ordinal].forEach { overlay ->
            if (monitor != null) {
                monitor.measure(overlay.work) {
                    overlay.draw(batch, state, now, camera)
                }
            } else {
                overlay.draw(batch, state, now, camera)
            }
        }
    }
}

/** Construct once on the GL thread. All effect textures are owned and disposed by [textures]. */
fun effectLayers(
    textures: TextureLibrary,
    foreground: List<EffectOverlay> = emptyList(),
    precipitation: Boolean = true,
    onGust: (pushesRight: Boolean, at: Instant) -> Unit = { _, _ -> },
    monitor: PerformanceMonitor? = null,
): EffectLayers {
    val fireflies = FireflyLayer(textures)
    val butterflies = ButterflyLayer(textures)
    val wind = WindLayer(textures, onGust)
    val fireworks = FireworkLayer(textures)
    val nightPulse = NightPulseLayer(textures)
    val shadows = ShadowLayer(textures)
    return EffectLayers(
        sources = listOf(wind, butterflies),
        overlays =
            listOf(
                SeasonSky(textures),
                WaterLayer(textures),
                nightPulse,
                shadows,
                fireflies,
                wind,
                LeafLayer(textures, wind.motion),
                RainTint(textures),
                fireflies.halos,
                BuildingGlowLayer(textures),
            ) + (if (precipitation) listOf(RainLayer(textures)) else emptyList()) + foreground + fireworks,
        butterflies = butterflies,
        fireworks = fireworks,
        nightPulse = nightPulse,
        shadows = shadows,
        photo = PhotoEffects(textures, SeasonSky(textures), WaterLayer(textures), shadows),
        monitor = monitor,
    )
}

/** The effects a farm photo keeps: they follow the photo's own camera, and shadows are finished before the shot. */
class PhotoEffects internal constructor(
    private val textures: TextureLibrary,
    private val sky: SeasonSky,
    private val water: WaterLayer,
    private val shadows: ShadowLayer,
) {
    private val rainTint = RainTint(textures)

    internal fun fresh(): PhotoEffects = PhotoEffects(textures, SeasonSky(textures), WaterLayer(textures), shadows)

    fun drawSky(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) = sky.draw(batch, state, now, camera)

    fun drawGround(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        water.draw(batch, state, now, camera)
        shadows.drawFinished(batch, state, now, camera)
    }

    /**
     * The rain's tint, the torches' glow and the rain or snow already falling, over the night's tint. The glow
     * and the rain start afresh, so each photo catches the sky full of drops at its first moment. The fireflies and butterflies a
     * photo runs for a tenth of a second have barely begun to fade in, so none are drawn.
     */
    fun drawWeather(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        camera: OrthographicCamera,
    ) {
        rainTint.draw(batch, state, now, camera)
        BuildingGlowLayer(textures).draw(batch, state, now, camera)
        RainLayer(textures) { 1f }.draw(batch, state, now, camera)
    }
}

internal data class Point(
    val x: Double,
    val y: Double,
)

internal data class View(
    val left: Double,
    val bottom: Double,
    val width: Double,
    val height: Double,
) {
    val right get() = left + width
    val top get() = bottom + height
}

internal fun OrthographicCamera.view(): View {
    val width = (viewportWidth * zoom).toDouble()
    val height = (viewportHeight * zoom).toDouble()
    return View(position.x - width / 2, position.y - height / 2, width, height)
}

/** SpriteKit limits ambient advancement to a tenth of a second, including after a pause. */
internal class EffectClock {
    private var last: Instant? = null
    var time = 0.0
        private set

    fun advance(now: Instant): Double {
        val seconds = last?.let { now.secondsSince(it).coerceIn(0.0, 0.1) } ?: 0.0
        last = now
        time += seconds
        return seconds
    }
}

internal fun darkness(
    state: GameState,
    now: Instant,
) = DayNightCycle.darkness(now, state.clockOffset)

internal fun rain(
    state: GameState,
    now: Instant,
) = Weather.rainAmount(now, state.clockOffset)

internal const val TILE = FarmRenderer.TILE.toDouble()
internal const val WORLD = FarmRenderer.WORLD.toDouble()

internal fun smooth(value: Double): Double = value * value * (3 - 2 * value)

internal fun envelope(
    age: Double,
    life: Double,
    fade: Double,
) = minOf(1.0, age / fade, (life - age) / fade).coerceAtLeast(0.0)

internal fun rgba(
    r: Int,
    g: Int,
    b: Int,
    a: Int = 255,
) = (r shl 24) or (g shl 16) or (b shl 8) or a.coerceIn(0, 255)

internal inline fun SpriteBatch.preserve(action: () -> Unit) {
    val color = packedColor
    val src = blendSrcFunc
    val dst = blendDstFunc
    val srcAlpha = blendSrcFuncAlpha
    val dstAlpha = blendDstFuncAlpha
    try {
        action()
    } finally {
        packedColor = color
        setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha)
    }
}

internal fun SpriteBatch.additive() = setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

internal fun SpriteBatch.pixel(
    textures: TextureLibrary,
    point: Point,
    size: Double,
) = draw(
    textures.white,
    (point.x - size / 2).toFloat(),
    (point.y - size / 2).toFloat(),
    size.toFloat(),
    size.toFloat(),
)

internal fun TextureLibrary.glow() =
    generated("effects/glow", 128, 128, smooth = true) { x, y ->
        val radius = (hypot(x - 63.5, y - 63.5) / 64).coerceAtMost(1.0)
        val alpha = if (radius < 0.35) 0.95 + (0.35 - 0.95) * radius / 0.35 else 0.35 * (1 - radius) / 0.65
        rgba(255, 255, 255, (alpha * 255).roundToInt())
    }

internal fun SpriteBatch.glow(
    textures: TextureLibrary,
    point: Point,
    size: Double,
) = draw(
    textures.glow(),
    (point.x - size / 2).toFloat(),
    (point.y - size / 2).toFloat(),
    size.toFloat(),
    size.toFloat(),
)
