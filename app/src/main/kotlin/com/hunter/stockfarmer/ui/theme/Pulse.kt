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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

    /** Number of soft rings that make up the halo. */
    const val GLOW_STEPS = 8

    /** Under Reduce Motion the view holds still at half swell and does not scale. */
    const val REDUCED_MOTION_SWELL = 0.5

    /** 0 to 1 and back once a period. */
    fun swell(seconds: Double): Double = (1 - cos(seconds * 2 * PI / PERIOD_SECONDS)) / 2

    /** The scale of the swell; a view under Reduce Motion does not scale. */
    fun scale(
        swell: Float,
        reduceMotion: Boolean,
    ): Float = if (reduceMotion) 1f else 1 + SWELL_SCALE * swell

    /** How far the halo reaches beyond the outline, in pixels. */
    fun glowRadius(
        swell: Float,
        density: Float,
    ): Float = GLOW_RADIUS_DP * density * swell

    /** Stroke width of ring [step] (0 is the widest, drawn first), centred on the outline so only half of it lies outside. */
    fun ringWidth(
        step: Int,
        radius: Float,
    ): Float = 2f * radius * (GLOW_STEPS - step) / GLOW_STEPS

    /** Alpha of each ring; the rings overlap, so the halo is strongest at the outline and fades outward. */
    fun ringAlpha(swell: Float): Float = GLOW_ALPHA * swell / (GLOW_STEPS / 2f)
}

/** A soft gold halo hugging the outside of [shape]. The inside of the outline is never painted, so a translucent view shows no box. */
fun Modifier.pulseGlow(
    shape: Shape,
    swell: () -> Float,
): Modifier =
    drawBehind {
        val amount = swell()
        if (amount <= 0f) return@drawBehind
        val radius = Pulse.glowRadius(amount, density)
        val path =
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
        clipPath(path, ClipOp.Difference) {
            val color = Theme.gold.copy(alpha = Pulse.ringAlpha(amount).coerceIn(0f, 1f))
            for (step in 0 until Pulse.GLOW_STEPS) {
                drawPath(path, color, style = Stroke(Pulse.ringWidth(step, radius), join = StrokeJoin.Round))
            }
        }
    }

/** True when the player has turned animations off in the system settings (Android's Reduce Motion). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Without [keepsPlace] a view that is not pulsing carries no clock at all, so nothing gets in the way of its taps. [shape] is the view's own outline. */
@Composable
fun Modifier.pulsing(
    isOn: Boolean,
    keepsPlace: Boolean = true,
    shape: Shape = RectangleShape,
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
        .pulseGlow(shape) { swell }
        .scale(Pulse.scale(swell, reduceMotion))
}

/** Highlights targets during the first-time tutorial walk through. */
@Composable
fun Modifier.lessonPulse(
    isOn: Boolean,
    keepsPlace: Boolean = true,
    shape: Shape = RectangleShape,
): Modifier = pulsing(isOn, keepsPlace, shape)

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
