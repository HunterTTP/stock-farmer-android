package com.hunter.stockfarmer.ui.theme

import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/** The swell and glow that invites a tap while a lesson points at a view. Driven by the clock, so it stops when the lesson moves on. */
object Pulse {
    const val PERIOD_SECONDS = 1.6

    /** Small enough that the swell and glow stay within the spacing around the view. */
    const val SWELL_SCALE = 0.05f
    const val GLOW_RADIUS_DP = 7f
    const val GLOW_ALPHA = 0.8f

    /** Under Reduce Motion the view holds still at half swell and does not scale. */
    const val REDUCED_MOTION_SWELL = 0.5

    /** 0 to 1 and back once a period. */
    fun swell(seconds: Double): Double = (1 - cos(seconds * 2 * PI / PERIOD_SECONDS)) / 2
}

/** True when the player has turned animations off in the system settings (Android's Reduce Motion). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Without [keepsPlace] a view that is not pulsing carries no clock at all, so nothing gets in the way of its taps. */
@Composable
fun Modifier.pulsing(
    isOn: Boolean,
    keepsPlace: Boolean = true,
): Modifier {
    if (!keepsPlace && !isOn) return this
    val reduceMotion = rememberReduceMotion()
    var nanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isOn, reduceMotion) {
        if (isOn && !reduceMotion) while (true) withFrameNanos { nanos = it }
    }
    val swell =
        when {
            !isOn -> 0.0
            reduceMotion -> Pulse.REDUCED_MOTION_SWELL
            else -> Pulse.swell(nanos / 1e9)
        }.toFloat()
    if (swell == 0f) return this
    return this
        .drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                paint.asFrameworkPaint().setShadowLayer(
                    Pulse.GLOW_RADIUS_DP.dp.toPx() * swell,
                    0f,
                    0f,
                    Theme.gold.copy(alpha = Pulse.GLOW_ALPHA * swell).toArgb(),
                )
                canvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }.scale(if (reduceMotion) 1f else 1 + Pulse.SWELL_SCALE * swell)
}

/** A short line of teaching shown beside what it points at. */
@Composable
fun LessonBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier.hudPanel(14.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        style = HudType.footnote.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
    )
}
