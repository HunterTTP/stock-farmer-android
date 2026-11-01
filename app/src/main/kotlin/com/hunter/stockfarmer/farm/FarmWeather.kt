package com.hunter.stockfarmer.farm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.Wind
import com.hunter.stockfarmer.farm.effects.Precipitation
import com.hunter.stockfarmer.farm.effects.RainMotion
import com.hunter.stockfarmer.farm.effects.WeatherPresentation
import com.hunter.stockfarmer.game.GameSession
import java.time.Instant

internal data class WeatherFrame(
    val drops: List<Precipitation> = emptyList(),
    val snow: Boolean = false,
)

internal val LocalFarmWeather = staticCompositionLocalOf<State<WeatherFrame>> { mutableStateOf(WeatherFrame()) }

/** Remains mounted when fishing opens. Both canvases use the same particles at the same screen positions. */
@Composable
fun FarmWeather(
    session: GameSession,
    content: @Composable () -> Unit,
) {
    val frame = remember(session) { mutableStateOf(WeatherFrame()) }
    val dimensions = remember { mutableStateOf(IntSize.Zero) }
    val motion = remember(session) { RainMotion() }
    val presentation = remember(session) { WeatherPresentation() }
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(session, owner, density) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last: Long? = null
            while (true) {
                withFrameNanos { time ->
                    val elapsed = last?.let { (time - it) / 1e9 } ?: 0.0
                    last = time
                    val now = Instant.now()
                    val look =
                        session.read {
                            presentation.update(
                                now,
                                Weather.rainAmount(now, it.clockOffset),
                                it.season == Season.winter,
                                Wind.pushesRight(now, it.clockOffset),
                            )
                        }
                    val size = dimensions.value
                    frame.value =
                        WeatherFrame(
                            motion.update(
                                elapsed.coerceIn(0.0, 0.1),
                                look.amount,
                                look.snow,
                                look.pushesRight,
                                size.width / density.toDouble(),
                                size.height / density.toDouble(),
                            ),
                            look.snow,
                        )
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

internal fun DrawScope.drawFarmWeather(frame: WeatherFrame) {
    for (drop in frame.drops) {
        val center = Offset((drop.point.x * density).toFloat(), size.height - (drop.point.y * density).toFloat())
        val alpha = drop.alpha.toFloat().coerceIn(0f, 1f)
        val width = ((if (frame.snow) 8 else 3) * drop.size * density).toFloat()
        val height = ((if (frame.snow) 8 else 28) * drop.size * density).toFloat()
        if (frame.snow) {
            val radius = width / 2
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = alpha),
                    0.35f to Color.White.copy(alpha = alpha),
                    1f to Color.White.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius,
                center,
            )
        } else {
            val tint = Color(0.3f, 0.6f, 1f)
            rotate(-drop.rotation.toFloat(), center) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0f), tint.copy(alpha = alpha)),
                        center.y - height / 2,
                        center.y + height / 2,
                    ),
                    center - Offset(width / 2, height / 2),
                    Size(width, height),
                )
            }
        }
    }
}
