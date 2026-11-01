package com.hunter.stockfarmer.farm

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.Wind
import com.hunter.stockfarmer.farm.effects.RainMotion
import com.hunter.stockfarmer.farm.effects.WeatherLook
import com.hunter.stockfarmer.farm.effects.WeatherPresentation
import com.hunter.stockfarmer.farm.effects.snowAlpha
import com.hunter.stockfarmer.game.GameSession
import java.time.Instant
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The drops in flight. One instance is reused and mutated each frame, so a frame allocates nothing. */
internal class WeatherFrame(
    val motion: RainMotion = RainMotion(),
)

/** Holds one [WeatherFrame] that is re-published each frame so only the draw phase observes the change. */
internal val LocalFarmWeather =
    staticCompositionLocalOf<State<WeatherFrame>> { mutableStateOf(WeatherFrame(), neverEqualPolicy()) }

/** How often the game state is sampled; the rain eases over minutes, so tenths of a second are invisible. */
private const val SAMPLE_SECONDS = 0.25

/** Remains mounted when fishing opens. Both canvases use the same particles at the same screen positions. */
@Composable
fun FarmWeather(
    session: GameSession,
    content: @Composable () -> Unit,
) {
    val weather = remember(session) { WeatherFrame() }
    val frame = remember(session) { mutableStateOf(weather, neverEqualPolicy()) }
    val dimensions = remember { mutableStateOf(IntSize.Zero) }
    val presentation = remember(session) { WeatherPresentation() }
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(session, owner, density) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last: Long? = null
            var sampled = SAMPLE_SECONDS
            var look = WeatherLook(0.0, false, false)
            var showing = false
            while (true) {
                withFrameNanos { time ->
                    val elapsed = last?.let { ((time - it) / 1e9).coerceIn(0.0, 0.1) } ?: 0.0
                    last = time
                    sampled += elapsed
                    if (sampled >= SAMPLE_SECONDS) {
                        sampled = 0.0
                        val now = Instant.now()
                        look =
                            session.read {
                                presentation.update(
                                    now,
                                    Weather.rainAmount(now, it.clockOffset),
                                    it.season == Season.winter,
                                    Wind.pushesRight(now, it.clockOffset),
                                )
                            }
                    }
                    val size = dimensions.value
                    val count =
                        weather.motion.update(
                            elapsed,
                            look.amount,
                            look.snow,
                            look.pushesRight,
                            size.width / density.toDouble(),
                            size.height / density.toDouble(),
                        )
                    if (count > 0 || showing) frame.value = weather
                    showing = count > 0
                }
            }
        }
    }
    CompositionLocalProvider(LocalFarmWeather provides frame) {
        Box(Modifier.fillMaxSize().onSizeChanged { dimensions.value = it }) {
            Canvas(Modifier.fillMaxSize()) { drawFarmWeather(frame.value) }
            content()
        }
    }
}

private const val RAIN_WIDTH = 3
private const val RAIN_HEIGHT = 28
private const val SNOW_SIZE = 8
private const val SNOW_TEXELS = 32

/** The streak fades in downward, tinted blue when drawn. */
private val rainSprite: ImageBitmap by lazy {
    val pixels = IntArray(RAIN_WIDTH * RAIN_HEIGHT) { index -> argb((index / RAIN_WIDTH) * 255.0 / (RAIN_HEIGHT - 1)) }
    Bitmap.createBitmap(pixels, RAIN_WIDTH, RAIN_HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** A solid core with a soft edge, the same falloff as the farm's snow texture. */
private val snowSprite: ImageBitmap by lazy {
    val half = SNOW_TEXELS / 2.0
    val pixels =
        IntArray(SNOW_TEXELS * SNOW_TEXELS) { index ->
            val x = index % SNOW_TEXELS + 0.5
            val y = index / SNOW_TEXELS + 0.5
            argb(snowAlpha(hypot(x - half, y - half) / half) * 255)
        }
    Bitmap.createBitmap(pixels, SNOW_TEXELS, SNOW_TEXELS, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val rainTint = ColorFilter.tint(Color(0.3f, 0.6f, 1f), BlendMode.Modulate)

private fun argb(alpha: Double): Int = (alpha.roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF

internal fun DrawScope.drawFarmWeather(frame: WeatherFrame) {
    val motion = frame.motion
    val count = motion.count
    if (count == 0) return
    val snow = motion.snow
    val sprite = if (snow) snowSprite else rainSprite
    val halfWidth = sprite.width / 2f
    val halfHeight = sprite.height / 2f
    val baseWidth = if (snow) SNOW_SIZE else RAIN_WIDTH
    val baseHeight = if (snow) SNOW_SIZE else RAIN_HEIGHT
    val filter = if (snow) null else rainTint
    val turn = -motion.rotation.toFloat()
    val unit = density
    val bottom = size.height
    for (i in 0 until count) {
        val width = baseWidth * motion.size[i] * unit
        val height = baseHeight * motion.size[i] * unit
        val centerX = motion.x[i] * unit
        val centerY = bottom - motion.y[i] * unit
        val alpha = motion.alpha[i].coerceIn(0f, 1f)
        withTransform({
            translate(centerX, centerY)
            rotate(turn, Offset.Zero)
            scale(width / sprite.width, height / sprite.height, Offset.Zero)
            translate(-halfWidth, -halfHeight)
        }) {
            drawImage(sprite, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Low)
        }
    }
}
